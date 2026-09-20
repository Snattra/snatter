package app.snatter.server.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

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

    private static String register(String username, String password) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
            .post("/api/v1/auth/register")
            .then()
            .statusCode(201)
            .body("token", startsWith("snt_"))
            .body("expiresAt", notNullValue())
            .body("account.id", notNullValue())
            .body("account.username", equalTo(username))
            .body("account.displayName", equalTo(username))
            .extract().path("token");
    }

    @Test
    void registerThenReadOwnAccount() {
        String username = uniqueUsername();
        String token = register(username, "a long enough password");

        given()
            .header("Authorization", "Bearer " + token)
            .get("/api/v1/accounts/me")
            .then()
            .statusCode(200)
            .body("username", equalTo(username));
    }

    @Test
    void registerUsesDisplayNameWhenGiven() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", uniqueUsername(), "password", "a long enough password", "displayName", "Quacky"))
            .post("/api/v1/auth/register")
            .then()
            .statusCode(201)
            .body("account.displayName", equalTo("Quacky"));
    }

    @Test
    void usernameIsUniqueIgnoringCase() {
        String username = uniqueUsername();
        register(username, "a long enough password");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username.toUpperCase(), "password", "a long enough password"))
            .post("/api/v1/auth/register")
            .then()
            .statusCode(409)
            .body("error", equalTo("username_taken"));
    }

    @Test
    void rejectsInvalidRegistration() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "no spaces allowed", "password", "short"))
            .post("/api/v1/auth/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("validation_failed"))
            .body("fields", hasKey("username"))
            .body("fields", hasKey("password"));
    }

    @Test
    void loginWithCorrectAndWrongPassword() {
        String username = uniqueUsername();
        register(username, "a long enough password");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", "a long enough password"))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("token", startsWith("snt_"))
            .body("account.username", equalTo(username));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", "wrong password"))
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
        String token = register(uniqueUsername(), "a long enough password");

        given()
            .header("Authorization", "Bearer " + token)
            .post("/api/v1/auth/logout")
            .then()
            .statusCode(204);

        given()
            .header("Authorization", "Bearer " + token)
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
