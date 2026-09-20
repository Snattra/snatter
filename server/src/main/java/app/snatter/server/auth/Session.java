package app.snatter.server.auth;

import app.snatter.server.account.AccountId;
import java.time.Instant;

/** A stored bearer session. The token itself is never stored, only its hash. */
public record Session(SessionId id, AccountId accountId, Instant createdAt, Instant expiresAt, Instant lastSeenAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
