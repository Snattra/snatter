package app.snatter.server.gateway;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return frame -> id.equals(frame.getString("channel.channel.id"));
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
            assertTrue(ready.getBoolean("roles[-1].isDefault"));
            assertTrue(ready.getList("members.id").contains(member.id()));
            assertEquals("general", ready.getString("channels.find { it.channel.id == '" + GENERAL_TEXT + "' }.channel.name"));
            assertTrue(ready.getList("channels.find { it.channel.id == '" + GENERAL_TEXT + "' }.permissions").contains("SEND_MESSAGES"));
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
            gateway.send("{\"type\":\"dance\"}");
            assertEquals(new Closed(4000, "invalid_frame"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            gateway.identify("snt_not-a-real-token");
            assertEquals(new Closed(4003, "authentication_failed"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.identified(token)) {
            gateway.identify(token);
            assertEquals(new Closed(4004, "already_identified"), gateway.awaitClose());
        }
        try (GatewayTestClient gateway = GatewayTestClient.connect()) {
            // The test profile gives connections one second to identify.
            assertEquals(new Closed(4002, "identify_timeout"), gateway.awaitClose());
        }
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
            JsonPath deleted = bobGateway.await("message_deleted", f -> id.equals(f.getString("messageId")));
            assertEquals(GENERAL_TEXT, deleted.getString("channelId"));
        }
    }

    @Test
    void privateChannelsAndTheirMessagesStayHidden() {
        String owner = TestUsers.ownerToken();
        TestUsers.User insider = TestUsers.register();
        TestUsers.User outsider = TestUsers.register();
        try (GatewayTestClient insiderGateway = GatewayTestClient.identified(insider.token());
             GatewayTestClient outsiderGateway = GatewayTestClient.identified(outsider.token())) {
            String secret = createChannel(Map.of("type", "text", "name", "secret", "overwrites", List.of(
                Map.of("roleId", TestUsers.defaultRoleId(), "allow", List.of(), "deny", List.of("VIEW_CHANNELS")),
                Map.of("accountId", insider.id(), "allow", List.of("VIEW_CHANNELS"), "deny", List.of()))));
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
        }
    }

    @Test
    void roleAndOverwriteChangesArriveAsDifferences() {
        String owner = TestUsers.ownerToken();
        TestUsers.User member = TestUsers.register();
        String seers = TestUsers.createRole("Seers " + UUID.randomUUID(), "KICK_MEMBERS");
        String hidden = createChannel(Map.of("type", "text", "name", "for seers", "overwrites", List.of(
            Map.of("roleId", TestUsers.defaultRoleId(), "allow", List.of(), "deny", List.of("VIEW_CHANNELS")),
            Map.of("roleId", seers, "allow", List.of("VIEW_CHANNELS"), "deny", List.of()))));
        boolean roleDeleted = false;
        try (GatewayTestClient gateway = GatewayTestClient.identified(member.token())) {
            assertFalse(gateway.ready().getList("channels.channel.id").contains(hidden));
            assertTrue(gateway.ready().getList("roles.id").contains(seers));

            TestUsers.assignRole(member.id(), seers);
            gateway.await("member_updated", f -> member.id().equals(f.getString("member.id")) && f.getList("member.roleIds").contains(seers));
            assertTrue(gateway.await("permissions_changed").getList("permissions.permissions").contains("KICK_MEMBERS"));
            gateway.await("channel_created", channel(hidden));

            // Denying the member in the channel updates their permissions there.
            as(owner).body(Map.of("allow", List.of(), "deny", List.of("SEND_MESSAGES")))
                .put("/api/v1/channels/" + hidden + "/overwrites/accounts/" + member.id()).then().statusCode(200);
            JsonPath updated = gateway.await("channel_updated", channel(hidden));
            assertFalse(updated.getList("channel.permissions").contains("SEND_MESSAGES"));

            TestUsers.patchRole(owner, seers, Map.of("name", "Renamed seers")).then().statusCode(200);
            assertEquals("Renamed seers", gateway.await("role_updated", f -> seers.equals(f.getString("role.id"))).getString("role.name"));

            given().header("Authorization", "Bearer " + owner).delete("/api/v1/accounts/" + member.id() + "/roles/" + seers)
                .then().statusCode(204);
            gateway.await("channel_deleted", f -> hidden.equals(f.getString("channelId")));
            assertFalse(gateway.await("permissions_changed").getList("permissions.permissions").contains("KICK_MEMBERS"));

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
                JsonPath updated = gateway.await("channel_updated", f -> channel.equals(f.getString("channel.channel.id"))
                    && "big lounge".equals(f.getString("channel.channel.name")));
                assertEquals(96000, updated.getInt("channel.channel.bitrate"));
                JsonPath notice = gateway.await("message_created", f -> "channel_renamed".equals(f.getString("message.notice.type")));
                assertEquals("big lounge", notice.getString("message.notice.to"));

                // Moving a channel to the top shifts the others; each moved channel is updated.
                as(owner).body(Map.of("position", 0)).patch("/api/v1/channels/" + channel).then().statusCode(200);
                gateway.await("channel_updated", f -> channel.equals(f.getString("channel.channel.id"))
                    && f.getInt("channel.channel.position") == 0);
                gateway.await("channel_updated", f -> GENERAL_TEXT.equals(f.getString("channel.channel.id"))
                    && f.getInt("channel.channel.position") == 1);
            } finally {
                deleteChannel(channel);
            }
            gateway.await("channel_deleted", f -> channel.equals(f.getString("channelId")));
            gateway.await("channel_updated", f -> GENERAL_TEXT.equals(f.getString("channel.channel.id"))
                && f.getInt("channel.channel.position") == 0);
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
            assertEquals(new Closed(4005, "session_ended"), first.awaitClose());
            assertEquals(new Closed(4005, "session_ended"), second.awaitClose());

            String id = send(otherSession, GENERAL_TEXT, Map.of("content", "still here")).then().statusCode(201).extract().path("id");
            elsewhere.await("message_created", f -> id.equals(f.getString("message.id")));
        }
    }
}
