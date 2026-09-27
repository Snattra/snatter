package app.snatter.server.moderation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.testing.GatewayTestClient;
import app.snatter.server.testing.GatewayTestClient.Closed;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ModerationResourceTest {

    private static final String GENERAL_TEXT = "00000000-0000-7000-8000-000000000101";

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private static Response ban(String token, String accountId, Map<String, Object> body) {
        return as(token).body(body).put("/api/v1/bans/" + accountId);
    }

    private static Response login(String username, String password) {
        return given().contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
            .post("/api/v1/auth/login");
    }

    private static TestUsers.User moderator() {
        TestUsers.User mod = TestUsers.register();
        TestUsers.assignRole(mod.id(), TestUsers.MODERATOR_ROLE);
        return mod;
    }

    @Test
    void banningEndsSessionsAndRefusesLoginWithTheReason() {
        TestUsers.User mod = moderator();
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            ban(mod.token(), member.id(), Map.of("reason", "  spamming  ")).then().statusCode(200)
                .body("accountId", equalTo(member.id()))
                .body("reason", equalTo("spamming"))
                .body("bannedBy", equalTo(mod.id()))
                .body("createdAt", notNullValue());
            assertEquals(new Closed(4007, "banned"), gateway.awaitClose());
        }
        as(member.token()).get("/api/v1/accounts/me").then().statusCode(401);
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify(member.token());
            assertEquals(new Closed(4003, "authentication_failed"), gateway.awaitClose());
        }

        login(member.username(), "wrong password here").then().statusCode(401).body("error", equalTo("invalid_credentials"));
        login(member.username(), TestUsers.DEFAULT_PASSWORD).then().statusCode(403)
            .body("error", equalTo("banned"))
            .body("ban.reason", equalTo("spamming"))
            .body("ban.bannedAt", notNullValue());

        // Banning again replaces the reason; a blank one means none.
        ban(mod.token(), member.id(), Map.of("reason", " ")).then().statusCode(200).body("reason", equalTo(null));
        as(mod.token()).get("/api/v1/bans").then().statusCode(200)
            .body("find { it.accountId == '" + member.id() + "' }.bannedBy", equalTo(mod.id()));

        as(mod.token()).delete("/api/v1/bans/" + member.id()).then().statusCode(204);
        as(mod.token()).delete("/api/v1/bans/" + member.id()).then().statusCode(204);
        as(mod.token()).get("/api/v1/bans").then().body("accountId", not(hasItem(member.id())));
        String token = login(member.username(), TestUsers.DEFAULT_PASSWORD).then().statusCode(200).extract().path("token");
        as(token).get("/api/v1/accounts/me").then().statusCode(200);
    }

    @Test
    void everyoneSeesWhoIsBannedButOnlyModeratorsWhy() {
        TestUsers.User mod = moderator();
        TestUsers.User member = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        try (GatewayTestClient watching = GatewayTestClient.identified(watcher.token())) {
            ban(mod.token(), member.id(), Map.of("reason", "spamming")).then().statusCode(200);
            watching.await("member_updated", f -> member.id().equals(f.getString("member.id")) && f.getString("member.bannedAt") != null);
            as(watcher.token()).get("/api/v1/accounts/" + member.id()).then().statusCode(200).body("bannedAt", notNullValue());
            as(watcher.token()).get("/api/v1/bans").then().statusCode(403);
            try (GatewayTestClient later = GatewayTestClient.identified(watcher.token())) {
                assertTrue(later.ready().getString("members.find { it.id == '" + member.id() + "' }.bannedAt") != null);
            }

            as(mod.token()).delete("/api/v1/bans/" + member.id()).then().statusCode(204);
            watching.await("member_updated", f -> member.id().equals(f.getString("member.id")) && f.getString("member.bannedAt") == null);
            as(watcher.token()).get("/api/v1/accounts/" + member.id()).then().statusCode(200).body("bannedAt", nullValue());
        }
    }

    @Test
    void onlyMembersWithinTheCallersPermissionsCanBeBanned() {
        TestUsers.User mod = moderator();
        TestUsers.User admin = TestUsers.register();
        TestUsers.assignRole(admin.id(), TestUsers.ADMIN_ROLE);
        TestUsers.User member = TestUsers.register();
        String ownerId = as(TestUsers.ownerToken()).get("/api/v1/accounts/me").then().extract().path("id");

        ban(member.token(), mod.id(), Map.of()).then().statusCode(403).body("error", equalTo("forbidden"));
        as(member.token()).get("/api/v1/bans").then().statusCode(403);
        ban(mod.token(), mod.id(), Map.of()).then().statusCode(400).body("error", equalTo("cannot_moderate_self"));
        ban(mod.token(), admin.id(), Map.of()).then().statusCode(403).body("error", equalTo("member_outranks_you"));
        ban(mod.token(), ownerId, Map.of()).then().statusCode(403).body("error", equalTo("member_outranks_you"));
        ban(admin.token(), ownerId, Map.of()).then().statusCode(403).body("error", equalTo("member_outranks_you"));
        ban(mod.token(), UUID.randomUUID().toString(), Map.of()).then().statusCode(404).body("error", equalTo("account_not_found"));
        ban(mod.token(), member.id(), Map.of("reason", "x".repeat(513))).then().statusCode(400).body("error", equalTo("validation_failed"));

        // Moving up: the admin may ban the moderator, and the owner anyone.
        ban(admin.token(), mod.id(), Map.of()).then().statusCode(200);
        ban(TestUsers.ownerToken(), admin.id(), Map.of("reason", "rogue")).then().statusCode(200);
        as(TestUsers.ownerToken()).delete("/api/v1/bans/" + mod.id()).then().statusCode(204);
        as(TestUsers.ownerToken()).delete("/api/v1/bans/" + admin.id()).then().statusCode(204);
    }

    private static Response timeOut(String token, String accountId, int seconds) {
        return as(token).body(Map.of("durationSeconds", seconds)).put("/api/v1/timeouts/" + accountId);
    }

    private static Response post(String token, String content) {
        return as(token).body(Map.of("content", content)).post("/api/v1/channels/" + GENERAL_TEXT + "/messages");
    }

    @Test
    void aTimeoutTakesPermissionsAwayUntilItEnds() {
        TestUsers.User mod = moderator();
        TestUsers.User member = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        try (GatewayTestClient memberGateway = GatewayTestClient.identified(member.token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            timeOut(mod.token(), member.id(), 2).then().statusCode(200)
                .body("timedOutUntil", notNullValue())
                .body("roleIds", hasItem(TestUsers.USER_ROLE));

            assertEquals(List.of(), memberGateway.await("permissions_changed").getList("permissions.permissions"));
            watcherGateway.await("member_updated", f -> member.id().equals(f.getString("member.id"))
                && f.getString("member.timedOutUntil") != null);
            as(member.token()).get("/api/v1/accounts/me/permissions").then().body("permissions", equalTo(List.of()));
            post(member.token(), "let me speak").then().statusCode(403);
            as(member.token()).get("/api/v1/channels").then().statusCode(200).body("id", hasItem(GENERAL_TEXT));

            // It runs out on its own, and the gateway says so.
            assertTrue(memberGateway.await("permissions_changed").getList("permissions.permissions").contains("SEND_MESSAGES"));
            watcherGateway.await("member_updated", f -> member.id().equals(f.getString("member.id"))
                && f.getString("member.timedOutUntil") == null);
            post(member.token(), "back").then().statusCode(201);

            // Or it is lifted early.
            timeOut(mod.token(), member.id(), 600).then().statusCode(200);
            memberGateway.await("permissions_changed");
            as(mod.token()).delete("/api/v1/timeouts/" + member.id()).then().statusCode(204);
            assertTrue(memberGateway.await("permissions_changed").getList("permissions.permissions").contains("SEND_MESSAGES"));
            as(member.token()).get("/api/v1/accounts/me").then().body("timedOutUntil", nullValue());
        }
    }

    @Test
    void timeoutsFollowTheModerationRules() {
        TestUsers.User mod = moderator();
        TestUsers.User admin = TestUsers.register();
        TestUsers.assignRole(admin.id(), TestUsers.ADMIN_ROLE);
        TestUsers.User member = TestUsers.register();
        String owner = TestUsers.ownerToken();

        timeOut(member.token(), mod.id(), 60).then().statusCode(403).body("error", equalTo("forbidden"));
        timeOut(mod.token(), mod.id(), 60).then().statusCode(400).body("error", equalTo("cannot_moderate_self"));
        timeOut(mod.token(), admin.id(), 60).then().statusCode(403).body("error", equalTo("member_outranks_you"));
        timeOut(mod.token(), member.id(), 0).then().statusCode(400).body("error", equalTo("validation_failed"));
        timeOut(mod.token(), member.id(), 2_419_201).then().statusCode(400).body("error", equalTo("validation_failed"));

        // A timeout lowers what someone can do, not their rank.
        timeOut(owner, admin.id(), 600).then().statusCode(200);
        ban(mod.token(), admin.id(), Map.of()).then().statusCode(403).body("error", equalTo("member_outranks_you"));
        as(owner).delete("/api/v1/timeouts/" + admin.id()).then().statusCode(204);

        // A timed-out moderator cannot moderate.
        timeOut(owner, mod.id(), 600).then().statusCode(200);
        timeOut(mod.token(), member.id(), 60).then().statusCode(403).body("error", equalTo("forbidden"));
        as(owner).delete("/api/v1/timeouts/" + mod.id()).then().statusCode(204);
        timeOut(mod.token(), member.id(), 60).then().statusCode(200);
        as(mod.token()).delete("/api/v1/timeouts/" + member.id()).then().statusCode(204);
    }
}
