package app.snatter.server.settings;

/**
 * Rate limits: per client IP for the unauthenticated endpoints, per account
 * for sending messages. {@code enabled} switches them all.
 */
public record RateLimits(
        boolean enabled,
        RateLimitPolicy login,
        RateLimitPolicy register,
        RateLimitPolicy challenge,
        RateLimitPolicy invite,
        RateLimitPolicy message) {

    /** The policy for a named endpoint group, as used by {@code @RateLimited}. */
    public RateLimitPolicy policy(String name) {
        return switch (name) {
            case "login" -> login;
            case "register" -> register;
            case "challenge" -> challenge;
            case "invite" -> invite;
            case "message" -> message;
            default -> throw new IllegalArgumentException("Unknown rate limit policy: " + name);
        };
    }
}
