package app.snatter.server.account;

import app.snatter.server.blob.BlobId;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * A member of this community, as exposed through the API.
 *
 * @param avatarId blob holding the profile picture, fetchable at
 *                 {@code /api/v1/blobs/{avatarId}}, or null if none is set
 */
public record Account(
        AccountId id,
        String username,
        String displayName,
        @JsonInclude(JsonInclude.Include.ALWAYS) BlobId avatarId,
        Instant createdAt) {
}
