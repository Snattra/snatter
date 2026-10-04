package app.snatter.server.gateway;

import static app.snatter.client.model.GatewayCloseReasonDto.ALREADY_IDENTIFIED;
import static app.snatter.client.model.GatewayCloseReasonDto.AUTHENTICATION_FAILED;
import static app.snatter.client.model.GatewayCloseReasonDto.CLIENT_OUTDATED;
import static app.snatter.client.model.GatewayCloseReasonDto.IDENTIFY_TIMEOUT;
import static app.snatter.client.model.GatewayCloseReasonDto.INVALID_FRAME;
import static app.snatter.client.model.GatewayCloseReasonDto.NOT_IDENTIFIED;
import static app.snatter.client.model.GatewayCloseReasonDto.SESSION_ENDED;
import static app.snatter.client.model.PermissionDto.SEND_MESSAGES;
import static app.snatter.client.model.PermissionDto.TIMEOUT_MEMBERS;
import static app.snatter.client.model.PresenceStatusDto.OFFLINE;
import static app.snatter.client.model.PresenceStatusDto.ONLINE;
import static app.snatter.server.testing.ApiClientFactory.authApi;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.messagesApi;
import static app.snatter.server.testing.ApiClientFactory.rolesApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.api.ChannelsApi;
import app.snatter.client.model.ChannelCreatedNoticeDto;
import app.snatter.client.model.ChannelDto;
import app.snatter.client.model.ChannelRenamedNoticeDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.ChannelUpdateDto;
import app.snatter.client.model.DeletedMessageDto;
import app.snatter.client.model.GatewayChannelCreatedDto;
import app.snatter.client.model.GatewayChannelDeletedDto;
import app.snatter.client.model.GatewayChannelUpdatedDto;
import app.snatter.client.model.GatewayIdentifyDto;
import app.snatter.client.model.GatewayMemberJoinedDto;
import app.snatter.client.model.GatewayMemberUpdatedDto;
import app.snatter.client.model.GatewayMessageCreatedDto;
import app.snatter.client.model.GatewayMessageUpdatedDto;
import app.snatter.client.model.GatewayMessagesPurgedDto;
import app.snatter.client.model.GatewayPermissionsChangedDto;
import app.snatter.client.model.GatewayPresenceUpdatedDto;
import app.snatter.client.model.GatewayReadStateUpdatedDto;
import app.snatter.client.model.GatewayReadyDto;
import app.snatter.client.model.GatewayRoleDeletedDto;
import app.snatter.client.model.GatewayRoleUpdatedDto;
import app.snatter.client.model.GatewayServerUpdatedDto;
import app.snatter.client.model.GatewayTypingDto;
import app.snatter.client.model.GatewayTypingStartedDto;
import app.snatter.client.model.MemberJoinedNoticeDto;
import app.snatter.client.model.MessageCreateDto;
import app.snatter.client.model.MessageUpdateDto;
import app.snatter.client.model.PresenceDto;
import app.snatter.client.model.PresenceStatusDto;
import app.snatter.client.model.ReadStateDto;
import app.snatter.client.model.ReadStateUpdateDto;
import app.snatter.client.model.RoleDto;
import app.snatter.client.model.RoleUpdateDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.client.model.SystemMessageDto;
import app.snatter.client.model.SystemNoticeDto;
import app.snatter.client.model.UserMessageDto;
import app.snatter.server.protocol.Protocol;
import app.snatter.server.protocol.ProtocolVersion;
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
class GatewayTest {

    private static final UUID GENERAL_TEXT = UUID.fromString("00000000-0000-7000-8000-000000000101");

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    private static GatewayTypingDto typing(UUID channel) {
        return new GatewayTypingDto().channelId(channel);
    }

    private static Predicate<GatewayChannelCreatedDto> created(UUID channel) {
        return frame -> frame.getChannel().getId().equals(channel);
    }

