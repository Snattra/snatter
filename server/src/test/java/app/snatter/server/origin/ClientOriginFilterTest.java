package app.snatter.server.origin;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.snatter.client.model.ApiErrorDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.GatewayTestClient;
import app.snatter.server.testing.TestDataService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ClientOriginFilterTest {

    private static final String HOSTED = "https://web.snatter.app";
    private static final String OTHER = "https://evil.example.com";
    private static final String GATEWAY = "/api/v1/gateway";

    private final TestDataService data = new TestDataService();

    @BeforeEach
    void setUpServer() {
        data.setUpServer();
    }

    @Test
    void theHostedAppAndLocalAppsAreAllowedByDefault() {
        assertEquals(List.of("http://localhost:*", HOSTED), serverApi(data.owner()).getServerSettings().getClientOrigins());
    }

    @Test
    void anAllowedAppIsAnsweredItsPreflight() {
        given().header("Origin", HOSTED)
            .header("Access-Control-Request-Method", "PATCH")
            .header("Access-Control-Request-Headers", "authorization, content-type")
            .when().options("/api/v1/server-settings")
            .then().statusCode(204)
            .header("Access-Control-Allow-Origin", HOSTED)
            .header("Access-Control-Allow-Methods", containsString("PATCH"))
            .header("Access-Control-Allow-Headers", containsString("Authorization"))
            .header("Vary", containsString("Origin"));
    }

    @Test
    void anotherPageIsRefusedItsPreflight() {
        given().header("Origin", OTHER)
            .header("Access-Control-Request-Method", "POST")
            .when().options("/api/v1/auth/login")
            .then().statusCode(403)
            .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void onlyAnAllowedAppMayReadAnswers() {
        given().header("Origin", "http://localhost:5173")
            .when().get("/api/v1/server-info")
            .then().statusCode(200)
            .header("Access-Control-Allow-Origin", "http://localhost:5173")
            .header("Access-Control-Expose-Headers", "Retry-After");
        given().header("Origin", OTHER)
            .when().get("/api/v1/server-info")
            .then().statusCode(200)
            .header("Access-Control-Allow-Origin", nullValue());
        given().when().get("/api/v1/server-info")
            .then().statusCode(200)
            .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void theServersOwnPagesMayUseItWhateverTheList() {
        data.updateSettings(new ServerSettingsUpdateDto().clientOrigins(List.of()));
        given().header("Origin", "http://localhost:" + RestAssured.port)
            .when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", "http://localhost:" + RestAssured.port);
        given().header("Origin", "https://chat.example.com")
            .when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", nullValue());

        data.updateSettings(new ServerSettingsUpdateDto().publicUrl("https://chat.example.com"));
        given().header("Origin", "https://chat.example.com")
            .when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", "https://chat.example.com");
    }

    @Test
    void theGatewayTurnsAwayOtherPages() {
        assertEquals(403, GatewayTestClient.handshakeStatus(OTHER, GATEWAY));
        assertEquals(101, GatewayTestClient.handshakeStatus(HOSTED, GATEWAY));
        assertEquals(101, GatewayTestClient.handshakeStatus(null, GATEWAY));
    }

    @Test
    void theGatewayIsTheGatewayHoweverItsPathIsWritten() {
        String encoded = "/api/v1/%67ateway";
        // Routing decodes the path, so this does reach the gateway.
        assertEquals(101, GatewayTestClient.handshakeStatus(null, encoded));
        assertEquals(403, GatewayTestClient.handshakeStatus(OTHER, encoded));
        // And no other handshake goes past the check either.
        assertEquals(403, GatewayTestClient.handshakeStatus(OTHER, "/api/v1/server-info"));
    }

    @Test
    void everyAnswerVariesByOrigin() {
        // So that a browser does not reuse an answer to a plain request, such as
        // an image, for a page that needs the CORS headers.
        given().when().get("/api/v1/server-info")
            .then().header("Vary", containsString("Origin"))
            .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void anAppsDefaultPortIsLeftOut() {
        List<String> origins = data.updateSettings(new ServerSettingsUpdateDto()
            .clientOrigins(List.of("https://chat.example.com:443", "http://lan.example.com:80", "https://alt.example.com:80"))).getClientOrigins();
        assertEquals(List.of("http://lan.example.com", "https://alt.example.com:80", "https://chat.example.com"), origins);
        given().header("Origin", "https://chat.example.com")
            .when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", "https://chat.example.com");
    }

    @Test
    void theOwnerChoosesTheApps() {
        List<String> origins = data.updateSettings(new ServerSettingsUpdateDto()
            .clientOrigins(List.of("HTTPS://Chat.Example.com", "snatter://app", "snatter://app"))).getClientOrigins();
        assertEquals(List.of("https://chat.example.com", "snatter://app"), origins);

        given().header("Origin", "https://chat.example.com")
            .when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", "https://chat.example.com");
        given().header("Origin", HOSTED)
            .when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", nullValue());
        assertEquals(403, GatewayTestClient.handshakeStatus(HOSTED, GATEWAY));
    }

    @Test
    void anAppMustBeAnOrigin() {
        for (String notAnOrigin : new String[] {"https://chat.example.com/app", "null", "chat.example.com"}) {
            ApiErrorDto error = assertApiError(400, "validation_failed",
                () -> data.updateSettings(new ServerSettingsUpdateDto().clientOrigins(List.of(notAnOrigin))));
            assertEquals(Set.of("clientOrigins[0]"), error.getFields().keySet());
        }
        given().header("Origin", HOSTED).when().get("/api/v1/server-info")
            .then().header("Access-Control-Allow-Origin", equalTo(HOSTED));
    }
}
