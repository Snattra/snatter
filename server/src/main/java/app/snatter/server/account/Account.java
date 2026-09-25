package app.snatter.server.account;

import app.snatter.server.blob.BlobId;
import app.snatter.server.role.RoleId;
import java.time.Instant;
import java.util.List;

/**
 * A member of this community.
 *
 * @param avatarId blob holding the profile picture, or null if none is set
 * @param roleIds  assigned roles
 */
public record Account(
        AccountId id,
        String username,
        String displayName,
        BlobId avatarId,
        List<RoleId> roleIds,
        Instant createdAt) {
}
