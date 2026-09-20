package app.snatter.server.common;

import java.util.UUID;

/**
 * A typed identifier wrapping a UUID, for example {@code AccountId}.
 *
 * <p>Every identifier is its own record so that an account id cannot be passed
 * where a session id is expected.
 */
public interface Id extends Value<UUID> {
}
