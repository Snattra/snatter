package app.snatter.server.auth;

import app.snatter.server.account.AccountId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.RoleId;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;

/**
 * The authenticated account behind a request, available from
 * {@code SecurityIdentity}, together with what it may do. Access checks read
 * only this record, never the database.
 *
 * @param owner         whether this is the server owner, who may do everything
 * @param permissions   the union of the assigned roles, without SPEAK while muted and none during a timeout;
 *                      every permission for the owner
 * @param roleIds       assigned roles
 * @param timedOutUntil end of the current timeout, or null if there is none
 */
public record AccountPrincipal(
        AccountId accountId,
        String username,
        SessionId sessionId,
        boolean owner,
        Set<Permission> permissions,
        Set<RoleId> roleIds,
        Instant timedOutUntil) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** Whether the account holds at least one of the roles. */
    public boolean hasAnyRole(Set<RoleId> roles) {
        return roles.stream().anyMatch(roleIds::contains);
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
