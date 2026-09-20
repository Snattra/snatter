package app.snatter.server.ratelimit;

import app.snatter.api.model.ApiErrorDto;
import app.snatter.server.settings.RateLimitPolicy;
import app.snatter.server.settings.RateLimits;
import app.snatter.server.settings.ServerSettingsService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.Optional;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Enforces {@link RateLimited} on resource methods using the policies from the
 * server settings, keyed by client IP. Rejected requests get a 429 with
 * {@code Retry-After}.
 */
@ApplicationScoped
public class RateLimitFilter {

    private final ServerSettingsService settings;
    private final RateLimiter limiter = new RateLimiter();

    public RateLimitFilter(ServerSettingsService settings) {
        this.settings = settings;
    }

    @ServerRequestFilter
    public Optional<RestResponse<ApiErrorDto>> filter(ResourceInfo resourceInfo, HttpServerRequest request) {
        Method method = resourceInfo.getResourceMethod();
        if (method == null) {
            return Optional.empty();
        }
        RateLimited limited = method.getAnnotation(RateLimited.class);
        if (limited == null) {
            return Optional.empty();
        }
        RateLimits limits = settings.current().rateLimits();
        if (!limits.enabled()) {
            return Optional.empty();
        }
        RateLimitPolicy policy = limits.policy(limited.value());
        String ip = request.remoteAddress() == null ? "unknown" : request.remoteAddress().hostAddress();
        RateLimiter.Decision decision = limiter.tryAcquire(limited.value() + ":" + ip, policy);
        if (decision.allowed()) {
            return Optional.empty();
        }
        long retryAfter = Math.max(1, decision.retryAfter().toSeconds());
        return Optional.of(RestResponse.ResponseBuilder
            .create(Response.Status.TOO_MANY_REQUESTS, new ApiErrorDto()
                .error("rate_limited")
                .message("Too many requests; try again in " + retryAfter + " seconds"))
            .type(MediaType.APPLICATION_JSON)
            .header("Retry-After", retryAfter)
            .build());
    }

    /** Policies changed: start every client from a full bucket under the new rules. */
    void onSettingsChanged(@Observes ServerSettingsService.Changed event) {
        limiter.reset();
    }
}
