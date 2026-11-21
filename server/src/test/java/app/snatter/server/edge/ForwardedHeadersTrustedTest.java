package app.snatter.server.edge;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ForwardedHeadersTrustedTest.TrustingThisMachine.class)
class ForwardedHeadersTrustedTest {

    public static final class TrustingThisMachine implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("snatter.trusted-proxies", "127.0.0.1,::1");
        }
    }

    @Test
    void aNamedProxyIsBelieved() {
        ForwardedHeaders.assertHostBelieved(true);
    }

    @Test
    void aNamedProxySaysWhichClientItIs() {
        ForwardedHeaders.assertClientAddressBelieved(true);
    }
}
