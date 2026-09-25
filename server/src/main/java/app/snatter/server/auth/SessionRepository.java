package app.snatter.server.auth;

import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class SessionRepository {

    private static final RowMapper<Session> MAPPER = (rs, ctx) -> new Session(
        new SessionId(uuid(rs, "id")),
        new AccountId(uuid(rs, "account_id")),
        instant(rs, "created_at"),
        instant(rs, "expires_at"),
        instant(rs, "last_seen_at"));

    private final Jdbi jdbi;

    public SessionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void insert(Session session, String tokenHash, String ip, String userAgent) {
        jdbi.useHandle(h -> h
            .createUpdate("""
                INSERT INTO session (id, account_id, token_hash, created_at, expires_at, last_seen_at, created_ip, user_agent)
                VALUES (:id, :accountId, :tokenHash, :createdAt, :expiresAt, :lastSeenAt, CAST(:ip AS inet), :userAgent)
                """)
            .bind("id", session.id())
            .bind("accountId", session.accountId())
            .bind("tokenHash", tokenHash)
            .bind("createdAt", session.createdAt())
            .bind("expiresAt", session.expiresAt())
            .bind("lastSeenAt", session.lastSeenAt())
            .bind("ip", ip)
            .bind("userAgent", userAgent)
            .execute());
    }

    /**
     * The session for a token. Sessions of banned accounts are never found,
     * even one opened by a login that raced with the ban.
     */
    public Optional<Session> findByTokenHash(String tokenHash) {
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT id, account_id, created_at, expires_at, last_seen_at
                FROM session s
                WHERE token_hash = :tokenHash
                  AND NOT EXISTS (SELECT 1 FROM ban b WHERE b.account_id = s.account_id)
                """)
            .bind("tokenHash", tokenHash)
            .map(MAPPER)
            .findOne());
    }

    /** Records use of a session and moves its expiry; returns false if the session is gone. */
    public boolean touch(SessionId id, Instant lastSeenAt, Instant expiresAt) {
        return jdbi.withHandle(h -> h
            .createUpdate("UPDATE session SET last_seen_at = :lastSeenAt, expires_at = :expiresAt WHERE id = :id")
            .bind("id", id)
            .bind("lastSeenAt", lastSeenAt)
            .bind("expiresAt", expiresAt)
            .execute()) == 1;
    }

    public boolean delete(SessionId id) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM session WHERE id = :id")
            .bind("id", id)
            .execute()) == 1;
    }

    public void deleteByAccount(AccountId accountId) {
        jdbi.useHandle(h -> h
            .createUpdate("DELETE FROM session WHERE account_id = :accountId")
            .bind("accountId", accountId)
            .execute());
    }

    public int deleteExpired(Instant now) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM session WHERE expires_at <= :now")
            .bind("now", now)
            .execute());
    }
}
