package app.snatter.server.blob;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class BlobRepository {

    private static final RowMapper<Blob> MAPPER = (rs, ctx) -> new Blob(
        new BlobId(uuid(rs, "id")),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getString("sha256"),
        id(rs, "owner_account_id", AccountId::new),
        rs.getString("purpose"),
        instant(rs, "created_at"));

    private final Jdbi jdbi;

    public BlobRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void insert(Blob blob) {
        jdbi.useHandle(h -> h
            .createUpdate("""
                INSERT INTO blob (id, content_type, size_bytes, sha256, owner_account_id, purpose, created_at)
                VALUES (:id, :contentType, :sizeBytes, :sha256, :ownerId, :purpose, :createdAt)
                """)
            .bind("id", blob.id())
            .bind("contentType", blob.contentType())
            .bind("sizeBytes", blob.sizeBytes())
            .bind("sha256", blob.sha256())
            .bind("ownerId", blob.ownerId())
            .bind("purpose", blob.purpose())
            .bind("createdAt", blob.createdAt())
            .execute());
    }

    public Optional<Blob> findById(BlobId id) {
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT id, content_type, size_bytes, sha256, owner_account_id, purpose, created_at
                FROM blob
                WHERE id = :id
                """)
            .bind("id", id)
            .map(MAPPER)
            .findOne());
    }

    public boolean delete(BlobId id) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM blob WHERE id = :id")
            .bind("id", id)
            .execute()) == 1;
    }
}
