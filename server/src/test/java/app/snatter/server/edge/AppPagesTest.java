package app.snatter.server.edge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.TestDataService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AppPagesTest {

    private final TestDataService data = new TestDataService();

    @Test
    void theServersAddressLeadsToTheWebApp() {
        given().redirects().follow(false)
            .when().get("/")
            .then().statusCode(302).header("Location", "https://web.snatter.app/");
    }

    @Test
    void anInviteLinkLeadsToTheWebAppWithThisServerNamed() {
        given().redirects().follow(false)
            .when().get("/invite/k3Jd8QzP")
            .then().statusCode(302)
            .header("Location", "https://web.snatter.app/invite#http%3A%2F%2Flocalhost%3A" + RestAssured.port + "/k3Jd8QzP");
    }

    @Test
    void theServerIsNamedByItsPublicAddressWhenItHasOne() {
        data.setUpServer();
        data.updateSettings(new ServerSettingsUpdateDto().publicUrl("https://chat.example.com"));
        given().redirects().follow(false)
            .when().get("/invite/k3Jd8QzP")
            .then().statusCode(302)
            .header("Location", "https://web.snatter.app/invite#https%3A%2F%2Fchat.example.com/k3Jd8QzP");
    }

    @Test
    void anythingElseIsNotFound() {
        given().redirects().follow(false).when().get("/invite/nope").then().statusCode(404);
        given().redirects().follow(false).when().get("/elsewhere").then().statusCode(404);
        given().when().get("/api/v1/server-info").then().statusCode(200).body("name", equalTo("Snatter"));
    }
}
