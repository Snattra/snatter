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

    public Optional<Session> findByTokenHash(String tokenHash) {
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT id, account_id, created_at, expires_at, last_seen_at
                FROM session
                WHERE token_hash = :tokenHash
                """)
            .bind("tokenHash", tokenHash)
            .map(MAPPER)
            .findOne());
    }

    public void touch(SessionId id, Instant lastSeenAt) {
        jdbi.useHandle(h -> h
            .createUpdate("UPDATE session SET last_seen_at = :lastSeenAt WHERE id = :id")
            .bind("id", id)
            .bind("lastSeenAt", lastSeenAt)
            .execute());
    }

    public boolean delete(SessionId id) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM session WHERE id = :id")
            .bind("id", id)
            .execute()) == 1;
    }

    public int deleteExpired(Instant now) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM session WHERE expires_at <= :now")
            .bind("now", now)
            .execute());
    }
}