    private static Predicate<GatewayMessageCreatedDto> messageCreated(UUID message) {
        return frame -> Messages.id(frame.getMessage()).equals(message);
    }

    /** A new system message with a notice of the kind. */
    private static Predicate<GatewayMessageCreatedDto> carrying(Class<? extends SystemNoticeDto> notice) {
        return frame -> frame.getMessage() instanceof SystemMessageDto message && notice.isInstance(message.getNotice());
    }

    private static Predicate<GatewayPresenceUpdatedDto> presence(UUID account, PresenceStatusDto status) {
        return frame -> frame.getPresence().getAccountId().equals(account) && frame.getPresence().getStatus() == status;
    }

    private static Predicate<GatewayReadStateUpdatedDto> readState(UUID channel) {
        return frame -> frame.getReadState().getChannelId().equals(channel);
    }

    private static List<UUID> present(GatewayReadyDto ready) {
        return ready.getPresences().stream().map(PresenceDto::getAccountId).toList();
    }

    /** Frames arrive in order, so anything sent before this marker has arrived once it has. */
    private static void awaitMarker(GatewayTestClient gateway, TestUsers.User sender) {
        gateway.await(GatewayMessageCreatedDto.class, messageCreated(Messages.send(sender, GENERAL_TEXT, "marker").getId()));
    }

