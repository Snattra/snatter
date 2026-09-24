package app.snatter.server.channel;

import static app.snatter.server.role.Permission.ADMINISTRATOR;
import static app.snatter.server.role.Permission.CONNECT;
import static app.snatter.server.role.Permission.CREATE_INVITE;
import static app.snatter.server.role.Permission.KICK_MEMBERS;
import static app.snatter.server.role.Permission.MANAGE_MESSAGES;
import static app.snatter.server.role.Permission.SEND_MESSAGES;
import static app.snatter.server.role.Permission.SPEAK;
import static app.snatter.server.role.Permission.VIEW_CHANNELS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.account.AccountId;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.auth.SessionId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.Role;
import app.snatter.server.role.RoleId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelPermissionsTest {

    private static final Set<Permission> BASE = EnumSet.of(VIEW_CHANNELS, SEND_MESSAGES, CONNECT, SPEAK, CREATE_INVITE);
    private static final RoleId MODS = RoleId.newId();
    private static final RoleId GUESTS = RoleId.newId();

    private static AccountPrincipal member(Set<Permission> permissions, RoleId... roles) {
        return new AccountPrincipal(AccountId.newId(), "m", new SessionId(UUID.randomUUID()), false,
            permissions, roles.length, Set.of(roles));
    }

    private static Channel channel(PermissionOverwrite... overwrites) {
        return new Channel(ChannelId.newId(), ChannelType.VOICE_TEXT, "c", null, 0, new VoiceSettings(64000, 0),
            List.of(overwrites), Instant.now(), Instant.now());
    }

    @Test
    void withoutOverwritesServerPermissionsApply() {
        assertEquals(BASE, ChannelPermissions.of(member(BASE), channel()));
    }

    @Test
    void everyoneThenRolesThenMemberInThatOrder() {
        AccountPrincipal mod = member(BASE, MODS);
        Channel channel = channel(
            PermissionOverwrite.forRole(Role.DEFAULT_ID, Set.of(), Set.of(SEND_MESSAGES)),
            PermissionOverwrite.forRole(MODS, Set.of(SEND_MESSAGES, MANAGE_MESSAGES), Set.of()));
        assertFalse(ChannelPermissions.of(member(BASE), channel).contains(SEND_MESSAGES), "denied for everyone");
        assertTrue(ChannelPermissions.of(mod, channel).containsAll(Set.of(SEND_MESSAGES, MANAGE_MESSAGES)), "role allow beats everyone deny");

        Channel muted = channel(
            PermissionOverwrite.forRole(MODS, Set.of(SEND_MESSAGES), Set.of()),
            PermissionOverwrite.forAccount(mod.accountId(), Set.of(), Set.of(SEND_MESSAGES)));
        assertFalse(ChannelPermissions.of(mod, muted).contains(SEND_MESSAGES), "member deny beats role allow");
    }

    @Test
    void roleAllowWinsOverAnotherRolesDeny() {
        AccountPrincipal both = member(BASE, MODS, GUESTS);
        Channel channel = channel(
            PermissionOverwrite.forRole(GUESTS, Set.of(), Set.of(SPEAK)),
            PermissionOverwrite.forRole(MODS, Set.of(SPEAK), Set.of()));
        assertTrue(ChannelPermissions.of(both, channel).contains(SPEAK));
        assertFalse(ChannelPermissions.of(member(BASE, GUESTS), channel).contains(SPEAK));
    }

    @Test
    void withoutViewNoChannelPermissionRemainsButServerOnesDo() {
        Channel hidden = channel(PermissionOverwrite.forRole(Role.DEFAULT_ID, Set.of(), Set.of(VIEW_CHANNELS)));
        Set<Permission> effective = ChannelPermissions.of(member(BASE), hidden);
        assertEquals(Set.of(CREATE_INVITE), effective);
        assertFalse(ChannelPermissions.canView(member(BASE), hidden));
        assertTrue(ChannelPermissions.canView(member(BASE, MODS),
            channel(PermissionOverwrite.forRole(Role.DEFAULT_ID, Set.of(), Set.of(VIEW_CHANNELS)),
                PermissionOverwrite.forRole(MODS, Set.of(VIEW_CHANNELS), Set.of()))));
    }

    @Test
    void ownerAndAdministratorsIgnoreOverwrites() {
        Channel locked = channel(PermissionOverwrite.forRole(Role.DEFAULT_ID, Set.of(), Set.of(VIEW_CHANNELS, SEND_MESSAGES)));
        Set<Permission> admin = EnumSet.of(ADMINISTRATOR, KICK_MEMBERS, VIEW_CHANNELS, SEND_MESSAGES);
        assertEquals(admin, ChannelPermissions.of(member(admin), locked));
        AccountPrincipal owner = new AccountPrincipal(AccountId.newId(), "o", new SessionId(UUID.randomUUID()), true,
            Permission.all(), Integer.MAX_VALUE, Set.of());
        assertEquals(Permission.all(), ChannelPermissions.of(owner, locked));
    }
}
