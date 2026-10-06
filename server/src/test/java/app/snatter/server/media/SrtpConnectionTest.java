package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Hashtable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.ExporterLabel;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.ice4j.TransportAddress;
import org.ice4j.ice.CandidatePair;
import org.jitsi.srtp.SrtpContextFactory;
import org.jitsi.srtp.SrtpErrorStatus;
import org.jitsi.srtp.SrtpPolicy;
import org.jitsi.utils.logging2.LoggerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class SrtpConnectionTest {

    @Inject
    MediaPort port;

    @ParameterizedTest
    @ValueSource(ints = {SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM, SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80})
    void aPeerWithTheNamedCertificateGetsKeysForBothWays(int profile) throws Exception {
        try (IceConnection ice = port.open(); IcePeer icePeer = new IcePeer(ice);
             SrtpConnection srtp = new SrtpConnection()) {
            DtlsPeer peer = new DtlsPeer(icePeer, profile);
            BlockingQueue<RtpPacket> received = new LinkedBlockingQueue<>();
            srtp.start(connect(ice, icePeer, peer), peer.fingerprint(), received::add);
            peer.handshake(srtp.fingerprint());
            srtp.ready().toCompletableFuture().get(10, SECONDS);

            byte[] sent = rtp(0x1234_5678, "from the peer");
            peer.send(sent);
            RtpPacket arrived = received.poll(5, SECONDS);
            assertArrayEquals(sent, Arrays.copyOfRange(arrived.getBuffer(), arrived.getOffset(),
                arrived.getOffset() + arrived.getLength()));

            byte[] back = rtp(0x9abc_def0, "from the server");
            srtp.send(new RtpPacket(back.clone(), 0, back.length));
            assertArrayEquals(back, peer.receive());
        }
    }

    @Test
    void aPeerWithAnotherCertificateIsTurnedAway() throws Exception {
        try (IceConnection ice = port.open(); IcePeer icePeer = new IcePeer(ice);
             SrtpConnection srtp = new SrtpConnection()) {
            DtlsPeer peer = new DtlsPeer(icePeer, SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM);
            srtp.start(connect(ice, icePeer, peer), DtlsIdentity.generate().fingerprint(), packet -> { });
            assertThrows(IOException.class, () -> peer.handshake(srtp.fingerprint()));
            ExecutionException failed = assertThrows(ExecutionException.class,
                () -> srtp.ready().toCompletableFuture().get(10, SECONDS));
            TlsFatalAlert alert = assertInstanceOf(TlsFatalAlert.class, failed.getCause());
            assertEquals(AlertDescription.bad_certificate, alert.getAlertDescription());
        }
    }

    /** Runs ICE, and points the peer's DTLS at the address the checks found. */
    private static IceConnection.Path connect(IceConnection ice, IcePeer icePeer, DtlsPeer peer) throws Exception {
        ice.start(icePeer.agent.getLocalUfrag(), icePeer.agent.getLocalPassword());
        CandidatePair pair = icePeer.connect();
        peer.server = pair.getRemoteCandidate().getTransportAddress();
        return ice.connected().toCompletableFuture().get(10, SECONDS);
    }

    /** An RTP packet: version 2, Opus's usual payload type, sequence number 1, timestamp 0. */
    private static byte[] rtp(int ssrc, String payload) {
        byte[] text = payload.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[12 + text.length];
        packet[0] = (byte) 0x80;
        packet[1] = 111;
        packet[3] = 1;
        packet[8] = (byte) (ssrc >>> 24);
        packet[9] = (byte) (ssrc >>> 16);
        packet[10] = (byte) (ssrc >>> 8);
        packet[11] = (byte) ssrc;
        System.arraycopy(text, 0, packet, 12, text.length);
        return packet;
    }

    /**
     * Stands in for a browser's DTLS and SRTP: the DTLS client, offering one
     * SRTP protection profile. Written apart from the server's code, so the
     * two agree only if both follow RFC 5764.
     */
    private static final class DtlsPeer {

        private static final org.jitsi.utils.logging2.Logger SRTP_LOG = new LoggerImpl(SrtpContextFactory.class.getName());

        private final DatagramSocket socket;
        private final int profile;
        private final DtlsIdentity identity = DtlsIdentity.generate();
        private TransportAddress server;
        private SrtpContextFactory sending;
        private SrtpContextFactory receiving;

        DtlsPeer(IcePeer ice, int profile) {
            this.socket = ice.component.getSocket();
            this.profile = profile;
        }

        String fingerprint() throws Exception {
            return fingerprintOf(identity.certificate());
        }

        static String fingerprintOf(byte[] certificate) throws Exception {
            return HexFormat.ofDelimiter(":").withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate));
        }

        /** Runs the handshake, checking the server shows the certificate it named. */
        void handshake(String serverFingerprint) throws IOException {
            Client client = new Client(serverFingerprint);
            new DTLSClientProtocol().connect(client, new Transport());
            boolean gcm = profile == SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM;
            int salt = gcm ? 12 : 14;
            SrtpPolicy policy = gcm
                ? new SrtpPolicy(SrtpPolicy.AESGCM_ENCRYPTION, 16, SrtpPolicy.NULL_AUTHENTICATION, 0, 16, salt)
                : new SrtpPolicy(SrtpPolicy.AESCM_ENCRYPTION, 16, SrtpPolicy.HMACSHA1_AUTHENTICATION, 20, 10, salt);
            // RFC 5764 4.2: the client's key, the server's key, the client's salt, the server's salt.
            byte[] m = client.material;
            sending = new SrtpContextFactory(true, Arrays.copyOfRange(m, 0, 16),
                Arrays.copyOfRange(m, 32, 32 + salt), policy, policy, SRTP_LOG);
            receiving = new SrtpContextFactory(false, Arrays.copyOfRange(m, 16, 32),
                Arrays.copyOfRange(m, 32 + salt, 32 + 2 * salt), policy, policy, SRTP_LOG);
        }

        void send(byte[] rtp) throws Exception {
            RtpPacket packet = new RtpPacket(Arrays.copyOf(rtp, rtp.length + 16), 0, rtp.length);
            assertEquals(SrtpErrorStatus.OK, sending.deriveContext(packet.ssrc(), 0).transformPacket(packet));
            socket.send(new DatagramPacket(packet.getBuffer(), packet.getOffset(), packet.getLength(), server));
        }

        byte[] receive() throws Exception {
            byte[] buffer = new byte[1500];
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            socket.setSoTimeout(5000);
            socket.receive(datagram);
            RtpPacket packet = new RtpPacket(buffer, 0, datagram.getLength());
            assertEquals(SrtpErrorStatus.OK,
                receiving.deriveContext(packet.ssrc(), 0).reverseTransformPacket(packet, false));
            return Arrays.copyOf(buffer, packet.getLength());
        }

        private final class Client extends DefaultTlsClient {

            private final String serverFingerprint;
            byte[] material;

            Client(String serverFingerprint) {
                super(new BcTlsCrypto(new SecureRandom()));
                this.serverFingerprint = serverFingerprint;
            }

            @Override
            protected ProtocolVersion[] getSupportedVersions() {
                return ProtocolVersion.DTLSv12.only();
            }

            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Hashtable getClientExtensions() throws IOException {
                Hashtable extensions = super.getClientExtensions();
                TlsSRTPUtils.addUseSRTPExtension(extensions, new UseSRTPData(new int[] {profile}, TlsUtils.EMPTY_BYTES));
                return extensions;
            }

            @Override
            public TlsAuthentication getAuthentication() {
                return new TlsAuthentication() {
                    @Override
                    public void notifyServerCertificate(TlsServerCertificate certificate) throws IOException {
                        try {
                            byte[] der = certificate.getCertificate().getCertificateAt(0).getEncoded();
                            if (!fingerprintOf(der).equals(serverFingerprint)) {
                                throw new TlsFatalAlert(AlertDescription.bad_certificate);
                            }
                        } catch (IOException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new IOException(e);
                        }
                    }

                    @Override
                    public TlsCredentials getClientCredentials(CertificateRequest request) throws IOException {
                        BcTlsCrypto crypto = (BcTlsCrypto) getCrypto();
                        TlsCertificate certificate = crypto.createCertificate(identity.certificate());
                        return new BcDefaultTlsCredentialedSigner(new TlsCryptoParameters(context), crypto,
                            identity.privateKey(), new Certificate(new TlsCertificate[] {certificate}),
                            SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
                    }
                };
            }

            @Override
            public void notifyHandshakeComplete() throws IOException {
                super.notifyHandshakeComplete();
                int salt = profile == SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM ? 12 : 14;
                material = context.exportKeyingMaterial(ExporterLabel.dtls_srtp, null, 2 * (16 + salt));
            }
        }

        /** DTLS straight over the peer's socket, which carries nothing else until the handshake is done. */
        private final class Transport implements DatagramTransport {

            @Override
            public int getReceiveLimit() {
                return 1500;
            }

            @Override
            public int getSendLimit() {
                return 1200;
            }

            @Override
            public int receive(byte[] buffer, int offset, int length, int waitMillis) throws IOException {
                DatagramPacket datagram = new DatagramPacket(buffer, offset, length);
                socket.setSoTimeout(waitMillis);
                try {
                    socket.receive(datagram);
                } catch (SocketTimeoutException e) {
                    return -1;
                }
                return datagram.getLength();
            }

            @Override
            public void send(byte[] buffer, int offset, int length) throws IOException {
                socket.send(new DatagramPacket(buffer, offset, length, server));
            }

            @Override
            public void close() {
            }
        }
    }
}
