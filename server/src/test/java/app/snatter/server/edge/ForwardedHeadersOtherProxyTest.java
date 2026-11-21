package app.snatter.server.edge;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ForwardedHeadersOtherProxyTest.TrustingAnotherMachine.class)
class ForwardedHeadersOtherProxyTest {

    public static final class TrustingAnotherMachine implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("snatter.trusted-proxies", "10.9.9.9");
        }
    }

    @Test
    void aClientThatIsNotANamedProxyIsNotBelieved() {
        ForwardedHeaders.assertBelieved(false);
    }
}
