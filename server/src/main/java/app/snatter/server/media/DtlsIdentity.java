package app.snatter.server.media;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * The server's certificate for one connection, and its key. It is
 * self-signed: the peer trusts it because its fingerprint is the one the
 * server named in signalling, not because anyone vouches for it.
 */
record DtlsIdentity(byte[] certificate, AsymmetricKeyParameter privateKey) {

    /** How SDP writes a fingerprint (RFC 8122): hex bytes separated by colons. */
    static final HexFormat FINGERPRINT_FORMAT = HexFormat.ofDelimiter(":").withUpperCase();

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A fresh ECDSA P-256 key and certificate, the kind browsers make for themselves. */
    static DtlsIdentity generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keys = generator.generateKeyPair();
            X500Name name = new X500Name("CN=snatter");
            // The peer sees it once, in the handshake moments after it is made;
            // the days either side allow for clocks that are off.
            Instant now = Instant.now();
            byte[] certificate = new JcaX509v3CertificateBuilder(name, new BigInteger(64, RANDOM).add(BigInteger.ONE),
                Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(Duration.ofDays(30))), name,
                keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate()))
                .getEncoded();
            return new DtlsIdentity(certificate, PrivateKeyFactory.createKey(keys.getPrivate().getEncoded()));
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new IllegalStateException("Could not make a DTLS certificate", e);
        }
    }

    /** The certificate's SHA-256 fingerprint, for signalling. */
    String fingerprint() {
        return FINGERPRINT_FORMAT.formatHex(sha256(certificate));
    }

    static byte[] sha256(byte[] certificate) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(certificate);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
