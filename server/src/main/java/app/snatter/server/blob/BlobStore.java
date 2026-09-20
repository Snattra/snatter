package app.snatter.server.blob;

import java.io.InputStream;
import java.util.Optional;

/**
 * Where blob bytes live. Implementations must make {@link #put} atomic: a
 * concurrent {@link #open} sees either nothing or the complete content.
 */
public interface BlobStore {

    void put(BlobId id, byte[] content);

    Optional<InputStream> open(BlobId id);

    /** @return whether something was deleted */
    boolean delete(BlobId id);
}
