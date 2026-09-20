package app.snatter.server.blob;

import app.snatter.server.account.AccountId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
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
    private final TransactionSynchronizationRegistry transactions;

    public BlobService(BlobRepository repository, BlobStore store, TransactionSynchronizationRegistry transactions) {
        this.repository = repository;
        this.store = store;
        this.transactions = transactions;
    }

    /** Stores content and records it. Must be called inside a transaction. */
    public Blob store(byte[] content, String contentType, AccountId owner, String purpose) {
        BlobId id = BlobId.newId();
        store.put(id, content);
        afterCompletion(Status.STATUS_ROLLEDBACK, () -> store.delete(id));
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
        afterCompletion(Status.STATUS_COMMITTED, () -> store.delete(id));
    }

    private void afterCompletion(int whenStatus, Runnable action) {
        if (transactions.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            if (whenStatus == Status.STATUS_COMMITTED) {
                action.run();
            }
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == whenStatus) {
                    action.run();
                }
            }
        });
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
