package app.snatter.server.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
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
 * client so it works in-process and against the packaged application.
 * Received frames are buffered; {@link #await} takes the first matching one
 * wherever it is in the buffer, so tests do not depend on the order of
 * unrelated frames. Every frame's {@code seq} is checked to follow the one
 * before.
 */
public final class GatewayTestClient implements AutoCloseable {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    public record Closed(int code, String reason) {
    }

    private final List<JsonPath> buffer = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final CompletableFuture<Closed> closed = new CompletableFuture<>();
    private final WebSocket socket;
    private long lastSeq;
    private JsonPath ready;

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
        client.ready = client.await("ready");
        return client;
    }

    public JsonPath ready() {
        return ready;
    }

    public void send(String text) {
        socket.sendText(text, true).join();
    }

    public void identify(String token) {
        send("{\"type\":\"identify\",\"token\":\"" + token + "\"}");
    }

    /** Takes the first buffered or arriving frame of the type. */
    public JsonPath await(String type) {
        return await(type, frame -> true);
    }

    /** Takes the first buffered or arriving frame of the type that matches. */
    public JsonPath await(String type, Predicate<JsonPath> matching) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        synchronized (buffer) {
            while (true) {
                for (Iterator<JsonPath> it = buffer.iterator(); it.hasNext(); ) {
                    JsonPath frame = it.next();
                    if (type.equals(frame.getString("type")) && matching.test(frame)) {
                        it.remove();
                        return frame;
                    }
                }
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    fail("no " + type + " frame arrived; buffered: " + describe());
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
    public void assertNone(String type, Predicate<JsonPath> matching) {
        synchronized (buffer) {
            for (JsonPath frame : buffer) {
                if (type.equals(frame.getString("type")) && matching.test(frame)) {
                    fail("unexpected " + type + " frame: " + frame.prettify());
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
            assertEquals(List.of(), errors, "frames must be numbered 1, 2, 3, ...");
        }
        if (!closed.isDone()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private String describe() {
        return buffer.stream().map(f -> f.getString("type")).toList().toString();
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                JsonPath frame = JsonPath.from(partial.toString());
                partial.setLength(0);
                synchronized (buffer) {
                    long seq = frame.getLong("seq");
                    if (seq != lastSeq + 1) {
                        errors.add("seq " + seq + " after " + lastSeq + " (" + frame.getString("type") + ")");
                    }
                    lastSeq = seq;
                    buffer.add(frame);
                    buffer.notifyAll();
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(new Closed(statusCode, reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.completeExceptionally(error);
        }
    }
}
