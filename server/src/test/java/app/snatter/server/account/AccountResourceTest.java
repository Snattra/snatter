package app.snatter.server.account;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.accountsApi;
import static app.snatter.server.testing.ApiClientFactory.blobsApi;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.snatter.client.api.AccountsApi;
import app.snatter.client.model.AccountDto;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountResourceTest {

    private final TestDataService data = new TestDataService();

    @BeforeEach
    void setUpServer() {
        data.setUpServer();
    }

    static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /** The content in a temporary file, which is how the generated client uploads it. */
    private static File file(byte[] content) throws IOException {
        Path file = Files.createTempFile("upload", null);
        file.toFile().deleteOnExit();
        return Files.write(file, content).toFile();
    }

    @Test
    void newAccountHasNoAvatarAndIsVisibleToOthers() {
        TestUsers.User a = TestUsers.register();
        AccountDto seen = accountsApi(TestUsers.register()).getAccount(a.id());
        assertEquals(a.id(), seen.getId());
        assertNull(seen.getAvatarId());
    }

    @Test
    void unknownAccountIs404() {
        AccountsApi accounts = accountsApi(TestUsers.register());
        assertApiError(404, "account_not_found", () -> accounts.getAccount(UUID.randomUUID()));
    }

    @Test
    void uploadReplaceAndClearAvatar() throws IOException {
        TestUsers.User u = TestUsers.register();
        AccountsApi accounts = accountsApi(u);

        // Declared as anything but an image, which does not matter: the bytes decide.
        UUID first = UUID.fromString(given()
            .header("Authorization", "Bearer " + u.token())
            .contentType("application/octet-stream")
            .body(png(128, 128))
            .put("/api/v1/accounts/me/avatar")
            .then()
            .statusCode(200)
            .extract().path("avatarId"));

        // Public, cacheable for good, and never sniffed.
        byte[] served = given()
            .get("/api/v1/blobs/" + first)
            .then()
            .statusCode(200)
            .contentType("image/png")
            .header("Cache-Control", equalTo("public, max-age=31536000, immutable"))
            .header("X-Content-Type-Options", equalTo("nosniff"))
            .extract().asByteArray();
        assertEquals(128, ImageIO.read(new ByteArrayInputStream(served)).getWidth());

        UUID second = accounts.setAvatar(file(png(64, 64))).getAvatarId();
        assertNotNull(second);
        assertNotEquals(first, second);

        assertApiError(404, "blob_not_found", () -> blobsApi().getBlob(first));
        blobsApi().getBlob(second);

        assertNull(accounts.clearAvatar().getAvatarId());
        assertApiError(404, "blob_not_found", () -> blobsApi().getBlob(second));
    }

    @Test
    void rejectsNonImageUploads() throws IOException {
        // The client declares it an image, which it is not.
        File svg = file("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes());
        assertApiError(400, "unsupported_image", () -> accountsApi(TestUsers.register()).setAvatar(svg));
    }

    @Test
    void rejectsWrongDimensions() throws IOException {
        AccountsApi accounts = accountsApi(TestUsers.register());
        File tooWide = file(png(AvatarService.MAX_DIMENSION + 1, 64));
        File tooSmall = file(png(16, 16));
        assertApiError(400, "image_dimensions", () -> accounts.setAvatar(tooWide));
        assertApiError(400, "image_dimensions", () -> accounts.setAvatar(tooSmall));
    }

    @Test
    void rejectsOversizedUploads() throws IOException {
        File big = file(new byte[AvatarService.MAX_BYTES + 1]);
        assertApiError(413, "image_too_large", () -> accountsApi(TestUsers.register()).setAvatar(big));
    }

    @Test
    void accountEndpointsRequireAuth() throws IOException {
        AccountsApi anonymous = accountsApi();
        File small = file(new byte[10]);
        assertApiStatus(401, anonymous::getCurrentAccount);
        assertApiStatus(401, () -> anonymous.getAccount(UUID.randomUUID()));
        assertApiStatus(401, () -> anonymous.setAvatar(small));
        assertApiStatus(401, anonymous::clearAvatar);
    }
}
