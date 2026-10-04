package app.snatter.server.moderation;

import static app.snatter.client.model.PermissionDto.SEND_MESSAGES;
import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.accountsApi;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.messagesApi;
import static app.snatter.server.testing.ApiClientFactory.moderationApi;
import static app.snatter.server.testing.ApiClientFactory.rolesApi;
import static app.snatter.server.testing.TestUsers.DEFAULT_PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.api.ModerationApi;
import app.snatter.client.model.AccountDto;
import app.snatter.client.model.BanCreateDto;
import app.snatter.client.model.BanDto;
import app.snatter.client.model.BanNoticeDto;
import app.snatter.client.model.GatewayCloseReasonDto;
import app.snatter.client.model.GatewayMemberUpdatedDto;
import app.snatter.client.model.GatewayPermissionsChangedDto;
import app.snatter.client.model.MessageCreateDto;
import app.snatter.client.model.TimeoutCreateDto;
import app.snatter.server.testing.GatewayTestClient;
import app.snatter.server.testing.GatewayTestClient.Closed;
import app.snatter.server.testing.Messages;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ModerationResourceTest {

    private static final UUID GENERAL_TEXT = UUID.fromString("00000000-0000-7000-8000-000000000101");

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    private TestUsers.User moderator() {
        TestUsers.User mod = TestUsers.register();
        data.assignRole(mod.id(), TestDataService.MODERATOR_ROLE);
        return mod;
    }

    private static TimeoutCreateDto timeout(int seconds) {
        return new TimeoutCreateDto().durationSeconds(seconds);
    }

    /** An update of the member that matches. */
    private static Predicate<GatewayMemberUpdatedDto> updateOf(TestUsers.User member, Predicate<AccountDto> matching) {
        return frame -> frame.getMember().getId().equals(member.id()) && matching.test(frame.getMember());
    }

    @Test
    void banningEndsSessionsAndRefusesLoginWithTheReason() {
        TestUsers.User mod = moderator();
        TestUsers.User member = TestUsers.register();
        ModerationApi asMod = moderationApi(mod);
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            BanDto ban = asMod.banMember(member.id(), new BanCreateDto().reason("  spamming  "));
            assertEquals(member.id(), ban.getAccountId());
            assertEquals("spamming", ban.getReason());
            assertEquals(mod.id(), ban.getBannedBy());
            assertNotNull(ban.getCreatedAt());
            assertEquals(new Closed(4005, GatewayCloseReasonDto.BANNED), gateway.awaitClose());
        }
        assertApiStatus(401, () -> accountsApi(member).getCurrentAccount());
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify(member.token());
            assertEquals(new Closed(4002, GatewayCloseReasonDto.AUTHENTICATION_FAILED), gateway.awaitClose());
        }

        assertApiError(401, "invalid_credentials", () -> TestUsers.login(member.username(), "wrong password here"));
        BanNoticeDto notice = assertApiError(403, "banned", () -> TestUsers.login(member.username(), DEFAULT_PASSWORD)).getBan();
        assertEquals("spamming", notice.getReason());
        assertNotNull(notice.getBannedAt());

        // Banning again replaces the reason; a blank one means none.
        assertNull(asMod.banMember(member.id(), new BanCreateDto().reason(" ")).getReason());
        assertEquals(mod.id(), asMod.listBans().stream()
            .filter(b -> b.getAccountId().equals(member.id())).findFirst().orElseThrow().getBannedBy());

        asMod.unbanMember(member.id());
        asMod.unbanMember(member.id());
        assertFalse(asMod.listBans().stream().anyMatch(b -> b.getAccountId().equals(member.id())));
        accountsApi(TestUsers.newSession(member)).getCurrentAccount();
    }

    @Test
    void everyoneSeesWhoIsBannedButOnlyModeratorsWhy() {
        TestUsers.User mod = moderator();
        TestUsers.User member = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        ModerationApi asMod = moderationApi(mod);
        try (GatewayTestClient watching = GatewayTestClient.identified(watcher.token())) {
            asMod.banMember(member.id(), new BanCreateDto().reason("spamming"));
            watching.await(GatewayMemberUpdatedDto.class, updateOf(member, m -> m.getBannedAt() != null));
            assertNotNull(accountsApi(watcher).getAccount(member.id()).getBannedAt());
            assertApiError(403, "forbidden", () -> moderationApi(watcher).listBans());
            try (GatewayTestClient later = GatewayTestClient.identified(watcher.token())) {
                assertNotNull(later.ready().getMembers().stream()
                    .filter(m -> m.getId().equals(member.id())).findFirst().orElseThrow().getBannedAt());
            }

            asMod.unbanMember(member.id());
            watching.await(GatewayMemberUpdatedDto.class, updateOf(member, m -> m.getBannedAt() == null));
            assertNull(accountsApi(watcher).getAccount(member.id()).getBannedAt());
        }
    }

    @Test
    void onlyMembersWithinTheCallersPermissionsCanBeBanned() {
        TestUsers.User mod = moderator();
        TestUsers.User admin = TestUsers.register();
        data.assignRole(admin.id(), TestDataService.ADMIN_ROLE);
        TestUsers.User member = TestUsers.register();
        ModerationApi asMod = moderationApi(mod);
        ModerationApi asAdmin = moderationApi(admin);
        ModerationApi asMember = moderationApi(member);
        ModerationApi asOwner = moderationApi(owner);

        assertApiError(403, "forbidden", () -> asMember.banMember(mod.id(), new BanCreateDto()));
        assertApiError(403, "forbidden", asMember::listBans);
        assertApiError(400, "cannot_moderate_self", () -> asMod.banMember(mod.id(), new BanCreateDto()));
        assertApiError(403, "member_outranks_you", () -> asMod.banMember(admin.id(), new BanCreateDto()));
        assertApiError(403, "member_outranks_you", () -> asMod.banMember(owner.id(), new BanCreateDto()));
        assertApiError(403, "member_outranks_you", () -> asAdmin.banMember(owner.id(), new BanCreateDto()));
        assertApiError(404, "account_not_found", () -> asMod.banMember(UUID.randomUUID(), new BanCreateDto()));
        assertApiError(400, "validation_failed", () -> asMod.banMember(member.id(), new BanCreateDto().reason("x".repeat(513))));

        // Moving up: the admin may ban the moderator, and the owner anyone.
        asAdmin.banMember(mod.id(), new BanCreateDto());
        asOwner.banMember(admin.id(), new BanCreateDto().reason("rogue"));
        asOwner.unbanMember(mod.id());
        asOwner.unbanMember(admin.id());
    }

    @Test
    void aTimeoutTakesPermissionsAwayUntilItEnds() {
        TestUsers.User mod = moderator();
        TestUsers.User member = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        ModerationApi asMod = moderationApi(mod);
        try (GatewayTestClient memberGateway = GatewayTestClient.identified(member.token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            AccountDto timedOut = asMod.timeOutMember(member.id(), timeout(2));
            assertNotNull(timedOut.getTimedOutUntil());
            assertTrue(timedOut.getRoleIds().contains(TestDataService.USER_ROLE));

            assertEquals(List.of(), memberGateway.await(GatewayPermissionsChangedDto.class).getPermissions().getPermissions());
            watcherGateway.await(GatewayMemberUpdatedDto.class, updateOf(member, m -> m.getTimedOutUntil() != null));
            assertEquals(List.of(), rolesApi(member).getMyPermissions().getPermissions());
            assertApiError(403, "forbidden",
                () -> messagesApi(member).createMessage(GENERAL_TEXT, new MessageCreateDto().content("let me speak")));
            assertTrue(channelsApi(member).listChannels().stream().anyMatch(c -> c.getId().equals(GENERAL_TEXT)));

            // It runs out on its own, and the gateway says so.
            assertTrue(memberGateway.await(GatewayPermissionsChangedDto.class).getPermissions().getPermissions().contains(SEND_MESSAGES));
            watcherGateway.await(GatewayMemberUpdatedDto.class, updateOf(member, m -> m.getTimedOutUntil() == null));
            Messages.send(member, GENERAL_TEXT, "back");

            // Or it is lifted early.
            asMod.timeOutMember(member.id(), timeout(600));
            memberGateway.await(GatewayPermissionsChangedDto.class);
            asMod.endTimeout(member.id());
            assertTrue(memberGateway.await(GatewayPermissionsChangedDto.class).getPermissions().getPermissions().contains(SEND_MESSAGES));
            assertNull(accountsApi(member).getCurrentAccount().getTimedOutUntil());
        }
    }

    @Test
    void timeoutsFollowTheModerationRules() {
        TestUsers.User mod = moderator();
        TestUsers.User admin = TestUsers.register();
        data.assignRole(admin.id(), TestDataService.ADMIN_ROLE);
        TestUsers.User member = TestUsers.register();
        ModerationApi asMod = moderationApi(mod);
        ModerationApi asMember = moderationApi(member);
        ModerationApi asOwner = moderationApi(owner);

        assertApiError(403, "forbidden", () -> asMember.timeOutMember(mod.id(), timeout(60)));
        assertApiError(400, "cannot_moderate_self", () -> asMod.timeOutMember(mod.id(), timeout(60)));
        assertApiError(403, "member_outranks_you", () -> asMod.timeOutMember(admin.id(), timeout(60)));
        assertApiError(400, "validation_failed", () -> asMod.timeOutMember(member.id(), timeout(0)));
        assertApiError(400, "validation_failed", () -> asMod.timeOutMember(member.id(), timeout(2_419_201)));

        // A timeout lowers what someone can do, not their rank.
        asOwner.timeOutMember(admin.id(), timeout(600));
        assertApiError(403, "member_outranks_you", () -> asMod.banMember(admin.id(), new BanCreateDto()));
        asOwner.endTimeout(admin.id());

        // A timed-out moderator cannot moderate.
        asOwner.timeOutMember(mod.id(), timeout(600));
        assertApiError(403, "forbidden", () -> asMod.timeOutMember(member.id(), timeout(60)));
        asOwner.endTimeout(mod.id());
        asMod.timeOutMember(member.id(), timeout(60));
        asMod.endTimeout(member.id());
    }
}
