package app.snatter.server.account;

import java.time.Instant;

/** A member of this community, as exposed through the API. */
public record Account(AccountId id, String username, String displayName, Instant createdAt) {
}
