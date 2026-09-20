package app.snatter.server.settings;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerInfoResourceTest {

    @Test
    void exposesNameVersionAndApiVersion() {
        given()
            .when().get("/api/v1/server-info")
            .then()
            .statusCode(200)
            .body("name", equalTo("Snatter"))
            .body("version", not(emptyString()))
            .body("apiVersion", equalTo(ServerSettingsResource.API_VERSION));
    }

    @Test
    void exposesCommunitySettingsFromDatabase() {
        given()
            .when().get("/api/v1/server-info")
            .then()
            .statusCode(200)
            .body("community.name", equalTo("My Snatter server"))
            .body("community.description", nullValue());
    }

    @Test
    void healthEndpointIsUp() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void servesTheHandWrittenContract() {
        given()
            .accept("application/yaml")
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .body(containsString("title: Snatter API"))
            .body(containsString("/api/v1/accounts/me/avatar:"))
            .body(containsString("operationId: getServerInfo"));
    }
}
