package app.snatter.server.message;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiAssertions.errorOf;
import static app.snatter.server.testing.ApiAssertions.header;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.messagesApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.ApiException;
import app.snatter.client.api.ChannelsApi;
import app.snatter.client.api.MessagesApi;
import app.snatter.client.model.ChannelCreatedNoticeDto;
import app.snatter.client.model.ChannelRenamedNoticeDto;
import app.snatter.client.model.ChannelTopicChangedNoticeDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.ChannelUpdateDto;
import app.snatter.client.model.DeletedMessageDto;
import app.snatter.client.model.MessageCreateDto;
import app.snatter.client.model.MessageDto;
import app.snatter.client.model.MessageUpdateDto;
import app.snatter.client.model.PermissionDto;
import app.snatter.client.model.RateLimitsDto;
import app.snatter.client.model.ReadStateDto;
import app.snatter.client.model.ReadStateUpdateDto;
import app.snatter.client.model.RegistrationModeChangedNoticeDto;
import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.ServerRenamedNoticeDto;
import app.snatter.client.model.ServerSettingsDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.client.model.SystemMessageDto;
import app.snatter.client.model.SystemNoticeDto;
import app.snatter.client.model.UserMessageDto;
import app.snatter.server.testing.Messages;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MessageResourceTest {

    private static final UUID GENERAL_TEXT = UUID.fromString("00000000-0000-7000-8000-000000000101");

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    private static MessageCreateDto message(String content) {
        return new MessageCreateDto().content(content);
    }

    private static MessageUpdateDto edit(String content) {
        return new MessageUpdateDto().content(content);
    }

    private static ReadStateUpdateDto readUpTo(UUID message) {
        return new ReadStateUpdateDto().lastReadMessageId(message);
    }

    private static ServerSettingsUpdateDto update() {
        return new ServerSettingsUpdateDto();
    }

    /** The newest page of the channel's messages, as the reader sees it. */
    private static List<MessageDto> list(TestUsers.User reader, UUID channel) {
        return messagesApi(reader).listMessages(channel, null, null, null);
    }

    private static UserMessageDto userMessage(MessageDto message) {
        return assertInstanceOf(UserMessageDto.class, message);
    }

    private static SystemMessageDto systemMessage(MessageDto message) {
        return assertInstanceOf(SystemMessageDto.class, message);
    }

    /** The content of each message, all of which must be user messages. */
    private static List<String> contents(List<MessageDto> messages) {
        return messages.stream().map(m -> userMessage(m).getContent()).toList();
    }

    /** The notice of each message, all of which must be system messages. */
    private static List<SystemNoticeDto> notices(List<MessageDto> messages) {
        return messages.stream().map(m -> systemMessage(m).getNotice()).toList();
    }

    private static MessageDto find(List<MessageDto> messages, UUID id) {
        return messages.stream().filter(m -> Messages.id(m).equals(id)).findFirst().orElseThrow();
    }

    @Test
    void membersSendEditAndDeleteTheirOwnMessages() {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "chat");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        MessagesApi asAlice = messagesApi(alice);
        MessagesApi asBob = messagesApi(bob);
        UserMessageDto sent = Messages.send(alice, channel, message("  hello <@" + bob.id() + ">  ").nonce("n-1"));
        assertEquals(channel, sent.getChannelId());
        assertEquals(alice.id(), sent.getAuthorId());
        assertEquals("hello <@" + bob.id() + ">", sent.getContent());
        assertEquals(List.of(bob.id()), sent.getMentions());
        assertEquals("n-1", sent.getNonce());
        assertNull(sent.getEditedAt());
        assertNotNull(sent.getCreatedAt());
        UUID id = sent.getId();
        UserMessageDto seen = userMessage(list(bob, channel).getLast());
        assertEquals(id, seen.getId());
        assertNull(seen.getNonce());

        assertApiError(403, "forbidden", () -> asBob.editMessage(channel, id, edit("hijacked")));
        UserMessageDto edited = userMessage(asAlice.editMessage(channel, id, edit("hello again")));
        assertEquals("hello again", edited.getContent());
        assertEquals(List.of(), edited.getMentions());
        assertNotNull(edited.getEditedAt());

        assertApiError(403, "forbidden", () -> asBob.deleteMessage(channel, id));
        asAlice.deleteMessage(channel, id);
        // What is left keeps its place, without anything alice wrote.
        DeletedMessageDto left = assertInstanceOf(DeletedMessageDto.class, asBob.getMessage(channel, id));
        assertEquals(alice.id(), left.getAuthorId());
        assertFalse(left.getRemovedByModerator());
        assertNotNull(left.getDeletedAt());
        // Deleting it again changes nothing; it can no longer be edited.
        asBob.deleteMessage(channel, id);
        assertApiError(403, "forbidden", () -> asAlice.editMessage(channel, id, edit("back")));
        // A message is only reachable through its own channel.
        UUID elsewhere = Messages.send(alice, GENERAL_TEXT, "hi general").getId();
        assertApiError(404, "message_not_found", () -> asAlice.getMessage(channel, elsewhere));
    }

    @Test
    void contentIsValidated() {
        TestUsers.User member = TestUsers.register();
        MessagesApi messages = messagesApi(member);
        assertApiError(400, "validation_failed", () -> messages.createMessage(GENERAL_TEXT, message("   ")));
        assertApiError(400, "validation_failed", () -> messages.createMessage(GENERAL_TEXT, message("")));
        assertApiError(400, "validation_failed", () -> messages.createMessage(GENERAL_TEXT, message("x".repeat(4001))));
        assertApiError(400, "validation_failed", () -> messages.createMessage(GENERAL_TEXT, message("x").nonce("n".repeat(65))));
        Messages.send(member, GENERAL_TEXT, "x".repeat(4000));
        UUID multiline = Messages.send(member, GENERAL_TEXT, "\nfirst line\nsecond line\n").getId();
        assertEquals("first line\nsecond line", userMessage(messages.getMessage(GENERAL_TEXT, multiline)).getContent());
    }

    @Test
    void mentionsAreTheExistingMembersNamedInTheContent() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        TestUsers.User carol = TestUsers.register();
        MessagesApi asAlice = messagesApi(alice);
        String nobody = UUID.randomUUID().toString();
        String role = UUID.randomUUID().toString();
        String content = "<@" + carol.id() + "> and <@" + bob.id() + ">, again <@" + carol.id().toString().toUpperCase() + ">,"
            + " <@" + nobody + "> <@&" + role + "> <#" + GENERAL_TEXT + "> <@not-an-id>";
        UserMessageDto sent = Messages.send(alice, GENERAL_TEXT, content);
        assertEquals(content, sent.getContent());
        assertEquals(List.of(carol.id(), bob.id()), sent.getMentions());
        UUID id = sent.getId();
        assertEquals(List.of(carol.id(), bob.id()), userMessage(messagesApi(bob).getMessage(GENERAL_TEXT, id)).getMentions());

        // Editing works them out again.
        assertEquals(List.of(alice.id()), userMessage(asAlice.editMessage(GENERAL_TEXT, id, edit("just <@" + alice.id() + ">"))).getMentions());
        assertEquals(List.of(alice.id()), userMessage(find(list(bob, GENERAL_TEXT), id)).getMentions());

        // At most 20 members, counted before checking they exist.
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            many.append("<@").append(UUID.randomUUID()).append("> ");
        }
        assertApiError(400, "too_many_mentions", () -> asAlice.createMessage(GENERAL_TEXT, message(many.toString())));
        assertApiError(400, "too_many_mentions", () -> asAlice.editMessage(GENERAL_TEXT, id, edit(many.toString())));
        // Twenty, one of them twice, is fine; only the one that exists is a mention.
        StringBuilder twenty = new StringBuilder("<@" + alice.id() + "> ");
        for (int i = 0; i < 19; i++) {
            twenty.append("<@").append(UUID.randomUUID()).append("> ");
        }
        twenty.append("<@").append(alice.id()).append(">");
        assertEquals(List.of(alice.id()), Messages.send(alice, GENERAL_TEXT, twenty.toString()).getMentions());
    }

    @Test
    void moderatorsPurgeAMembersRecentMessages() {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "purged");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        MessagesApi asOwner = messagesApi(owner);
        MessagesApi asBob = messagesApi(bob);
        UUID older = Messages.send(alice, channel, "before").getId();
        UserMessageDto first = Messages.send(alice, channel, "spam 1");
        Instant since = first.getCreatedAt();
        UUID second = Messages.send(alice, GENERAL_TEXT, "spam 2").getId();
        UUID reply = Messages.send(alice, channel, message("spam 3").replyToId(first.getId())).getId();
        UUID bobs = Messages.send(bob, channel, "not spam").getId();

        // Only with MANAGE_MESSAGES, for an existing account, and from a time that has passed.
        assertApiError(403, "forbidden", () -> asBob.purgeMessages(alice.id(), since));
        assertApiError(404, "account_not_found", () -> asOwner.purgeMessages(UUID.randomUUID(), since));
        assertApiError(400, "invalid_since", () -> asOwner.purgeMessages(alice.id(), Instant.parse("2999-01-01T00:00:00Z")));

        assertEquals(3, asOwner.purgeMessages(alice.id(), since).getRemoved());
        for (UUID id : List.of(first.getId(), reply)) {
            assertTrue(assertInstanceOf(DeletedMessageDto.class, asBob.getMessage(channel, id)).getRemovedByModerator());
        }
        assertInstanceOf(DeletedMessageDto.class, asBob.getMessage(GENERAL_TEXT, second));
        userMessage(asBob.getMessage(channel, older));
        userMessage(asBob.getMessage(channel, bobs));
        // Nothing left to purge.
        assertEquals(0, asOwner.purgeMessages(alice.id(), since).getRemoved());
    }

    @Test
    void sendingIsRateLimitedPerAccount() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        RateLimitsDto limits = data.updateSettings(update().rateLimits(TestDataService.messageRateLimit(2, 60))).getRateLimits();
        assertEquals(2, limits.getMessage().getLimit());
        assertEquals(60, limits.getMessage().getPeriodSeconds());
        UUID first = Messages.send(alice, GENERAL_TEXT, "one").getId();
        Messages.send(alice, GENERAL_TEXT, "two");
        ApiException limited = assertApiStatus(429, () -> messagesApi(alice).createMessage(GENERAL_TEXT, message("three")));
        assertEquals("rate_limited", errorOf(limited).getError());
        assertNotNull(header(limited, "Retry-After"));
        // Counted per account: bob, from the same address, still has his own.
        Messages.send(bob, GENERAL_TEXT, "mine");
        // Only sending counts: alice can still edit and read.
        messagesApi(alice).editMessage(GENERAL_TEXT, first, edit("one, edited"));
        list(alice, GENERAL_TEXT);
    }

    @Test
    void pagesAreChronologicalAndCursorsWorkInBothDirections() {
        UUID channel = data.createChannel(ChannelTypeDto.VOICE_TEXT, "history");
        TestUsers.User member = TestUsers.register();
        MessagesApi messages = messagesApi(member);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ids.add(Messages.send(member, channel, "m" + i).getId());
        }
        assertEquals(List.of("m4", "m5", "m6"), contents(messages.listMessages(channel, null, null, 3)));
        assertEquals(List.of("m1", "m2", "m3"), contents(messages.listMessages(channel, ids.get(4), null, 3)));
        assertEquals(List.of("m2", "m3"), contents(messages.listMessages(channel, null, ids.get(1), 2)));
        assertEquals(List.of(), messages.listMessages(channel, null, ids.get(6), null));
        // The first page reaches back to the channel_created notice.
        List<MessageDto> firstPage = messages.listMessages(channel, null, null, null);
        assertEquals(8, firstPage.size());
        systemMessage(firstPage.getFirst());

        // A deleted message still works as a cursor.
        messages.deleteMessage(channel, ids.get(3));
        assertEquals(List.of("m1", "m2"), contents(messages.listMessages(channel, ids.get(3), null, 2)));
        assertEquals(List.of("m4", "m5"), contents(messages.listMessages(channel, null, ids.get(3), 2)));

        assertApiError(400, "invalid_paging", () -> messages.listMessages(channel, ids.get(4), ids.get(1), null));
        assertApiError(400, "validation_failed", () -> messages.listMessages(channel, null, null, 0));
        assertApiError(400, "validation_failed", () -> messages.listMessages(channel, null, null, 101));
    }

    @Test
    void repliesCarryAPreviewUntilTheOriginalIsDeleted() {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "replies");
        UUID other = data.createChannel(ChannelTypeDto.TEXT, "other");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        MessagesApi asBob = messagesApi(bob);
        UUID question = Messages.send(alice, channel, "anyone up for a game?").getId();
        UserMessageDto answer = Messages.send(bob, channel, message("sure").replyToId(question));
        assertEquals(question, answer.getReplyToId());
        assertEquals(question, answer.getReplyTo().getId());
        assertEquals(alice.id(), answer.getReplyTo().getAuthorId());
        assertEquals("anyone up for a game?", answer.getReplyTo().getContent());

        UUID elsewhere = Messages.send(alice, other, "wrong room").getId();
        assertApiError(400, "invalid_reply", () -> asBob.createMessage(channel, message("x").replyToId(elsewhere)));
        UUID notice = Messages.id(list(bob, channel).getFirst());
        assertApiError(400, "invalid_reply", () -> asBob.createMessage(channel, message("x").replyToId(notice)));

        messagesApi(alice).deleteMessage(channel, question);
        UserMessageDto orphan = userMessage(asBob.getMessage(channel, answer.getId()));
        assertEquals(question, orphan.getReplyToId());
        assertNull(orphan.getReplyTo());
    }

    @Test
    void rolesGovernReadingSendingAndModerating() {
        UUID crew = data.createRole("Crew");
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "moderated");
        UUID voice = data.createChannel(ChannelTypeDto.VOICE, "voice only");
        UUID hidden = data.createChannel(ChannelTypeDto.TEXT, "hidden", crew);
        TestUsers.User member = TestUsers.register();
        TestUsers.User mod = data.registerWithPermissions(PermissionDto.MANAGE_MESSAGES);
        MessagesApi asMember = messagesApi(member);
        MessagesApi asMod = messagesApi(mod);
        MessagesApi asOwner = messagesApi(owner);
        assertApiError(404, "channel_not_found", () -> asMember.listMessages(hidden, null, null, null));
        assertApiError(404, "channel_not_found", () -> asMember.createMessage(hidden, message("hello?")));

        assertApiError(400, "voice_only_channel", () -> asMember.listMessages(voice, null, null, null));
        assertApiError(400, "voice_only_channel", () -> asMember.createMessage(voice, message("hello?")));

        // Without the User role, and so without SEND_MESSAGES, a member can still read.
        UUID announcement = Messages.send(owner, channel, "patch notes").getId();
        data.unassignRole(member.id(), TestDataService.USER_ROLE);
        assertEquals(announcement, Messages.id(list(member, channel).getLast()));
        assertApiError(403, "forbidden", () -> asMember.createMessage(channel, message("first!")));

        // A moderator may delete anyone's messages and notices, but not edit them.
        UUID notice = Messages.id(list(mod, channel).getFirst());
        assertApiError(403, "forbidden", () -> asMod.editMessage(channel, announcement, edit("edited")));
        assertApiError(403, "forbidden", () -> asOwner.editMessage(channel, notice, edit("edited")));
        assertApiError(403, "forbidden", () -> asMember.deleteMessage(channel, announcement));
        asMod.deleteMessage(channel, announcement);
        asMod.deleteMessage(channel, notice);
        // The notice is gone; the announcement is marked as removed by a moderator.
        List<MessageDto> left = list(member, channel);
        assertEquals(1, left.size());
        DeletedMessageDto removed = assertInstanceOf(DeletedMessageDto.class, left.getFirst());
        assertEquals(announcement, removed.getId());
        assertTrue(removed.getRemovedByModerator());
    }

    @Test
    void readMarkersOnlyMoveForwardAndSendingMovesTheSenders() {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "reading");
        UUID other = data.createChannel(ChannelTypeDto.TEXT, "elsewhere");
        UUID voice = data.createChannel(ChannelTypeDto.VOICE, "voice only");
        UUID crew = data.createRole("Crew");
        UUID hidden = data.createChannel(ChannelTypeDto.TEXT, "hidden", crew);
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        MessagesApi asAlice = messagesApi(alice);
        UUID first = Messages.send(bob, channel, "one").getId();
        UUID second = Messages.send(bob, channel, "two").getId();

        ReadStateDto read = asAlice.markRead(channel, readUpTo(second));
        assertEquals(channel, read.getChannelId());
        assertEquals(second, read.getLastReadMessageId());
        assertEquals(second, read.getLastMessageId());
        assertEquals(second, asAlice.markRead(channel, readUpTo(first)).getLastReadMessageId());

        // Bob's own messages are read to him as he sends them.
        UUID third = Messages.send(bob, channel, "three").getId();
        ReadStateDto bobs = messagesApi(bob).markRead(channel, readUpTo(first));
        assertEquals(third, bobs.getLastReadMessageId());
        assertEquals(third, bobs.getLastMessageId());

        UUID elsewhere = Messages.send(bob, other, "not here").getId();
        assertApiError(404, "message_not_found", () -> asAlice.markRead(channel, readUpTo(elsewhere)));
        assertApiError(404, "message_not_found", () -> asAlice.markRead(channel, readUpTo(UUID.randomUUID())));
        assertApiError(400, "voice_only_channel", () -> asAlice.markRead(voice, readUpTo(first)));
        assertApiError(404, "channel_not_found", () -> asAlice.markRead(hidden, readUpTo(first)));
        // An id that is not a UUID can only be sent as plain JSON.
        given().header("Authorization", "Bearer " + alice.token()).contentType(ContentType.JSON)
            .body(Map.of("lastReadMessageId", "not-a-uuid")).put("/api/v1/channels/" + channel + "/read-state")
            .then().statusCode(400);
    }

    @Test
    void channelChangesLeaveNoticesInTheChannel() {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "noticed");
        UUID voice = data.createChannel(ChannelTypeDto.VOICE, "quiet");
        ChannelsApi channels = channelsApi(owner);
        channels.updateChannel(channel, new ChannelUpdateDto().name("renamed").topic("news"));
        channels.updateChannel(channel, new ChannelUpdateDto().topic(""));
        channels.updateChannel(voice, new ChannelUpdateDto().name("still quiet"));

        List<MessageDto> messages = list(owner, channel);
        assertEquals(List.of(owner.id(), owner.id(), owner.id(), owner.id()),
            messages.stream().map(m -> systemMessage(m).getAuthorId()).toList());
        List<SystemNoticeDto> notices = notices(messages);
        assertInstanceOf(ChannelCreatedNoticeDto.class, notices.get(0));
        ChannelRenamedNoticeDto renamed = assertInstanceOf(ChannelRenamedNoticeDto.class, notices.get(1));
        assertEquals("noticed", renamed.getFrom());
        assertEquals("renamed", renamed.getTo());
        ChannelTopicChangedNoticeDto topicSet = assertInstanceOf(ChannelTopicChangedNoticeDto.class, notices.get(2));
        assertNull(topicSet.getFrom());
        assertEquals("news", topicSet.getTo());
        ChannelTopicChangedNoticeDto topicCleared = assertInstanceOf(ChannelTopicChangedNoticeDto.class, notices.get(3));
        assertEquals("news", topicCleared.getFrom());
        assertNull(topicCleared.getTo());
    }

    @Test
    void serverWideNoticesGoToTheSystemChannel() {
        UUID system = data.createChannel(ChannelTypeDto.TEXT, "system");
        UUID voice = data.createChannel(ChannelTypeDto.VOICE, "not for notices");
        ServerSettingsDto before = serverApi(owner).getServerSettings();
        assertEquals(GENERAL_TEXT, before.getSystemChannelId());
        assertApiError(400, "voice_only_channel", () -> data.updateSettings(update().systemChannelId(voice.toString())));
        assertApiError(400, "channel_not_found",
            () -> data.updateSettings(update().systemChannelId("00000000-0000-7000-8000-00000000ffff")));
        assertApiError(400, "validation_failed", () -> data.updateSettings(update().systemChannelId("not-a-uuid")));
        assertEquals(system, data.updateSettings(update().systemChannelId(system.toString())).getSystemChannelId());

        TestUsers.User joined = TestUsers.register();
        data.updateSettings(update().name("Renamed community"));
        data.updateSettings(update().registrationMode(RegistrationModeDto.INVITE_ONLY));
        data.updateSettings(update().registrationMode(RegistrationModeDto.OPEN));

        List<MessageDto> messages = list(owner, system);
        List<SystemNoticeDto> notices = notices(messages);
        assertEquals(List.of("channel_created", "member_joined", "server_renamed", "registration_mode_changed", "registration_mode_changed"),
            notices.stream().map(SystemNoticeDto::getType).toList());
        assertEquals(joined.id(), systemMessage(messages.get(1)).getAuthorId());
        assertEquals(owner.id(), systemMessage(messages.get(2)).getAuthorId());
        ServerRenamedNoticeDto renamed = assertInstanceOf(ServerRenamedNoticeDto.class, notices.get(2));
        assertEquals(before.getName(), renamed.getFrom());
        assertEquals("Renamed community", renamed.getTo());
        RegistrationModeChangedNoticeDto closed = assertInstanceOf(RegistrationModeChangedNoticeDto.class, notices.get(3));
        assertEquals(RegistrationModeDto.OPEN, closed.getFrom());
        assertEquals(RegistrationModeDto.INVITE_ONLY, closed.getTo());

        // Turning notices off, and deleting the system channel, both stop them.
        assertNull(data.updateSettings(update().systemChannelId("")).getSystemChannelId());
        TestUsers.register();
        assertEquals(5, list(owner, system).size());
        data.updateSettings(update().systemChannelId(system.toString()));
        channelsApi(owner).deleteChannel(system);
        assertNull(serverApi(owner).getServerSettings().getSystemChannelId());
        TestUsers.register();
    }

    @Test
    void membersJoiningAreAnnouncedInTheDefaultSystemChannel() {
        TestUsers.User joined = TestUsers.register();
        List<String> byThem = messagesApi(joined).listMessages(GENERAL_TEXT, null, null, 100).stream()
            .filter(SystemMessageDto.class::isInstance)
            .map(SystemMessageDto.class::cast)
            .filter(m -> joined.id().equals(m.getAuthorId()))
            .map(m -> m.getNotice().getType())
            .toList();
        assertEquals(List.of("member_joined"), byThem);
    }
}
