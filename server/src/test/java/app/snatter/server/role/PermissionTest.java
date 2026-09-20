package app.snatter.server.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void bitsAreUnique() {
        Set<Long> masks = new HashSet<>();
        for (Permission p : Permission.values()) {
            assertTrue(masks.add(p.mask()), p + " shares a bit");
        }
    }

    @Test
    void maskRoundTrips() {
        Set<Permission> some = EnumSet.of(Permission.MANAGE_SERVER, Permission.STREAM, Permission.MOVE_MEMBERS);
        assertEquals(some, Permission.fromMask(Permission.toMask(some)));
        assertEquals(Permission.all(), Permission.fromMask(Permission.toMask(Permission.all())));
        assertEquals(Set.of(), Permission.fromMask(0));
    }

    @Test
    void defaultRoleMaskMatchesTheMigration() {
        // V6__roles.sql seeds the default role with this literal; keep them in step.
        assertEquals(7616L, Permission.toMask(Permission.DEFAULT_ROLE));
    }
}
