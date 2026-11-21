package app.snatter.server.media;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.net.Inet6Address;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An IPv6 address people would reach voice at, as on a container network
 * that may have no IPv6: the server starts, and the address stands in front
 * of IPv6 sockets only, if there are any.
 */
@QuarkusTest
@TestProfile(MediaPortOtherFamilyTest.Ipv6.class)
class MediaPortOtherFamilyTest {

    public static final class Ipv6 implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("snatter.media.address", "2001:db8::7");
        }
    }

    @Inject
    MediaPort port;

    @Test
    void theServerStartsAndTheAddressStandsOnlyBeforeItsOwnFamily() {
        given().when().get("/api/v1/server-info").then().statusCode(200);
        try (IceConnection connection = port.open()) {
            assertTrue(connection.candidates().stream()
                    .filter(c -> c.type().equals("srflx"))
                    .allMatch(c -> c.address().getAddress() instanceof Inet6Address && c.related().getAddress() instanceof Inet6Address),
                connection.candidates()::toString);
        }
    }
}
