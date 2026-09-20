package app.snatter.server.auth;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/** Tuning for the proof-of-work registration challenge. */
@ConfigMapping(prefix = "snatter.registration.challenge")
public interface ChallengeConfig {

    /** Upper bound of the number clients search for; sets the difficulty. */
    @WithDefault("100000")
    int maxNumber();

    /** How long a challenge may be used after it was issued. */
    @WithDefault("PT10M")
    Duration ttl();
}
