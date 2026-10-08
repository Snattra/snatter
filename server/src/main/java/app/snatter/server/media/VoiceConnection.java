package app.snatter.server.media;

import app.snatter.server.media.Sdp.SendLine;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 * One member's voice connection: the server's offers, the peer's answers,
 * and the ICE and SRTP they set up. The server offers and the peer answers,
 * so the server decides what is carried: the member's voice to the server,
 * and the voice of each of the others in the channel to the member, each on
 * a line of its own. When the others change, the server offers again.
 *
 * <p>{@link #hear} and {@link #accept} are called on one thread at a time;
 * forwarding runs on the reading threads of the others' connections.
 */
public final class VoiceConnection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(VoiceConnection.class);

    /**
     * From the offer: time to answer and for ICE and DTLS to connect, about
     * as long as browsers keep trying ICE before they give up.
     */
    private static final Duration DEADLINE = Duration.ofSeconds(30);

    /** A line carrying a member's voice, or no one's. */
    private record Line(String mid, int ssrc, VoiceConnection sender) {
    }

    private final String member;
    private final IceConnection ice;
    private final SrtpConnection srtp = new SrtpConnection();
    private final Sdp.Transport transport;
    private final long session = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    private final List<Line> lines = new ArrayList<>();
    private int bitrate;
    private long version;
    /** The lines as offered, while the peer has not answered. */
    private List<Line> offered;
    /** The lines as the peer last answered them: those it can play. */
    private List<Line> answered = List.of();
    /** Whether something changed since the offer waiting for its answer. */
    private boolean changed;
    private boolean started;

    /** Those who hear this member, read by the reading thread. */
    private volatile List<VoiceConnection> hearers = List.of();
    /** The source the member's voice comes from; the reading thread's alone. */
    private Integer voiceSource;
    /** The source each sender's voice goes out as, once the peer can play it; read by others' reading threads. */
    private volatile Map<VoiceConnection, Integer> sources = Map.of();

    /** A connection for the member with the given id, which names their voice in others' offers. */
    public VoiceConnection(IceConnection ice, String member) {
        this(ice, member, DEADLINE);
    }

    /** With a deadline other than browsers', which tests use to see it pass. */
    VoiceConnection(IceConnection ice, String member, Duration deadline) {
        this.ice = ice;
        this.member = member;
        transport = new Sdp.Transport(ice.ufrag(), ice.password(), srtp.fingerprint(), ice.candidates());
        ready.orTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Who the member hears and is heard by, and the bitrate to send at.
     * Returns the offer for the peer when there is one to make: the first,
     * or one for a change, unless an offer is waiting for its answer, in
     * which case the change waits for {@link #accept}. A member who left is
     * no longer heard at once; one who came is heard once the peer answers.
     */
    public Optional<String> hear(List<VoiceConnection> others, int bitrate) {
        hearers = List.copyOf(others);
        boolean change = bitrate != this.bitrate;
        this.bitrate = bitrate;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.sender() != null && !others.contains(line.sender())) {
                lines.set(i, new Line(line.mid(), 0, null));
                srtp.forget(line.ssrc());
                change = true;
            }
        }
        for (VoiceConnection other : others) {
            if (lines.stream().noneMatch(line -> line.sender() == other)) {
                carry(other);
                change = true;
            }
        }
        publishSources();
        if (offered != null) {
            changed |= change;
            return Optional.empty();
        }
        return change || version == 0 ? Optional.of(offer()) : Optional.empty();
    }

    /** On a line of no one's if there is one, so lines are reused, or else a new one. */
    private void carry(VoiceConnection other) {
        int ssrc = freshSsrc();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).sender() == null) {
                lines.set(i, new Line(lines.get(i).mid(), ssrc, other));
                return;
            }
        }
        lines.add(new Line(String.valueOf(lines.size() + 1), ssrc, other));
    }

    /** A source never used before, so the peer never mistakes one member's voice for another's. */
    private int freshSsrc() {
        while (true) {
            int ssrc = ThreadLocalRandom.current().nextInt();
            if (ssrc != 0 && srtp.claim(ssrc)) {
                return ssrc;
            }
        }
    }

    private String offer() {
        offered = List.copyOf(lines);
        changed = false;
        version++;
        List<SendLine> sending = lines.stream()
            .map(line -> new SendLine(line.mid(), line.ssrc(), line.sender() == null ? null : line.sender().member))
            .toList();
        return Sdp.offer(session, version, transport, bitrate, sending);
    }

    /**
     * Takes the peer's SDP answer to the offer waiting for one; any other is
     * ignored. The first starts connecting. Returns the next offer if
     * something changed meanwhile.
     */
    public Optional<String> accept(String answer) throws InvalidAnswerException {
        if (offered == null) {
            return Optional.empty();
        }
        Sdp.Answer peer = Sdp.answer(answer, 1 + offered.size());
        if (!started) {
            started = true;
            connect(peer);
        }
        answered = offered;
        offered = null;
        publishSources();
        return changed ? Optional.of(offer()) : Optional.empty();
    }

    private void connect(Sdp.Answer peer) {
        ice.start(peer.iceUfrag(), peer.icePassword());
        ice.connected()
            .thenCompose(path -> {
                srtp.start(path, peer.fingerprint(), this::received);
                return srtp.ready();
            })
            .whenComplete((done, failure) -> {
                if (failure == null) {
                    ready.complete(null);
                } else {
                    ready.completeExceptionally(failure);
                }
            });
    }

    /** Forwards to those still carried that the peer can play: a line it has answered, unchanged since. */
    private void publishSources() {
        Map<VoiceConnection, Integer> playable = new HashMap<>();
        for (Line line : lines) {
            if (line.sender() != null && answered.contains(line)) {
                playable.put(line.sender(), line.ssrc());
            }
        }
        sources = Map.copyOf(playable);
    }

    /**
     * The member's voice, decrypted, goes on to everyone who hears them,
     * from one source only: the first to arrive, as a browser sends its
     * voice from one. Another's sequence numbers would run into it on the
     * listeners' lines, which carry them as they are, and reuse their nonces.
     */
    private void received(RtpPacket packet) {
        if (voiceSource == null) {
            voiceSource = packet.ssrc();
        } else if (packet.ssrc() != voiceSource) {
            return;
        }
        for (VoiceConnection hearer : hearers) {
            hearer.forward(this, packet);
        }
    }

    /** Sends another member's voice to this peer, on that member's line, if the peer can play it. */
    private void forward(VoiceConnection sender, RtpPacket packet) {
        Integer ssrc = sources.get(sender);
        if (ssrc == null) {
            return;
        }
        int offset = packet.getOffset();
        RtpPacket copy = new RtpPacket(Arrays.copyOfRange(packet.getBuffer(), offset,
            offset + packet.getLength() + SrtpConnection.TAG_LENGTH), 0, packet.getLength());
        copy.setSsrc(ssrc);
        try {
            srtp.send(copy);
        } catch (IOException e) {
            LOG.debugf("Could not forward voice to %s: %s", member, e.getMessage());
        }
    }

    /**
     * Completes once the peer is connected and the keys are agreed. Fails
     * when either fails, or when that has not happened within 30 seconds of
     * the offer; the owner then closes the connection.
     */
    public CompletionStage<Void> ready() {
        return ready.minimalCompletionStage();
    }

    @Override
    public void close() {
        hearers = List.of();
        sources = Map.of();
        ready.completeExceptionally(new IOException("Voice connection closed"));
        srtp.close();
        ice.close();
    }
}
