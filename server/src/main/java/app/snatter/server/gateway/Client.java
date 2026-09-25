package app.snatter.server.gateway;

import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.channel.Channel;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.role.Role;
import app.snatter.server.role.RoleId;
import io.quarkus.websockets.next.WebSocketConnection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One gateway connection and what it has been told. Only the dispatcher
 * thread reads or writes the fields, except {@link #pending}, which send
 * completions decrement.
 */
final class Client {

    final WebSocketConnection connection;
    final AtomicInteger pending = new AtomicInteger();

    /** Null until the connection has identified. */
    AccountPrincipal principal;
    long seq;
    boolean closing;
    Map<RoleId, Role> roles = new LinkedHashMap<>();
    Map<ChannelId, Channel> channels = new LinkedHashMap<>();
    /** When a {@code typing} frame for each channel was last passed on, from {@link System#nanoTime()}. */
    final Map<ChannelId, Long> typingPassedOn = new HashMap<>();

    Client(WebSocketConnection connection) {
        this.connection = connection;
    }

    boolean identified() {
        return principal != null;
    }

    boolean live() {
        return identified() && !closing;
    }
}
