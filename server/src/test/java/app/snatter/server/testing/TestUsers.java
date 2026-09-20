package app.snatter.server.testing;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Registers users through the HTTP API exactly as a client would, so it works
 * both in-process and against the packaged application.
 *
 * <p>The first call bootstraps the server: on an empty database the first
 * registration becomes the owner, who then opens registration and switches
 * rate limiting off for the rest of the run. Every test must register through
 * this class so that the owner is always the well-known account.
 */
public final class TestUsers {

    public record User(String token, String id, String username) {
    }

    public static final String OWNER_USERNAME = "owner";
    public static final String OWNER_PASSWORD = "owner password 1234";
    public static final String DEFAULT_PASSWORD = "a long enough password";

    private static String ownerToken;

    private TestUsers() {
    }

    /** Token of the server owner; bootstraps the server on first use. */
    public static synchronized String ownerToken() {
        if (ownerToken == null) {
            ownerToken = registerOrLogin(OWNER_USERNAME, OWNER_PASSWORD);
            Map<String, Object> defaults = new HashMap<>();
            defaults.put("registrationMode", "open");
            defaults.put("rateLimits", rateLimits(false, 10, 60, 5, 3600, 30, 60));
            patchSettings(defaults).then().statusCode(200);
        }
        return ownerToken;
    }

    public static Response patchSettings(Map<String, Object> update) {
        return given()
            .header("Authorization", "Bearer " + ownerToken())
            .contentType(ContentType.JSON)
            .body(update)
            .patch("/api/v1/server-settings");
    }

    public static Map<String, Object> rateLimits(boolean enabled, int loginLimit, int loginPeriod,
                                                 int registerLimit, int registerPeriod,
                                                 int challengeLimit, int challengePeriod) {
        return Map.of(
            "enabled", enabled,
            "login", Map.of("limit", loginLimit, "periodSeconds", loginPeriod),
            "register", Map.of("limit", registerLimit, "periodSeconds", registerPeriod),
            "challenge", Map.of("limit", challengeLimit, "periodSeconds", challengePeriod),
            "invite", Map.of("limit", 30, "periodSeconds", 60));
    }

    /** Registers a fresh user with a random username. */
    public static User register() {
        return register("user_" + UUID.randomUUID().toString().substring(0, 8));
    }

    public static User register(String username) {
        ownerToken();
        ExtractableResponse<Response> r = registerRaw(registration(username, DEFAULT_PASSWORD, null))
            .then().statusCode(201).extract();
        return new User(r.path("token"), r.path("account.id"), username);
    }

    /** A registration body with a freshly solved challenge if the server requires one. */
    public static Map<String, Object> registration(String username, String password, String displayName) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        if (displayName != null) {
            body.put("displayName", displayName);
        }
        if (challengeRequired()) {
            body.put("altcha", solveChallenge());
        }
        return body;
    }

    public static Response registerRaw(Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/auth/register");
    }

    public static boolean challengeRequired() {
        return given().get("/api/v1/server-info").then().statusCode(200)
            .extract().path("registration.challengeRequired");
    }

    /** Fetches a challenge and brute-forces it, returning the base64 payload the server expects. */
    public static String solveChallenge() {
        ExtractableResponse<Response> c = given().get("/api/v1/auth/challenge").then().statusCode(200).extract();
        String challenge = c.path("challenge");
        String salt = c.path("salt");
        String signature = c.path("signature");
        int max = c.path("maxnumber");
        for (int number = 0; number <= max; number++) {
            if (sha256Hex(salt + number).equals(challenge)) {
                return payload(challenge, salt, signature, number);
            }
        }
        throw new AssertionError("challenge has no solution up to " + max);
    }

    public static String payload(String challenge, String salt, String signature, int number) {
        String json = "{\"algorithm\":\"SHA-256\",\"challenge\":\"" + challenge + "\",\"number\":" + number
            + ",\"salt\":\"" + salt + "\",\"signature\":\"" + signature + "\"}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String registerOrLogin(String username, String password) {
        Response r = registerRaw(registration(username, password, null));
        if (r.statusCode() == 201) {
            return r.path("token");
        }
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
            .post("/api/v1/auth/login")
            .then().statusCode(200)
            .extract().path("token");
    }

    private static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- Roles ---------------------------------------------------------------

    /** Creates a role as the owner and returns its id. */
    public static String createRole(String name, String... permissions) {
        return given()
            .header("Authorization", "Bearer " + ownerToken())
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "permissions", java.util.List.of(permissions)))
            .post("/api/v1/roles")
            .then().statusCode(201)
            .extract().path("id");
    }

    public static Response patchRole(String token, String roleId, Map<String, Object> update) {
        return given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(update)
            .patch("/api/v1/roles/" + roleId);
    }

    public static void assignRole(String accountId, String roleId) {
        given()
            .header("Authorization", "Bearer " + ownerToken())
            .put("/api/v1/accounts/" + accountId + "/roles/" + roleId)
            .then().statusCode(204);
    }

    public static void deleteRole(String roleId) {
        given()
            .header("Authorization", "Bearer " + ownerToken())
            .delete("/api/v1/roles/" + roleId)
            .then().statusCode(204);
    }

    public static String defaultRoleId() {
        return given()
            .header("Authorization", "Bearer " + ownerToken())
            .get("/api/v1/roles")
            .then().statusCode(200)
            .extract().path("find { it.isDefault }.id");
    }

    /** A fresh member holding exactly the given permissions through a new role. */
    public static User registerWithPermissions(String... permissions) {
        User user = register();
        assignRole(user.id(), createRole("perm_" + UUID.randomUUID().toString().substring(0, 8), permissions));
        return user;
    }
}
