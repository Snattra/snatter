package app.snatter.server.gateway;

import io.quarkus.websockets.next.CloseReason;
import java.util.Locale;

/** Why the server closes a gateway connection; the contract's {@code GatewayCloseReason}. */
public enum GatewayClose {
    INVALID_FRAME(4000),
    NOT_IDENTIFIED(4001),
    IDENTIFY_TIMEOUT(4002),
    AUTHENTICATION_FAILED(4003),
    ALREADY_IDENTIFIED(4004),
    SESSION_ENDED(4005),
    TOO_SLOW(4006);

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
