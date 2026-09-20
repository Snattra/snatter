package app.snatter.server.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthResourceTest {

    private static String uniqueUsername() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registerThenReadOwnAccount() {
        String username = uniqueUsername();
        TestUsers.registerRaw(TestUsers.registration(username, TestUsers.DEFAULT_PASSWORD, null))
            .then()
            .statusCode(201)
            .body("token", startsWith("snt_"))
            .body("expiresAt", notNullValue())
            .body("account.id", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            .body("account.username", equalTo(username))
            .body("account.displayName", equalTo(username));

        TestUsers.User u = TestUsers.register();
        given()
            .header("Authorization", "Bearer " + u.token())
            .get("/api/v1/accounts/me")
            .then()
            .statusCode(200)
            .body("username", equalTo(u.username()));
    }

    @Test
    void registerUsesDisplayNameWhenGiven() {
        TestUsers.registerRaw(TestUsers.registration(uniqueUsername(), TestUsers.DEFAULT_PASSWORD, "Quacky"))
            .then()
            .statusCode(201)
            .body("account.displayName", equalTo("Quacky"));
    }

    @Test
    void usernameIsUniqueIgnoringCase() {
        TestUsers.User u = TestUsers.register();
        TestUsers.registerRaw(TestUsers.registration(u.username().toUpperCase(), TestUsers.DEFAULT_PASSWORD, null))
            .then()
            .statusCode(409)
            .body("error", equalTo("username_taken"));
    }

    @Test
    void rejectsInvalidRegistration() {
        TestUsers.registerRaw(TestUsers.registration("no spaces allowed", "short", null))
            .then()
            .statusCode(400)
            .body("error", equalTo("validation_failed"))
            .body("fields", hasKey("username"))
            .body("fields", hasKey("password"));
    }

    @Test
    void registrationRequiresASolvedChallenge() {
        TestUsers.ownerToken();
        Map<String, Object> withoutChallenge = Map.of("username", uniqueUsername(), "password", TestUsers.DEFAULT_PASSWORD);
        TestUsers.registerRaw(withoutChallenge)
            .then()
            .statusCode(400)
            .body("error", equalTo("challenge_required"));

        Map<String, Object> garbage = Map.of("username", uniqueUsername(), "password", TestUsers.DEFAULT_PASSWORD,
            "altcha", "bm90IGEgY2hhbGxlbmdl");
        TestUsers.registerRaw(garbage)
            .then()
            .statusCode(400)
            .body("error", equalTo("challenge_invalid"));
    }

    @Test
    void rejectsWrongSolutionsAndReplays() {
        TestUsers.ownerToken();
        var c = given().get("/api/v1/auth/challenge").then().statusCode(200)
            .body("algorithm", equalTo("SHA-256"))
            .extract();
        String challenge = c.path("challenge");
        String salt = c.path("salt");
        String signature = c.path("signature");

        // A number that is (almost certainly) not the solution.
        String wrong = TestUsers.payload(challenge, salt, signature, -1 + 0);
        TestUsers.registerRaw(Map.of("username", uniqueUsername(), "password", TestUsers.DEFAULT_PASSWORD, "altcha", wrong))
            .then().statusCode(400).body("error", equalTo("challenge_invalid"));

        // A forged signature.
        String forged = TestUsers.payload(challenge, salt, "00" + signature.substring(2), 1);
        TestUsers.registerRaw(Map.of("username", uniqueUsername(), "password", TestUsers.DEFAULT_PASSWORD, "altcha", forged))
            .then().statusCode(400).body("error", equalTo("challenge_invalid"));

        // A real solution works once and only once.
        String solved = TestUsers.solveChallenge();
        TestUsers.registerRaw(Map.of("username", uniqueUsername(), "password", TestUsers.DEFAULT_PASSWORD, "altcha", solved))
            .then().statusCode(201);
        TestUsers.registerRaw(Map.of("username", uniqueUsername(), "password", TestUsers.DEFAULT_PASSWORD, "altcha", solved))
            .then().statusCode(400).body("error", equalTo("challenge_invalid"));
    }

    @Test
    void registrationCanBeClosedByTheOwner() {
        TestUsers.ownerToken();
        try {
            TestUsers.patchSettings(Map.of("registrationMode", "invite_only")).then().statusCode(200);
            given().get("/api/v1/server-info").then().body("registration.mode", equalTo("invite_only"));

            TestUsers.registerRaw(TestUsers.registration(uniqueUsername(), TestUsers.DEFAULT_PASSWORD, null))
                .then()
                .statusCode(403)
                .body("error", equalTo("registration_closed"));
        } finally {
            TestUsers.patchSettings(Map.of("registrationMode", "open")).then().statusCode(200);
        }
    }

    @Test
    void loginWithCorrectAndWrongPassword() {
        TestUsers.User u = TestUsers.register();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", u.username(), "password", TestUsers.DEFAULT_PASSWORD))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("token", startsWith("snt_"))
            .body("account.username", equalTo(u.username()));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", u.username(), "password", "wrong password"))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(401)
            .body("error", equalTo("invalid_credentials"));
    }

    @Test
    void loginWithUnknownUserLooksLikeWrongPassword() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "nobody_" + UUID.randomUUID(), "password", "whatever it is"))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(401)
            .body("error", equalTo("invalid_credentials"));
    }

    @Test
    void logoutRevokesTheToken() {
        TestUsers.User u = TestUsers.register();

        given()
            .header("Authorization", "Bearer " + u.token())
            .post("/api/v1/auth/logout")
            .then()
            .statusCode(204);

        given()
            .header("Authorization", "Bearer " + u.token())
            .get("/api/v1/accounts/me")
            .then()
            .statusCode(401);
    }

    @Test
    void protectedEndpointsRequireAValidToken() {
        given()
            .get("/api/v1/accounts/me")
            .then()
            .statusCode(401)
            .header("WWW-Authenticate", equalTo("Bearer"));

        given()
            .header("Authorization", "Bearer snt_not_a_real_token")
            .get("/api/v1/accounts/me")
            .then()
            .statusCode(401);
    }
}
