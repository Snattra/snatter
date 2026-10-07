package app.snatter.server.media;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One member's voice connection: the server's offer, the peer's answer, and
 * the ICE and SRTP they set up. The server offers and the peer answers, so
 * the server decides what is carried.
 */
public final class VoiceConnection implements AutoCloseable {

    /**
     * From the offer: time to answer and for ICE and DTLS to connect, about
     * as long as browsers keep trying ICE before they give up.
     */
    private static final long CONNECT_SECONDS = 30;

    private final IceConnection ice;
    private final SrtpConnection srtp = new SrtpConnection();
    private final String offer;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    /** Offers to receive the member's voice at up to {@code bitrate} bits per second. */
    public VoiceConnection(IceConnection ice, int bitrate) {
        this.ice = ice;
        offer = Sdp.offer(ice.ufrag(), ice.password(), srtp.fingerprint(), ice.candidates(), bitrate);
        ready.orTimeout(CONNECT_SECONDS, TimeUnit.SECONDS);
    }

    /** The SDP offer, for the peer to answer. */
    public String offer() {
        return offer;
    }

    /**
     * Takes the peer's SDP answer, once, and starts connecting. RTP the peer
     * sends is decrypted and handed to {@code received}, on the reading
     * thread.
     */
    public void accept(String answer, Consumer<RtpPacket> received) throws InvalidAnswerException {
        Sdp.Answer peer = Sdp.answer(answer);
        ice.start(peer.iceUfrag(), peer.icePassword());
        ice.connected()
            .thenCompose(path -> {
                srtp.start(path, peer.fingerprint(), received);
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
        ready.completeExceptionally(new IOException("Voice connection closed"));
        srtp.close();
        ice.close();
    }
}
