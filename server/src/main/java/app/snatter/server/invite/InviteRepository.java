package app.snatter.server.invite;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.instant;

import app.snatter.server.account.AccountId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class InviteRepository {

    private static final RowMapper<Invite> MAPPER = (rs, ctx) -> new Invite(
        new InviteCode(rs.getString("code")),
        id(rs, "created_by", AccountId::new),
        instant(rs, "created_at"),
        instant(rs, "expires_at"),
        rs.getObject("max_uses", Integer.class),
        rs.getInt("uses"),
        instant(rs, "revoked_at"));

    private static final String SELECT = """
        SELECT code, created_by, created_at, expires_at, max_uses, uses, revoked_at
        FROM invite
        """;

    private final Jdbi jdbi;

    public InviteRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Returns false if the code is already taken. */
    public boolean insert(Invite invite) {
        return jdbi.withHandle(h -> h
            .createUpdate("""
                INSERT INTO invite (code, created_by, created_at, expires_at, max_uses, uses)
                VALUES (:code, :createdBy, :createdAt, :expiresAt, :maxUses, 0)
                ON CONFLICT (code) DO NOTHING
                """)
            .bind("code", invite.code())
            .bind("createdBy", invite.createdBy())
            .bind("createdAt", invite.createdAt())
            .bind("expiresAt", invite.expiresAt())
            .bind("maxUses", invite.maxUses())
            .execute()) == 1;
    }

    public Optional<Invite> find(InviteCode code) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE code = :code")
            .bind("code", code)
            .map(MAPPER)
            .findOne());
    }

    public List<Invite> findAll() {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "ORDER BY created_at DESC")
            .map(MAPPER)
            .list());
    }

    public List<Invite> findByCreator(AccountId creator) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE created_by = :creator ORDER BY created_at DESC")
            .bind("creator", creator)
            .map(MAPPER)
            .list());
    }

    /**
     * Counts one use if the invite is still usable at {@code now}. Returns the
     * invite as it was before the use, or empty if it could not be used.
     */
    public Optional<Invite> redeem(InviteCode code, Instant now) {
        return jdbi.withHandle(h -> h
            .createQuery("""
                UPDATE invite
                SET uses = uses + 1
                WHERE code = :code
                  AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > :now)
                  AND (max_uses IS NULL OR uses < max_uses)
                RETURNING code, created_by, created_at, expires_at, max_uses, uses - 1 AS uses, revoked_at
                """)
            .bind("code", code)
            .bind("now", now)
            .map(MAPPER)
            .findOne());
    }

    public boolean revoke(InviteCode code, Instant now) {
        return jdbi.withHandle(h -> h
            .createUpdate("UPDATE invite SET revoked_at = :now WHERE code = :code AND revoked_at IS NULL")
            .bind("code", code)
            .bind("now", now)
            .execute()) == 1;
    }
}
