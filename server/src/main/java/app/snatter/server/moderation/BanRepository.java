package app.snatter.server.moderation;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class BanRepository {

    private static final RowMapper<Ban> MAPPER = (rs, ctx) -> new Ban(
        new AccountId(uuid(rs, "account_id")),
        rs.getString("reason"),
        id(rs, "banned_by", AccountId::new),
        instant(rs, "created_at"));

    private static final String SELECT = "SELECT account_id, reason, banned_by, created_at FROM ban ";

    private final Jdbi jdbi;

    public BanRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Every ban, newest first. */
    public List<Ban> findAll() {
        return jdbi.withHandle(h -> h.createQuery(SELECT + "ORDER BY created_at DESC").map(MAPPER).list());
    }

    public Optional<Ban> find(AccountId accountId) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE account_id = :accountId")
            .bind("accountId", accountId)
            .map(MAPPER)
            .findOne());
    }

    /** Stores the ban, replacing an earlier one for the same account. */
    public void save(Ban ban) {
        jdbi.useHandle(h -> h
            .createUpdate("""
                INSERT INTO ban (account_id, reason, banned_by, created_at)
                VALUES (:accountId, :reason, :bannedBy, :createdAt)
                ON CONFLICT (account_id) DO UPDATE
                SET reason = excluded.reason, banned_by = excluded.banned_by, created_at = excluded.created_at
                """)
            .bind("accountId", ban.accountId())
            .bind("reason", ban.reason())
            .bind("bannedBy", ban.bannedBy())
            .bind("createdAt", ban.createdAt())
            .execute());
    }

    public void delete(AccountId accountId) {
        jdbi.useHandle(h -> h
            .createUpdate("DELETE FROM ban WHERE account_id = :accountId")
            .bind("accountId", accountId)
            .execute());
    }
}
