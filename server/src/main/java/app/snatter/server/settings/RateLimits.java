package app.snatter.server.settings;

/** Rate limits for the unauthenticated endpoints. */
public record RateLimits(
        boolean enabled,
        RateLimitPolicy login,
        RateLimitPolicy register,
        RateLimitPolicy challenge,
        RateLimitPolicy invite) {

    /** The policy for a named endpoint group, as used by {@code @RateLimited}. */
    public RateLimitPolicy policy(String name) {
        return switch (name) {
            case "login" -> login;
            case "register" -> register;
            case "challenge" -> challenge;
            case "invite" -> invite;
            default -> throw new IllegalArgumentException("Unknown rate limit policy: " + name);
        };
    }
}
