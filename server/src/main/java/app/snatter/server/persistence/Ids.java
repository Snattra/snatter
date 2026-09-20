package app.snatter.server.persistence;

import java.util.UUID;

/**
 * Generates UUID version 7 identifiers: a millisecond timestamp in the high
 * bits followed by random bits. They sort by creation time, which keeps
 * B-tree indexes compact compared to fully random version 4 ids.
 */
public final class Ids {

    private Ids() {
    }

    public static UUID newId() {
        long now = System.currentTimeMillis();
        UUID rnd = UUID.randomUUID();

        long msb = (now << 16)                          // 48-bit timestamp
            | 0x7000L                                   // version 7
            | (rnd.getMostSignificantBits() & 0x0FFFL); // 12 random bits
        long lsb = (rnd.getLeastSignificantBits() & 0x3FFFFFFFFFFFFFFFL)
            | 0x8000000000000000L;                      // RFC 4122 variant, 62 random bits
        return new UUID(msb, lsb);
    }
}
