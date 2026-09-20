package app.snatter.server.account;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.ids;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.blob.BlobId;
import app.snatter.server.role.RoleId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class AccountRepository {

    /** Issuer used in the identity table for accounts created with a local password. */
    public static final String LOCAL_ISSUER = "local";

    private static final RowMapper<Account> MAPPER = (rs, ctx) -> new Account(
        new AccountId(uuid(rs, "id")),
        rs.getString("username"),
        rs.getString("display_name"),
        id(rs, "avatar_blob_id", BlobId::new),
        ids(rs, "role_ids", RoleId::new),
        instant(rs, "created_at"));

    private static final String SELECT = """
        SELECT a.id, a.username, a.display_name, a.avatar_blob_id, a.created_at,
               array_remove(array_agg(ar.role_id), NULL) AS role_ids
        FROM account a
        LEFT JOIN account_role ar ON ar.account_id = a.id
        """;

    private static final String GROUP = " GROUP BY a.id";

    private final Jdbi jdbi;

    public AccountRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Optional<Account> findById(AccountId id) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE a.id = :id" + GROUP)
            .bind("id", id)
            .map(MAPPER)
            .findOne());
    }

    public Optional<Account> findByUsername(String username) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE lower(a.username) = lower(:username)" + GROUP)
            .bind("username", username)
            .map(MAPPER)
            .findOne());
    }

    public long count() {
        return jdbi.withHandle(h -> h.createQuery("SELECT count(*) FROM account").mapTo(Long.class).one());
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
    public Account createLocal(AccountId id, String username, String displayName, String passwordHash) {
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
        return new Account(id, username, displayName, null, List.of(), now);
    }

    public Optional<String> findPasswordHash(AccountId accountId) {
        return jdbi.withHandle(h -> h
            .createQuery("SELECT password_hash FROM local_credential WHERE account_id = :id")
            .bind("id", accountId)
            .mapTo(String.class)
            .findOne());
    }

    /** Records which invite let the account in and who created it. */
    public void linkInvite(AccountId id, String inviteCode, AccountId invitedBy) {
        jdbi.useHandle(h -> h
            .createUpdate("UPDATE account SET invite_code = :code, invited_by = :invitedBy WHERE id = :id")
            .bind("id", id)
            .bind("code", inviteCode)
            .bind("invitedBy", invitedBy)
            .execute());
    }

    /** Sets or clears (null) the avatar. Returns false if the account does not exist. */
    public boolean setAvatar(AccountId id, BlobId avatarId) {
        return jdbi.withHandle(h -> h
            .createUpdate("UPDATE account SET avatar_blob_id = :avatarId, updated_at = :now WHERE id = :id")
            .bind("id", id)
            .bind("avatarId", avatarId)
            .bind("now", Instant.now())
            .execute()) == 1;
    }
}
