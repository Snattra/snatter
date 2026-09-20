package app.snatter.server.account;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountResourceTest {

    record User(String token, String id) {
    }

    static User register() {
        var response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "user_" + UUID.randomUUID().toString().substring(0, 8), "password", "a long enough password"))
            .post("/api/v1/auth/register")
            .then().statusCode(201)
            .extract();
        return new User(response.path("token"), response.path("account.id"));
    }

    static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void newAccountHasNoAvatarAndIsVisibleToOthers() {
        User a = register();
        User b = register();

        given()
            .header("Authorization", "Bearer " + b.token())
            .get("/api/v1/accounts/" + a.id())
            .then()
            .statusCode(200)
            .body("id", equalTo(a.id()))
            .body("avatarId", nullValue());
    }

    @Test
    void unknownAccountIs404() {
        User a = register();
        given()
            .header("Authorization", "Bearer " + a.token())
            .get("/api/v1/accounts/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("account_not_found"));
    }

    @Test
    void uploadReplaceAndClearAvatar() throws IOException {
        User u = register();

        String first = given()
            .header("Authorization", "Bearer " + u.token())
            .contentType("application/octet-stream")   // deliberately wrong; bytes decide
            .body(png(128, 128))
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(200)
            .body("avatarId", notNullValue())
            .extract().path("avatarId");

        byte[] served = given()
            .get("/api/v1/blobs/" + first)             // public, no token
            .then()
            .statusCode(200)
            .contentType("image/png")
            .header("Cache-Control", equalTo("public, max-age=31536000, immutable"))
            .header("X-Content-Type-Options", equalTo("nosniff"))
            .extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertEquals(128, ImageIO.read(new java.io.ByteArrayInputStream(served)).getWidth());

        String second = given()
            .header("Authorization", "Bearer " + u.token())
            .body(png(64, 64))
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(200)
            .extract().path("avatarId");
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);

        given().get("/api/v1/blobs/" + first).then().statusCode(404).body("error", equalTo("blob_not_found"));
        given().get("/api/v1/blobs/" + second).then().statusCode(200);

        given()
            .header("Authorization", "Bearer " + u.token())
            .delete("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(200)
            .body("avatarId", nullValue());
        given().get("/api/v1/blobs/" + second).then().statusCode(404);
    }

    @Test
    void rejectsNonImageUploads() {
        User u = register();
        given()
            .header("Authorization", "Bearer " + u.token())
            .contentType("image/png")                  // lies about the content
            .body("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes())
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_image"));
    }

    @Test
    void rejectsWrongDimensions() throws IOException {
        User u = register();
        given()
            .header("Authorization", "Bearer " + u.token())
            .body(png(AvatarService.MAX_DIMENSION + 1, 64))
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(400)
            .body("error", equalTo("image_dimensions"));
        given()
            .header("Authorization", "Bearer " + u.token())
            .body(png(16, 16))
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(400)
            .body("error", equalTo("image_dimensions"));
    }

    @Test
    void rejectsOversizedUploads() {
        User u = register();
        byte[] big = new byte[AvatarService.MAX_BYTES + 1];
        given()
            .header("Authorization", "Bearer " + u.token())
            .body(big)
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(413)
            .body("error", equalTo("image_too_large"));
    }

    @Test
    void avatarEndpointsRequireAuth() {
        given().get("/api/v1/accounts/me").then().statusCode(401);
        given().body(new byte[10]).put("/api/v1/accounts/me/avatar").then().statusCode(401);
        given().delete("/api/v1/accounts/me/avatar").then().statusCode(401);
    }
}
