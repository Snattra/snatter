package app.snatter.server.persistence;

import java.util.UUID;

/**
 * Generates UUID version 7 identifiers: a millisecond timestamp in the high
 * bits followed by random bits. They sort by creation time, which keeps
 * B-tree indexes compact compared to fully random version 4 ids.
 *
 * <p>Ids from this process are strictly increasing, so they can order
 * records such as messages: the 12 bits after the timestamp are a counter
 * that starts at a random value each millisecond and counts up within it
 * (RFC 9562, section 6.2, method 1). A burst that exhausts the counter
 * borrows the next millisecond.
 */
public final class Ids {

    private static final long MAX_COUNTER = 0xFFF;

    private static long lastMillis;
    private static long counter;

    private Ids() {
    }

    public static UUID newId() {
        UUID rnd = UUID.randomUUID();
        long millis;
        long seq;
        synchronized (Ids.class) {
            long now = System.currentTimeMillis();
            if (now > lastMillis) {
                lastMillis = now;
                // Start in the lower half so a millisecond has room to count up.
                counter = rnd.getMostSignificantBits() & 0x7FF;
            } else if (++counter > MAX_COUNTER) {
                lastMillis++;
                counter = 0;
            }
            millis = lastMillis;
            seq = counter;
        }
        long msb = (millis << 16)                       // 48-bit timestamp
            | 0x7000L                                   // version 7
            | seq;                                      // 12-bit counter
        long lsb = (rnd.getLeastSignificantBits() & 0x3FFFFFFFFFFFFFFFL)
            | 0x8000000000000000L;                      // RFC 4122 variant, 62 random bits
        return new UUID(msb, lsb);
    }
}
