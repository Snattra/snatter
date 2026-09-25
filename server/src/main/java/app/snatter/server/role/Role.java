package app.snatter.server.role;

import java.time.Instant;
import java.util.Set;

/**
 * A named bundle of permissions.
 *
 * @param color     {@code #RRGGBB} or null
 * @param position  display order, highest first
 */
public record Role(
        RoleId id,
        String name,
        String color,
        int position,
        Set<Permission> permissions,
        Instant createdAt,
        Instant updatedAt) {

    public Role withName(String name) {
        return new Role(id, name, color, position, permissions, createdAt, updatedAt);
    }

    public Role withColor(String color) {
        return new Role(id, name, color, position, permissions, createdAt, updatedAt);
    }

    public Role withPermissions(Set<Permission> permissions) {
        return new Role(id, name, color, position, permissions, createdAt, updatedAt);
    }
}
