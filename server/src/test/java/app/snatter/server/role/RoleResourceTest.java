package app.snatter.server.role;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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
    void defaultRoleIsLastAndGrantsTheBaseline() {
        TestUsers.User member = TestUsers.register();
        given().header("Authorization", "Bearer " + member.token()).get("/api/v1/roles")
            .then().statusCode(200)
            .body("[-1].isDefault", equalTo(true))
            .body("[-1].position", equalTo(0))
            .body("[-1].permissions", hasItems("CREATE_INVITE", "VIEW_CHANNELS", "SEND_MESSAGES", "CONNECT", "SPEAK", "STREAM"))
            .body("[-1].permissions", not(hasItem("MANAGE_SERVER")));

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
                .body("find { it.id == '" + second + "' }.position", equalTo(1))
                .body("find { it.id == '" + first + "' }.position", equalTo(2))
                .body("find { it.id == '" + first + "' }.permissions", contains("KICK_MEMBERS"))
                .body("find { it.id == '" + first + "' }.color", nullValue());

            TestUsers.assignRole(member.id(), first);
            TestUsers.assignRole(member.id(), first); // idempotent
            given().header("Authorization", "Bearer " + member.token()).get("/api/v1/accounts/me")
                .then().body("roleIds", contains(first));
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
    void managersAreBoundByHierarchyAndCannotEscalate() {
        // Owner sets up: a senior "Admin" role above a "Manager" role that holds MANAGE_ROLES + KICK_MEMBERS.
        String manager = TestUsers.createRole("Manager " + UUID.randomUUID(), "MANAGE_ROLES", "KICK_MEMBERS");
        String admin = TestUsers.createRole("Admin " + UUID.randomUUID(), "MANAGE_SERVER", "BAN_MEMBERS");
        TestUsers.patchRole(TestUsers.ownerToken(), admin, Map.of("position", 5)).then().statusCode(200).body("position", equalTo(5));
        TestUsers.patchRole(TestUsers.ownerToken(), manager, Map.of("position", 3)).then().statusCode(200);
        TestUsers.User mgr = TestUsers.register();
        TestUsers.assignRole(mgr.id(), manager);
        permissionsOf(mgr.token()).then().statusCode(200).body("permissions", hasItems("MANAGE_ROLES", "KICK_MEMBERS"));
        TestUsers.User target = TestUsers.register();
        String created = null;
        try {
            // Can create a role with a subset of own permissions; it lands at the bottom.
            created = create(mgr.token(), Map.of("name", "Helper", "permissions", List.of("KICK_MEMBERS")))
                .then().statusCode(201).body("position", equalTo(1)).extract().path("id");
            // Cannot grant what they do not hold.
            create(mgr.token(), Map.of("name", "Sneaky", "permissions", List.of("BAN_MEMBERS")))
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            TestUsers.patchRole(mgr.token(), created, Map.of("permissions", List.of("MANAGE_SERVER")))
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            // Cannot touch roles at or above their own.
            TestUsers.patchRole(mgr.token(), admin, Map.of("name", "Pwned")).then().statusCode(403).body("error", equalTo("role_hierarchy"));
            TestUsers.patchRole(mgr.token(), manager, Map.of("name", "Self")).then().statusCode(403).body("error", equalTo("role_hierarchy"));
            given().header("Authorization", "Bearer " + mgr.token()).delete("/api/v1/roles/" + admin)
                .then().statusCode(403).body("error", equalTo("role_hierarchy"));
            given().header("Authorization", "Bearer " + mgr.token()).put("/api/v1/accounts/" + target.id() + "/roles/" + admin)
                .then().statusCode(403).body("error", equalTo("role_hierarchy"));
            // Cannot move a role up to or past themselves (creating Helper shifted every role up by one).
            int managerPosition = given().header("Authorization", "Bearer " + mgr.token()).get("/api/v1/roles")
                .then().extract().path("find { it.id == '" + manager + "' }.position");
            TestUsers.patchRole(mgr.token(), created, Map.of("position", managerPosition)).then().statusCode(403).body("error", equalTo("role_hierarchy"));
            TestUsers.patchRole(mgr.token(), created, Map.of("position", managerPosition + 1)).then().statusCode(403).body("error", equalTo("role_hierarchy"));
            // But can manage what is below them.
            TestUsers.patchRole(mgr.token(), created, Map.of("name", "Helper renamed", "color", "#123ABC", "position", 2))
                .then().statusCode(200).body("name", equalTo("Helper renamed")).body("color", equalTo("#123ABC")).body("position", equalTo(2));
            given().header("Authorization", "Bearer " + mgr.token()).put("/api/v1/accounts/" + target.id() + "/roles/" + created)
                .then().statusCode(204);
            given().header("Authorization", "Bearer " + mgr.token()).delete("/api/v1/roles/" + created).then().statusCode(204);
            created = null;
            given().header("Authorization", "Bearer " + target.token()).get("/api/v1/accounts/me").then().body("roleIds", equalTo(List.of()));
        } finally {
            if (created != null) {
                TestUsers.deleteRole(created);
            }
            TestUsers.deleteRole(manager);
            TestUsers.deleteRole(admin);
        }
    }

    @Test
    void defaultRoleIsProtected() {
        String defaultRole = TestUsers.defaultRoleId();
        TestUsers.User member = TestUsers.register();
        String owner = TestUsers.ownerToken();

        TestUsers.patchRole(owner, defaultRole, Map.of("name", "everybody")).then().statusCode(400).body("error", equalTo("default_role"));
        TestUsers.patchRole(owner, defaultRole, Map.of("position", 2)).then().statusCode(400).body("error", equalTo("default_role"));
        given().header("Authorization", "Bearer " + owner).delete("/api/v1/roles/" + defaultRole)
            .then().statusCode(400).body("error", equalTo("default_role"));
        given().header("Authorization", "Bearer " + owner).put("/api/v1/accounts/" + member.id() + "/roles/" + defaultRole)
            .then().statusCode(400).body("error", equalTo("default_role"));

        List<String> original = given().header("Authorization", "Bearer " + owner).get("/api/v1/roles")
            .then().extract().path("find { it.isDefault }.permissions");
        try {
            TestUsers.patchRole(owner, defaultRole, Map.of("permissions", List.of("VIEW_CHANNELS")))
                .then().statusCode(200).body("permissions", containsInAnyOrder("VIEW_CHANNELS"));
            permissionsOf(member.token()).then().body("permissions", containsInAnyOrder("VIEW_CHANNELS"));
        } finally {
            TestUsers.patchRole(owner, defaultRole, Map.of("permissions", original)).then().statusCode(200);
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
