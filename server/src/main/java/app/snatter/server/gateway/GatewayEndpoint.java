package app.snatter.server.gateway;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

/** The WebSocket at {@code /api/v1/gateway}; everything happens in {@link Gateway}. */
@WebSocket(path = "/api/v1/gateway")
public class GatewayEndpoint {

    private final Gateway gateway;

    public GatewayEndpoint(Gateway gateway) {
        this.gateway = gateway;
    }

    @OnOpen
    void opened(WebSocketConnection connection) {
        gateway.opened(connection);
    }

    @OnTextMessage
    void received(WebSocketConnection connection, String text) {
        gateway.received(connection, text);
    }

    @OnClose
    void closed(WebSocketConnection connection) {
        gateway.closed(connection);
    }
}
