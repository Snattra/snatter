package app.snatter.server.role;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RoleResourceTest {

    private static io.restassured.response.Response create(String token, Map<String, Object> body) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(body).post("/api/v1/roles");
    }

    private static io.restassured.response.Response permissionsOf(String token) {
        return given().header("Authorization", "Bearer " + token).get("/api/v1/accounts/me/permissions");
    }

    @Test
    void newMembersGetTheUserRoleAndTheStandardRolesExist() {
        TestUsers.User member = TestUsers.register();
        Object[] allButServerSettings = Arrays.stream(Permission.values())
            .filter(p -> p != Permission.MANAGE_SERVER).map(Enum::name).toArray();
        given().header("Authorization", "Bearer " + member.token()).get("/api/v1/roles")
            .then().statusCode(200)
            .body("find { it.id == '" + TestUsers.USER_ROLE + "' }.name", equalTo("User"))
            .body("find { it.id == '" + TestUsers.USER_ROLE + "' }.permissions",
                containsInAnyOrder("CREATE_INVITE", "SEND_MESSAGES", "CONNECT", "SPEAK", "STREAM"))
            .body("find { it.id == '" + TestUsers.MODERATOR_ROLE + "' }.permissions",
                containsInAnyOrder("CREATE_INVITE", "SEND_MESSAGES", "CONNECT", "SPEAK", "STREAM",
                    "KICK_MEMBERS", "BAN_MEMBERS", "MANAGE_MESSAGES", "MUTE_MEMBERS", "MOVE_MEMBERS"))
            .body("find { it.id == '" + TestUsers.ADMIN_ROLE + "' }.permissions", containsInAnyOrder(allButServerSettings));

        given().header("Authorization", "Bearer " + member.token()).get("/api/v1/accounts/me")
            .then().body("roleIds", contains(TestUsers.USER_ROLE));
        permissionsOf(member.token()).then().statusCode(200)
            .body("owner", equalTo(false))
            .body("permissions", not(hasItem("MANAGE_ROLES")))
            .body("permissions", hasItem("SEND_MESSAGES"));

        permissionsOf(TestUsers.ownerToken()).then().statusCode(200)
            .body("owner", equalTo(true))
            .body("permissions", hasItems("MANAGE_SERVER", "MANAGE_ROLES", "BAN_MEMBERS"));

        given().get("/api/v1/roles").then().statusCode(401);
    }

    @Test
    void newRolesGoToTheBottomAndCanBeAssigned() {
        TestUsers.User member = TestUsers.register();
        String first = TestUsers.createRole("First " + UUID.randomUUID(), "KICK_MEMBERS");
        String second = TestUsers.createRole("Second " + UUID.randomUUID(), "MUTE_MEMBERS");
        try {
            given().header("Authorization", "Bearer " + member.token()).get("/api/v1/roles")
                .then()
                .body("find { it.id == '" + second + "' }.position", equalTo(0))
                .body("find { it.id == '" + first + "' }.position", equalTo(1))
                .body("find { it.id == '" + first + "' }.permissions", contains("KICK_MEMBERS"))
                .body("find { it.id == '" + first + "' }.color", nullValue());

            TestUsers.assignRole(member.id(), first);
            TestUsers.assignRole(member.id(), first); // idempotent
            given().header("Authorization", "Bearer " + member.token()).get("/api/v1/accounts/me")
                .then().body("roleIds", containsInAnyOrder(TestUsers.USER_ROLE, first));
            permissionsOf(member.token()).then().body("permissions", hasItem("KICK_MEMBERS"));

            given().header("Authorization", "Bearer " + TestUsers.ownerToken())
                .delete("/api/v1/accounts/" + member.id() + "/roles/" + first).then().statusCode(204);
            permissionsOf(member.token()).then().body("permissions", not(hasItem("KICK_MEMBERS")));
        } finally {
            TestUsers.deleteRole(first);
            TestUsers.deleteRole(second);
        }
    }

    @Test
    void managersOnlyManageRolesWithinTheirOwnPermissions() {
        String manager = TestUsers.createRole("Manager " + UUID.randomUUID(), "MANAGE_ROLES", "KICK_MEMBERS");
        String admin = TestUsers.createRole("Admin " + UUID.randomUUID(), "MANAGE_SERVER", "BAN_MEMBERS");
        TestUsers.User mgr = TestUsers.register();
        TestUsers.assignRole(mgr.id(), manager);
        permissionsOf(mgr.token()).then().statusCode(200).body("permissions", hasItems("MANAGE_ROLES", "KICK_MEMBERS"));
        TestUsers.User target = TestUsers.register();
        String created = null;
        try {
            // Can create a role with a subset of own permissions; it lands at the bottom.
            created = create(mgr.token(), Map.of("name", "Helper", "permissions", List.of("KICK_MEMBERS")))
                .then().statusCode(201).body("position", equalTo(0)).extract().path("id");
            // Cannot grant what they do not hold.
            create(mgr.token(), Map.of("name", "Sneaky", "permissions", List.of("BAN_MEMBERS")))
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            TestUsers.patchRole(mgr.token(), created, Map.of("permissions", List.of("MANAGE_SERVER")))
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            // Cannot touch a role holding permissions they lack.
            TestUsers.patchRole(mgr.token(), admin, Map.of("name", "Pwned")).then().statusCode(403).body("error", equalTo("permission_escalation"));
            given().header("Authorization", "Bearer " + mgr.token()).delete("/api/v1/roles/" + admin)
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            given().header("Authorization", "Bearer " + mgr.token()).put("/api/v1/accounts/" + target.id() + "/roles/" + admin)
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            // But can manage everything within their permissions, wherever it sits in the list.
            TestUsers.patchRole(mgr.token(), created, Map.of("name", "Helper renamed", "color", "#123ABC", "position", 50))
                .then().statusCode(200).body("name", equalTo("Helper renamed")).body("color", equalTo("#123ABC")).body("position", equalTo(50));
            given().header("Authorization", "Bearer " + mgr.token()).put("/api/v1/accounts/" + target.id() + "/roles/" + created)
                .then().statusCode(204);
            given().header("Authorization", "Bearer " + mgr.token()).delete("/api/v1/roles/" + created).then().statusCode(204);
            created = null;
            given().header("Authorization", "Bearer " + target.token()).get("/api/v1/accounts/me").then().body("roleIds", contains(TestUsers.USER_ROLE));
        } finally {
            if (created != null) {
                TestUsers.deleteRole(created);
            }
            TestUsers.deleteRole(manager);
            TestUsers.deleteRole(admin);
        }
    }

    @Test
    void theNewMemberRoleSettingDecidesWhatNewMembersGet() {
        String owner = TestUsers.ownerToken();
        String greeter = TestUsers.createRole("Greeter " + UUID.randomUUID(), "CREATE_INVITE");
        try {
            TestUsers.patchSettings(Map.of("newMemberRoleId", greeter)).then().statusCode(200).body("newMemberRoleId", equalTo(greeter));
            TestUsers.User greeted = TestUsers.register();
            given().header("Authorization", "Bearer " + greeted.token()).get("/api/v1/accounts/me")
                .then().body("roleIds", contains(greeter));
            given().header("Authorization", "Bearer " + owner).delete("/api/v1/roles/" + greeter)
                .then().statusCode(409).body("error", equalTo("role_in_use"));

            // Without a role new members can only browse.
            TestUsers.patchSettings(Map.of("newMemberRoleId", "")).then().statusCode(200).body("newMemberRoleId", nullValue());
            TestUsers.User browser = TestUsers.register();
            given().header("Authorization", "Bearer " + browser.token()).get("/api/v1/accounts/me")
                .then().body("roleIds", equalTo(List.of()));
            permissionsOf(browser.token()).then().body("permissions", equalTo(List.of()));
            given().header("Authorization", "Bearer " + browser.token()).get("/api/v1/channels")
                .then().statusCode(200).body("size()", greaterThan(0));

            TestUsers.patchSettings(Map.of("newMemberRoleId", UUID.randomUUID().toString()))
                .then().statusCode(400).body("error", equalTo("role_not_found"));
        } finally {
            TestUsers.patchSettings(Map.of("newMemberRoleId", TestUsers.USER_ROLE)).then().statusCode(200);
            TestUsers.deleteRole(greeter);
        }
    }

    @Test
    void rolesRequireTheManageRolesPermissionAndReportNotFound() {
        TestUsers.User member = TestUsers.register();
        create(member.token(), Map.of("name", "Nope")).then().statusCode(403).body("error", equalTo("forbidden"));
        given().header("Authorization", "Bearer " + member.token()).delete("/api/v1/roles/" + UUID.randomUUID())
            .then().statusCode(403);

        String owner = TestUsers.ownerToken();
        TestUsers.patchRole(owner, UUID.randomUUID().toString(), Map.of("name", "x"))
            .then().statusCode(404).body("error", equalTo("role_not_found"));
        String role = TestUsers.createRole("Temp " + UUID.randomUUID());
        try {
            given().header("Authorization", "Bearer " + owner).put("/api/v1/accounts/" + UUID.randomUUID() + "/roles/" + role)
                .then().statusCode(404).body("error", equalTo("account_not_found"));
            create(owner, Map.of("name", "", "permissions", List.of("SPEAK")))
                .then().statusCode(400).body("error", equalTo("validation_failed"));
            create(owner, Map.of("name", "Bad colour", "color", "red"))
                .then().statusCode(400).body("error", equalTo("validation_failed"));
        } finally {
            TestUsers.deleteRole(role);
        }
    }
}
