package app.snatter.server.edge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.TestDataService;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Forwarded headers, believed or not, as the tests see them from this
 * machine: the address in X-Forwarded-Host through the server knowing its
 * own pages (origin.ClientOriginFilter), and the one in X-Forwarded-For
 * through rate limits, counted per client address.
 */
final class ForwardedHeaders {

    private static final String PAGE = "https://chat.example.com";

    private ForwardedHeaders() {
    }

    static void assertHostBelieved(boolean believed) {
        var response = given().header("Origin", PAGE)
            .header("X-Forwarded-Host", "chat.example.com")
            .header("X-Forwarded-Proto", "https")
            .when().get("/api/v1/server-info")
            .then().statusCode(200);
        if (believed) {
            response.header("Access-Control-Allow-Origin", PAGE);
        } else {
            response.header("Access-Control-Allow-Origin", nullValue());
        }
    }

    /**
     * Three sign-ins, each claiming another client address, where two are
     * allowed per address: all three are counted apart if the claims are
     * believed, and the third is refused if they are not.
     */
    static void assertClientAddressBelieved(boolean believed) {
        TestDataService data = new TestDataService();
        data.setUpServer();
        data.updateSettings(new ServerSettingsUpdateDto().rateLimits(TestDataService.rateLimits(true, 2, 60, 5, 3600, 30, 60)));
        List<Integer> statuses = IntStream.rangeClosed(1, 3)
            .mapToObj(i -> given().header("X-Forwarded-For", "198.51.100." + i)
                .contentType("application/json")
                .body("{\"username\":\"nobody\",\"password\":\"wrong password 1\"}")
                .when().post("/api/v1/auth/login")
                .statusCode())
            .toList();
        assertEquals(believed ? List.of(401, 401, 401) : List.of(401, 401, 429), statuses);
    }
}
