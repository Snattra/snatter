package app.snatter.server.message;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MessageResourceTest {

    private static final String GENERAL_TEXT = "00000000-0000-7000-8000-000000000101";
    private static final String GENERAL_VOICE = "00000000-0000-7000-8000-000000000102";

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private static String createChannel(String type, String name) {
        return as(TestUsers.ownerToken()).body(Map.of("type", type, "name", name))
            .post("/api/v1/channels").then().statusCode(201).extract().path("id");
    }

    private static void deleteChannel(String id) {
        as(TestUsers.ownerToken()).delete("/api/v1/channels/" + id).then().statusCode(204);
    }

    private static Response send(String token, String channel, Map<String, Object> body) {
        return as(token).body(body).post("/api/v1/channels/" + channel + "/messages");
    }

    private static String send(String token, String channel, String content) {
        return send(token, channel, Map.of("content", content)).then().statusCode(201).extract().path("id");
    }

    private static Response list(String token, String channel, String query) {
        return as(token).get("/api/v1/channels/" + channel + "/messages" + query);
    }

    private static Response message(String token, String channel, String id) {
        return as(token).get("/api/v1/channels/" + channel + "/messages/" + id);
    }

    private static Response patchSettings(Map<String, Object> update) {
        return TestUsers.patchSettings(update);
    }

    @Test
    void membersSendEditAndDeleteTheirOwnMessages() {
        String channel = createChannel("text", "chat");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try {
            String id = send(alice.token(), channel, Map.of("content", "  hello <@" + bob.id() + ">  ", "nonce", "n-1"))
                .then().statusCode(201)
                .body("kind", equalTo("user"))
                .body("channelId", equalTo(channel))
                .body("authorId", equalTo(alice.id()))
                .body("content", equalTo("hello <@" + bob.id() + ">"))
                .body("$", not(hasKey("notice")))
                .body("nonce", equalTo("n-1"))
                .body("editedAt", nullValue())
                .body("createdAt", notNullValue())
                .extract().path("id");
            list(bob.token(), channel, "").then().statusCode(200)
                .body("[-1].id", equalTo(id))
                .body("[-1].nonce", nullValue());

            as(bob.token()).body(Map.of("content", "hijacked")).patch("/api/v1/channels/" + channel + "/messages/" + id)
                .then().statusCode(403).body("error", equalTo("forbidden"));
            as(alice.token()).body(Map.of("content", "hello again")).patch("/api/v1/channels/" + channel + "/messages/" + id)
                .then().statusCode(200).body("content", equalTo("hello again")).body("editedAt", notNullValue());

            as(bob.token()).delete("/api/v1/channels/" + channel + "/messages/" + id)
                .then().statusCode(403).body("error", equalTo("forbidden"));
            as(alice.token()).delete("/api/v1/channels/" + channel + "/messages/" + id).then().statusCode(204);
            message(alice.token(), channel, id).then().statusCode(404).body("error", equalTo("message_not_found"));
            // A message is only reachable through its own channel.
            String elsewhere = send(alice.token(), GENERAL_TEXT, "hi general");
            message(alice.token(), channel, elsewhere).then().statusCode(404);
            as(alice.token()).delete("/api/v1/channels/" + GENERAL_TEXT + "/messages/" + elsewhere).then().statusCode(204);
        } finally {
            deleteChannel(channel);
        }
    }

    @Test
    void contentIsValidated() {
        String member = TestUsers.register().token();
        send(member, GENERAL_TEXT, Map.of("content", "   ")).then().statusCode(400).body("error", equalTo("validation_failed"));
        send(member, GENERAL_TEXT, Map.of("content", "")).then().statusCode(400);
        send(member, GENERAL_TEXT, Map.of("content", "x".repeat(4001))).then().statusCode(400);
        send(member, GENERAL_TEXT, Map.of("content", "x", "nonce", "n".repeat(65))).then().statusCode(400);
        String longest = send(member, GENERAL_TEXT, "x".repeat(4000));
        String multiline = send(member, GENERAL_TEXT, "\nfirst line\nsecond line\n");
        message(member, GENERAL_TEXT, multiline).then().body("content", equalTo("first line\nsecond line"));
        as(member).delete("/api/v1/channels/" + GENERAL_TEXT + "/messages/" + longest).then().statusCode(204);
        as(member).delete("/api/v1/channels/" + GENERAL_TEXT + "/messages/" + multiline).then().statusCode(204);
    }

    @Test
    void pagesAreChronologicalAndCursorsWorkInBothDirections() {
        String channel = createChannel("voice_text", "history");
        String member = TestUsers.register().token();
        try {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                ids.add(send(member, channel, "m" + i));
            }
            list(member, channel, "?limit=3").then().statusCode(200).body("content", equalTo(List.of("m4", "m5", "m6")));
            list(member, channel, "?limit=3&before=" + ids.get(4)).then().body("content", equalTo(List.of("m1", "m2", "m3")));
            list(member, channel, "?limit=2&after=" + ids.get(1)).then().body("content", equalTo(List.of("m2", "m3")));
            list(member, channel, "?after=" + ids.get(6)).then().body("size()", equalTo(0));
            // The first page reaches back to the channel_created notice.
            list(member, channel, "").then().body("size()", equalTo(8)).body("[0].kind", equalTo("system"));

            // A deleted message still works as a cursor.
            as(member).delete("/api/v1/channels/" + channel + "/messages/" + ids.get(3)).then().statusCode(204);
            list(member, channel, "?limit=2&before=" + ids.get(3)).then().body("content", equalTo(List.of("m1", "m2")));
            list(member, channel, "?limit=2&after=" + ids.get(3)).then().body("content", equalTo(List.of("m4", "m5")));

            list(member, channel, "?before=" + ids.get(4) + "&after=" + ids.get(1))
                .then().statusCode(400).body("error", equalTo("invalid_paging"));
            list(member, channel, "?limit=0").then().statusCode(400);
            list(member, channel, "?limit=101").then().statusCode(400);
        } finally {
            deleteChannel(channel);
        }
    }

    @Test
    void repliesCarryAPreviewUntilTheOriginalIsDeleted() {
        String channel = createChannel("text", "replies");
        String other = createChannel("text", "other");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try {
            String question = send(alice.token(), channel, "anyone up for a game?");
            String answer = send(bob.token(), channel, Map.of("content", "sure", "replyToId", question))
                .then().statusCode(201)
                .body("replyToId", equalTo(question))
                .body("replyTo.id", equalTo(question))
                .body("replyTo.authorId", equalTo(alice.id()))
                .body("replyTo.content", equalTo("anyone up for a game?"))
                .extract().path("id");

            String elsewhere = send(alice.token(), other, "wrong room");
            send(bob.token(), channel, Map.of("content", "x", "replyToId", elsewhere))
                .then().statusCode(400).body("error", equalTo("invalid_reply"));
            String notice = list(bob.token(), channel, "").then().extract().path("[0].id");
            send(bob.token(), channel, Map.of("content", "x", "replyToId", notice))
                .then().statusCode(400).body("error", equalTo("invalid_reply"));

            as(alice.token()).delete("/api/v1/channels/" + channel + "/messages/" + question).then().statusCode(204);
            message(bob.token(), channel, answer).then().statusCode(200)
                .body("replyToId", equalTo(question))
                .body("replyTo", nullValue());
        } finally {
            deleteChannel(channel);
            deleteChannel(other);
        }
    }

    @Test
    void rolesGovernReadingSendingAndModerating() {
        String owner = TestUsers.ownerToken();
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String channel = createChannel("text", "moderated");
        String hidden = as(owner).body(Map.of("type", "text", "name", "hidden", "requiredRoleIds", List.of(crew)))
            .post("/api/v1/channels").then().statusCode(201).extract().path("id");
        TestUsers.User member = TestUsers.register();
        TestUsers.User mod = TestUsers.registerWithPermissions("MANAGE_MESSAGES");
        try {
            list(member.token(), hidden, "").then().statusCode(404).body("error", equalTo("channel_not_found"));
            send(member.token(), hidden, Map.of("content", "hello?")).then().statusCode(404);

            list(member.token(), GENERAL_VOICE, "").then().statusCode(400).body("error", equalTo("voice_only_channel"));
            send(member.token(), GENERAL_VOICE, Map.of("content", "hello?")).then().statusCode(400).body("error", equalTo("voice_only_channel"));

            // Without the User role, and so without SEND_MESSAGES, a member can still read.
            String announcement = send(owner, channel, "patch notes");
            TestUsers.unassignRole(member.id(), TestUsers.USER_ROLE);
            list(member.token(), channel, "").then().statusCode(200).body("[-1].id", equalTo(announcement));
            send(member.token(), channel, Map.of("content", "first!")).then().statusCode(403).body("error", equalTo("forbidden"));

            // A moderator may delete anyone's messages and notices, but not edit them.
            String notice = list(mod.token(), channel, "").then().extract().path("[0].id");
            as(mod.token()).body(Map.of("content", "edited")).patch("/api/v1/channels/" + channel + "/messages/" + announcement)
                .then().statusCode(403);
            as(owner).body(Map.of("content", "edited")).patch("/api/v1/channels/" + channel + "/messages/" + notice)
                .then().statusCode(403);
            as(member.token()).delete("/api/v1/channels/" + channel + "/messages/" + announcement).then().statusCode(403);
            as(mod.token()).delete("/api/v1/channels/" + channel + "/messages/" + announcement).then().statusCode(204);
            as(mod.token()).delete("/api/v1/channels/" + channel + "/messages/" + notice).then().statusCode(204);
            list(member.token(), channel, "").then().body("size()", equalTo(0));
        } finally {
            deleteChannel(channel);
            deleteChannel(hidden);
            TestUsers.deleteRole(crew);
        }
    }

    private static Response markRead(String token, String channel, String messageId) {
        return as(token).body(Map.of("lastReadMessageId", messageId)).put("/api/v1/channels/" + channel + "/read-state");
    }

    @Test
    void readMarkersOnlyMoveForwardAndSendingMovesTheSenders() {
        String owner = TestUsers.ownerToken();
        String channel = createChannel("text", "reading");
        String other = createChannel("text", "elsewhere");
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String hidden = as(owner).body(Map.of("type", "text", "name", "hidden", "requiredRoleIds", List.of(crew)))
            .post("/api/v1/channels").then().statusCode(201).extract().path("id");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try {
            String first = send(bob.token(), channel, "one");
            String second = send(bob.token(), channel, "two");

            markRead(alice.token(), channel, second).then().statusCode(200)
                .body("channelId", equalTo(channel))
                .body("lastReadMessageId", equalTo(second))
                .body("lastMessageId", equalTo(second));
            markRead(alice.token(), channel, first).then().statusCode(200)
                .body("lastReadMessageId", equalTo(second));

            // Bob's own messages are read to him as he sends them.
            String third = send(bob.token(), channel, "three");
            markRead(bob.token(), channel, first).then().statusCode(200)
                .body("lastReadMessageId", equalTo(third))
                .body("lastMessageId", equalTo(third));

            String elsewhere = send(bob.token(), other, "not here");
            markRead(alice.token(), channel, elsewhere).then().statusCode(404).body("error", equalTo("message_not_found"));
            markRead(alice.token(), channel, UUID.randomUUID().toString()).then().statusCode(404).body("error", equalTo("message_not_found"));
            markRead(alice.token(), GENERAL_VOICE, first).then().statusCode(400).body("error", equalTo("voice_only_channel"));
            markRead(alice.token(), hidden, first).then().statusCode(404).body("error", equalTo("channel_not_found"));
            markRead(alice.token(), channel, "not-a-uuid").then().statusCode(400);
        } finally {
            deleteChannel(channel);
            deleteChannel(other);
            deleteChannel(hidden);
            TestUsers.deleteRole(crew);
        }
    }

    @Test
    void channelChangesLeaveNoticesInTheChannel() {
        String owner = TestUsers.ownerToken();
        String ownerId = as(owner).get("/api/v1/accounts/me").then().extract().path("id");
        String channel = createChannel("text", "noticed");
        String voice = createChannel("voice", "quiet");
        try {
            as(owner).body(Map.of("name", "renamed", "topic", "news")).patch("/api/v1/channels/" + channel).then().statusCode(200);
            as(owner).body(Map.of("topic", "")).patch("/api/v1/channels/" + channel).then().statusCode(200);
            as(owner).body(Map.of("name", "still quiet")).patch("/api/v1/channels/" + voice).then().statusCode(200);

            list(owner, channel, "").then().statusCode(200)
                .body("kind", equalTo(List.of("system", "system", "system", "system")))
                .body("authorId", equalTo(List.of(ownerId, ownerId, ownerId, ownerId)))
                .body("[0]", not(hasKey("content")))
                .body("notice.type", equalTo(List.of("channel_created", "channel_renamed", "channel_topic_changed", "channel_topic_changed")))
                .body("[1].notice.from", equalTo("noticed"))
                .body("[1].notice.to", equalTo("renamed"))
                .body("[2].notice.from", nullValue())
                .body("[2].notice.to", equalTo("news"))
                .body("[3].notice.from", equalTo("news"))
                .body("[3].notice.to", nullValue());
        } finally {
            deleteChannel(channel);
            deleteChannel(voice);
        }
    }

    @Test
    void serverWideNoticesGoToTheSystemChannel() {
        String owner = TestUsers.ownerToken();
        String ownerId = as(owner).get("/api/v1/accounts/me").then().extract().path("id");
        String system = createChannel("text", "system");
        String voice = createChannel("voice", "not for notices");
        String originalName = as(owner).get("/api/v1/server-settings").then().statusCode(200)
            .body("systemChannelId", equalTo(GENERAL_TEXT))
            .extract().path("name");
        try {
            patchSettings(Map.of("systemChannelId", voice)).then().statusCode(400).body("error", equalTo("voice_only_channel"));
            patchSettings(Map.of("systemChannelId", "00000000-0000-7000-8000-00000000ffff"))
                .then().statusCode(400).body("error", equalTo("channel_not_found"));
            patchSettings(Map.of("systemChannelId", "not-a-uuid")).then().statusCode(400).body("error", equalTo("validation_failed"));
            patchSettings(Map.of("systemChannelId", system)).then().statusCode(200).body("systemChannelId", equalTo(system));

            TestUsers.User joined = TestUsers.register();
            patchSettings(Map.of("name", "Renamed community")).then().statusCode(200);
            patchSettings(Map.of("registrationMode", "invite_only")).then().statusCode(200);
            patchSettings(Map.of("registrationMode", "open")).then().statusCode(200);

            list(owner, system, "").then().statusCode(200)
                .body("notice.type", equalTo(List.of("channel_created", "member_joined", "server_renamed",
                    "registration_mode_changed", "registration_mode_changed")))
                .body("[1].authorId", equalTo(joined.id()))
                .body("[2].authorId", equalTo(ownerId))
                .body("[2].notice.from", equalTo(originalName))
                .body("[2].notice.to", equalTo("Renamed community"))
                .body("[3].notice.from", equalTo("open"))
                .body("[3].notice.to", equalTo("invite_only"));

            // Turning notices off, and deleting the system channel, both stop them.
            patchSettings(Map.of("systemChannelId", "")).then().statusCode(200).body("systemChannelId", nullValue());
            TestUsers.register();
            list(owner, system, "").then().body("size()", equalTo(5));
            patchSettings(Map.of("systemChannelId", system)).then().statusCode(200);
            deleteChannel(system);
            system = null;
            as(owner).get("/api/v1/server-settings").then().body("systemChannelId", nullValue());
            TestUsers.register();
        } finally {
            Map<String, Object> restore = new HashMap<>();
            restore.put("name", originalName);
            restore.put("registrationMode", "open");
            restore.put("systemChannelId", GENERAL_TEXT);
            patchSettings(restore).then().statusCode(200);
            if (system != null) {
                deleteChannel(system);
            }
            deleteChannel(voice);
        }
    }

    @Test
    void membersJoiningAreAnnouncedInTheDefaultSystemChannel() {
        TestUsers.User joined = TestUsers.register();
        List<String> byThem = list(joined.token(), GENERAL_TEXT, "?limit=100").then().statusCode(200)
            .extract().path("findAll { it.authorId == '" + joined.id() + "' }.notice.type");
        assertEquals(List.of("member_joined"), byThem);
    }
}
