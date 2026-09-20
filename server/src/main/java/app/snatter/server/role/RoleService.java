package app.snatter.server.role;

import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.settings.ServerSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Role management with Discord's two rules: you may only touch roles below
 * your own highest role, and you may only grant permissions you hold. The
 * server owner is exempt from both.
 */
@ApplicationScoped
public class RoleService {

    /** What an account is allowed to do, derived from its roles. */
    public record Resolution(Set<Permission> permissions, int highestPosition) {
    }

    private final RoleRepository roles;
    private final AccountRepository accounts;
    private final ServerSettingsService settings;

    public RoleService(RoleRepository roles, AccountRepository accounts, ServerSettingsService settings) {
        this.roles = roles;
        this.accounts = accounts;
        this.settings = settings;
    }

    /** Effective permissions of an account: its roles plus the default role, or everything for the owner. */
    public Resolution resolve(AccountId accountId) {
        if (settings.current().isOwner(accountId)) {
            return new Resolution(Permission.all(), Integer.MAX_VALUE);
        }
        EnumSet<Permission> effective = EnumSet.copyOf(roles.findDefault().permissions());
        int highest = 0;
        for (Role role : roles.findByAccount(accountId)) {
            effective.addAll(role.permissions());
            highest = Math.max(highest, role.position());
        }
        return new Resolution(effective, highest);
    }

    public List<Role> list() {
        return roles.findAll();
    }

    @Transactional
    public Role create(AccountPrincipal actor, String name, String color, Set<Permission> permissions) {
        requireNoEscalation(actor, permissions);
        return roles.insertAtBottom(RoleId.newId(), name.strip(), color, permissions);
    }

    /** Null arguments mean "unchanged"; an empty color clears it. */
    @Transactional
    public Role update(AccountPrincipal actor, RoleId id, String name, String color, Integer position, Set<Permission> permissions) {
        Role role = require(id);
        requireOutranks(actor, role);
        if (role.isDefault() && (name != null || color != null || position != null)) {
            throw ApiException.badRequest("default_role", "Only the permissions of the default role can be changed");
        }
        if (permissions != null) {
            requireNoEscalation(actor, permissions);
            role = role.withPermissions(permissions);
        }
        if (name != null) {
            role = role.withName(name.strip());
        }
        if (color != null) {
            role = role.withColor(color.isBlank() ? null : color);
        }
        roles.update(role);
        if (position != null && position != role.position()) {
            if (!actor.owner() && position >= actor.highestRolePosition()) {
                throw new ApiException(403, "role_hierarchy", "You cannot move a role to or above your own highest role");
            }
            roles.moveTo(id, position);
        }
        return require(id);
    }

    @Transactional
    public void delete(AccountPrincipal actor, RoleId id) {
        Role role = require(id);
        if (role.isDefault()) {
            throw ApiException.badRequest("default_role", "The default role cannot be deleted");
        }
        requireOutranks(actor, role);
        roles.delete(id);
    }

    @Transactional
    public void assign(AccountPrincipal actor, AccountId accountId, RoleId roleId) {
        Role role = requireAssignable(actor, accountId, roleId);
        roles.assign(accountId, role.id());
    }

    @Transactional
    public void unassign(AccountPrincipal actor, AccountId accountId, RoleId roleId) {
        Role role = requireAssignable(actor, accountId, roleId);
        roles.unassign(accountId, role.id());
    }

    private Role requireAssignable(AccountPrincipal actor, AccountId accountId, RoleId roleId) {
        Role role = require(roleId);
        if (role.isDefault()) {
            throw ApiException.badRequest("default_role", "Every member has the default role; it cannot be assigned or removed");
        }
        requireOutranks(actor, role);
        if (accounts.findById(accountId).isEmpty()) {
            throw ApiException.notFound("account_not_found", "No such account");
        }
        return role;
    }

    private Role require(RoleId id) {
        return roles.find(id).orElseThrow(() -> ApiException.notFound("role_not_found", "No such role"));
    }

    private static void requireOutranks(AccountPrincipal actor, Role role) {
        if (!actor.owner() && role.position() >= actor.highestRolePosition()) {
            throw new ApiException(403, "role_hierarchy", "You can only manage roles below your own highest role");
        }
    }

    private static void requireNoEscalation(AccountPrincipal actor, Set<Permission> permissions) {
        if (!actor.owner() && !actor.permissions().containsAll(permissions)) {
            throw new ApiException(403, "permission_escalation", "You can only grant permissions you hold yourself");
        }
    }
}
