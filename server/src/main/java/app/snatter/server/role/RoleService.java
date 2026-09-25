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
 * Role management with one rule: you may only create, change, delete, assign
 * or take away roles whose permissions you all hold yourself, and only grant
 * permissions you hold. The server owner is exempt.
 */
@ApplicationScoped
public class RoleService {

    /**
     * What an account is allowed to do, derived from its roles.
     *
     * @param roleIds assigned roles
     */
    public record Resolution(boolean owner, Set<Permission> permissions, Set<RoleId> roleIds) {
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

    /** Effective permissions of an account: the union of its roles, or everything for the owner. */
    public Resolution resolve(AccountId accountId) {
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        Set<RoleId> roleIds = new HashSet<>();
        for (Role role : roles.findByAccount(accountId)) {
            effective.addAll(role.permissions());
            roleIds.add(role.id());
        }
        boolean owner = settings.current().isOwner(accountId);
        return new Resolution(owner, owner ? Permission.all() : effective, Set.copyOf(roleIds));
    }

    public List<Role> list() {
        return roles.findAll();
    }

    @Transactional
    public Role create(AccountPrincipal actor, String name, String color, Set<Permission> permissions) {
        requireHeld(actor, permissions);
        Role role = roles.insertAtBottom(RoleId.newId(), name.strip(), color, permissions);
        events.fire(new RoleEvent.Created(role.id(), actor.accountId()));
        return role;
    }

    /** Null arguments mean "unchanged"; an empty color clears it. */
    @Transactional
    public Role update(AccountPrincipal actor, RoleId id, String name, String color, Integer position, Set<Permission> permissions) {
        Role role = requireManageable(actor, id);
        if (permissions != null) {
            requireHeld(actor, permissions);
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
            roles.moveTo(id, position);
        }
        events.fire(new RoleEvent.Updated(id, actor.accountId()));
        return require(id);
    }

    @Transactional
    public void delete(AccountPrincipal actor, RoleId id) {
        requireManageable(actor, id);
        if (id.equals(settings.current().newMemberRoleId())) {
            throw ApiException.conflict("role_in_use", "New members get this role; choose another in the server settings first");
        }
        if (roles.isRequiredByChannel(id)) {
            throw ApiException.conflict("role_in_use", "Channels require this role; remove it from them first");
        }
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
        Role role = requireManageable(actor, roleId);
        if (accounts.findById(accountId).isEmpty()) {
            throw ApiException.notFound("account_not_found", "No such account");
        }
        return role;
    }

    private Role requireManageable(AccountPrincipal actor, RoleId id) {
        Role role = require(id);
        requireHeld(actor, role.permissions());
        return role;
    }

    private Role require(RoleId id) {
        return roles.find(id).orElseThrow(() -> ApiException.notFound("role_not_found", "No such role"));
    }

    private static void requireHeld(AccountPrincipal actor, Set<Permission> permissions) {
        if (!actor.owner() && !actor.permissions().containsAll(permissions)) {
            throw new ApiException(403, "permission_escalation", "You can only manage roles and grant permissions you hold yourself");
        }
    }
}
