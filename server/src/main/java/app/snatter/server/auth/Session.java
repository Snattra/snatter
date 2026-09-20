package app.snatter.server.auth;

import java.time.Instant;
import java.util.UUID;

/** A stored bearer session. The token itself is never stored, only its hash. */
public record Session(UUID id, UUID accountId, Instant createdAt, Instant expiresAt, Instant lastSeenAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
