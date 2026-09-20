package app.snatter.server.auth;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

@ConfigMapping(prefix = "snatter.auth")
public interface AuthConfig {

    /** How long a session stays valid after login. */
    @WithDefault("P30D")
    Duration sessionLifetime();
}
