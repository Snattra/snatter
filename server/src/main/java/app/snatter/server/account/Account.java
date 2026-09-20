package app.snatter.server.account;

import java.time.Instant;
import java.util.UUID;

/** A member of this community, as exposed through the API. */
public record Account(UUID id, String username, String displayName, Instant createdAt) {
}
