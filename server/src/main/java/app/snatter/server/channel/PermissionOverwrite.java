package app.snatter.server.channel;

import app.snatter.server.account.AccountId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.RoleId;
import java.util.Objects;
import java.util.Set;

/**
 * Refines permissions in one channel for the members of a role or for one
 * member. Exactly one of {@code roleId} and {@code accountId} is set.
 */
public record PermissionOverwrite(RoleId roleId, AccountId accountId, Set<Permission> allow, Set<Permission> deny) {

    public PermissionOverwrite {
        if ((roleId == null) == (accountId == null)) {
            throw new IllegalArgumentException("exactly one of roleId and accountId must be set");
        }
        allow = Set.copyOf(allow);
        deny = Set.copyOf(deny);
    }

    public static PermissionOverwrite forRole(RoleId roleId, Set<Permission> allow, Set<Permission> deny) {
        return new PermissionOverwrite(roleId, null, allow, deny);
    }

    public static PermissionOverwrite forAccount(AccountId accountId, Set<Permission> allow, Set<Permission> deny) {
        return new PermissionOverwrite(null, accountId, allow, deny);
    }

    /** The same target with no permissions changed, which is how an absent overwrite behaves. */
    public PermissionOverwrite cleared() {
        return new PermissionOverwrite(roleId, accountId, Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return allow.isEmpty() && deny.isEmpty();
    }

    public boolean sameTarget(PermissionOverwrite other) {
        return Objects.equals(roleId, other.roleId) && Objects.equals(accountId, other.accountId);
    }
}
