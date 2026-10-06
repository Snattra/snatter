package app.snatter.server.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.snatter.server.media.IceConnection.Candidate;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class SdpTest {

    private static final String FINGERPRINT =
        "4A:AD:B9:B1:3F:82:18:3B:54:02:12:DF:3E:5D:49:6B:19:E5:7C:AB:3D:E6:51:B5:A6:3D:91:D3:A3:2A:8D:1B";

    @Test
    void theOfferAsksForTheMembersVoiceOnOneLine() throws Exception {
        InetSocketAddress local = new InetSocketAddress("192.0.2.1", 8080);
        List<Candidate> candidates = List.of(
            new Candidate("1", 2130706431, "host", local, null),
            new Candidate("2", 1694498815, "srflx", new InetSocketAddress("203.0.113.7", 8080), local),
            new Candidate("3", 2130706175, "host",
                new InetSocketAddress(InetAddress.getByName("2001:db8::1%1"), 8080), null));

        String offer = Sdp.offer("ufrag", "a-password-of-24-chars!!", FINGERPRINT, candidates, 64000);

        assertEquals("""
            v=0
            o=- SESSION 1 IN IP4 0.0.0.0
            s=-
            t=0 0
            a=group:BUNDLE 0
            a=ice-lite
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=ice-ufrag:ufrag
            a=ice-pwd:a-password-of-24-chars!!
            a=fingerprint:sha-256 %s
            a=setup:actpass
            a=mid:0
            a=recvonly
            a=rtcp-mux
            a=rtpmap:111 opus/48000/2
            a=fmtp:111 minptime=10;useinbandfec=1;maxaveragebitrate=64000
            a=candidate:1 1 udp 2130706431 192.0.2.1 8080 typ host
            a=candidate:2 1 udp 1694498815 203.0.113.7 8080 typ srflx raddr 192.0.2.1 rport 8080
            a=candidate:3 1 udp 2130706175 2001:db8:0:0:0:0:0:1 8080 typ host
            a=end-of-candidates
            """.formatted(FINGERPRINT).replace("\n", "\r\n"),
            offer.replaceFirst("o=- \\d+ ", "o=- SESSION "));
    }

    @Test
    void anAnswerShapedLikeChromesGivesItsCredentialsAndFingerprint() throws Exception {
        String answer = """
            v=0
            o=- 4611731400430051336 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE 0
            a=extmap-allow-mixed
            a=msid-semantic: WMS 5d1b0e1c-0d51-4f0e-9f3c-4b8f43b5a9a1
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=rtcp:9 IN IP4 0.0.0.0
            a=ice-ufrag:Wq4B
            a=ice-pwd:9x2LwNfPk7l1aBnqTq0aZx3T
            a=ice-options:trickle
            a=fingerprint:sha-256 %s
            a=setup:active
            a=mid:0
            a=sendonly
            a=msid:5d1b0e1c-0d51-4f0e-9f3c-4b8f43b5a9a1 0a6c5b0e-6f8f-4a52-8f45-0d43c1d6e2b7
            a=rtcp-mux
            a=rtpmap:111 opus/48000/2
            a=fmtp:111 minptime=10;useinbandfec=1
            a=ssrc:2109014017 cname:Yz8cQ1w3nY0pJk2a
            """.formatted(FINGERPRINT).replace("\n", "\r\n");

        assertEquals(new Sdp.Answer("Wq4B", "9x2LwNfPk7l1aBnqTq0aZx3T", FINGERPRINT), Sdp.answer(answer));
    }

    @Test
    void anAnswerShapedLikeFirefoxsHasItsFingerprintAboveTheMLine() throws Exception {
        String answer = """
            v=0
            o=mozilla...THIS_IS_SDPARTA-99.0 7031717148151862043 0 IN IP4 0.0.0.0
            s=-
            t=0 0
            a=fingerprint:sha-256 %s
            a=group:BUNDLE 0
            a=ice-options:trickle
            a=msid-semantic:WMS *
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=sendonly
            a=fmtp:111 maxplaybackrate=48000;stereo=1;useinbandfec=1
            a=ice-pwd:3b6c0b2d9e5a4f1c8d7e6a5b4c3d2e1f
            a=ice-ufrag:0e1f2a3b
            a=mid:0
            a=rtcp-mux
            a=rtpmap:111 opus/48000/2
            a=setup:active
            a=ssrc:3510681183 cname:{6d1c2f2a-8b1e-4c55-9a0e-2f4b1d3c5e6f}
            """.formatted(FINGERPRINT.toLowerCase()).replace("\n", "\r\n");

        assertEquals(new Sdp.Answer("0e1f2a3b", "3b6c0b2d9e5a4f1c8d7e6a5b4c3d2e1f", FINGERPRINT.toLowerCase()),
            Sdp.answer(answer));
    }

    @Test
    void anAnswerTheServerCannotConnectWithIsRefused() {
        String good = answer("a=setup:active", "a=fingerprint:sha-256 " + FINGERPRINT);
        assertRefused(good.replace("a=setup:active", "a=setup:passive"), "Expected a=setup:active, got passive");
        assertRefused(good.replace("m=audio 9 ", "m=audio 0 "), "The audio m-line is turned down");
        assertRefused(good.replace("a=ice-pwd:password\r\n", ""), "No ICE credentials");
        assertRefused(good.replace("sha-256 " + FINGERPRINT, "sha-1 4A:AD:B9:B1:3F:82:18:3B:54:02:12:DF:3E:5D:49:6B:19:E5:7C:AB"),
            "No SHA-256 fingerprint");
        assertRefused(good.replace(FINGERPRINT, "4A:AD"), "Not a SHA-256 fingerprint: 4A:AD");
        assertRefused(good + "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n", "Expected 1 m-line, got 2");
    }

    private static String answer(String... attributes) {
        return "v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
            + "a=ice-ufrag:ufrag\r\na=ice-pwd:password\r\n" + String.join("\r\n", attributes) + "\r\n";
    }

    private static void assertRefused(String answer, String why) {
        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> Sdp.answer(answer));
        assertEquals(why, refused.getMessage());
    }
}
