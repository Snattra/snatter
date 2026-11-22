package app.snatter.server.blob;

import app.snatter.server.account.AccountId;
import app.snatter.server.persistence.AfterCommit;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Keeps the blob table and the blob store consistent across transactions.
 *
 * <p>Bytes are written to the store before the row is inserted and removed
 * from the store only after the row deletion has committed, so a rolled-back
 * transaction never leaves a row without bytes, and a reader never finds a row
 * whose bytes are already gone.
 */
@ApplicationScoped
public class BlobService {

    private final BlobRepository repository;
    private final BlobStore store;
    private final AfterCommit afterCommit;

    public BlobService(BlobRepository repository, BlobStore store, AfterCommit afterCommit) {
        this.repository = repository;
        this.store = store;
        this.afterCommit = afterCommit;
    }

    /** Stores content and records it. Must be called inside a transaction. */
    public Blob store(byte[] content, String contentType, AccountId owner, String purpose) {
        BlobId id = BlobId.newId();
        store.put(id, content);
        afterCommit.onRollback(() -> store.delete(id));
        Blob blob = new Blob(id, contentType, content.length, sha256(content), owner, purpose, Instant.now());
        repository.insert(blob);
        return blob;
    }

    public Optional<Blob> find(BlobId id) {
        return repository.findById(id);
    }

    public Optional<java.io.InputStream> open(BlobId id) {
        return store.open(id);
    }

    /** Deletes the row now and the bytes once the transaction commits. */
    public void delete(BlobId id) {
        repository.delete(id);
        afterCommit.run(() -> store.delete(id));
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
