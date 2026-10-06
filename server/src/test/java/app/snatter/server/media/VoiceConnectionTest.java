package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import app.snatter.server.media.IceConnection.Candidate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VoiceConnectionTest {

    private static final Pattern CANDIDATE =
        Pattern.compile("a=candidate:(\\S+) 1 udp (\\d+) (\\S+) (\\d+) typ (\\S+).*");

    @Inject
    MediaPort port;

    @Test
    void aPeerThatAnswersTheOfferConnectsAndItsVoiceArrives() throws Exception {
        try (VoiceConnection voice = new VoiceConnection(port.open(), 64000)) {
            String offer = voice.offer();
            try (IcePeer ice = new IcePeer(attribute(offer, "ice-ufrag"), attribute(offer, "ice-pwd"),
                candidates(offer))) {
                DtlsPeer dtls = new DtlsPeer(ice, SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM);
                BlockingQueue<RtpPacket> received = new LinkedBlockingQueue<>();
                voice.accept(answer(ice, dtls), received::add);

                dtls.server = ice.connect().getRemoteCandidate().getTransportAddress();
                dtls.handshake(attribute(offer, "fingerprint").substring("sha-256 ".length()));
                voice.ready().toCompletableFuture().get(10, SECONDS);

                byte[] sent = DtlsPeer.rtp(0x1234_5678, "hello");
                dtls.send(sent);
                RtpPacket arrived = received.poll(5, SECONDS);
                assertArrayEquals(sent, Arrays.copyOfRange(arrived.getBuffer(), arrived.getOffset(),
                    arrived.getOffset() + arrived.getLength()));
            }
        }
    }

    /** An answer as a browser with a microphone writes one, shaped like Chrome's. */
    private static String answer(IcePeer ice, DtlsPeer dtls) throws Exception {
        return """
            v=0
            o=- 4611731400430051336 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=ice-ufrag:%s
            a=ice-pwd:%s
            a=fingerprint:sha-256 %s
            a=setup:active
            a=mid:0
            a=sendonly
            a=rtcp-mux
            a=rtpmap:111 opus/48000/2
            a=fmtp:111 minptime=10;useinbandfec=1
            a=ssrc:305419896 cname:stand-in
            """.formatted(ice.agent.getLocalUfrag(), ice.agent.getLocalPassword(), dtls.fingerprint())
            .replace("\n", "\r\n");
    }

    private static String attribute(String sdp, String name) {
        return sdp.lines().filter(line -> line.startsWith("a=" + name + ":")).findFirst().orElseThrow()
            .substring(name.length() + 3);
    }

    private static List<Candidate> candidates(String sdp) {
        List<Candidate> candidates = new ArrayList<>();
        for (String line : sdp.lines().toList()) {
            Matcher m = CANDIDATE.matcher(line);
            if (m.matches()) {
                candidates.add(new Candidate(m.group(1), Long.parseLong(m.group(2)), m.group(5),
                    new InetSocketAddress(m.group(3), Integer.parseInt(m.group(4))), null));
            }
        }
        return candidates;
    }
}
