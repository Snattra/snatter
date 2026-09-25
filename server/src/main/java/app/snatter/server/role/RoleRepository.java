package app.snatter.server.role;

import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class RoleRepository {

    private static final RowMapper<Role> MAPPER = (rs, ctx) -> new Role(
        new RoleId(uuid(rs, "id")),
        rs.getString("name"),
        rs.getString("color"),
        rs.getInt("position"),
        Permission.fromMask(rs.getLong("permissions")),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));

    private static final String SELECT = """
        SELECT id, name, color, position, permissions, created_at, updated_at
        FROM role
        """;

    private final Jdbi jdbi;

    public RoleRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** All roles, highest position first. */
    public List<Role> findAll() {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "ORDER BY position DESC, created_at")
            .map(MAPPER)
            .list());
    }

    public Optional<Role> find(RoleId id) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE id = :id")
            .bind("id", id)
            .map(MAPPER)
            .findOne());
    }

    /** Roles assigned to the account, highest position first. */
    public List<Role> findByAccount(AccountId accountId) {
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT r.id, r.name, r.color, r.position, r.permissions, r.created_at, r.updated_at
                FROM role r
                JOIN account_role ar ON ar.role_id = r.id
                WHERE ar.account_id = :accountId
                ORDER BY r.position DESC
                """)
            .bind("accountId", accountId)
            .map(MAPPER)
            .list());
    }

    /** Inserts a role at position 0, moving every other role up one. Run in a transaction. */
    public Role insertAtBottom(RoleId id, String name, String color, Set<Permission> permissions) {
        Instant now = Instant.now();
        jdbi.useHandle(h -> {
            h.createUpdate("UPDATE role SET position = position + 1").execute();
            h.createUpdate("""
                INSERT INTO role (id, name, color, position, permissions, created_at, updated_at)
                VALUES (:id, :name, :color, 0, :permissions, :now, :now)
                """)
                .bind("id", id)
                .bind("name", name)
                .bind("color", color)
                .bind("permissions", Permission.toMask(permissions))
                .bind("now", now)
                .execute();
        });
        return new Role(id, name, color, 0, Set.copyOf(permissions), now, now);
    }

    public void update(Role role) {
        jdbi.useHandle(h -> h
            .createUpdate("""
                UPDATE role
                SET name = :name, color = :color, permissions = :permissions, updated_at = :now
                WHERE id = :id
                """)
            .bind("id", role.id())
            .bind("name", role.name())
            .bind("color", role.color())
            .bind("permissions", Permission.toMask(role.permissions()))
            .bind("now", Instant.now())
            .execute());
    }

    /** Moves a role to a position, shifting roles at or above it up by one. Run in a transaction. */
    public void moveTo(RoleId id, int position) {
        jdbi.useHandle(h -> {
            h.createUpdate("UPDATE role SET position = position + 1 WHERE position >= :position AND id <> :id")
                .bind("position", position)
                .bind("id", id)
                .execute();
            h.createUpdate("UPDATE role SET position = :position, updated_at = :now WHERE id = :id")
                .bind("position", position)
                .bind("id", id)
                .bind("now", Instant.now())
                .execute();
        });
    }

    public boolean delete(RoleId id) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM role WHERE id = :id")
            .bind("id", id)
            .execute()) == 1;
    }

    /** Whether any channel lists the role among its required roles. */
    public boolean isRequiredByChannel(RoleId id) {
        return jdbi.withHandle(h -> h
            .createQuery("SELECT EXISTS (SELECT 1 FROM channel_required_role WHERE role_id = :id)")
            .bind("id", id)
            .mapTo(Boolean.class)
            .one());
    }

    public void assign(AccountId accountId, RoleId roleId) {
        jdbi.useHandle(h -> h
            .createUpdate("""
                INSERT INTO account_role (account_id, role_id, assigned_at)
                VALUES (:accountId, :roleId, :now)
                ON CONFLICT DO NOTHING
                """)
            .bind("accountId", accountId)
            .bind("roleId", roleId)
            .bind("now", Instant.now())
            .execute());
    }

    public void unassign(AccountId accountId, RoleId roleId) {
        jdbi.useHandle(h -> h
            .createUpdate("DELETE FROM account_role WHERE account_id = :accountId AND role_id = :roleId")
            .bind("accountId", accountId)
            .bind("roleId", roleId)
            .execute());
    }
}
