package app.snatter.server.role;

import app.snatter.api.RolesApi;
import app.snatter.api.model.PermissionSetDto;
import app.snatter.api.model.RoleCreateDto;
import app.snatter.api.model.RoleDto;
import app.snatter.api.model.RoleUpdateDto;
import app.snatter.server.account.AccountId;
import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import org.jboss.resteasy.reactive.RestResponse;

public class RoleResource implements RolesApi {

    private final RoleService roles;
    private final SecurityIdentity identity;

    public RoleResource(RoleService roles, SecurityIdentity identity) {
        this.roles = roles;
        this.identity = identity;
    }

    @Override
    @Authenticated
    public RestResponse<List<RoleDto>> listRoles() {
        return RestResponse.ok(roles.list().stream().map(RoleResource::toDto).toList());
    }

    @Override
    @PermissionsAllowed("MANAGE_ROLES")
    public RestResponse<RoleDto> createRole(RoleCreateDto body) {
        Set<Permission> permissions = body.getPermissions() == null ? Set.of() : PermissionDtos.fromDto(body.getPermissions());
        return RestResponse.status(Response.Status.CREATED, toDto(roles.create(actor(), body.getName(), body.getColor(), permissions)));
    }

    @Override
    @PermissionsAllowed("MANAGE_ROLES")
    public RestResponse<RoleDto> updateRole(RoleId id, RoleUpdateDto body) {
        Set<Permission> permissions = body.getPermissions() == null ? null : PermissionDtos.fromDto(body.getPermissions());
        return RestResponse.ok(toDto(roles.update(actor(), id, body.getName(), body.getColor(), body.getPosition(), permissions)));
    }

    @Override
    @PermissionsAllowed("MANAGE_ROLES")
    public RestResponse<Void> deleteRole(RoleId id) {
        roles.delete(actor(), id);
        return RestResponse.noContent();
    }

    @Override
    @PermissionsAllowed("MANAGE_ROLES")
    public RestResponse<Void> assignRole(AccountId id, RoleId roleId) {
        roles.assign(actor(), id, roleId);
        return RestResponse.noContent();
    }

    @Override
    @PermissionsAllowed("MANAGE_ROLES")
    public RestResponse<Void> unassignRole(AccountId id, RoleId roleId) {
        roles.unassign(actor(), id, roleId);
        return RestResponse.noContent();
    }

    @Override
    @Authenticated
    public RestResponse<PermissionSetDto> getMyPermissions() {
        AccountPrincipal actor = actor();
        return RestResponse.ok(new PermissionSetDto()
            .owner(actor.owner())
            .permissions(PermissionDtos.toDto(actor.permissions())));
    }

    private AccountPrincipal actor() {
        return (AccountPrincipal) identity.getPrincipal();
    }

    public static RoleDto toDto(Role role) {
        return new RoleDto()
            .id(role.id())
            .name(role.name())
            .color(role.color())
            .position(role.position())
            .permissions(PermissionDtos.toDto(role.permissions()))
            .createdAt(role.createdAt());
    }
}
