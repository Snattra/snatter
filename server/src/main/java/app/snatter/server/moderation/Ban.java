package app.snatter.server.moderation;

import app.snatter.server.account.AccountId;
import java.time.Instant;

/**
 * A member kept out of the server.
 *
 * @param reason   shown to the member when they try to log in, or null
 * @param bannedBy who banned them, or null if that account is gone
 */
public record Ban(AccountId accountId, String reason, AccountId bannedBy, Instant createdAt) {
}
