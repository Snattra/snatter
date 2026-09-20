package app.snatter.server.settings;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerSettingsResourceTest {

    @Test
    void settingsRequireTheManageServerPermission() {
        TestUsers.User member = TestUsers.register();
        TestUsers.User admin = TestUsers.registerWithPermissions("MANAGE_SERVER");

        given().get("/api/v1/server-settings").then().statusCode(401);

        given()
            .header("Authorization", "Bearer " + member.token())
            .get("/api/v1/server-settings")
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));

        given()
            .header("Authorization", "Bearer " + member.token())
            .contentType(ContentType.JSON)
            .body(Map.of("name", "hijacked"))
            .patch("/api/v1/server-settings")
            .then()
            .statusCode(403);

        given()
            .header("Authorization", "Bearer " + admin.token())
            .get("/api/v1/server-settings")
            .then()
            .statusCode(200);

        given()
            .header("Authorization", "Bearer " + TestUsers.ownerToken())
            .get("/api/v1/server-settings")
            .then()
            .statusCode(200)
            .body("name", notNullValue())
            .body("registrationMode", equalTo("open"))
            .body("rateLimits.enabled", equalTo(false))
            .body("rateLimits.login.limit", equalTo(10));
    }

    @Test
    void ownerCanRenameAndDescribeTheCommunity() {
        String originalName = given()
            .header("Authorization", "Bearer " + TestUsers.ownerToken())
            .get("/api/v1/server-settings").then().statusCode(200).extract().path("name");
        try {
            TestUsers.patchSettings(Map.of("name", "  Snattra HQ  ", "description", "Where the ducks quack"))
                .then()
                .statusCode(200)
                .body("name", equalTo("Snattra HQ"))
                .body("description", equalTo("Where the ducks quack"));

            given().get("/api/v1/server-info").then()
                .body("community.name", equalTo("Snattra HQ"))
                .body("community.description", equalTo("Where the ducks quack"));

            TestUsers.patchSettings(Map.of("description", ""))
                .then()
                .statusCode(200)
                .body("description", nullValue());
        } finally {
            TestUsers.patchSettings(Map.of("name", originalName, "description", "")).then().statusCode(200);
        }
    }

    @Test
    void rejectsInvalidSettings() {
        TestUsers.patchSettings(Map.of("name", ""))
            .then()
            .statusCode(400)
            .body("error", equalTo("validation_failed"))
            .body("fields", hasKey("name"));

        TestUsers.patchSettings(Map.of("rateLimits", TestUsers.rateLimits(true, 0, 60, 5, 3600, 30, 60)))
            .then()
            .statusCode(400)
            .body("error", equalTo("validation_failed"))
            .body("fields", hasKey("limit"));
    }

    @Test
    void rateLimitsApplyImmediatelyAndReturn429() {
        TestUsers.User u = TestUsers.register();
        try {
            TestUsers.patchSettings(Map.of("rateLimits", TestUsers.rateLimits(true, 2, 60, 5, 3600, 30, 60)))
                .then().statusCode(200).body("rateLimits.enabled", equalTo(true));

            for (int i = 0; i < 2; i++) {
                login(u.username(), "wrong password").then().statusCode(401);
            }
            login(u.username(), "wrong password")
                .then()
                .statusCode(429)
                .header("Retry-After", notNullValue())
                .body("error", equalTo("rate_limited"));
            given().get("/api/v1/server-info").then().statusCode(200); // unrelated endpoints unaffected
        } finally {
            TestUsers.patchSettings(Map.of("rateLimits", TestUsers.rateLimits(false, 10, 60, 5, 3600, 30, 60)))
                .then().statusCode(200);
        }
        login(u.username(), TestUsers.DEFAULT_PASSWORD).then().statusCode(200);
    }

    @Test
    void challengeRequirementCanBeSwitchedOff() {
        try {
            TestUsers.patchSettings(Map.of("challengeRequired", false)).then().statusCode(200);
            given().get("/api/v1/server-info").then().body("registration.challengeRequired", equalTo(false));

            TestUsers.registerRaw(Map.of("username", "nochallenge_" + System.nanoTime() % 100000,
                    "password", TestUsers.DEFAULT_PASSWORD))
                .then().statusCode(201);
        } finally {
            TestUsers.patchSettings(Map.of("challengeRequired", true)).then().statusCode(200);
        }
    }

    @Test
    void challengesHaveTheAdvertisedShape() {
        given().get("/api/v1/auth/challenge")
            .then()
            .statusCode(200)
            .body("algorithm", equalTo("SHA-256"))
            .body("challenge", notNullValue())
            .body("salt", org.hamcrest.Matchers.containsString("?expires="))
            .body("signature", notNullValue())
            .body("maxnumber", greaterThanOrEqualTo(1));
    }

    private static io.restassured.response.Response login(String username, String password) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
            .post("/api/v1/auth/login");
    }
}
