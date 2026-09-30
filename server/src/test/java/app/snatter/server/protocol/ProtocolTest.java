package app.snatter.server.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProtocolTest {

    @Test
    void currentIsTheContractsVersion() throws IOException {
        // The build copies the contract here to serve it at /q/openapi.
        try (InputStream in = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            String contract = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("^info:\\n(?: .*\\n)*?  version: \"([^\"]+)\"$", Pattern.MULTILINE).matcher(contract);
            assertTrue(m.find(), "info.version in openapi.yaml");
            assertEquals(m.group(1), Protocol.CURRENT.toString(), "Protocol.CURRENT must follow info.version in openapi.yaml");
        }
    }

    @Test
    void parsesMajorDotMinor() {
        assertEquals(Optional.of(new ProtocolVersion(1, 0)), ProtocolVersion.parse("1.0"));
        assertEquals(Optional.of(new ProtocolVersion(12, 34)), ProtocolVersion.parse("12.34"));
        for (String bad : new String[] {null, "", "1", "1.0.0", "v1.0", " 1.0", "1.-1", "99999999999.0"}) {
            assertEquals(Optional.empty(), ProtocolVersion.parse(bad), bad);
        }
    }

    @Test
    void comparesPartsAsNumbers() {
        assertTrue(new ProtocolVersion(1, 9).isOlderThan(new ProtocolVersion(1, 10)));
        assertTrue(new ProtocolVersion(1, 10).isOlderThan(new ProtocolVersion(2, 0)));
        assertEquals(0, new ProtocolVersion(1, 2).compareTo(new ProtocolVersion(1, 2)));
    }

    @Test
    void acceptsClientsFromTheMinimumOn() {
        assertEquals(Protocol.CURRENT.major(), Protocol.MIN_CLIENT.major(), "no clients of an earlier major");
        assertFalse(Protocol.CURRENT.isOlderThan(Protocol.MIN_CLIENT), "the server's own clients get in");
        assertTrue(Protocol.accepts(Protocol.MIN_CLIENT));
        assertTrue(Protocol.accepts(Protocol.CURRENT));
        assertFalse(Protocol.accepts(new ProtocolVersion(Protocol.MIN_CLIENT.major() - 1, 99)));
    }
}
