package app.snatter.server.auth;

import app.snatter.server.account.AccountId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.RoleId;
import java.security.Principal;
import java.util.Set;

/**
 * The authenticated account behind a request, available from
 * {@code SecurityIdentity}, together with what it may do.
 *
 * @param owner               whether this is the server owner, who may do everything
 * @param permissions         effective server-level permissions; every permission for the owner
 * @param highestRolePosition position of the most senior assigned role, 0 with none,
 *                            {@code Integer.MAX_VALUE} for the owner
 * @param roleIds             assigned roles, excluding the implicit default role
 */
public record AccountPrincipal(
        AccountId accountId,
        String username,
        SessionId sessionId,
        boolean owner,
        Set<Permission> permissions,
        int highestRolePosition,
        Set<RoleId> roleIds) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** The owner or an administrator: not bound by channel overwrites. */
    public boolean bypassesOverwrites() {
        return owner || has(Permission.ADMINISTRATOR);
    }

    /** For Quarkus permission checks, which pass the permission by name. */
    public boolean hasNamed(String permission) {
        try {
            return has(Permission.valueOf(permission));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }
}
