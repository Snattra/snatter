package app.snatter.server.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void idsAreVersion7AndStrictlyIncreasingEvenWithinOneMillisecond() {
        UUID previous = Ids.newId();
        for (int i = 0; i < 100_000; i++) {
            UUID next = Ids.newId();
            assertEquals(7, next.version());
            assertEquals(2, next.variant());
            // Lowercase hex strings of equal length compare like PostgreSQL compares uuids.
            assertTrue(next.toString().compareTo(previous.toString()) > 0, previous + " then " + next);
            previous = next;
        }
    }

    @Test
    void timestampIsTheCurrentTime() {
        long before = System.currentTimeMillis();
        long millis = Ids.newId().getMostSignificantBits() >>> 16;
        assertTrue(millis >= before && millis <= System.currentTimeMillis() + 1000);
    }
}
