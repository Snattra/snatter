package app.snatter.server.channel;

import static io.restassured.RestAssured.given;
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
            .body("requiredRoleIds", equalTo(List.of()))
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
    void privateChannelsAreVisibleOnlyToTheirRoles() {
        String owner = TestUsers.ownerToken();
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String guests = TestUsers.createRole("Guests " + UUID.randomUUID());
        String channel = create(owner, Map.of("type", "text", "name", "crew only", "requiredRoleIds", List.of(crew, guests)))
            .then().statusCode(201)
            .body("requiredRoleIds", containsInAnyOrder(crew, guests))
            .extract().path("id");
        try {
            TestUsers.User outsider = TestUsers.register();
            assertEquals(false, visibleIds(outsider.token()).contains(channel));
            as(outsider.token()).get("/api/v1/channels/" + channel).then().statusCode(404).body("error", equalTo("channel_not_found"));

            TestUsers.User crewMember = TestUsers.register();
            TestUsers.assignRole(crewMember.id(), crew);
            assertEquals(true, visibleIds(crewMember.token()).contains(channel));
            TestUsers.User guest = TestUsers.register();
            TestUsers.assignRole(guest.id(), guests);
            assertEquals(true, visibleIds(guest.token()).contains(channel), "any one of the required roles is enough");
            assertEquals(true, visibleIds(owner).contains(channel), "the owner sees every channel");

            // Administrators manage the channels they see, but do not see every channel.
            TestUsers.User admin = TestUsers.register();
            TestUsers.assignRole(admin.id(), TestUsers.ADMIN_ROLE);
            assertEquals(false, visibleIds(admin.token()).contains(channel));
            patch(admin.token(), channel, Map.of("name", "x")).then().statusCode(404);
            as(admin.token()).delete("/api/v1/channels/" + channel).then().statusCode(404);

            patch(owner, channel, Map.of("requiredRoleIds", List.of())).then().statusCode(200).body("requiredRoleIds", equalTo(List.of()));
            assertEquals(true, visibleIds(outsider.token()).contains(channel));
        } finally {
            deleteChannel(channel);
            TestUsers.deleteRole(crew);
            TestUsers.deleteRole(guests);
        }
    }

    @Test
    void requiredRolesMustExistAndIncludeOneOfTheCallers() {
        String owner = TestUsers.ownerToken();
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String builders = TestUsers.createRole("Builders " + UUID.randomUUID(), "MANAGE_CHANNELS");
        TestUsers.User builder = TestUsers.register();
        TestUsers.assignRole(builder.id(), builders);
        int before = visibleIds(owner).size();
        String channel = null;
        try {
            create(owner, Map.of("type", "text", "name", "x", "requiredRoleIds", List.of(UUID.randomUUID().toString())))
                .then().statusCode(400).body("error", equalTo("invalid_required_role"));
            // Nobody but the owner can lock themselves out.
            create(builder.token(), Map.of("type", "text", "name", "x", "requiredRoleIds", List.of(crew)))
                .then().statusCode(400).body("error", equalTo("required_role_not_held"));
            assertEquals(before, visibleIds(owner).size(), "rejected creations leave no channel behind");

            channel = create(builder.token(), Map.of("type", "text", "name", "builders", "requiredRoleIds", List.of(builders)))
                .then().statusCode(201).extract().path("id");
            patch(builder.token(), channel, Map.of("requiredRoleIds", List.of(crew)))
                .then().statusCode(400).body("error", equalTo("required_role_not_held"));
            patch(builder.token(), channel, Map.of("requiredRoleIds", List.of(crew, builders)))
                .then().statusCode(200).body("requiredRoleIds", containsInAnyOrder(crew, builders));

            // Changing channels needs MANAGE_CHANNELS.
            TestUsers.User member = TestUsers.register();
            patch(member.token(), GENERAL_TEXT, Map.of("topic", "nope")).then().statusCode(403).body("error", equalTo("forbidden"));
            as(member.token()).delete("/api/v1/channels/" + GENERAL_TEXT).then().statusCode(403);
        } finally {
            if (channel != null) {
                deleteChannel(channel);
            }
            TestUsers.deleteRole(crew);
            TestUsers.deleteRole(builders);
        }
    }

    @Test
    void rolesThatChannelsRequireCannotBeDeleted() {
        String owner = TestUsers.ownerToken();
        String crew = TestUsers.createRole("Crew " + UUID.randomUUID());
        String channel = create(owner, Map.of("type", "text", "name", "crew", "requiredRoleIds", List.of(crew)))
            .then().statusCode(201).extract().path("id");
        try {
            as(owner).delete("/api/v1/roles/" + crew).then().statusCode(409).body("error", equalTo("role_in_use"));
            patch(owner, channel, Map.of("requiredRoleIds", List.of())).then().statusCode(200);
            TestUsers.deleteRole(crew);
        } finally {
            deleteChannel(channel);
        }
    }

    @Test
    void administratorsHoldEverythingButServerSettings() {
        TestUsers.User admin = TestUsers.register();
        String adminRole = TestUsers.ADMIN_ROLE;
        TestUsers.assignRole(admin.id(), adminRole);
        String channel = null;
        String deputy = null;
        try {
            as(admin.token()).get("/api/v1/accounts/me/permissions").then().statusCode(200)
                .body("owner", equalTo(false))
                .body("permissions", hasItems("MANAGE_ROLES", "MANAGE_CHANNELS", "MANAGE_MESSAGES", "TIMEOUT_MEMBERS", "BAN_MEMBERS"))
                .body("permissions", not(hasItem("MANAGE_SERVER")));
            as(admin.token()).get("/api/v1/server-settings").then().statusCode(403);
            Map<String, Object> rename = new HashMap<>();
            rename.put("name", "taken over");
            as(admin.token()).body(rename).patch("/api/v1/server-settings").then().statusCode(403);

            channel = create(admin.token(), Map.of("type", "text", "name", "admin made"))
                .then().statusCode(201).extract().path("id");

            // Admins may create further admin-level roles, but not hand out server settings.
            List<String> adminPermissions = as(admin.token()).get("/api/v1/accounts/me/permissions").then().extract().path("permissions");
            deputy = as(admin.token()).body(Map.of("name", "Deputy", "permissions", adminPermissions))
                .post("/api/v1/roles").then().statusCode(201).extract().path("id");
            as(admin.token()).body(Map.of("name", "Settings", "permissions", List.of("MANAGE_SERVER")))
                .post("/api/v1/roles").then().statusCode(403).body("error", equalTo("permission_escalation"));
        } finally {
            if (channel != null) {
                deleteChannel(channel);
            }
            if (deputy != null) {
                TestUsers.deleteRole(deputy);
            }
            given().header("Authorization", "Bearer " + TestUsers.ownerToken())
                .delete("/api/v1/accounts/" + admin.id() + "/roles/" + adminRole).then().statusCode(204);
        }
    }
}