    @Test
    void readyDescribesTheMembersWorld() {
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            GatewayReadyDto ready = gateway.ready();
            assertEquals(1L, ready.getSeq());
            assertEquals(member.id(), ready.getAccount().getId());
            assertFalse(ready.getPermissions().getOwner());
            assertTrue(ready.getPermissions().getPermissions().contains(SEND_MESSAGES));
            assertEquals("Snatter", ready.getServer().getName());
            assertTrue(ready.getRoles().stream().map(RoleDto::getName).toList().containsAll(List.of("User", "Moderator", "Admin")));
            assertTrue(ready.getMembers().stream().anyMatch(m -> m.getId().equals(member.id())));
            assertEquals("General", ready.getChannels().stream()
                .filter(c -> c.getId().equals(GENERAL_TEXT)).findFirst().orElseThrow().getName());
        }
    }

    @Test
    void protocolViolationsCloseTheConnectionWithAReason() {
        String token = TestUsers.register().token();
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("not json");
            assertEquals(new Closed(4000, INVALID_FRAME), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("{\"token\":\"" + token + "\"}");
            assertEquals(new Closed(4000, INVALID_FRAME), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send(typing(GENERAL_TEXT));
            assertEquals(new Closed(4001, NOT_IDENTIFIED), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.identified(token)) {
            gateway.send("{\"type\":\"typing\",\"channelId\":\"not-a-channel-id\"}");
            assertEquals(new Closed(4000, INVALID_FRAME), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify("snt_not-a-real-token");
            assertEquals(new Closed(4002, AUTHENTICATION_FAILED), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.identified(token)) {
            gateway.identify(token);
            assertEquals(new Closed(4003, ALREADY_IDENTIFIED), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            // The test profile gives connections one second to identify.
            assertEquals(new Closed(4500, IDENTIFY_TIMEOUT), gateway.awaitClose());
        }
    }

    @Test
    void frameTypesFromNewerClientsAreIgnored() {
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("{\"type\":\"dance\",\"style\":\"waltz\"}");
            gateway.identify(member.token());
            assertEquals(member.id(), gateway.await(GatewayReadyDto.class).getAccount().getId());
            // Frames are handled in order, so this answer shows the one before did not close the connection.
            gateway.send("{\"type\":\"dance\"}");
            gateway.identify(member.token());
            assertEquals(new Closed(4003, ALREADY_IDENTIFIED), gateway.awaitClose());
        }
    }

    @Test
    void clientsStateTheirProtocolVersion() {
        String token = TestUsers.register().token();
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send(new GatewayIdentifyDto().token(token));
            assertEquals(new Closed(4000, INVALID_FRAME), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify(token, "one");
            assertEquals(new Closed(4000, INVALID_FRAME), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify(token, "0.9");
            assertEquals(new Closed(4006, CLIENT_OUTDATED), gateway.awaitClose());
        }
        // Newer clients get in; whether they can work with this server is theirs to decide.
        ProtocolVersion current = Protocol.CURRENT;
        for (String newer : List.of(current.major() + "." + (current.minor() + 1), (current.major() + 1) + ".0")) {
            try (GatewayTestClient gateway = GatewayTestClient.connect()) {
                gateway.identify(token, newer);
                assertEquals(current.toString(), gateway.await(GatewayReadyDto.class).getServer().getProtocol().getVersion());
            }
        }
    }

    @Test
    void membersAreOnlineWhileAnyConnectionIsOpen() {
        TestUsers.User watcher = TestUsers.register();
        TestUsers.User member = TestUsers.register();
        Predicate<GatewayPresenceUpdatedDto> memberOnline = presence(member.id(), ONLINE);
        try (GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            assertFalse(present(watcherGateway.ready()).contains(member.id()));
            assertTrue(present(watcherGateway.ready()).contains(watcher.id()), "ready includes the caller");

            GatewayTestClient first = GatewayTestClient.identified(member.token());
            watcherGateway.await(GatewayPresenceUpdatedDto.class, memberOnline);
            assertTrue(present(first.ready()).containsAll(List.of(member.id(), watcher.id())));
            first.assertNone(GatewayPresenceUpdatedDto.class, memberOnline);

            // A second connection changes nothing, and neither does closing one of two.
            try (GatewayTestClient second = GatewayTestClient.identified(member.token())) {
                first.close();
                awaitMarker(watcherGateway, watcher);
                watcherGateway.assertNone(GatewayPresenceUpdatedDto.class, f -> f.getPresence().getAccountId().equals(member.id()));
            }
            watcherGateway.await(GatewayPresenceUpdatedDto.class, presence(member.id(), OFFLINE));
        }
    }

    @Test
    void typingReachesTheOthersWhoCanSeeTheChannel() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        UUID crew = data.createRole("Crew");
        data.assignRole(alice.id(), crew);
        UUID crewOnly = data.createChannel(ChannelTypeDto.TEXT, "crew", crew);
        UUID other = data.createChannel(ChannelTypeDto.TEXT, "other");
        UUID voice = data.createChannel(ChannelTypeDto.VOICE, "voice only");
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient aliceElsewhere = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            aliceGateway.send(typing(GENERAL_TEXT));
            GatewayTypingStartedDto started = bobGateway.await(GatewayTypingStartedDto.class, f -> f.getAccountId().equals(alice.id()));
            assertEquals(GENERAL_TEXT, started.getChannelId());

            // Too soon after the first, in a voice-only channel, or where bob cannot see: nothing is passed on.
            aliceGateway.send(typing(GENERAL_TEXT));
            aliceGateway.send(typing(voice));
            aliceGateway.send(typing(crewOnly));
            aliceGateway.send(typing(UUID.randomUUID()));
            // A connection's frames are handled in order, so once this one is through, so are those above.
            aliceGateway.send(typing(other));
            bobGateway.await(GatewayTypingStartedDto.class, f -> f.getChannelId().equals(other));
            bobGateway.assertNone(GatewayTypingStartedDto.class, f -> f.getAccountId().equals(alice.id()));

            // Alice's own connections never hear about her typing.
            bobGateway.send(typing(other));
            aliceElsewhere.await(GatewayTypingStartedDto.class, f -> f.getAccountId().equals(bob.id()));
            aliceElsewhere.assertNone(GatewayTypingStartedDto.class, f -> f.getAccountId().equals(alice.id()));
        }
    }

    @Test
    void messagesArriveLiveAndOnlyTheSendingSessionGetsTheNonce() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            UUID id = Messages.send(alice, GENERAL_TEXT, new MessageCreateDto().content("live!").nonce("pending-1")).getId();

            UserMessageDto own = assertInstanceOf(UserMessageDto.class,
                aliceGateway.await(GatewayMessageCreatedDto.class, messageCreated(id)).getMessage());
            assertEquals("live!", own.getContent());
            assertEquals("pending-1", own.getNonce());
            UserMessageDto theirs = assertInstanceOf(UserMessageDto.class,
                bobGateway.await(GatewayMessageCreatedDto.class, messageCreated(id)).getMessage());
            assertNull(theirs.getNonce());

            messagesApi(alice).editMessage(GENERAL_TEXT, id, new MessageUpdateDto().content("edited"));
            UserMessageDto edited = assertInstanceOf(UserMessageDto.class,
                bobGateway.await(GatewayMessageUpdatedDto.class, f -> Messages.id(f.getMessage()).equals(id)).getMessage());
            assertEquals("edited", edited.getContent());

            messagesApi(alice).deleteMessage(GENERAL_TEXT, id);
            DeletedMessageDto deleted = (DeletedMessageDto) bobGateway.await(GatewayMessageUpdatedDto.class,
                f -> f.getMessage() instanceof DeletedMessageDto d && d.getId().equals(id)).getMessage();
            assertFalse(deleted.getRemovedByModerator());

            UserMessageDto spam = Messages.send(alice, GENERAL_TEXT, "spam");
            messagesApi(owner).purgeMessages(alice.id(), spam.getCreatedAt());
            GatewayMessagesPurgedDto purged = bobGateway.await(GatewayMessagesPurgedDto.class,
                f -> f.getFromMessageId().equals(spam.getId()));
            assertEquals(GENERAL_TEXT, purged.getChannelId());
            assertEquals(alice.id(), purged.getAuthorId());
            assertEquals(spam.getId(), purged.getToMessageId());
            assertTrue(purged.getRemovedByModerator());
        }
    }

    @Test
    void privateChannelsAndTheirMessagesStayHidden() {
        TestUsers.User insider = TestUsers.register();
        TestUsers.User outsider = TestUsers.register();
        UUID insiders = data.createRole("Insiders");
        data.assignRole(insider.id(), insiders);
        try (GatewayTestClient insiderGateway = GatewayTestClient.identified(insider.token());
             GatewayTestClient outsiderGateway = GatewayTestClient.identified(outsider.token())) {
            UUID secret = data.createChannel(ChannelTypeDto.TEXT, "secret", insiders);
            insiderGateway.await(GatewayChannelCreatedDto.class, created(secret));
            UUID whisper = Messages.send(owner, secret, "psst").getId();
            insiderGateway.await(GatewayMessageCreatedDto.class, messageCreated(whisper));

            // Frames reach a connection in order, so once the marker arrives the secret would have too.
            awaitMarker(outsiderGateway, owner);
            outsiderGateway.assertNone(GatewayChannelCreatedDto.class, created(secret));
            outsiderGateway.assertNone(GatewayMessageCreatedDto.class, f -> Messages.channelId(f.getMessage()).equals(secret));

            channelsApi(owner).deleteChannel(secret);
            insiderGateway.await(GatewayChannelDeletedDto.class, f -> f.getChannelId().equals(secret));
        }
    }

    @Test
    void readMarkersStartAtWhatIsThereAndFollowTheMember() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        UUID crew = data.createRole("Crew");
        UUID reading = data.createChannel(ChannelTypeDto.TEXT, "reading");
        UUID voice = data.createChannel(ChannelTypeDto.VOICE, "voice only");
        UUID secret = data.createChannel(ChannelTypeDto.TEXT, "secret", crew);
        UUID before = Messages.send(owner, reading, "before alice looked").getId();
        UUID hiddenBefore = Messages.send(owner, secret, "before alice could see").getId();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient aliceElsewhere = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            // What was there before she first saw the channel counts as read; voice channels have no state.
            List<ReadStateDto> states = aliceGateway.ready().getReadStates();
            ReadStateDto state = states.stream().filter(s -> s.getChannelId().equals(reading)).findFirst().orElseThrow();
            assertEquals(before, state.getLastReadMessageId());
            assertEquals(before, state.getLastMessageId());
            List<UUID> withState = states.stream().map(ReadStateDto::getChannelId).toList();
            assertFalse(withState.contains(voice));
            assertFalse(withState.contains(secret));

            UUID unread = Messages.send(bob, reading, "for alice").getId();
            messagesApi(alice).markRead(reading, new ReadStateUpdateDto().lastReadMessageId(unread));
            for (GatewayTestClient gateway : List.of(aliceGateway, aliceElsewhere)) {
                assertEquals(unread, gateway.await(GatewayReadStateUpdatedDto.class, readState(reading)).getReadState().getLastReadMessageId());
            }

            // Sending reads up to the message sent; marking an older message read changes nothing.
            UUID own = Messages.send(alice, reading, "mine").getId();
            for (GatewayTestClient gateway : List.of(aliceGateway, aliceElsewhere)) {
                assertEquals(own, gateway.await(GatewayReadStateUpdatedDto.class, readState(reading)).getReadState().getLastReadMessageId());
            }
            messagesApi(alice).markRead(reading, new ReadStateUpdateDto().lastReadMessageId(before));

            // A channel that becomes visible starts out read.
            data.assignRole(alice.id(), crew);
            aliceGateway.await(GatewayChannelCreatedDto.class, created(secret));
            assertEquals(hiddenBefore,
                aliceGateway.await(GatewayReadStateUpdatedDto.class, readState(secret)).getReadState().getLastReadMessageId());

            // Nobody else hears about Alice's reading.
            awaitMarker(aliceGateway, bob);
            aliceGateway.assertNone(GatewayReadStateUpdatedDto.class, readState(reading));
            awaitMarker(bobGateway, bob);
            bobGateway.assertNone(GatewayReadStateUpdatedDto.class, f -> own.equals(f.getReadState().getLastReadMessageId()));
        }
    }

    @Test
    void roleAndChannelAccessChangesArriveAsDifferences() {
        TestUsers.User member = TestUsers.register();
        UUID seers = data.createRole("Seers", TIMEOUT_MEMBERS);
        UUID hidden = data.createChannel(ChannelTypeDto.TEXT, "for seers", seers);
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            assertFalse(gateway.ready().getChannels().stream().anyMatch(c -> c.getId().equals(hidden)));
            assertTrue(gateway.ready().getRoles().stream().anyMatch(r -> r.getId().equals(seers)));

            data.assignRole(member.id(), seers);
            gateway.await(GatewayMemberUpdatedDto.class,
                f -> f.getMember().getId().equals(member.id()) && f.getMember().getRoleIds().contains(seers));
            assertTrue(gateway.await(GatewayPermissionsChangedDto.class).getPermissions().getPermissions().contains(TIMEOUT_MEMBERS));
            gateway.await(GatewayChannelCreatedDto.class, created(hidden));

            rolesApi(owner).updateRole(seers, new RoleUpdateDto().name("Renamed seers"));
            assertEquals("Renamed seers",
                gateway.await(GatewayRoleUpdatedDto.class, f -> f.getRole().getId().equals(seers)).getRole().getName());

            data.unassignRole(member.id(), seers);
            gateway.await(GatewayChannelDeletedDto.class, f -> f.getChannelId().equals(hidden));
            assertFalse(gateway.await(GatewayPermissionsChangedDto.class).getPermissions().getPermissions().contains(TIMEOUT_MEMBERS));

            // Making the channel public reveals it.
            channelsApi(owner).updateChannel(hidden, new ChannelUpdateDto().requiredRoleIds(List.of()));
            assertEquals(List.of(), gateway.await(GatewayChannelCreatedDto.class, created(hidden)).getChannel().getRequiredRoleIds());

            data.deleteRole(seers);
            gateway.await(GatewayRoleDeletedDto.class, f -> f.getRoleId().equals(seers));
        }
    }

    @Test
    void channelChangesArriveWithTheirNotices() {
        TestUsers.User member = TestUsers.register();
        ChannelsApi channels = channelsApi(owner);
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            UUID channel = data.createChannel(ChannelTypeDto.VOICE_TEXT, "lounge");
            gateway.await(GatewayChannelCreatedDto.class, created(channel));
            gateway.await(GatewayMessageCreatedDto.class,
                carrying(ChannelCreatedNoticeDto.class).and(f -> Messages.channelId(f.getMessage()).equals(channel)));

            channels.updateChannel(channel, new ChannelUpdateDto().name("big lounge").bitrate(96000));
            ChannelDto updated = gateway.await(GatewayChannelUpdatedDto.class,
                f -> f.getChannel().getId().equals(channel) && "big lounge".equals(f.getChannel().getName())).getChannel();
            assertEquals(96000, updated.getBitrate());
            SystemMessageDto notice = (SystemMessageDto) gateway.await(GatewayMessageCreatedDto.class,
                carrying(ChannelRenamedNoticeDto.class)).getMessage();
            assertEquals("big lounge", ((ChannelRenamedNoticeDto) notice.getNotice()).getTo());

            // Moving a channel to the top shifts the others; each moved channel is updated.
            channels.updateChannel(channel, new ChannelUpdateDto().position(0));
            gateway.await(GatewayChannelUpdatedDto.class, f -> f.getChannel().getId().equals(channel) && f.getChannel().getPosition() == 0);
            gateway.await(GatewayChannelUpdatedDto.class, f -> f.getChannel().getId().equals(GENERAL_TEXT) && f.getChannel().getPosition() == 1);

            channels.deleteChannel(channel);
            gateway.await(GatewayChannelDeletedDto.class, f -> f.getChannelId().equals(channel));
            gateway.await(GatewayChannelUpdatedDto.class, f -> f.getChannel().getId().equals(GENERAL_TEXT) && f.getChannel().getPosition() == 0);
        }
    }

    @Test
    void membersJoiningAndServerChangesAreBroadcast() {
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            TestUsers.User joined = TestUsers.register();
            gateway.await(GatewayMemberJoinedDto.class, f -> f.getMember().getId().equals(joined.id()));
            gateway.await(GatewayMessageCreatedDto.class, carrying(MemberJoinedNoticeDto.class)
                .and(f -> joined.id().equals(((SystemMessageDto) f.getMessage()).getAuthorId())));

            data.updateSettings(new ServerSettingsUpdateDto().name("Live community"));
            assertEquals("Live community", gateway.await(GatewayServerUpdatedDto.class).getServer().getCommunity().getName());
        }
    }

    @Test
    void loggingOutClosesTheSessionsConnectionsOnly() {
        TestUsers.User member = TestUsers.register();
        TestUsers.User otherSession = TestUsers.newSession(member);
        try (GatewayTestClient first = GatewayTestClient.identified(member.token());
             GatewayTestClient second = GatewayTestClient.identified(member.token());
             GatewayTestClient elsewhere = GatewayTestClient.identified(otherSession.token())) {
            authApi(member).logout();
            assertEquals(new Closed(4004, SESSION_ENDED), first.awaitClose());
            assertEquals(new Closed(4004, SESSION_ENDED), second.awaitClose());

            UUID id = Messages.send(otherSession, GENERAL_TEXT, "still here").getId();
            elsewhere.await(GatewayMessageCreatedDto.class, messageCreated(id));
        }
    }
}
