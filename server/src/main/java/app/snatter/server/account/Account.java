package app.snatter.server.account;

import app.snatter.server.blob.BlobId;
import app.snatter.server.role.RoleId;
import java.time.Instant;
import java.util.List;

/**
 * A member of this community.
 *
 * @param avatarId       blob holding the profile picture, or null if none is set
 * @param roleIds        assigned roles
 * @param timedOutUntil  end of the member's timeout, or null; a past instant means none
 * @param bannedAt       when the member was banned, or null if they are not
 */
public record Account(
        AccountId id,
        String username,
        String displayName,
        BlobId avatarId,
        List<RoleId> roleIds,
        Instant timedOutUntil,
        Instant bannedAt,
        Instant createdAt) {

    public boolean isTimedOut(Instant now) {
        return timedOutUntil != null && timedOutUntil.isAfter(now);
    }
}
