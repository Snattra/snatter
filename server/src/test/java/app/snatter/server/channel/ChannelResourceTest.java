package app.snatter.server.channel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChannelResourceTest {

    private static final String GENERAL_TEXT = "00000000-0000-7000-8000-000000000101";
    private static final String GENERAL_VOICE = "00000000-0000-7000-8000-000000000102";

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private static Response create(String token, Map<String, Object> body) {
        return as(token).body(body).post("/api/v1/channels");
    }

    private static String createChannel(String type, String name) {
        return create(TestUsers.ownerToken(), Map.of("type", type, "name", name))
            .then().statusCode(201).extract().path("id");
    }

    private static void deleteChannel(String id) {
        as(TestUsers.ownerToken()).delete("/api/v1/channels/" + id).then().statusCode(204);
    }

    private static Response patch(String token, String channel, Map<String, Object> body) {
        return as(token).body(body).patch("/api/v1/channels/" + channel);
    }

    private static Response roleOverwrite(String token, String channel, String role, List<String> allow, List<String> deny) {
        return as(token).body(Map.of("allow", allow, "deny", deny))
            .put("/api/v1/channels/" + channel + "/overwrites/roles/" + role);
    }

    private static Response accountOverwrite(String token, String channel, String account, List<String> allow, List<String> deny) {
        return as(token).body(Map.of("allow", allow, "deny", deny))
            .put("/api/v1/channels/" + channel + "/overwrites/accounts/" + account);
    }

    private static Response permissionsIn(String token, String channel) {
        return as(token).get("/api/v1/channels/" + channel + "/permissions");
    }

    private static List<String> visibleIds(String token) {
        return as(token).get("/api/v1/channels").then().statusCode(200).extract().path("id");
    }

    @Test
    void freshServerHasAGeneralTextAndVoiceChannel() {
        TestUsers.User member = TestUsers.register();
        as(member.token()).get("/api/v1/channels").then().statusCode(200)
            .body("find { it.id == '" + GENERAL_TEXT + "' }.type", equalTo("text"))
            .body("find { it.id == '" + GENERAL_TEXT + "' }.name", equalTo("general"))
            .body("find { it.id == '" + GENERAL_TEXT + "' }.bitrate", nullValue())
            .body("find { it.id == '" + GENERAL_VOICE + "' }.type", equalTo("voice"))
            .body("find { it.id == '" + GENERAL_VOICE + "' }.bitrate", equalTo(64000))
            .body("find { it.id == '" + GENERAL_VOICE + "' }.userLimit", equalTo(0));
        given().get("/api/v1/channels").then().statusCode(401);
    }

    @Test
    void createsChannelsLastWithVoiceDefaults() {
        String owner = TestUsers.ownerToken();
        int defaultBitrate = given().get("/api/v1/server-info").then().extract().path("voice.defaultBitrate");
        String voice = create(owner, Map.of("type", "voice_text", "name", "  Lounge  ", "topic", "hang out"))
            .then().statusCode(201)
            .body("name", equalTo("Lounge"))
            .body("topic", equalTo("hang out"))
            .body("bitrate", equalTo(defaultBitrate))
            .body("userLimit", equalTo(0))
            .body("overwrites", equalTo(List.of()))
            .extract().path("id");
        String text = null;
        try {
            List<String> ids = visibleIds(owner);
            assertEquals(voice, ids.get(ids.size() - 1), "new channels go last");
            text = create(owner, Map.of("type", "text", "name", "notes"))
                .then().statusCode(201).body("bitrate", nullValue()).body("userLimit", nullValue())
                .body("position", equalTo(ids.size())).extract().path("id");
            String hifi = create(owner, Map.of("type", "voice", "name", "hifi", "bitrate", 128000, "userLimit", 5))
                .then().statusCode(201).body("bitrate", equalTo(128000)).body("userLimit", equalTo(5))
                .extract().path("id");
            deleteChannel(hifi);

            create(owner, Map.of("type", "text", "name", "x", "bitrate", 64000))
                .then().statusCode(400).body("error", equalTo("not_a_voice_channel"));
            create(owner, Map.of("type", "voice", "name", "x", "bitrate", 300000))
                .then().statusCode(400).body("error", equalTo("bitrate_too_high"));
            create(owner, Map.of("type", "voice", "name", "x", "bitrate", 7000))
                .then().statusCode(400).body("error", equalTo("validation_failed"));
            create(owner, Map.of("type", "text", "name", "   "))
                .then().statusCode(400).body("error", equalTo("validation_failed"));
            create(owner, Map.of("type", "stage", "name", "x"))
                .then().statusCode(400);
            create(TestUsers.register().token(), Map.of("type", "text", "name", "nope"))
                .then().statusCode(403).body("error", equalTo("forbidden"));
        } finally {
            deleteChannel(voice);
            if (text != null) {
                deleteChannel(text);
            }
        }
    }

    @Test
    void updatesMovesAndDeletesKeepingPositionsContiguous() {
        String owner = TestUsers.ownerToken();
        String a = createChannel("text", "a " + UUID.randomUUID());
        String b = createChannel("voice", "b");
        String c = createChannel("text", "c");
        try {
            patch(owner, a, Map.of("name", "renamed", "topic", "about a")).then().statusCode(200)
                .body("name", equalTo("renamed")).body("topic", equalTo("about a"));
            patch(owner, a, Map.of("topic", "")).then().statusCode(200)
                .body("name", equalTo("renamed")).body("topic", nullValue());
            patch(owner, b, Map.of("bitrate", 96000, "userLimit", 10)).then().statusCode(200)
                .body("bitrate", equalTo(96000)).body("userLimit", equalTo(10));
            patch(owner, a, Map.of("bitrate", 96000)).then().statusCode(400).body("error", equalTo("not_a_voice_channel"));
            patch(owner, b, Map.of("bitrate", 300000)).then().statusCode(400).body("error", equalTo("bitrate_too_high"));

            int aPosition = as(owner).get("/api/v1/channels/" + a).then().extract().path("position");
            patch(owner, c, Map.of("position", aPosition)).then().statusCode(200).body("position", equalTo(aPosition));
            List<String> ids = visibleIds(owner);
            assertEquals(List.of(c, a, b), ids.subList(aPosition, aPosition + 3));
            assertContiguous(owner);

            patch(owner, c, Map.of("position", 10_000)).then().statusCode(200).body("position", equalTo(ids.size() - 1));
            assertContiguous(owner);

            deleteChannel(b);
            b = null;
            as(owner).get("/api/v1/channels/" + UUID.randomUUID()).then().statusCode(404).body("error", equalTo("channel_not_found"));
            assertContiguous(owner);
        } finally {
            deleteChannel(a);
            deleteChannel(c);
            if (b != null) {
                deleteChannel(b);
            }
        }
    }

    private static void assertContiguous(String token) {
        List<Integer> positions = as(token).get("/api/v1/channels").then().extract().path("position");
        assertEquals(IntStream.range(0, positions.size()).boxed().toList(), positions);
    }

    @Test
    void privateChannelsAreHiddenFromEveryoneElse() {
        String owner = TestUsers.ownerToken();
        String channel = createChannel("text", "crew only");
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String admins = TestUsers.createRole("Admins " + UUID.randomUUID(), "ADMINISTRATOR");
        try {
            roleOverwrite(owner, channel, TestUsers.defaultRoleId(), List.of(), List.of("VIEW_CHANNELS")).then().statusCode(200);
            roleOverwrite(owner, channel, crew, List.of("VIEW_CHANNELS"), List.of()).then().statusCode(200)
                .body("overwrites.size()", equalTo(2))
                .body("overwrites.find { it.roleId == '" + crew + "' }.allow", contains("VIEW_CHANNELS"))
                .body("overwrites.find { it.roleId == '" + crew + "' }.accountId", nullValue());

            TestUsers.User outsider = TestUsers.register();
            assertEquals(false, visibleIds(outsider.token()).contains(channel));
            as(outsider.token()).get("/api/v1/channels/" + channel).then().statusCode(404).body("error", equalTo("channel_not_found"));
            permissionsIn(outsider.token(), channel).then().statusCode(404);
            patch(outsider.token(), channel, Map.of("name", "x")).then().statusCode(404);

            TestUsers.User member = TestUsers.register();
            TestUsers.assignRole(member.id(), crew);
            assertEquals(true, visibleIds(member.token()).contains(channel));
            permissionsIn(member.token(), channel).then().statusCode(200)
                .body("permissions", hasItems("VIEW_CHANNELS", "SEND_MESSAGES"));

            TestUsers.User admin = TestUsers.register();
            TestUsers.assignRole(admin.id(), admins);
            assertEquals(true, visibleIds(admin.token()).contains(channel));
            assertEquals(true, visibleIds(owner).contains(channel));

            as(owner).delete("/api/v1/channels/" + channel + "/overwrites/roles/" + TestUsers.defaultRoleId()).then().statusCode(204);
            as(owner).delete("/api/v1/channels/" + channel + "/overwrites/roles/" + TestUsers.defaultRoleId()).then().statusCode(204);
            assertEquals(true, visibleIds(outsider.token()).contains(channel));
        } finally {
            deleteChannel(channel);
            TestUsers.deleteRole(crew);
            TestUsers.deleteRole(admins);
        }
    }

    @Test
    void channelModeratorsManageOnlyTheirChannel() {
        String owner = TestUsers.ownerToken();
        String theirs = createChannel("voice_text", "modded");
        String other = createChannel("voice_text", "other");
        TestUsers.User mod = TestUsers.register();
        try {
            accountOverwrite(owner, theirs, mod.id(), List.of("MANAGE_CHANNELS", "MANAGE_MESSAGES", "MUTE_MEMBERS", "MOVE_MEMBERS"), List.of())
                .then().statusCode(200)
                .body("overwrites[0].accountId", equalTo(mod.id()))
                .body("overwrites[0].roleId", nullValue());

            permissionsIn(mod.token(), theirs).then().statusCode(200).body("owner", equalTo(false))
                .body("permissions", hasItems("MANAGE_CHANNELS", "MANAGE_MESSAGES", "MUTE_MEMBERS", "MOVE_MEMBERS"));
            permissionsIn(mod.token(), other).then().statusCode(200)
                .body("permissions", not(hasItem("MANAGE_MESSAGES")));
            as(mod.token()).get("/api/v1/accounts/me/permissions").then()
                .body("permissions", not(hasItem("MANAGE_MESSAGES")));

            patch(mod.token(), theirs, Map.of("topic", "moderated")).then().statusCode(200).body("topic", equalTo("moderated"));
            patch(mod.token(), other, Map.of("topic", "nope")).then().statusCode(403).body("error", equalTo("forbidden"));
            as(mod.token()).delete("/api/v1/channels/" + other).then().statusCode(403);
            create(mod.token(), Map.of("type", "text", "name", "nope")).then().statusCode(403);
            // Moderating a channel does not include handing out its overwrites.
            accountOverwrite(mod.token(), theirs, TestUsers.register().id(), List.of("MANAGE_MESSAGES"), List.of())
                .then().statusCode(403).body("error", equalTo("forbidden"));
        } finally {
            deleteChannel(theirs);
            deleteChannel(other);
        }
    }

    @Test
    void overwritesFollowTheRoleRules() {
        String owner = TestUsers.ownerToken();
        String channel = createChannel("voice_text", "rules");
        // Created first so it ends up above the manager's role.
        String senior = TestUsers.createRole("Senior " + UUID.randomUUID());
        String manager = TestUsers.createRole("Manager " + UUID.randomUUID(), "MANAGE_ROLES", "MANAGE_MESSAGES");
        TestUsers.User mgr = TestUsers.register();
        TestUsers.assignRole(mgr.id(), manager);
        TestUsers.User member = TestUsers.register();
        try {
            roleOverwrite(owner, channel, manager, List.of("KICK_MEMBERS"), List.of())
                .then().statusCode(400).body("error", equalTo("invalid_overwrite"));
            roleOverwrite(owner, channel, manager, List.of("SPEAK"), List.of("SPEAK"))
                .then().statusCode(400).body("error", equalTo("invalid_overwrite"));
            roleOverwrite(owner, channel, UUID.randomUUID().toString(), List.of("SPEAK"), List.of())
                .then().statusCode(404).body("error", equalTo("role_not_found"));
            accountOverwrite(owner, channel, UUID.randomUUID().toString(), List.of("SPEAK"), List.of())
                .then().statusCode(404).body("error", equalTo("account_not_found"));
            roleOverwrite(owner, UUID.randomUUID().toString(), manager, List.of("SPEAK"), List.of())
                .then().statusCode(404).body("error", equalTo("channel_not_found"));

            // Below the manager: the default role and plain members.
            roleOverwrite(mgr.token(), channel, TestUsers.defaultRoleId(), List.of(), List.of("SEND_MESSAGES")).then().statusCode(200);
            accountOverwrite(mgr.token(), channel, member.id(), List.of("MANAGE_MESSAGES"), List.of()).then().statusCode(200);
            permissionsIn(member.token(), channel).then()
                .body("permissions", hasItem("MANAGE_MESSAGES"))
                .body("permissions", not(hasItem("SEND_MESSAGES")));
            // The manager is one of everyone too, so no longer holds SEND_MESSAGES here to hand out.
            accountOverwrite(mgr.token(), channel, member.id(), List.of("MANAGE_MESSAGES", "SEND_MESSAGES"), List.of())
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            // At or above the manager.
            roleOverwrite(mgr.token(), channel, senior, List.of("SPEAK"), List.of())
                .then().statusCode(403).body("error", equalTo("role_hierarchy"));
            roleOverwrite(mgr.token(), channel, manager, List.of("SPEAK"), List.of())
                .then().statusCode(403).body("error", equalTo("role_hierarchy"));
            TestUsers.User seniorMember = TestUsers.register();
            TestUsers.assignRole(seniorMember.id(), senior);
            accountOverwrite(mgr.token(), channel, seniorMember.id(), List.of("SPEAK"), List.of())
                .then().statusCode(403).body("error", equalTo("role_hierarchy"));
            // Only permissions the manager holds in the channel.
            accountOverwrite(mgr.token(), channel, member.id(), List.of("MUTE_MEMBERS"), List.of())
                .then().statusCode(403).body("error", equalTo("permission_escalation"));

            // The owner adds a bit the manager lacks; the manager may still change the rest but not that bit.
            accountOverwrite(owner, channel, member.id(), List.of("MANAGE_MESSAGES", "MUTE_MEMBERS"), List.of()).then().statusCode(200);
            accountOverwrite(mgr.token(), channel, member.id(), List.of("MUTE_MEMBERS"), List.of("MANAGE_MESSAGES"))
                .then().statusCode(200)
                .body("overwrites.find { it.accountId == '" + member.id() + "' }.allow", contains("MUTE_MEMBERS"))
                .body("overwrites.find { it.accountId == '" + member.id() + "' }.deny", contains("MANAGE_MESSAGES"));
            as(mgr.token()).delete("/api/v1/channels/" + channel + "/overwrites/accounts/" + member.id())
                .then().statusCode(403).body("error", equalTo("permission_escalation"));
            as(owner).delete("/api/v1/channels/" + channel + "/overwrites/accounts/" + member.id()).then().statusCode(204);

            // Deleting a role removes its overwrites.
            roleOverwrite(owner, channel, senior, List.of("STREAM"), List.of()).then().statusCode(200);
            TestUsers.deleteRole(senior);
            senior = null;
            as(owner).get("/api/v1/channels/" + channel).then().statusCode(200)
                .body("overwrites.roleId", containsInAnyOrder(TestUsers.defaultRoleId()));
        } finally {
            deleteChannel(channel);
            TestUsers.deleteRole(manager);
            if (senior != null) {
                TestUsers.deleteRole(senior);
            }
        }
    }

    @Test
    void administratorsHoldEverythingButServerSettings() {
        TestUsers.User admin = TestUsers.register();
        String adminRole = given().header("Authorization", "Bearer " + TestUsers.ownerToken()).get("/api/v1/roles")
            .then().statusCode(200)
            .body("find { it.name == 'Admin' }.permissions", contains("ADMINISTRATOR"))
            .extract().path("find { it.name == 'Admin' }.id");
        TestUsers.assignRole(admin.id(), adminRole);
        String channel = null;
        String lower = null;
        try {
            as(admin.token()).get("/api/v1/accounts/me/permissions").then().statusCode(200)
                .body("owner", equalTo(false))
                .body("permissions", hasItems("ADMINISTRATOR", "MANAGE_ROLES", "MANAGE_CHANNELS", "KICK_MEMBERS", "BAN_MEMBERS"))
                .body("permissions", not(hasItem("MANAGE_SERVER")));
            as(admin.token()).get("/api/v1/server-settings").then().statusCode(403);
            Map<String, Object> rename = new HashMap<>();
            rename.put("name", "taken over");
            as(admin.token()).body(rename).patch("/api/v1/server-settings").then().statusCode(403);

            channel = create(admin.token(), Map.of("type", "text", "name", "admin made"))
                .then().statusCode(201).extract().path("id");
            accountOverwrite(admin.token(), channel, admin.id(), List.of(), List.of("VIEW_CHANNELS")).then().statusCode(200);
            assertEquals(true, visibleIds(admin.token()).contains(channel), "administrators ignore overwrites");

            // Admins may create further admin-level roles, but not hand out server settings.
            lower = as(admin.token()).body(Map.of("name", "Deputy", "permissions", List.of("ADMINISTRATOR")))
                .post("/api/v1/roles").then().statusCode(201).extract().path("id");
            as(admin.token()).body(Map.of("name", "Settings", "permissions", List.of("MANAGE_SERVER")))
                .post("/api/v1/roles").then().statusCode(403).body("error", equalTo("permission_escalation"));
        } finally {
            if (channel != null) {
                deleteChannel(channel);
            }
            if (lower != null) {
                TestUsers.deleteRole(lower);
            }
            given().header("Authorization", "Bearer " + TestUsers.ownerToken())
                .delete("/api/v1/accounts/" + admin.id() + "/roles/" + adminRole).then().statusCode(204);
        }
    }
}
