package app.snatter.server.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TrustedProxiesTest {

    @Test
    void noProxiesNamedMeansNoForwardedHeaders() {
        assertTrue(TrustedProxies.settingsFor(null).isEmpty());
        assertTrue(TrustedProxies.settingsFor("  ").isEmpty());
    }

    @Test
    void namedProxiesAreTheOnlyOnesBelieved() {
        Map<String, String> settings = TrustedProxies.settingsFor(" 10.0.0.0/8,192.168.1.2 ").orElseThrow();
        assertEquals("true", settings.get("quarkus.http.proxy.proxy-address-forwarding"));
        assertEquals("true", settings.get("quarkus.http.proxy.allow-x-forwarded"));
        assertEquals("true", settings.get("quarkus.http.proxy.enable-forwarded-host"));
        assertEquals("10.0.0.0/8,192.168.1.2", settings.get("quarkus.http.proxy.trusted-proxies"));
    }

    @Test
    void theWebAppIsAnAddressWithNothingAfterHostAndPort() {
        assertEquals("https://web.snatter.app", AppPages.checked("https://web.snatter.app/"));
        assertEquals("http://localhost:5173", AppPages.checked(" http://localhost:5173 "));
        for (String notOne : new String[] {"web.snatter.app", "ftp://web.snatter.app", "https://web.snatter.app/app", "https://web.snatter.app?x=1"}) {
            assertThrows(IllegalArgumentException.class, () -> AppPages.checked(notOne), notOne);
        }
    }
}
