package app.snatter.server.blob;

import app.snatter.server.account.AccountId;
import java.time.Instant;

/**
 * Metadata for stored binary content. The bytes themselves live in the
 * {@link BlobStore}.
 *
 * @param ownerId account that uploaded it, or null if that account is gone
 * @param purpose what the blob is for, for example {@code avatar}; used for quotas and cleanup
 */
public record Blob(
        BlobId id,
        String contentType,
        long sizeBytes,
        String sha256,
        AccountId ownerId,
        String purpose,
        Instant createdAt) {
}
