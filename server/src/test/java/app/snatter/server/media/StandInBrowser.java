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
 * Stands in for a browser in voice: it reads the server's offer with its own
 * parsing, answers as Chrome would, and connects ICE and DTLS. Public for
 * the gateway's tests.
 */
public final class StandInBrowser implements AutoCloseable {

    private static final Pattern CANDIDATE =
        Pattern.compile("a=candidate:(\\S+) 1 udp (\\d+) (\\S+) (\\d+) typ (\\S+).*");

    private final String offer;
    private final IcePeer ice;
    private final DtlsPeer dtls;

    public StandInBrowser(String offer) throws IOException {
        this.offer = offer;
        ice = new IcePeer(attribute(offer, "ice-ufrag"), attribute(offer, "ice-pwd"), candidates(offer));
        dtls = new DtlsPeer(ice, SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM);
    }

    /** The answer of a browser with a microphone, shaped like Chrome's. */
    public String answer() throws Exception {
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

    /** Runs ICE and the DTLS handshake, checking the server shows the certificate its offer named. */
    public void connect() throws Exception {
        dtls.server = ice.connect().getRemoteCandidate().getTransportAddress();
        dtls.handshake(attribute(offer, "fingerprint").substring("sha-256 ".length()));
    }

    /** Encrypts an RTP packet and sends it to the server. */
    public void send(byte[] rtp) throws Exception {
        dtls.send(rtp);
    }

    @Override
    public void close() {
        ice.close();
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
