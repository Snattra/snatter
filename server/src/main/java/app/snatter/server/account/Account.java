package app.snatter.server.account;

import app.snatter.server.blob.BlobId;
import java.time.Instant;

/**
 * A member of this community.
 *
 * @param avatarId blob holding the profile picture, or null if none is set
 */
public record Account(
        AccountId id,
        String username,
        String displayName,
        BlobId avatarId,
        Instant createdAt) {
}
