package app.snatter.server.gateway;

import io.quarkus.websockets.next.CloseReason;
import java.util.Locale;

/**
 * Why the server closes a gateway connection; the contract's
 * {@code GatewayCloseReason}. Codes from 4000 to 4499 tell the client not to
 * reconnect, from 4500 to 4999 to reconnect.
 */
public enum GatewayClose {
    INVALID_FRAME(4000),
    NOT_IDENTIFIED(4001),
    AUTHENTICATION_FAILED(4002),
    ALREADY_IDENTIFIED(4003),
    SESSION_ENDED(4004),
    BANNED(4005),
    CLIENT_OUTDATED(4006),
    IDENTIFY_TIMEOUT(4500),
    TOO_SLOW(4501);

    private final int code;

    GatewayClose(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /** The reason text sent with the close frame, for example {@code session_ended}. */
    public String reasonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public CloseReason toCloseReason() {
        return new CloseReason(code, reasonName());
    }
}
