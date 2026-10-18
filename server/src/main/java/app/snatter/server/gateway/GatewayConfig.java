package app.snatter.server.gateway;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/**
 * Gateway timing that tests shorten; not something to configure in
 * production, which is why the default lives here.
 */
@ConfigMapping(prefix = "snatter.gateway")
public interface GatewayConfig {

    /** How long a new connection may take to identify before it is closed. */
    @WithDefault("PT10S")
    Duration identifyTimeout();

    /** How long a client may take to answer a voice offer before its voice ends. */
    @WithDefault("PT10S")
    Duration voiceAnswerTimeout();
}
