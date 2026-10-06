package app.snatter.server.media;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Hashtable;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.ExporterLabel;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.util.Arrays;

/**
 * The server's side of a DTLS handshake that exists only to agree on SRTP
 * keys (RFC 5764). The peer must show the certificate whose fingerprint it
 * named in signalling, which ties the keys to whoever signalled.
 */
final class DtlsServer extends DefaultTlsServer {

    /** The SRTP protection profiles the server takes, preferred first. Both use 16-byte keys. */
    enum Profile {
        /** RFC 7714. */
        AEAD_AES_128_GCM(SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM, 12),
        /** RFC 5764's default, which every browser offers. */
        AES128_CM_HMAC_SHA1_80(SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80, 14);

        static final int KEY_LENGTH = 16;

        final int id;
        final int saltLength;

        Profile(int id, int saltLength) {
            this.id = id;
            this.saltLength = saltLength;
        }
    }

    /** What the handshake agreed on: the peer's key for what it sends, and the server's for what it sends. */
    record SrtpKeys(Profile profile, byte[] peerKey, byte[] peerSalt, byte[] ownKey, byte[] ownSalt) {
    }

    private final DtlsIdentity identity;
    private final byte[] peerFingerprint;
    private Profile profile;
    private SrtpKeys keys;

    /** The peer's fingerprint is the SHA-256 of its certificate. */
    DtlsServer(DtlsIdentity identity, byte[] peerFingerprint) {
        super(new BcTlsCrypto(new SecureRandom()));
        this.identity = identity;
        this.peerFingerprint = peerFingerprint;
    }

    /** The keys, once the handshake is done. */
    SrtpKeys keys() {
        return keys;
    }

    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return ProtocolVersion.DTLSv12.only();
    }

    /** Those for the server's ECDSA certificate. */
    @Override
    protected int[] getSupportedCipherSuites() {
        return new int[] {
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
        };
    }

    /** A peer that stops halfway fails the handshake rather than holding it open. */
    @Override
    public int getHandshakeTimeoutMillis() {
        return 10_000;
    }

    @Override
    protected TlsCredentialedSigner getECDSASignerCredentials() throws IOException {
        BcTlsCrypto crypto = (BcTlsCrypto) getCrypto();
        TlsCertificate certificate = crypto.createCertificate(identity.certificate());
        return new BcDefaultTlsCredentialedSigner(new TlsCryptoParameters(context), crypto, identity.privateKey(),
            new Certificate(new TlsCertificate[] {certificate}),
            SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
    }

    @Override
    public CertificateRequest getCertificateRequest() {
        return new CertificateRequest(new short[] {ClientCertificateType.ecdsa_sign, ClientCertificateType.rsa_sign},
            TlsUtils.getDefaultSupportedSignatureAlgorithms(context), null);
    }

    @Override
    public void notifyClientCertificate(Certificate certificate) throws IOException {
        if (certificate.isEmpty()
            || !MessageDigest.isEqual(DtlsIdentity.sha256(certificate.getCertificateAt(0).getEncoded()),
                peerFingerprint)) {
            throw new TlsFatalAlert(AlertDescription.bad_certificate, "Not the certificate signalling named");
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void processClientExtensions(Hashtable clientExtensions) throws IOException {
        super.processClientExtensions(clientExtensions);
        UseSRTPData offered = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);
        if (offered != null) {
            for (Profile candidate : Profile.values()) {
                if (Arrays.contains(offered.getProtectionProfiles(), candidate.id)) {
                    profile = candidate;
                    break;
                }
            }
        }
        if (profile == null) {
            throw new TlsFatalAlert(AlertDescription.handshake_failure, "No SRTP protection profile in common");
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Hashtable getServerExtensions() throws IOException {
        Hashtable extensions = super.getServerExtensions();
        TlsSRTPUtils.addUseSRTPExtension(extensions, new UseSRTPData(new int[] {profile.id}, TlsUtils.EMPTY_BYTES));
        return extensions;
    }

    @Override
    public void notifyHandshakeComplete() throws IOException {
        super.notifyHandshakeComplete();
        int key = Profile.KEY_LENGTH;
        int salt = profile.saltLength;
        // RFC 5764 4.2: the client's key, the server's key, the client's salt, the server's salt.
        byte[] material = context.exportKeyingMaterial(ExporterLabel.dtls_srtp, null, 2 * (key + salt));
        keys = new SrtpKeys(profile,
            Arrays.copyOfRange(material, 0, key),
            Arrays.copyOfRange(material, 2 * key, 2 * key + salt),
            Arrays.copyOfRange(material, key, 2 * key),
            Arrays.copyOfRange(material, 2 * key + salt, 2 * (key + salt)));
    }
}
