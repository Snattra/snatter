package app.snatter.server.gateway;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/** Tuning for the WebSocket gateway. */
@ConfigMapping(prefix = "snatter.gateway")
public interface GatewayConfig {

    /** How long a new connection may take to identify before it is closed. */
    @WithDefault("PT10S")
    Duration identifyTimeout();

    /** How often open connections keep their sessions alive and check they have not ended. */
    @WithDefault("PT5M")
    Duration keepAliveInterval();

    /** Frames a connection may have unsent before it counts as too slow and is closed. */
    @WithDefault("1000")
    int maxPendingFrames();
}
