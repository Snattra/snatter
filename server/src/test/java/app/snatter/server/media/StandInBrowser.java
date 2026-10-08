package app.snatter.server.media;

import app.snatter.server.media.IceConnection.Candidate;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.tls.SRTPProtectionProfile;

/**
 * Stands in for a browser in voice: it reads the server's offers with its
 * own parsing, answers them as Chrome would, and connects ICE and DTLS.
 * Public for the gateway's tests.
 */
public final class StandInBrowser implements AutoCloseable {

    private static final Pattern CANDIDATE =
        Pattern.compile("a=candidate:(\\S+) 1 udp (\\d+) (\\S+) (\\d+) typ (\\S+).*");

    private final String offer;
    private final IcePeer ice;
    private final DtlsPeer dtls;

    /** From the first offer of a connection. */
    public StandInBrowser(String offer) throws IOException {
        this.offer = offer;
        ice = new IcePeer(attribute(offer, "ice-ufrag"), attribute(offer, "ice-pwd"), candidates(offer));
        dtls = new DtlsPeer(ice, SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM);
    }

    /** The answer to the first offer. */
    public String answer() throws Exception {
        return answer(offer);
    }

    /**
     * The answer of a browser with a microphone, shaped like Chrome's: line
     * by line, sending its voice where the server receives, receiving where
     * the server sends, and inactive where the server is.
     */
    public String answer(String offer) throws Exception {
        List<String> mids = new ArrayList<>();
        StringBuilder lines = new StringBuilder();
        for (List<String> section : sections(offer)) {
            String mid = attribute(section, "mid");
            mids.add(mid);
            String direction = switch (direction(section)) {
                case "recvonly" -> "sendonly";
                case "sendonly" -> "recvonly";
                default -> "inactive";
            };
            lines.append("""
                m=audio 9 UDP/TLS/RTP/SAVPF 111
                c=IN IP4 0.0.0.0
                a=ice-ufrag:%s
                a=ice-pwd:%s
                a=fingerprint:sha-256 %s
                a=setup:active
                a=mid:%s
                a=%s
                a=rtcp-mux
                a=rtpmap:111 opus/48000/2
                a=fmtp:111 minptime=10;useinbandfec=1
                """.formatted(ice.agent.getLocalUfrag(), ice.agent.getLocalPassword(), dtls.fingerprint(), mid,
                direction));
            if (direction.equals("sendonly")) {
                lines.append("a=ssrc:305419896 cname:stand-in\n");
            }
        }
        return ("""
            v=0
            o=- 4611731400430051336 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE %s
            """.formatted(String.join(" ", mids)) + lines).replace("\n", "\r\n");
    }

    /** Runs ICE and the DTLS handshake, checking the server shows the certificate its offer named. */
    public void connect() throws Exception {
        dtls.server = ice.connect().getRemoteCandidate().getTransportAddress();
        dtls.handshake(attribute(offer, "fingerprint").substring("sha-256 ".length()));
    }

    /** Encrypts an RTP packet and sends it to the server. */
    public void send(byte[] rtp) throws Exception {
        dtls.send(rtp);
    }

    /** The next RTP packet the server sends, decrypted, or null when none comes in time. */
    public byte[] receive(int timeoutMillis) throws Exception {
        return dtls.receive(timeoutMillis);
    }

    /** An RTP packet from the given source, with a sequence number and text for its payload. */
    public static byte[] rtp(int ssrc, int sequence, String payload) {
        return DtlsPeer.rtp(ssrc, sequence, payload);
    }

    /** The source an offer carries the member's voice as, or null when it carries no line for them. */
    public static Integer ssrcOf(String offer, String member) {
        for (List<String> section : sections(offer)) {
            if (section.stream().anyMatch(line -> line.startsWith("a=msid:" + member + " "))) {
                String ssrc = attribute(section, "ssrc").split(" ")[0];
                return Integer.parseUnsignedInt(ssrc);
            }
        }
        return null;
    }

    @Override
    public void close() {
        ice.close();
    }

    private static List<List<String>> sections(String sdp) {
        List<List<String>> sections = new ArrayList<>();
        for (String line : sdp.lines().toList()) {
            if (line.startsWith("m=")) {
                sections.add(new ArrayList<>());
            }
            if (!sections.isEmpty()) {
                sections.getLast().add(line);
            }
        }
        return sections;
    }

    private static String direction(List<String> section) {
        for (String direction : List.of("sendrecv", "sendonly", "recvonly", "inactive")) {
            if (section.contains("a=" + direction)) {
                return direction;
            }
        }
        return "sendrecv";
    }

    private static String attribute(String sdp, String name) {
        return attribute(sdp.lines().toList(), name);
    }

    private static String attribute(List<String> lines, String name) {
        return lines.stream().filter(line -> line.startsWith("a=" + name + ":")).findFirst().orElseThrow()
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
