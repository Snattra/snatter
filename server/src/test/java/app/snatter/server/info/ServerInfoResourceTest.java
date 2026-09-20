package app.snatter.server.info;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;

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
            .body("apiVersion", equalTo(ServerInfoResource.API_VERSION));
    }

    @Test
    void healthEndpointIsUp() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
}
