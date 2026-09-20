package app.snatter.server.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.settings.RateLimitPolicy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicLong clock = new AtomicLong(0);
    private final RateLimiter limiter = new RateLimiter(clock::get, 1000);
    private final RateLimitPolicy threePerMinute = new RateLimitPolicy(3, Duration.ofMinutes(1));

    @Test
    void allowsUpToTheLimitThenRefuses() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("a", threePerMinute).allowed(), "attempt " + i);
        }
        RateLimiter.Decision refused = limiter.tryAcquire("a", threePerMinute);
        assertFalse(refused.allowed());
        assertEquals(Duration.ofSeconds(20), refused.retryAfter(), "one token refills every 20s");
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a", threePerMinute);
        }
        clock.addAndGet(Duration.ofSeconds(20).toNanos());
        assertTrue(limiter.tryAcquire("a", threePerMinute).allowed());
        assertFalse(limiter.tryAcquire("a", threePerMinute).allowed());
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a", threePerMinute);
        }
        assertFalse(limiter.tryAcquire("a", threePerMinute).allowed());
        assertTrue(limiter.tryAcquire("b", threePerMinute).allowed());
    }

    @Test
    void resetForgetsEverything() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a", threePerMinute);
        }
        limiter.reset();
        assertTrue(limiter.tryAcquire("a", threePerMinute).allowed());
    }
}
