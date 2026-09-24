package app.snatter.server.role;

import java.time.Instant;
import java.util.Set;

/**
 * A named bundle of permissions.
 *
 * @param color     {@code #RRGGBB} or null
 * @param position  seniority; higher outranks lower, the default role is 0
 * @param isDefault the one role every member has implicitly
 */
public record Role(
        RoleId id,
        String name,
        String color,
        int position,
        Set<Permission> permissions,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt) {

    /** Id of the default role, seeded by the roles migration. */
    public static final RoleId DEFAULT_ID = RoleId.fromString("00000000-0000-7000-8000-000000000001");

    public Role withName(String name) {
        return new Role(id, name, color, position, permissions, isDefault, createdAt, updatedAt);
    }

    public Role withColor(String color) {
        return new Role(id, name, color, position, permissions, isDefault, createdAt, updatedAt);
    }

    public Role withPermissions(Set<Permission> permissions) {
        return new Role(id, name, color, position, permissions, isDefault, createdAt, updatedAt);
    }
}
