package app.snatter.server.account;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.ids;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.blob.BlobId;
import app.snatter.server.role.RoleId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
        instant(rs, "timed_out_until"),
        instant(rs, "muted_at"),
        instant(rs, "banned_at"),
        instant(rs, "created_at"));

    private static final String SELECT = """
        SELECT a.id, a.username, a.display_name, a.avatar_blob_id, a.timed_out_until, a.muted_at, a.created_at,
               (SELECT b.created_at FROM ban b WHERE b.account_id = a.id) AS banned_at,
               json_group_array(ar.role_id) FILTER (WHERE ar.role_id IS NOT NULL) AS role_ids
        FROM account a
        LEFT JOIN account_role ar ON ar.account_id = a.id
        """;

    private static final String GROUP = " GROUP BY a.id";

    private final Jdbi jdbi;

    public AccountRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Every account, by username. */
    public List<Account> findAll() {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + GROUP + " ORDER BY lower(a.username)")
            .map(MAPPER)
            .list());
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

    /** Which of these accounts exist. */
    public Set<AccountId> existing(Collection<AccountId> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return jdbi.withHandle(h -> h
            .createQuery("SELECT id FROM account WHERE id IN (<ids>)")
            .bindList("ids", List.copyOf(ids))
            .map((rs, ctx) -> new AccountId(uuid(rs, "id")))
            .collect(Collectors.toSet()));
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
        return new Account(id, username, displayName, null, List.of(), null, null, null, now);
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

    /** Starts, extends or, with null, lifts the member's timeout. */
    public void setTimedOutUntil(AccountId id, Instant until) {
        jdbi.useHandle(h -> h
            .createUpdate("UPDATE account SET timed_out_until = :until, updated_at = :now WHERE id = :id")
            .bind("id", id)
            .bind("until", until)
            .bind("now", Instant.now())
            .execute());
    }

    /**
     * Mutes the member in voice from {@code at}, or with null unmutes them;
     * false when they already were, or were not, muted.
     */
    public boolean setMutedAt(AccountId id, Instant at) {
        return jdbi.withHandle(h -> h
            .createUpdate("""
                UPDATE account SET muted_at = :at, updated_at = :now
                WHERE id = :id AND (muted_at IS NULL) <> (:at IS NULL)
                """)
            .bind("id", id)
            .bind("at", at)
            .bind("now", Instant.now())
            .execute()) == 1;
    }
}
