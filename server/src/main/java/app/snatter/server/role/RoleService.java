package app.snatter.server.role;

import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.settings.ServerSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Role management with Discord's two rules: you may only touch roles below
 * your own highest role, and you may only grant permissions you hold. The
 * server owner is exempt from both.
 */
@ApplicationScoped
public class RoleService {

    /**
     * What an account is allowed to do, derived from its roles.
     *
     * @param roleIds         assigned roles, excluding the default role
     * @param highestPosition position of the most senior assigned role, 0 with none,
     *                        {@code Integer.MAX_VALUE} for the owner
     */
    public record Resolution(boolean owner, Set<Permission> permissions, int highestPosition, Set<RoleId> roleIds) {
    }

    private final RoleRepository roles;
    private final AccountRepository accounts;
    private final ServerSettingsService settings;
    private final Event<RoleEvent> events;

    public RoleService(RoleRepository roles, AccountRepository accounts, ServerSettingsService settings, Event<RoleEvent> events) {
        this.roles = roles;
        this.accounts = accounts;
        this.settings = settings;
        this.events = events;
    }

    /**
     * Effective permissions of an account: its roles plus the default role,
     * widened by {@link Permission#ADMINISTRATOR}, or everything for the owner.
     */
    public Resolution resolve(AccountId accountId) {
        if (settings.current().isOwner(accountId)) {
            return new Resolution(true, Permission.all(), Integer.MAX_VALUE, Set.of());
        }
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        effective.addAll(roles.findDefault().permissions());
        Set<RoleId> roleIds = new HashSet<>();
        int highest = 0;
        for (Role role : roles.findByAccount(accountId)) {
            effective.addAll(role.permissions());
            roleIds.add(role.id());
            highest = Math.max(highest, role.position());
        }
        if (effective.contains(Permission.ADMINISTRATOR)) {
            effective.addAll(Permission.ADMINISTRATOR_IMPLIES);
        }
        return new Resolution(false, effective, highest, Set.copyOf(roleIds));
    }

    public List<Role> list() {
        return roles.findAll();
    }

    @Transactional
    public Role create(AccountPrincipal actor, String name, String color, Set<Permission> permissions) {
        requireNoEscalation(actor, permissions);
        Role role = roles.insertAtBottom(RoleId.newId(), name.strip(), color, permissions);
        events.fire(new RoleEvent.Created(role.id(), actor.accountId()));
        return role;
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
        events.fire(new RoleEvent.Updated(id, actor.accountId()));
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
        events.fire(new RoleEvent.Deleted(id, actor.accountId()));
    }

    @Transactional
    public void assign(AccountPrincipal actor, AccountId accountId, RoleId roleId) {
        Role role = requireAssignable(actor, accountId, roleId);
        roles.assign(accountId, role.id());
        events.fire(new RoleEvent.Assigned(role.id(), accountId, actor.accountId()));
    }

    @Transactional
    public void unassign(AccountPrincipal actor, AccountId accountId, RoleId roleId) {
        Role role = requireAssignable(actor, accountId, roleId);
        roles.unassign(accountId, role.id());
        events.fire(new RoleEvent.Unassigned(role.id(), accountId, actor.accountId()));
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
