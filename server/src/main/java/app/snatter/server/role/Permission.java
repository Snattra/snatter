package app.snatter.server.role;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Permissions, granted server-wide by roles. Each has a fixed bit in the
 * {@code role.permissions} bitmask; never renumber. The API exposes them by
 * name.
 */
public enum Permission {
    MANAGE_SERVER(0),
    MANAGE_ROLES(1),
    MANAGE_CHANNELS(2),
    MANAGE_INVITES(3),
    TIMEOUT_MEMBERS(4),
    BAN_MEMBERS(5),
    CREATE_INVITE(6),
    SEND_MESSAGES(7),
    MANAGE_MESSAGES(8),
    CONNECT(9),
    SPEAK(10),
    STREAM(11),
    MUTE_MEMBERS(12),
    MOVE_MEMBERS(13);

    private final int bit;

    Permission(int bit) {
        this.bit = bit;
    }

    public long mask() {
        return 1L << bit;
    }

    public static Set<Permission> all() {
        return EnumSet.allOf(Permission.class);
    }

    public static Set<Permission> fromMask(long mask) {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (Permission p : values()) {
            if ((mask & p.mask()) != 0) {
                set.add(p);
            }
        }
        return set;
    }

    public static long toMask(Collection<Permission> permissions) {
        long mask = 0;
        for (Permission p : permissions) {
            mask |= p.mask();
        }
        return mask;
    }
}
