package app.snatter.server.common;

import java.util.UUID;

/**
 * A typed identifier wrapping a UUID, for example {@code AccountId}.
 *
 * <p>Every identifier is its own record so that an account id cannot be passed
 * where a session id is expected. Implementations serialise to JSON as the
 * plain UUID string and bind to SQL as a UUID through
 * {@code persistence.IdArgumentFactory}.
 */
public interface Id {

    UUID value();
}
