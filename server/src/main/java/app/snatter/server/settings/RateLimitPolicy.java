package app.snatter.server.settings;

import java.time.Duration;

/** A token bucket allowing {@code limit} requests per {@code period}, per client. */
public record RateLimitPolicy(int limit, Duration period) {

    public RateLimitPolicy {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive");
        }
    }
}
