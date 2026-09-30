package app.snatter.server.settings;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.role.RoleId;
import java.time.Duration;
import java.time.Instant;

/**
 * Community-wide settings. Exactly one row exists in {@code server_settings}.
 *
 * @param ownerId             the server owner, or null on a fresh server with no accounts yet
 * @param publicUrl           address clients reach this server at, without trailing slash, or null
 * @param challengeMaxNumber  difficulty of the registration challenge: the largest number a client may have to find
 * @param sessionLifetime     how long a session stays valid without being used; every use extends it
 * @param voiceDefaultBitrate bitrate of new voice channels, in bits per second
 * @param systemChannelId     channel for server-wide notices, or null for none
 * @param newMemberRoleId     role given to accounts when they register, or null for none
 */
public record ServerSettings(
        String name,
        String description,
        String publicUrl,
        AccountId ownerId,
        RegistrationMode registrationMode,
        boolean challengeRequired,
        int challengeMaxNumber,
        RateLimits rateLimits,
        Duration sessionLifetime,
        int voiceDefaultBitrate,
        ChannelId systemChannelId,
        RoleId newMemberRoleId,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isOwner(AccountId accountId) {
        return ownerId != null && ownerId.equals(accountId);
    }

    public ServerSettings withName(String name) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withDescription(String description) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withPublicUrl(String publicUrl) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withRegistrationMode(RegistrationMode mode) {
        return new ServerSettings(name, description, publicUrl, ownerId, mode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withChallengeRequired(boolean required) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, required, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withChallengeMaxNumber(int maxNumber) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, maxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withRateLimits(RateLimits limits) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, limits, sessionLifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withSessionLifetime(Duration lifetime) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, lifetime, voiceDefaultBitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withVoiceDefaultBitrate(int bitrate) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, bitrate, systemChannelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withSystemChannelId(ChannelId channelId) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, channelId, newMemberRoleId, createdAt, updatedAt);
    }

    public ServerSettings withNewMemberRoleId(RoleId roleId) {
        return new ServerSettings(name, description, publicUrl, ownerId, registrationMode, challengeRequired, challengeMaxNumber, rateLimits, sessionLifetime, voiceDefaultBitrate, systemChannelId, roleId, createdAt, updatedAt);
    }
}
