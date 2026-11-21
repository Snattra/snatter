package app.snatter.server.media;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An address people would reach voice at that does not resolve: the server starts all the same, offering its own. */
@QuarkusTest
@TestProfile(MediaPortUnusableAddressTest.Unresolvable.class)
class MediaPortUnusableAddressTest {

    public static final class Unresolvable implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("snatter.media.address", "voice.unresolvable.invalid");
        }
    }

    @Inject
    MediaPort port;

    @Test
    void theServerStartsAndVoiceOffersOnlyItsOwnAddresses() {
        given().when().get("/api/v1/server-info").then().statusCode(200);
        try (IceConnection connection = port.open()) {
            assertTrue(connection.candidates().stream().noneMatch(c -> c.type().equals("srflx")), connection.candidates()::toString);
            assertTrue(connection.candidates().stream().anyMatch(c -> c.type().equals("host")));
        }
    }
}
