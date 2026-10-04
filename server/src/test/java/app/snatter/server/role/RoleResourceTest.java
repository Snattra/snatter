package app.snatter.server.role;

import static app.snatter.client.model.PermissionDto.BAN_MEMBERS;
import static app.snatter.client.model.PermissionDto.CONNECT;
import static app.snatter.client.model.PermissionDto.CREATE_INVITE;
import static app.snatter.client.model.PermissionDto.MANAGE_MESSAGES;
import static app.snatter.client.model.PermissionDto.MANAGE_ROLES;
import static app.snatter.client.model.PermissionDto.MANAGE_SERVER;
import static app.snatter.client.model.PermissionDto.MOVE_MEMBERS;
import static app.snatter.client.model.PermissionDto.MUTE_MEMBERS;
import static app.snatter.client.model.PermissionDto.SEND_MESSAGES;
import static app.snatter.client.model.PermissionDto.SPEAK;
import static app.snatter.client.model.PermissionDto.STREAM;
import static app.snatter.client.model.PermissionDto.TIMEOUT_MEMBERS;
import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.accountsApi;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.rolesApi;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.api.RolesApi;
import app.snatter.client.model.PermissionDto;
import app.snatter.client.model.PermissionSetDto;
import app.snatter.client.model.RoleCreateDto;
import app.snatter.client.model.RoleDto;
import app.snatter.client.model.RoleUpdateDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RoleResourceTest {

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    private static RoleDto find(List<RoleDto> roles, UUID id) {
        return roles.stream().filter(role -> role.getId().equals(id)).findFirst().orElseThrow();
    }

    private static Set<PermissionDto> permissionsOf(TestUsers.User user) {
        return Set.copyOf(rolesApi(user).getMyPermissions().getPermissions());
    }

    @Test
    void newMembersGetTheUserRoleAndTheStandardRolesExist() {
        TestUsers.User member = TestUsers.register();
        Set<PermissionDto> allButServerSettings = Arrays.stream(Permission.values())
            .filter(p -> p != Permission.MANAGE_SERVER).map(p -> PermissionDto.fromValue(p.name())).collect(toSet());
        List<RoleDto> roles = rolesApi(member).listRoles();
        RoleDto user = find(roles, TestDataService.USER_ROLE);
        assertEquals("User", user.getName());
        assertEquals(Set.of(CREATE_INVITE, SEND_MESSAGES, CONNECT, SPEAK, STREAM), Set.copyOf(user.getPermissions()));
        assertEquals(Set.of(CREATE_INVITE, SEND_MESSAGES, CONNECT, SPEAK, STREAM,
                TIMEOUT_MEMBERS, BAN_MEMBERS, MANAGE_MESSAGES, MUTE_MEMBERS, MOVE_MEMBERS),
            Set.copyOf(find(roles, TestDataService.MODERATOR_ROLE).getPermissions()));
        assertEquals(allButServerSettings, Set.copyOf(find(roles, TestDataService.ADMIN_ROLE).getPermissions()));

        assertEquals(List.of(TestDataService.USER_ROLE), accountsApi(member).getCurrentAccount().getRoleIds());
        PermissionSetDto memberPermissions = rolesApi(member).getMyPermissions();
        assertFalse(memberPermissions.getOwner());
        assertFalse(memberPermissions.getPermissions().contains(MANAGE_ROLES));
        assertTrue(memberPermissions.getPermissions().contains(SEND_MESSAGES));

        PermissionSetDto ownerPermissions = rolesApi(owner).getMyPermissions();
        assertTrue(ownerPermissions.getOwner());
        assertTrue(ownerPermissions.getPermissions().containsAll(List.of(MANAGE_SERVER, MANAGE_ROLES, BAN_MEMBERS)));

        assertApiStatus(401, () -> rolesApi().listRoles());
    }

    @Test
    void newRolesGoToTheBottomAndCanBeAssigned() {
        TestUsers.User member = TestUsers.register();
        UUID first = data.createRole("First", TIMEOUT_MEMBERS);
        UUID second = data.createRole("Second", MUTE_MEMBERS);
        List<RoleDto> roles = rolesApi(member).listRoles();
        assertEquals(0, find(roles, second).getPosition());
        assertEquals(1, find(roles, first).getPosition());
        assertEquals(List.of(TIMEOUT_MEMBERS), find(roles, first).getPermissions());
        assertNull(find(roles, first).getColor());

        data.assignRole(member.id(), first);
        data.assignRole(member.id(), first); // idempotent
        assertEquals(Set.of(TestDataService.USER_ROLE, first), Set.copyOf(accountsApi(member).getCurrentAccount().getRoleIds()));
        assertTrue(permissionsOf(member).contains(TIMEOUT_MEMBERS));

        data.unassignRole(member.id(), first);
        assertFalse(permissionsOf(member).contains(TIMEOUT_MEMBERS));
    }

    @Test
    void managersOnlyManageRolesWithinTheirOwnPermissions() {
        UUID manager = data.createRole("Manager", MANAGE_ROLES, TIMEOUT_MEMBERS);
        UUID settings = data.createRole("Settings", MANAGE_SERVER, BAN_MEMBERS);
        TestUsers.User mgr = TestUsers.register();
        data.assignRole(mgr.id(), manager);
        assertTrue(permissionsOf(mgr).containsAll(List.of(MANAGE_ROLES, TIMEOUT_MEMBERS)));
        TestUsers.User target = TestUsers.register();
        RolesApi asManager = rolesApi(mgr);
        // Can create a role with a subset of own permissions; it lands at the bottom.
        RoleDto helper = asManager.createRole(new RoleCreateDto().name("Helper").permissions(List.of(TIMEOUT_MEMBERS)));
        assertEquals(0, helper.getPosition());
        // Cannot grant what they do not hold.
        assertApiError(403, "permission_escalation",
            () -> asManager.createRole(new RoleCreateDto().name("Sneaky").permissions(List.of(BAN_MEMBERS))));
        assertApiError(403, "permission_escalation",
            () -> asManager.updateRole(helper.getId(), new RoleUpdateDto().permissions(List.of(MANAGE_SERVER))));
        // Cannot touch a role holding permissions they lack.
        assertApiError(403, "permission_escalation", () -> asManager.updateRole(settings, new RoleUpdateDto().name("Pwned")));
        assertApiError(403, "permission_escalation", () -> asManager.deleteRole(settings));
        assertApiError(403, "permission_escalation", () -> asManager.assignRole(target.id(), settings));
        // But can manage everything within their permissions, wherever it sits in the list.
        RoleDto renamed = asManager.updateRole(helper.getId(),
            new RoleUpdateDto().name("Helper renamed").color("#123ABC").position(50));
        assertEquals("Helper renamed", renamed.getName());
        assertEquals("#123ABC", renamed.getColor());
        assertEquals(50, renamed.getPosition());
        asManager.assignRole(target.id(), helper.getId());
        asManager.deleteRole(helper.getId());
        assertEquals(List.of(TestDataService.USER_ROLE), accountsApi(target).getCurrentAccount().getRoleIds());
    }

    @Test
    void theNewMemberRoleSettingDecidesWhatNewMembersGet() {
        UUID greeter = data.createRole("Greeter", CREATE_INVITE);
        assertEquals(greeter, data.updateSettings(new ServerSettingsUpdateDto().newMemberRoleId(greeter.toString())).getNewMemberRoleId());
        TestUsers.User greeted = TestUsers.register();
        assertEquals(List.of(greeter), accountsApi(greeted).getCurrentAccount().getRoleIds());
        assertApiError(409, "role_in_use", () -> data.deleteRole(greeter));

        // Without a role new members can only browse.
        assertNull(data.updateSettings(new ServerSettingsUpdateDto().newMemberRoleId("")).getNewMemberRoleId());
        TestUsers.User browser = TestUsers.register();
        assertEquals(List.of(), accountsApi(browser).getCurrentAccount().getRoleIds());
        assertEquals(Set.of(), permissionsOf(browser));
        assertFalse(channelsApi(browser).listChannels().isEmpty());

        assertApiError(400, "role_not_found",
            () -> data.updateSettings(new ServerSettingsUpdateDto().newMemberRoleId(UUID.randomUUID().toString())));
    }

    @Test
    void rolesRequireTheManageRolesPermissionAndReportNotFound() {
        RolesApi asMember = rolesApi(TestUsers.register());
        assertApiError(403, "forbidden", () -> asMember.createRole(new RoleCreateDto().name("Nope")));
        assertApiError(403, "forbidden", () -> asMember.deleteRole(UUID.randomUUID()));

        RolesApi asOwner = rolesApi(owner);
        assertApiError(404, "role_not_found", () -> asOwner.updateRole(UUID.randomUUID(), new RoleUpdateDto().name("x")));
        UUID role = data.createRole("Temp");
        assertApiError(404, "account_not_found", () -> asOwner.assignRole(UUID.randomUUID(), role));
        assertApiError(400, "validation_failed", () -> asOwner.createRole(new RoleCreateDto().name("").permissions(List.of(SPEAK))));
        assertApiError(400, "validation_failed", () -> asOwner.createRole(new RoleCreateDto().name("Bad colour").color("red")));
    }
}
