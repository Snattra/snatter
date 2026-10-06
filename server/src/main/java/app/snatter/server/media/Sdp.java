package app.snatter.server.media;

import app.snatter.server.media.IceConnection.Candidate;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The SDP the server offers and the little it needs from the answer
 * (RFC 8829, JSEP). The offer has one audio m-line, on which the peer sends
 * its voice; the server decides everything else, so the answer only has to
 * say how to reach the peer and which certificate it will show.
 */
final class Sdp {

    /** What the server needs from an answer: the peer's ICE credentials and SHA-256 fingerprint. */
    record Answer(String iceUfrag, String icePassword, String fingerprint) {
    }

    /** Opus, at the payload type browsers use for it. */
    private static final int OPUS = 111;

    /**
     * The offer. ICE lite tells the peer to lead the checks, which it does
     * anyway as the controlling side. {@code actpass} is what an offer must
     * say (RFC 8842); browsers answer {@code active}. The bitrate caps what
     * the peer's encoder sends.
     */
    static String offer(String iceUfrag, String icePassword, String fingerprint, List<Candidate> candidates,
                        int bitrate) {
        StringBuilder sdp = new StringBuilder();
        line(sdp, "v=0");
        line(sdp, "o=- " + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE) + " 1 IN IP4 0.0.0.0");
        line(sdp, "s=-");
        line(sdp, "t=0 0");
        line(sdp, "a=group:BUNDLE 0");
        line(sdp, "a=ice-lite");
        line(sdp, "m=audio 9 UDP/TLS/RTP/SAVPF " + OPUS);
        line(sdp, "c=IN IP4 0.0.0.0");
        line(sdp, "a=ice-ufrag:" + iceUfrag);
        line(sdp, "a=ice-pwd:" + icePassword);
        line(sdp, "a=fingerprint:sha-256 " + fingerprint);
        line(sdp, "a=setup:actpass");
        line(sdp, "a=mid:0");
        line(sdp, "a=recvonly");
        line(sdp, "a=rtcp-mux");
        line(sdp, "a=rtpmap:" + OPUS + " opus/48000/2");
        line(sdp, "a=fmtp:" + OPUS + " minptime=10;useinbandfec=1;maxaveragebitrate=" + bitrate);
        for (Candidate c : candidates) {
            String related = c.related() == null ? ""
                : " raddr " + host(c.related()) + " rport " + c.related().getPort();
            line(sdp, "a=candidate:" + c.foundation() + " 1 udp " + c.priority() + " " + host(c.address()) + " "
                + c.address().getPort() + " typ " + c.type() + related);
        }
        line(sdp, "a=end-of-candidates");
        return sdp.toString();
    }

    /**
     * Reads the answer. Attributes may sit on the m-line or above it, and
     * the m-line's win. The peer must start the DTLS handshake, as the
     * server can only be its server.
     */
    static Answer answer(String sdp) throws InvalidAnswerException {
        List<String> session = new ArrayList<>();
        List<List<String>> media = new ArrayList<>();
        for (String line : sdp.split("\r?\n")) {
            if (line.startsWith("m=")) {
                media.add(new ArrayList<>());
            }
            (media.isEmpty() ? session : media.getLast()).add(line);
        }
        if (media.size() != 1) {
            throw new InvalidAnswerException("Expected 1 m-line, got " + media.size());
        }
        List<String> audio = media.getFirst();
        // m=audio <port> ...: a port of 0 turns the m-line down.
        String[] mLine = audio.getFirst().split(" ");
        if (mLine.length < 2 || mLine[1].equals("0")) {
            throw new InvalidAnswerException("The audio m-line is turned down");
        }
        String setup = attribute(session, audio, "setup");
        if (!"active".equals(setup)) {
            throw new InvalidAnswerException("Expected a=setup:active, got " + setup);
        }
        String ufrag = attribute(session, audio, "ice-ufrag");
        String password = attribute(session, audio, "ice-pwd");
        if (ufrag == null || password == null) {
            throw new InvalidAnswerException("No ICE credentials");
        }
        String fingerprint = sha256Fingerprint(audio);
        if (fingerprint == null) {
            fingerprint = sha256Fingerprint(session);
        }
        if (fingerprint == null) {
            throw new InvalidAnswerException("No SHA-256 fingerprint");
        }
        if (!isSha256(fingerprint)) {
            throw new InvalidAnswerException("Not a SHA-256 fingerprint: " + fingerprint);
        }
        return new Answer(ufrag, password, fingerprint);
    }

    /** The value of {@code a=<name>:}, from the m-line's attributes or else the session's. */
    private static String attribute(List<String> session, List<String> media, String name) {
        String value = attribute(media, name);
        return value != null ? value : attribute(session, name);
    }

    private static String attribute(List<String> lines, String name) {
        String prefix = "a=" + name + ":";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).strip();
            }
        }
        return null;
    }

    /** There may be one fingerprint per hash function (RFC 8122); the server checks SHA-256's. */
    private static String sha256Fingerprint(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("a=fingerprint:")) {
                String[] parts = line.substring("a=fingerprint:".length()).strip().split(" ", 2);
                if (parts.length == 2 && parts[0].equalsIgnoreCase("sha-256")) {
                    return parts[1].strip();
                }
            }
        }
        return null;
    }

    /** 32 bytes, written as SDP writes them. */
    private static boolean isSha256(String fingerprint) {
        try {
            return DtlsIdentity.FINGERPRINT_FORMAT.parseHex(fingerprint).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The address alone, without a zone such as {@code %en0}, which SDP has no room for. */
    private static String host(InetSocketAddress address) {
        String host = address.getAddress().getHostAddress();
        int zone = host.indexOf('%');
        return zone < 0 ? host : host.substring(0, zone);
    }

    private static void line(StringBuilder sdp, String line) {
        sdp.append(line).append("\r\n");
    }

    private Sdp() {
    }
}
