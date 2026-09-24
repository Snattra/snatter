package app.snatter.server.settings;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import java.time.Instant;

/**
 * Community-wide settings. Exactly one row exists in {@code server_settings}.
 *
 * @param ownerId         the server owner, or null on a fresh server with no accounts yet
 * @param publicUrl       address clients reach this server at, without trailing slash, or null
 * @param systemChannelId channel for server-wide notices, or null for none
 */
public record ServerSettings(
        String name,
        String description,
        String publicUrl,
        AccountId ownerId,
        RegistrationMode registrationMode,
        boolean challengeRequired,
        RateLimits rateLimits,
        ChannelId systemChannelId,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isOwner(AccountId accountId) {
        return ownerId != null && ownerId.equals(accountId);
    }

    public ServerSettings withName(String name) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, rateLimits, systemChannelId, createdAt, updatedAt);
    }

    public ServerSettings withDescription(String description) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, rateLimits, systemChannelId, createdAt, updatedAt);
    }

    public ServerSettings withPublicUrl(String publicUrl) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, rateLimits, systemChannelId, createdAt, updatedAt);
    }

    public ServerSettings withRegistrationMode(RegistrationMode mode) {
        return new ServerSettings(name, description, publicUrl, ownerId, mode, challengeRequired, rateLimits, systemChannelId, createdAt, updatedAt);
    }

    public ServerSettings withChallengeRequired(boolean required) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, required, rateLimits, systemChannelId, createdAt, updatedAt);
    }

    public ServerSettings withRateLimits(RateLimits limits) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, limits, systemChannelId, createdAt, updatedAt);
    }

    public ServerSettings withSystemChannelId(ChannelId channelId) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, rateLimits, channelId, createdAt, updatedAt);
    }
}
