package app.snatter.server.invite;

import app.snatter.server.account.AccountId;
import java.time.Instant;

/**
 * An invite link.
 *
 * @param createdBy null if the creator's account is gone
 * @param expiresAt null for an invite that never expires
 * @param maxUses   null for unlimited uses
 * @param revokedAt null while the invite is active
 */
public record Invite(
        InviteCode code,
        AccountId createdBy,
        Instant createdAt,
        Instant expiresAt,
        Integer maxUses,
        int uses,
        Instant revokedAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isExhausted() {
        return maxUses != null && uses >= maxUses;
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now) && !isExhausted();
    }
}
