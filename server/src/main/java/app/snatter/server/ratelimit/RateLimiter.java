package app.snatter.server.ratelimit;

import app.snatter.server.settings.RateLimitPolicy;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory token buckets keyed by an arbitrary string, usually
 * {@code policy:clientIp}. Suitable for a single server instance.
 */
public class RateLimiter {

    /** Outcome of an attempt: allowed, or how long to wait. */
    public record Decision(boolean allowed, Duration retryAfter) {
        static final Decision ALLOWED = new Decision(true, Duration.ZERO);
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
        }
    }

    private final LongSupplier nanoTime;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int pruneEvery;
    private int operations;

    public RateLimiter() {
        this(System::nanoTime, 1000);
    }

    RateLimiter(LongSupplier nanoTime, int pruneEvery) {
        this.nanoTime = nanoTime;
        this.pruneEvery = pruneEvery;
    }

    /** Takes one token from the bucket for {@code key} under {@code policy}. */
    public Decision tryAcquire(String key, RateLimitPolicy policy) {
        long now = nanoTime.getAsLong();
        double refillPerNano = (double) policy.limit() / policy.period().toNanos();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(policy.limit(), now));
        synchronized (bucket) {
            double refilled = bucket.tokens + (now - bucket.lastRefillNanos) * refillPerNano;
            bucket.tokens = Math.min(policy.limit(), refilled);
            bucket.lastRefillNanos = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                maybePrune(now, policy);
                return Decision.ALLOWED;
            }
            long waitNanos = (long) Math.ceil((1 - bucket.tokens) / refillPerNano);
            return new Decision(false, Duration.ofNanos(waitNanos));
        }
    }

    /** Forgets all buckets, for example after policies changed. */
    public void reset() {
        buckets.clear();
    }

    /** Drops buckets that have been idle long enough to be full again. */
    private void maybePrune(long now, RateLimitPolicy policy) {
        if (++operations % pruneEvery != 0) {
            return;
        }
        long idleNanos = policy.period().toNanos();
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Bucket b = it.next().getValue();
            if (now - b.lastRefillNanos > idleNanos) {
                it.remove();
            }
        }
    }
}
