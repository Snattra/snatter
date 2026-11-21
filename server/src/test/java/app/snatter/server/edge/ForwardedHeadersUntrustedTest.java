package app.snatter.server.edge;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ForwardedHeadersUntrustedTest {

    @Test
    void withNoProxiesNamedNoClientIsBelieved() {
        ForwardedHeaders.assertBelieved(false);
    }
}
