package app.snatter.server.role;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Server-level permissions. Each has a fixed bit in the {@code role.permissions}
 * bitmask; never renumber. The API exposes them by name.
 */
public enum Permission {
    MANAGE_SERVER(0),
    MANAGE_ROLES(1),
    MANAGE_CHANNELS(2),
    MANAGE_INVITES(3),
    KICK_MEMBERS(4),
    BAN_MEMBERS(5),
    CREATE_INVITE(6),
    VIEW_CHANNELS(7),
    SEND_MESSAGES(8),
    MANAGE_MESSAGES(9),
    CONNECT(10),
    SPEAK(11),
    STREAM(12),
    MUTE_MEMBERS(13),
    MOVE_MEMBERS(14);

    /** What a fresh server's default role grants. */
    public static final Set<Permission> DEFAULT_ROLE = Set.of(
        CREATE_INVITE, VIEW_CHANNELS, SEND_MESSAGES, CONNECT, SPEAK, STREAM);

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
