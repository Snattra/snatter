package app.snatter.server.channel;

import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.Permission;
import app.snatter.server.role.Role;
import java.util.Set;

/**
 * Computes a member's effective permissions in a channel from their
 * server-level permissions and the channel's overwrites, the way Discord does:
 *
 * <ol>
 *   <li>the default role's overwrite,</li>
 *   <li>the overwrites of all the member's other roles, combined,</li>
 *   <li>the member's own overwrite,</li>
 * </ol>
 *
 * each removing its denied permissions and then adding its allowed ones. A
 * member who cannot view the channel keeps none of the channel-scoped
 * permissions. The owner and administrators are not affected by overwrites.
 */
public final class ChannelPermissions {

    private static final long CHANNEL_SCOPED = Permission.toMask(Permission.CHANNEL_SCOPED);

    private ChannelPermissions() {
    }

    public static Set<Permission> of(AccountPrincipal member, Channel channel) {
        if (member.bypassesOverwrites()) {
            return member.permissions();
        }
        long permissions = Permission.toMask(member.permissions());

        for (PermissionOverwrite o : channel.overwrites()) {
            if (Role.DEFAULT_ID.equals(o.roleId())) {
                permissions = apply(permissions, Permission.toMask(o.allow()), Permission.toMask(o.deny()));
            }
        }

        long allow = 0;
        long deny = 0;
        for (PermissionOverwrite o : channel.overwrites()) {
            if (o.roleId() != null && member.roleIds().contains(o.roleId())) {
                allow |= Permission.toMask(o.allow());
                deny |= Permission.toMask(o.deny());
            }
        }
        permissions = apply(permissions, allow, deny);

        for (PermissionOverwrite o : channel.overwrites()) {
            if (member.accountId().equals(o.accountId())) {
                permissions = apply(permissions, Permission.toMask(o.allow()), Permission.toMask(o.deny()));
            }
        }

        if ((permissions & Permission.VIEW_CHANNELS.mask()) == 0) {
            permissions &= ~CHANNEL_SCOPED;
        }
        return Permission.fromMask(permissions);
    }

    public static boolean canView(AccountPrincipal member, Channel channel) {
        return of(member, channel).contains(Permission.VIEW_CHANNELS);
    }

    private static long apply(long permissions, long allow, long deny) {
        return (permissions & ~deny) | allow;
    }
}
