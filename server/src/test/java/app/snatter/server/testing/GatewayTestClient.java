package app.snatter.server.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import app.snatter.client.model.GatewayClientFrameDto;
import app.snatter.client.model.GatewayCloseReasonDto;
import app.snatter.client.model.GatewayIdentifyDto;
import app.snatter.client.model.GatewayReadyDto;
import app.snatter.client.model.GatewayServerFrameDto;
import app.snatter.server.protocol.Protocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A gateway connection as a client would make it, over the JDK's WebSocket
 * client, with frames as the generated models. Received frames are
 * buffered; {@link #await} takes the first matching one wherever it is in
 * the buffer, so tests do not depend on the order of unrelated frames.
 *
 * <p>Like the API clients, it is strict: a frame of a type, or with a field,
 * the contract does not have fails the test, and so does a frame whose
 * {@code seq} does not follow the one before.
 */
public final class GatewayTestClient implements AutoCloseable {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    public record Closed(int code, GatewayCloseReasonDto reason) {
    }

    private static final ObjectMapper JSON = ApiClientFactory.json();

    private final List<GatewayServerFrameDto> buffer = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final CompletableFuture<Closed> closed = new CompletableFuture<>();
    private final WebSocket socket;
    private long lastSeq;
    private GatewayReadyDto ready;

    private GatewayTestClient() {
        URI uri = URI.create("ws://localhost:" + RestAssured.port + "/api/v1/gateway");
        this.socket = HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(uri, new Listener())
            .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .join();
    }

    public static GatewayTestClient connect() {
        return new GatewayTestClient();
    }

    /** Connects and identifies; the {@code ready} frame is then available from {@link #ready()}. */
    public static GatewayTestClient identified(String token) {
        GatewayTestClient client = connect();
        client.identify(token);
        client.ready = client.await(GatewayReadyDto.class);
        return client;
    }

    public GatewayReadyDto ready() {
        return ready;
    }

    public void send(GatewayClientFrameDto frame) {
        try {
            send(JSON.writerFor(GatewayClientFrameDto.class).writeValueAsString(frame));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Sends the text as it is, for frames the contract does not allow. */
    public void send(String text) {
        socket.sendText(text, true).join();
    }

    public void identify(String token) {
        identify(token, Protocol.CURRENT.toString());
    }

    public void identify(String token, String protocol) {
        send(new GatewayIdentifyDto().token(token).protocol(protocol));
    }

    /** Takes the first buffered or arriving frame of the type. */
    public <T extends GatewayServerFrameDto> T await(Class<T> type) {
        return await(type, frame -> true);
    }

    /** Takes the first buffered or arriving frame of the type that matches. */
    public <T extends GatewayServerFrameDto> T await(Class<T> type, Predicate<? super T> matching) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        synchronized (buffer) {
            while (true) {
                assertEquals(List.of(), errors, "frames as the contract describes them");
                for (Iterator<GatewayServerFrameDto> it = buffer.iterator(); it.hasNext(); ) {
                    GatewayServerFrameDto frame = it.next();
                    if (type.isInstance(frame) && matching.test(type.cast(frame))) {
                        it.remove();
                        return type.cast(frame);
                    }
                }
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    fail("no matching " + type.getSimpleName() + " arrived; buffered: " + describe());
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(buffer, left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /** Asserts that no buffered frame of the type matches; pair it with an awaited later frame. */
    public <T extends GatewayServerFrameDto> void assertNone(Class<T> type, Predicate<? super T> matching) {
        synchronized (buffer) {
            for (GatewayServerFrameDto frame : buffer) {
                if (type.isInstance(frame) && matching.test(type.cast(frame))) {
                    fail("unexpected frame: " + frame);
                }
            }
        }
    }

    public Closed awaitClose() {
        return closed.orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).join();
    }

    @Override
    public void close() {
        synchronized (buffer) {
            assertEquals(List.of(), errors, "frames as the contract describes them");
        }
        if (!closed.isDone()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private String describe() {
        return buffer.stream().map(GatewayServerFrameDto::getType).toList().toString();
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                synchronized (buffer) {
                    receive(text);
                    buffer.notifyAll();
                }
            }
            webSocket.request(1);
            return null;
        }

        private void receive(String text) {
            try {
                JsonNode tree = JSON.readTree(text);
                long seq = tree.path("seq").asLong();
                if (seq != lastSeq + 1) {
                    errors.add("seq " + seq + " after " + lastSeq + ": " + text);
                }
                lastSeq = seq;
                buffer.add(JSON.treeToValue(tree, GatewayServerFrameDto.class));
            } catch (JsonProcessingException e) {
                errors.add(e.getOriginalMessage() + ": " + text);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (statusCode == WebSocket.NORMAL_CLOSURE) {
                closed.complete(new Closed(statusCode, null));
            } else {
                try {
                    closed.complete(new Closed(statusCode, GatewayCloseReasonDto.fromValue(reason)));
                } catch (IllegalArgumentException e) {
                    closed.completeExceptionally(new AssertionError("a close reason the contract does not have: " + reason));
                }
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.completeExceptionally(error);
        }
    }
}
