package app.snatter.server.settings;

import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.model.CommunityDto;
import app.snatter.client.model.ServerInfoDto;
import app.snatter.server.protocol.Protocol;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerInfoResourceTest {

    private final TestDataService data = new TestDataService();

    @Test
    void exposesNameVersionAndProtocol() {
        ServerInfoDto info = serverApi().getServerInfo();
        assertEquals("Snatter", info.getName());
        assertFalse(info.getVersion().isEmpty());
        assertEquals(Protocol.CURRENT.toString(), info.getProtocol().getVersion());
        assertEquals(Protocol.CURRENT.major() + ".0", info.getProtocol().getMinClient());
        assertEquals(64000, info.getVoice().getDefaultBitrate());
    }

    @Test
    void exposesCommunitySettingsFromDatabase() {
        CommunityDto community = serverApi().getServerInfo().getCommunity();
        assertEquals("My Snatter server", community.getName());
        assertNull(community.getDescription());
    }

    @Test
    void aFreshServerNeedsSettingUp() {
        ServerInfoDto info = serverApi().getServerInfo();
        assertTrue(info.getRegistration().getSetupRequired());
        assertNull(info.getOwnerId());
    }

    @Test
    void setupIsOverOnceTheOwnerExists() {
        TestUsers.User owner = data.setUpServer();
        ServerInfoDto info = serverApi().getServerInfo();
        assertFalse(info.getRegistration().getSetupRequired());
        assertEquals(owner.id(), info.getOwnerId());
    }

    // Health and the contract itself are served by Quarkus, outside the contract.

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
