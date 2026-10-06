package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsFatalAlertReceived;
import org.ice4j.ice.CandidatePair;
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

            byte[] sent = DtlsPeer.rtp(0x1234_5678, "from the peer");
            peer.send(sent);
            RtpPacket arrived = received.poll(5, SECONDS);
            assertArrayEquals(sent, Arrays.copyOfRange(arrived.getBuffer(), arrived.getOffset(),
                arrived.getOffset() + arrived.getLength()));

            byte[] back = DtlsPeer.rtp(0x9abc_def0, "from the server");
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
            TlsFatalAlertReceived told = assertThrows(TlsFatalAlertReceived.class,
                () -> peer.handshake(srtp.fingerprint()));
            assertEquals(AlertDescription.bad_certificate, told.getAlertDescription());
            ExecutionException failed = assertThrows(ExecutionException.class,
                () -> srtp.ready().toCompletableFuture().get(10, SECONDS));
            TlsFatalAlert alert = assertInstanceOf(TlsFatalAlert.class, failed.getCause());
            assertEquals(AlertDescription.bad_certificate, alert.getAlertDescription());
        }
    }

    @Test
    void aPeerSendsFromAFewSourcesAtMost() throws Exception {
        try (IceConnection ice = port.open(); IcePeer icePeer = new IcePeer(ice);
             SrtpConnection srtp = new SrtpConnection()) {
            DtlsPeer peer = new DtlsPeer(icePeer, SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM);
            BlockingQueue<RtpPacket> received = new LinkedBlockingQueue<>();
            srtp.start(connect(ice, icePeer, peer), peer.fingerprint(), received::add);
            peer.handshake(srtp.fingerprint());
            srtp.ready().toCompletableFuture().get(10, SECONDS);

            for (int ssrc = 1; ssrc <= SrtpConnection.MAX_SOURCES + 1; ssrc++) {
                peer.send(DtlsPeer.rtp(ssrc, "source " + ssrc));
            }
            for (int ssrc = 1; ssrc <= SrtpConnection.MAX_SOURCES; ssrc++) {
                assertEquals(ssrc, received.poll(5, SECONDS).ssrc());
            }
            assertNull(received.poll(500, MILLISECONDS));
        }
    }

    /** Runs ICE, and points the peer's DTLS at the address the checks found. */
    private static IceConnection.Path connect(IceConnection ice, IcePeer icePeer, DtlsPeer peer) throws Exception {
        ice.start(icePeer.agent.getLocalUfrag(), icePeer.agent.getLocalPassword());
        CandidatePair pair = icePeer.connect();
        peer.server = pair.getRemoteCandidate().getTransportAddress();
        return ice.connected().toCompletableFuture().get(10, SECONDS);
    }

}
