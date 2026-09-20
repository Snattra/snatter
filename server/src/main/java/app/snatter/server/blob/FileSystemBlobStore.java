package app.snatter.server.blob;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Stores blobs as files under {@code <root>/blobs/<first two hex digits>/<id>}.
 * Writes go to a temporary file first and are moved into place atomically.
 */
@ApplicationScoped
public class FileSystemBlobStore implements BlobStore {

    private final Path root;

    public FileSystemBlobStore(StorageConfig config) {
        this.root = config.root().toAbsolutePath().normalize().resolve("blobs");
    }

    @Override
    public void put(BlobId id, byte[] content) {
        Path target = pathFor(id);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), id.toString(), ".tmp");
            try {
                Files.write(tmp, content);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store blob " + id, e);
        }
    }

    @Override
    public Optional<InputStream> open(BlobId id) {
        try {
            return Optional.of(Files.newInputStream(pathFor(id)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open blob " + id, e);
        }
    }

    @Override
    public boolean delete(BlobId id) {
        try {
            return Files.deleteIfExists(pathFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete blob " + id, e);
        }
    }

    private Path pathFor(BlobId id) {
        String name = id.toString();
        return root.resolve(name.substring(0, 2)).resolve(name);
    }
}
