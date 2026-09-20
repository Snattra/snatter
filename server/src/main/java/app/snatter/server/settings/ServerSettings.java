package app.snatter.server.settings;

import app.snatter.server.account.AccountId;
import java.time.Instant;

/**
 * Community-wide settings. Exactly one row exists in {@code server_settings}.
 *
 * @param ownerId the server owner, or null on a fresh server with no accounts yet
 */
public record ServerSettings(
        String name,
        String description,
        AccountId ownerId,
        RegistrationMode registrationMode,
        boolean challengeRequired,
        RateLimits rateLimits,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isOwner(AccountId accountId) {
        return ownerId != null && ownerId.equals(accountId);
    }

    public ServerSettings withName(String name) {
        return new ServerSettings(name, description, ownerId, registrationMode, challengeRequired, rateLimits, createdAt, updatedAt);
    }

    public ServerSettings withDescription(String description) {
        return new ServerSettings(name, description, ownerId, registrationMode, challengeRequired, rateLimits, createdAt, updatedAt);
    }

    public ServerSettings withRegistrationMode(RegistrationMode mode) {
        return new ServerSettings(name, description, ownerId, mode, challengeRequired, rateLimits, createdAt, updatedAt);
    }

    public ServerSettings withChallengeRequired(boolean required) {
        return new ServerSettings(name, description, ownerId, registrationMode, required, rateLimits, createdAt, updatedAt);
    }

    public ServerSettings withRateLimits(RateLimits limits) {
        return new ServerSettings(name, description, ownerId, registrationMode, challengeRequired, limits, createdAt, updatedAt);
    }
}
