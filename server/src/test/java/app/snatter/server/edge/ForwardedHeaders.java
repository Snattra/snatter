package app.snatter.server.edge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

/**
 * Forwarded headers, as seen through the server knowing its own pages: a page
 * at the address in X-Forwarded-Host is its own only if the header is
 * believed (origin.ClientOriginFilter). The tests reach the server from this
 * machine.
 */
final class ForwardedHeaders {

    private static final String PAGE = "https://chat.example.com";

    private ForwardedHeaders() {
    }

    static void assertBelieved(boolean believed) {
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
}
