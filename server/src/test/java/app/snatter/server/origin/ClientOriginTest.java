package app.snatter.server.origin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientOriginTest {

    @Test
    void anOriginIsKeptInLowerCase() {
        assertEquals("https://chat.example.com", new ClientOrigin("HTTPS://Chat.Example.com").value());
    }

    @Test
    void onlyOriginsAreAccepted() {
        for (String notAnOrigin : new String[] {"null", "chat.example.com", "https://chat.example.com/",
            "https://chat.example.com/app", "https://*.example.com", "https://chat.example.com:", "file://"}) {
            assertThrows(IllegalArgumentException.class, () -> new ClientOrigin(notAnOrigin), notAnOrigin);
        }
        new ClientOrigin("snatter://app");
        new ClientOrigin("http://[::1]:5173");
    }

    @Test
    void anExactOriginAllowsOnlyItself() {
        ClientOrigin app = new ClientOrigin("https://web.snatter.app");
        assertTrue(app.allows("https://web.snatter.app"));
        assertTrue(app.allows("https://Web.Snatter.app"));
        assertFalse(app.allows("http://web.snatter.app"));
        assertFalse(app.allows("https://web.snatter.app:8443"));
        assertFalse(app.allows("https://web.snatter.app.example.com"));
        assertFalse(app.allows("null"));
    }

    @Test
    void anyPortAllowsTheHostOnEveryPort() {
        ClientOrigin local = new ClientOrigin("http://localhost:*");
        assertTrue(local.allows("http://localhost"));
        assertTrue(local.allows("http://localhost:5173"));
        assertFalse(local.allows("https://localhost:5173"));
        assertFalse(local.allows("http://localhost.example.com"));
        assertFalse(local.allows("http://localhost:"));
        assertFalse(local.allows("http://localhost:51x3"));
    }

    @Test
    void theServersOwnAddressIsTheOneRequestedOrThePublicOne() {
        String requested = "chat.example.com";
        assertTrue(ClientOriginFilter.isOwnAddress("https://chat.example.com", requested, null));
        // Behind a proxy that terminates TLS, the server sees plain HTTP.
        assertTrue(ClientOriginFilter.isOwnAddress("http://chat.example.com", requested, null));
        assertFalse(ClientOriginFilter.isOwnAddress("https://evil.example.com", requested, null));
        assertTrue(ClientOriginFilter.isOwnAddress("http://localhost:8080", "localhost:8080", null));
        assertFalse(ClientOriginFilter.isOwnAddress("http://localhost:5173", "localhost:8080", null));

        String internal = "server:8080";
        assertTrue(ClientOriginFilter.isOwnAddress("https://chat.example.com", internal, "https://chat.example.com"));
        assertFalse(ClientOriginFilter.isOwnAddress("https://chat.example.com", internal, null));
        assertFalse(ClientOriginFilter.isOwnAddress("null", requested, "https://chat.example.com"));
    }
}
