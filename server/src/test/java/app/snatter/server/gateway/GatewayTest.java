package app.snatter.server.gateway;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.protocol.Protocol;
import app.snatter.server.protocol.ProtocolVersion;
import app.snatter.server.testing.GatewayTestClient;
import app.snatter.server.testing.GatewayTestClient.Closed;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GatewayTest {

    private static final String GENERAL_TEXT = "00000000-0000-7000-8000-000000000101";

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private static Response send(String token, String channel, Map<String, Object> body) {
        return as(token).body(body).post("/api/v1/channels/" + channel + "/messages");
    }

    private static String createChannel(Map<String, Object> body) {
        return as(TestUsers.ownerToken()).body(body).post("/api/v1/channels").then().statusCode(201).extract().path("id");
    }

    private static void deleteChannel(String id) {
        as(TestUsers.ownerToken()).delete("/api/v1/channels/" + id).then().statusCode(204);
    }

    private static Predicate<JsonPath> channel(String id) {
        return frame -> id.equals(frame.getString("channel.id"));
    }

    @Test
    void readyDescribesTheMembersWorld() {
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            JsonPath ready = gateway.ready();
            assertEquals(1, ready.getInt("seq"));
            assertEquals(member.id(), ready.getString("account.id"));
            assertFalse(ready.getBoolean("permissions.owner"));
            assertTrue(ready.getList("permissions.permissions").contains("SEND_MESSAGES"));
            assertEquals("Snatter", ready.getString("server.name"));
            assertTrue(ready.getList("roles.name").containsAll(List.of("User", "Moderator", "Admin")));
            assertTrue(ready.getList("members.id").contains(member.id()));
            assertEquals("General", ready.getString("channels.find { it.id == '" + GENERAL_TEXT + "' }.name"));
        }
    }

    @Test
    void protocolViolationsCloseTheConnectionWithAReason() {
        String token = TestUsers.register().token();
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("not json");
            assertEquals(new Closed(4000, "invalid_frame"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("{\"token\":\"" + token + "\"}");
            assertEquals(new Closed(4000, "invalid_frame"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send(typing(GENERAL_TEXT));
            assertEquals(new Closed(4001, "not_identified"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.identified(token)) {
            gateway.send(typing("not-a-channel-id"));
            assertEquals(new Closed(4000, "invalid_frame"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify("snt_not-a-real-token");
            assertEquals(new Closed(4002, "authentication_failed"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.identified(token)) {
            gateway.identify(token);
            assertEquals(new Closed(4003, "already_identified"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            // The test profile gives connections one second to identify.
            assertEquals(new Closed(4500, "identify_timeout"), gateway.awaitClose());
        }
    }

    @Test
    void frameTypesFromNewerClientsAreIgnored() {
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("{\"type\":\"dance\",\"style\":\"waltz\"}");
            gateway.identify(member.token());
            assertEquals(member.id(), gateway.await("ready").getString("account.id"));
            // Frames are handled in order, so this answer shows the one before did not close the connection.
            gateway.send("{\"type\":\"dance\"}");
            gateway.identify(member.token());
            assertEquals(new Closed(4003, "already_identified"), gateway.awaitClose());
        }
    }

    @Test
    void clientsStateTheirProtocolVersion() {
        String token = TestUsers.register().token();
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.send("{\"type\":\"identify\",\"token\":\"" + token + "\"}");
            assertEquals(new Closed(4000, "invalid_frame"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify(token, "one");
            assertEquals(new Closed(4000, "invalid_frame"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify(token, "0.9");
            assertEquals(new Closed(4006, "client_outdated"), gateway.awaitClose());
        }
        // Newer clients get in; whether they can work with this server is theirs to decide.
        ProtocolVersion current = Protocol.CURRENT;
        for (String newer : List.of(current.major() + "." + (current.minor() + 1), (current.major() + 1) + ".0")) {
            try (GatewayTestClient gateway = GatewayTestClient.connect()) {
                gateway.identify(token, newer);
                assertEquals(current.toString(), gateway.await("ready").getString("server.protocol.version"));
            }
        }
    }

    @Test
    void membersAreOnlineWhileAnyConnectionIsOpen() {
        TestUsers.User watcher = TestUsers.register();
        TestUsers.User member = TestUsers.register();
        Predicate<JsonPath> memberOnline = presence(member.id(), "online");
        try (GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            assertFalse(watcherGateway.ready().getList("presences.accountId").contains(member.id()));
            assertTrue(watcherGateway.ready().getList("presences.accountId").contains(watcher.id()), "ready includes the caller");

            GatewayTestClient first = GatewayTestClient.identified(member.token());
            watcherGateway.await("presence_updated", memberOnline);
            assertTrue(first.ready().getList("presences.accountId").containsAll(List.of(member.id(), watcher.id())));
            first.assertNone("presence_updated", memberOnline);

            // A second connection changes nothing, and neither does closing one of two.
            try (GatewayTestClient second = GatewayTestClient.identified(member.token())) {
                first.close();
                awaitMarker(watcherGateway, watcher);
                watcherGateway.assertNone("presence_updated", f -> member.id().equals(f.getString("presence.accountId")));
            }
            watcherGateway.await("presence_updated", presence(member.id(), "offline"));
        }
    }

    @Test
    void typingReachesTheOthersWhoCanSeeTheChannel() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        TestUsers.assignRole(alice.id(), crew);
        String crewOnly = createChannel(Map.of("type", "text", "name", "crew", "requiredRoleIds", List.of(crew)));
        String other = createChannel(Map.of("type", "text", "name", "other"));
        String voice = createChannel(Map.of("type", "voice", "name", "voice only"));
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient aliceElsewhere = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            aliceGateway.send(typing(GENERAL_TEXT));
            JsonPath started = bobGateway.await("typing_started", f -> alice.id().equals(f.getString("accountId")));
            assertEquals(GENERAL_TEXT, started.getString("channelId"));

            // Too soon after the first, in a voice-only channel, or where bob cannot see: nothing is passed on.
            aliceGateway.send(typing(GENERAL_TEXT));
            aliceGateway.send(typing(voice));
            aliceGateway.send(typing(crewOnly));
            aliceGateway.send(typing(UUID.randomUUID().toString()));
            // A connection's frames are handled in order, so once this one is through, so are those above.
            aliceGateway.send(typing(other));
            bobGateway.await("typing_started", f -> other.equals(f.getString("channelId")));
            bobGateway.assertNone("typing_started", f -> alice.id().equals(f.getString("accountId")));

            // Alice's own connections never hear about her typing.
            bobGateway.send(typing(other));
            aliceElsewhere.await("typing_started", f -> bob.id().equals(f.getString("accountId")));
            aliceElsewhere.assertNone("typing_started", f -> alice.id().equals(f.getString("accountId")));
        } finally {
            deleteChannel(crewOnly);
            deleteChannel(other);
            deleteChannel(voice);
            TestUsers.deleteRole(crew);
        }
    }

    /** Frames arrive in order, so anything sent before this marker has arrived once it has. */
    private static void awaitMarker(GatewayTestClient gateway, TestUsers.User sender) {
        String marker = send(sender.token(), GENERAL_TEXT, Map.of("content", "marker")).then().statusCode(201).extract().path("id");
        gateway.await("message_created", f -> marker.equals(f.getString("message.id")));
    }

    private static Predicate<JsonPath> presence(String accountId, String status) {
        return f -> accountId.equals(f.getString("presence.accountId")) && status.equals(f.getString("presence.status"));
    }

    private static String typing(String channelId) {
        return "{\"type\":\"typing\",\"channelId\":\"" + channelId + "\"}";
    }

    @Test
    void messagesArriveLiveAndOnlyTheSendingSessionGetsTheNonce() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            String id = send(alice.token(), GENERAL_TEXT, Map.of("content", "live!", "nonce", "pending-1"))
                .then().statusCode(201).extract().path("id");

            JsonPath own = aliceGateway.await("message_created", f -> id.equals(f.getString("message.id")));
            assertEquals("user", own.getString("message.kind"));
            assertEquals("live!", own.getString("message.content"));
            assertEquals("pending-1", own.getString("message.nonce"));
            JsonPath other = bobGateway.await("message_created", f -> id.equals(f.getString("message.id")));
            assertNull(other.getString("message.nonce"));

            as(alice.token()).body(Map.of("content", "edited")).patch("/api/v1/channels/" + GENERAL_TEXT + "/messages/" + id)
                .then().statusCode(200);
            assertEquals("edited", bobGateway.await("message_updated", f -> id.equals(f.getString("message.id"))).getString("message.content"));

            as(alice.token()).delete("/api/v1/channels/" + GENERAL_TEXT + "/messages/" + id).then().statusCode(204);
            JsonPath deleted = bobGateway.await("message_updated",
                f -> id.equals(f.getString("message.id")) && "deleted".equals(f.getString("message.kind")));
            assertEquals(false, deleted.getBoolean("message.removedByModerator"));
            assertNull(deleted.getString("message.content"));

            String spam = as(alice.token()).body(Map.of("content", "spam")).post("/api/v1/channels/" + GENERAL_TEXT + "/messages")
                .then().statusCode(201).extract().path("id");
            String since = as(alice.token()).get("/api/v1/channels/" + GENERAL_TEXT + "/messages/" + spam).path("createdAt");
            as(TestUsers.ownerToken()).queryParam("since", since).delete("/api/v1/accounts/" + alice.id() + "/messages")
                .then().statusCode(200);
            JsonPath purged = bobGateway.await("messages_purged", f -> spam.equals(f.getString("fromMessageId")));
            assertEquals(GENERAL_TEXT, purged.getString("channelId"));
            assertEquals(alice.id(), purged.getString("authorId"));
            assertEquals(spam, purged.getString("toMessageId"));
            assertEquals(true, purged.getBoolean("removedByModerator"));
        }
    }

    @Test
    void privateChannelsAndTheirMessagesStayHidden() {
        String owner = TestUsers.ownerToken();
        TestUsers.User insider = TestUsers.register();
        TestUsers.User outsider = TestUsers.register();
        String insiders = TestUsers.createRole("Insiders " + UUID.randomUUID());
        TestUsers.assignRole(insider.id(), insiders);
        try (GatewayTestClient insiderGateway = GatewayTestClient.identified(insider.token());
             GatewayTestClient outsiderGateway = GatewayTestClient.identified(outsider.token())) {
            String secret = createChannel(Map.of("type", "text", "name", "secret", "requiredRoleIds", List.of(insiders)));
            try {
                insiderGateway.await("channel_created", channel(secret));
                String whisper = send(owner, secret, Map.of("content", "psst")).then().statusCode(201).extract().path("id");
                insiderGateway.await("message_created", f -> whisper.equals(f.getString("message.id")));

                // Frames reach a connection in order, so once the marker arrives the secret would have too.
                String marker = send(owner, GENERAL_TEXT, Map.of("content", "marker")).then().statusCode(201).extract().path("id");
                outsiderGateway.await("message_created", f -> marker.equals(f.getString("message.id")));
                outsiderGateway.assertNone("channel_created", channel(secret));
                outsiderGateway.assertNone("message_created", f -> secret.equals(f.getString("message.channelId")));
            } finally {
                deleteChannel(secret);
            }
            insiderGateway.await("channel_deleted", f -> secret.equals(f.getString("channelId")));
        } finally {
            TestUsers.deleteRole(insiders);
        }
    }

    @Test
    void readMarkersStartAtWhatIsThereAndFollowTheMember() {
        String owner = TestUsers.ownerToken();
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String reading = createChannel(Map.of("type", "text", "name", "reading"));
        String voice = createChannel(Map.of("type", "voice", "name", "voice only"));
        String secret = createChannel(Map.of("type", "text", "name", "secret", "requiredRoleIds", List.of(crew)));
        try {
            String before = send(owner, reading, Map.of("content", "before alice looked")).then().statusCode(201).extract().path("id");
            String hiddenBefore = send(owner, secret, Map.of("content", "before alice could see")).then().statusCode(201).extract().path("id");
            try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
                 GatewayTestClient aliceElsewhere = GatewayTestClient.identified(alice.token());
                 GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
                // What was there before she first saw the channel counts as read; voice channels have no state.
                String state = "readStates.find { it.channelId == '" + reading + "' }";
                assertEquals(before, aliceGateway.ready().getString(state + ".lastReadMessageId"));
                assertEquals(before, aliceGateway.ready().getString(state + ".lastMessageId"));
                assertFalse(aliceGateway.ready().getList("readStates.channelId").contains(voice));
                assertFalse(aliceGateway.ready().getList("readStates.channelId").contains(secret));

                String unread = send(bob.token(), reading, Map.of("content", "for alice")).then().statusCode(201).extract().path("id");
                as(alice.token()).body(Map.of("lastReadMessageId", unread)).put("/api/v1/channels/" + reading + "/read-state")
                    .then().statusCode(200);
                for (GatewayTestClient gateway : List.of(aliceGateway, aliceElsewhere)) {
                    JsonPath moved = gateway.await("read_state_updated", readState(reading));
                    assertEquals(unread, moved.getString("readState.lastReadMessageId"));
                }

                // Sending reads up to the message sent; marking an older message read changes nothing.
                String own = send(alice.token(), reading, Map.of("content", "mine")).then().statusCode(201).extract().path("id");
                for (GatewayTestClient gateway : List.of(aliceGateway, aliceElsewhere)) {
                    assertEquals(own, gateway.await("read_state_updated", readState(reading)).getString("readState.lastReadMessageId"));
                }
                as(alice.token()).body(Map.of("lastReadMessageId", before)).put("/api/v1/channels/" + reading + "/read-state")
                    .then().statusCode(200);

                // A channel that becomes visible starts out read.
                TestUsers.assignRole(alice.id(), crew);
                aliceGateway.await("channel_created", channel(secret));
                JsonPath revealed = aliceGateway.await("read_state_updated", readState(secret));
                assertEquals(hiddenBefore, revealed.getString("readState.lastReadMessageId"));

                // Nobody else hears about Alice's reading.
                awaitMarker(aliceGateway, bob);
                aliceGateway.assertNone("read_state_updated", readState(reading));
                awaitMarker(bobGateway, bob);
                bobGateway.assertNone("read_state_updated", f -> own.equals(f.getString("readState.lastReadMessageId")));
            }
        } finally {
            deleteChannel(reading);
            deleteChannel(voice);
            deleteChannel(secret);
            TestUsers.deleteRole(crew);
        }
    }

    private static Predicate<JsonPath> readState(String channelId) {
        return frame -> channelId.equals(frame.getString("readState.channelId"));
    }

    @Test
    void roleAndChannelAccessChangesArriveAsDifferences() {
        String owner = TestUsers.ownerToken();
        TestUsers.User member = TestUsers.register();
        String seers = TestUsers.createRole("Seers " + UUID.randomUUID(), "TIMEOUT_MEMBERS");
        String hidden = createChannel(Map.of("type", "text", "name", "for seers", "requiredRoleIds", List.of(seers)));
        boolean roleDeleted = false;
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            assertFalse(gateway.ready().getList("channels.id").contains(hidden));
            assertTrue(gateway.ready().getList("roles.id").contains(seers));

            TestUsers.assignRole(member.id(), seers);
            gateway.await("member_updated", f -> member.id().equals(f.getString("member.id")) && f.getList("member.roleIds").contains(seers));
            assertTrue(gateway.await("permissions_changed").getList("permissions.permissions").contains("TIMEOUT_MEMBERS"));
            gateway.await("channel_created", channel(hidden));

            TestUsers.patchRole(owner, seers, Map.of("name", "Renamed seers")).then().statusCode(200);
            assertEquals("Renamed seers", gateway.await("role_updated", f -> seers.equals(f.getString("role.id"))).getString("role.name"));

            given().header("Authorization", "Bearer " + owner).delete("/api/v1/accounts/" + member.id() + "/roles/" + seers)
                .then().statusCode(204);
            gateway.await("channel_deleted", f -> hidden.equals(f.getString("channelId")));
            assertFalse(gateway.await("permissions_changed").getList("permissions.permissions").contains("TIMEOUT_MEMBERS"));

            // Making the channel public reveals it.
            as(owner).body(Map.of("requiredRoleIds", List.of())).patch("/api/v1/channels/" + hidden).then().statusCode(200);
            assertEquals(List.of(), gateway.await("channel_created", channel(hidden)).getList("channel.requiredRoleIds"));

            TestUsers.deleteRole(seers);
            roleDeleted = true;
            gateway.await("role_deleted", f -> seers.equals(f.getString("roleId")));
        } finally {
            deleteChannel(hidden);
            if (!roleDeleted) {
                TestUsers.deleteRole(seers);
            }
        }
    }

    @Test
    void channelChangesArriveWithTheirNotices() {
        String owner = TestUsers.ownerToken();
        TestUsers.User member = TestUsers.register();
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            String channel = createChannel(Map.of("type", "voice_text", "name", "lounge"));
            try {
                gateway.await("channel_created", channel(channel));
                gateway.await("message_created", f -> channel.equals(f.getString("message.channelId"))
                    && "channel_created".equals(f.getString("message.notice.type")));

                as(owner).body(Map.of("name", "big lounge", "bitrate", 96000)).patch("/api/v1/channels/" + channel).then().statusCode(200);
                JsonPath updated = gateway.await("channel_updated", f -> channel.equals(f.getString("channel.id"))
                    && "big lounge".equals(f.getString("channel.name")));
                assertEquals(96000, updated.getInt("channel.bitrate"));
                JsonPath notice = gateway.await("message_created", f -> "channel_renamed".equals(f.getString("message.notice.type")));
                assertEquals("big lounge", notice.getString("message.notice.to"));

                // Moving a channel to the top shifts the others; each moved channel is updated.
                as(owner).body(Map.of("position", 0)).patch("/api/v1/channels/" + channel).then().statusCode(200);
                gateway.await("channel_updated", f -> channel.equals(f.getString("channel.id"))
                    && f.getInt("channel.position") == 0);
                gateway.await("channel_updated", f -> GENERAL_TEXT.equals(f.getString("channel.id"))
                    && f.getInt("channel.position") == 1);
            } finally {
                deleteChannel(channel);
            }
            gateway.await("channel_deleted", f -> channel.equals(f.getString("channelId")));
            gateway.await("channel_updated", f -> GENERAL_TEXT.equals(f.getString("channel.id"))
                && f.getInt("channel.position") == 0);
        }
    }

    @Test
    void membersJoiningAndServerChangesAreBroadcast() {
        TestUsers.User member = TestUsers.register();
        String originalName = as(TestUsers.ownerToken()).get("/api/v1/server-settings").then().extract().path("name");
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            TestUsers.User joined = TestUsers.register();
            gateway.await("member_joined", f -> joined.id().equals(f.getString("member.id")));
            gateway.await("message_created", f -> "member_joined".equals(f.getString("message.notice.type"))
                && joined.id().equals(f.getString("message.authorId")));

            TestUsers.patchSettings(Map.of("name", "Live community")).then().statusCode(200);
            assertEquals("Live community", gateway.await("server_updated").getString("server.community.name"));
        } finally {
            TestUsers.patchSettings(Map.of("name", originalName)).then().statusCode(200);
        }
    }

    @Test
    void loggingOutClosesTheSessionsConnectionsOnly() {
        TestUsers.User member = TestUsers.register();
        String otherSession = given().contentType(ContentType.JSON)
            .body(Map.of("username", member.username(), "password", TestUsers.DEFAULT_PASSWORD))
            .post("/api/v1/auth/login").then().statusCode(200).extract().path("token");
        try (GatewayTestClient first = GatewayTestClient.identified(member.token());
             GatewayTestClient second = GatewayTestClient.identified(member.token());
             GatewayTestClient elsewhere = GatewayTestClient.identified(otherSession)) {
            as(member.token()).post("/api/v1/auth/logout").then().statusCode(204);
            assertEquals(new Closed(4004, "session_ended"), first.awaitClose());
            assertEquals(new Closed(4004, "session_ended"), second.awaitClose());

            String id = send(otherSession, GENERAL_TEXT, Map.of("content", "still here")).then().statusCode(201).extract().path("id");
            elsewhere.await("message_created", f -> id.equals(f.getString("message.id")));
        }
    }
}
