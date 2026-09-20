package app.snatter.server.account;

import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class AccountRepository {

    /** Issuer used in the identity table for accounts created with a local password. */
    public static final String LOCAL_ISSUER = "local";

    private static final RowMapper<Account> MAPPER = (rs, ctx) -> new Account(
        uuid(rs, "id"),
        rs.getString("username"),
        rs.getString("display_name"),
        instant(rs, "created_at"));

    private static final String SELECT = """
        SELECT id, username, display_name, created_at
        FROM account
        """;

    private final Jdbi jdbi;

    public AccountRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Optional<Account> findById(UUID id) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE id = :id")
            .bind("id", id)
            .map(MAPPER)
            .findOne());
    }

    public Optional<Account> findByUsername(String username) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE lower(username) = lower(:username)")
            .bind("username", username)
            .map(MAPPER)
            .findOne());
    }

    public boolean usernameExists(String username) {
        return jdbi.withHandle(h -> h
            .createQuery("SELECT 1 FROM account WHERE lower(username) = lower(:username)")
            .bind("username", username)
            .mapTo(Integer.class)
            .findOne()
            .isPresent());
    }

    /**
     * Inserts a local account together with its identity row and password.
     * Callers must run inside a transaction.
     */
    public Account createLocal(UUID id, String username, String displayName, String passwordHash) {
        Instant now = Instant.now();
        jdbi.useHandle(h -> {
            h.createUpdate("""
                INSERT INTO account (id, username, display_name, created_at, updated_at)
                VALUES (:id, :username, :displayName, :now, :now)
                """)
                .bind("id", id)
                .bind("username", username)
                .bind("displayName", displayName)
                .bind("now", now)
                .execute();
            h.createUpdate("""
                INSERT INTO identity (issuer, subject, account_id, created_at)
                VALUES (:issuer, :subject, :accountId, :now)
                """)
                .bind("issuer", LOCAL_ISSUER)
                .bind("subject", id.toString())
                .bind("accountId", id)
                .bind("now", now)
                .execute();
            h.createUpdate("""
                INSERT INTO local_credential (account_id, password_hash, updated_at)
                VALUES (:accountId, :hash, :now)
                """)
                .bind("accountId", id)
                .bind("hash", passwordHash)
                .bind("now", now)
                .execute();
        });
        return new Account(id, username, displayName, now);
    }

    public Optional<String> findPasswordHash(UUID accountId) {
        return jdbi.withHandle(h -> h
            .createQuery("SELECT password_hash FROM local_credential WHERE account_id = :id")
            .bind("id", accountId)
            .mapTo(String.class)
            .findOne());
    }
}
