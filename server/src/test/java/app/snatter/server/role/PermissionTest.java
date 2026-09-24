package app.snatter.server.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void adminRoleMaskMatchesTheMigration() {
        // V7 seeds the Admin role with this literal.
        assertEquals(32768L, Permission.ADMINISTRATOR.mask());
    }

    @Test
    void administratorImpliesEverythingButServerSettings() {
        Set<Permission> expected = EnumSet.allOf(Permission.class);
        expected.remove(Permission.MANAGE_SERVER);
        assertEquals(expected, Permission.ADMINISTRATOR_IMPLIES);
    }

    @Test
    void onlyChannelPermissionsAreChannelScoped() {
        assertTrue(Permission.CHANNEL_SCOPED.contains(Permission.VIEW_CHANNELS));
        for (Permission serverOnly : EnumSet.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_INVITES,
                Permission.CREATE_INVITE, Permission.KICK_MEMBERS, Permission.BAN_MEMBERS)) {
            assertFalse(Permission.CHANNEL_SCOPED.contains(serverOnly), serverOnly + " is server-wide");
        }
    }
}
