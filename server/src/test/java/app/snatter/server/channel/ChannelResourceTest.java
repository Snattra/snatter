package app.snatter.server.channel;

import static app.snatter.client.model.PermissionDto.BAN_MEMBERS;
import static app.snatter.client.model.PermissionDto.MANAGE_CHANNELS;
import static app.snatter.client.model.PermissionDto.MANAGE_MESSAGES;
import static app.snatter.client.model.PermissionDto.MANAGE_ROLES;
import static app.snatter.client.model.PermissionDto.MANAGE_SERVER;
import static app.snatter.client.model.PermissionDto.TIMEOUT_MEMBERS;
import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.rolesApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.api.ChannelsApi;
import app.snatter.client.model.ChannelCreateDto;
import app.snatter.client.model.ChannelDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.ChannelUpdateDto;
import app.snatter.client.model.PermissionSetDto;
import app.snatter.client.model.RoleCreateDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChannelResourceTest {

    private static final UUID GENERAL_TEXT = UUID.fromString("00000000-0000-7000-8000-000000000101");

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    private static ChannelCreateDto newChannel(ChannelTypeDto type, String name) {
        return new ChannelCreateDto().type(type).name(name);
    }

    private static List<UUID> visibleIds(TestUsers.User user) {
        return channelsApi(user).listChannels().stream().map(ChannelDto::getId).toList();
    }

    @Test
    void freshServerHasOneGeneralTextChannel() {
        List<ChannelDto> channels = channelsApi(TestUsers.register()).listChannels();
        assertEquals(List.of(GENERAL_TEXT), channels.stream().map(ChannelDto::getId).toList());
        ChannelDto general = channels.getFirst();
        assertEquals(ChannelTypeDto.TEXT, general.getType());
        assertEquals("General", general.getName());
        assertEquals(0, general.getPosition());
        assertNull(general.getBitrate());
        assertApiStatus(401, () -> channelsApi().listChannels());
    }

    @Test
    void channelNamesAreUniqueRegardlessOfCase() {
        ChannelsApi channels = channelsApi(owner);
        UUID lobby = data.createChannel(ChannelTypeDto.TEXT, "Lobby");
        UUID other = data.createChannel(ChannelTypeDto.VOICE, "Lobby two");
        assertApiError(409, "channel_name_taken", () -> channels.createChannel(newChannel(ChannelTypeDto.VOICE, "  lobby ")));
        assertApiError(409, "channel_name_taken", () -> channels.updateChannel(other, new ChannelUpdateDto().name("LOBBY")));
        // A channel may change the case of its own name.
        assertEquals("lobby", channels.updateChannel(lobby, new ChannelUpdateDto().name("lobby")).getName());
        // Other changes to a channel are not held up by its own name.
        channels.updateChannel(lobby, new ChannelUpdateDto().topic("hello"));
        // Deleting a channel frees its name.
        channels.deleteChannel(lobby);
        data.createChannel(ChannelTypeDto.TEXT, "Lobby");
    }

    @Test
    void createsChannelsLastWithVoiceDefaults() {
        ChannelsApi channels = channelsApi(owner);
        int defaultBitrate = serverApi().getServerInfo().getVoice().getDefaultBitrate();
        ChannelDto lounge = channels.createChannel(newChannel(ChannelTypeDto.VOICE_TEXT, "  Lounge  ").topic("hang out"));
        assertEquals("Lounge", lounge.getName());
        assertEquals("hang out", lounge.getTopic());
        assertEquals(defaultBitrate, lounge.getBitrate());
        assertEquals(0, lounge.getUserLimit());
        assertEquals(List.of(), lounge.getRequiredRoleIds());
        List<UUID> ids = visibleIds(owner);
        assertEquals(lounge.getId(), ids.getLast(), "new channels go last");
        ChannelDto notes = channels.createChannel(newChannel(ChannelTypeDto.TEXT, "notes"));
        assertNull(notes.getBitrate());
        assertNull(notes.getUserLimit());
        assertEquals(ids.size(), notes.getPosition());
        ChannelDto hifi = channels.createChannel(newChannel(ChannelTypeDto.VOICE, "hifi").bitrate(128000).userLimit(5));
        assertEquals(128000, hifi.getBitrate());
        assertEquals(5, hifi.getUserLimit());

        assertApiError(400, "not_a_voice_channel", () -> channels.createChannel(newChannel(ChannelTypeDto.TEXT, "x").bitrate(64000)));
        assertApiError(400, "validation_failed", () -> channels.createChannel(newChannel(ChannelTypeDto.VOICE, "x").bitrate(600000)));
        assertApiError(400, "validation_failed", () -> channels.createChannel(newChannel(ChannelTypeDto.VOICE, "x").bitrate(7000)));
        assertApiError(400, "validation_failed", () -> channels.createChannel(newChannel(ChannelTypeDto.TEXT, "   ")));
        // A type the contract does not have can only be sent as plain JSON.
        given().header("Authorization", "Bearer " + owner.token()).contentType(ContentType.JSON)
            .body(Map.of("type", "stage", "name", "x")).post("/api/v1/channels")
            .then().statusCode(400);
        ChannelsApi asMember = channelsApi(TestUsers.register());
        assertApiError(403, "forbidden", () -> asMember.createChannel(newChannel(ChannelTypeDto.TEXT, "nope")));
    }

    @Test
    void updatesMovesAndDeletesKeepingPositionsContiguous() {
        ChannelsApi channels = channelsApi(owner);
        UUID a = data.createChannel(ChannelTypeDto.TEXT, "a");
        UUID b = data.createChannel(ChannelTypeDto.VOICE, "b");
        UUID c = data.createChannel(ChannelTypeDto.TEXT, "c");
        ChannelDto renamed = channels.updateChannel(a, new ChannelUpdateDto().name("renamed").topic("about a"));
        assertEquals("renamed", renamed.getName());
        assertEquals("about a", renamed.getTopic());
        ChannelDto withoutTopic = channels.updateChannel(a, new ChannelUpdateDto().topic(""));
        assertEquals("renamed", withoutTopic.getName());
        assertNull(withoutTopic.getTopic());
        ChannelDto limited = channels.updateChannel(b, new ChannelUpdateDto().bitrate(96000).userLimit(10));
        assertEquals(96000, limited.getBitrate());
        assertEquals(10, limited.getUserLimit());
        assertApiError(400, "not_a_voice_channel", () -> channels.updateChannel(a, new ChannelUpdateDto().bitrate(96000)));
        assertEquals(510000, channels.updateChannel(b, new ChannelUpdateDto().bitrate(510000)).getBitrate());
        assertApiError(400, "validation_failed", () -> channels.updateChannel(b, new ChannelUpdateDto().bitrate(600000)));

        int aPosition = channels.getChannel(a).getPosition();
        assertEquals(aPosition, channels.updateChannel(c, new ChannelUpdateDto().position(aPosition)).getPosition());
        List<UUID> ids = visibleIds(owner);
        assertEquals(List.of(c, a, b), ids.subList(aPosition, aPosition + 3));
        assertContiguous(channels);

        assertEquals(ids.size() - 1, channels.updateChannel(c, new ChannelUpdateDto().position(10_000)).getPosition());
        assertContiguous(channels);

        channels.deleteChannel(b);
        assertApiError(404, "channel_not_found", () -> channels.getChannel(UUID.randomUUID()));
        assertContiguous(channels);
    }

    private static void assertContiguous(ChannelsApi channels) {
        List<Integer> positions = channels.listChannels().stream().map(ChannelDto::getPosition).toList();
        assertEquals(IntStream.range(0, positions.size()).boxed().toList(), positions);
    }

    @Test
    void privateChannelsAreVisibleOnlyToTheirRoles() {
        ChannelsApi channels = channelsApi(owner);
        UUID crew = data.createRole("Crew");
        UUID guests = data.createRole("Guests");
        ChannelDto created = channels.createChannel(newChannel(ChannelTypeDto.TEXT, "crew only").requiredRoleIds(List.of(crew, guests)));
        assertEquals(Set.of(crew, guests), Set.copyOf(created.getRequiredRoleIds()));
        UUID channel = created.getId();
        TestUsers.User outsider = TestUsers.register();
        assertFalse(visibleIds(outsider).contains(channel));
        assertApiError(404, "channel_not_found", () -> channelsApi(outsider).getChannel(channel));

        TestUsers.User crewMember = TestUsers.register();
        data.assignRole(crewMember.id(), crew);
        assertTrue(visibleIds(crewMember).contains(channel));
        TestUsers.User guest = TestUsers.register();
        data.assignRole(guest.id(), guests);
        assertTrue(visibleIds(guest).contains(channel), "any one of the required roles is enough");
        assertTrue(visibleIds(owner).contains(channel), "the owner sees every channel");

        // Administrators manage the channels they see, but do not see every channel.
        TestUsers.User admin = TestUsers.register();
        data.assignRole(admin.id(), TestDataService.ADMIN_ROLE);
        ChannelsApi asAdmin = channelsApi(admin);
        assertFalse(visibleIds(admin).contains(channel));
        assertApiError(404, "channel_not_found", () -> asAdmin.updateChannel(channel, new ChannelUpdateDto().name("x")));
        assertApiError(404, "channel_not_found", () -> asAdmin.deleteChannel(channel));

        assertEquals(List.of(), channels.updateChannel(channel, new ChannelUpdateDto().requiredRoleIds(List.of())).getRequiredRoleIds());
        assertTrue(visibleIds(outsider).contains(channel));
    }

    @Test
    void requiredRolesMustExistAndIncludeOneOfTheCallers() {
        ChannelsApi channels = channelsApi(owner);
        UUID crew = data.createRole("Crew");
        UUID builders = data.createRole("Builders", MANAGE_CHANNELS);
        TestUsers.User builder = TestUsers.register();
        data.assignRole(builder.id(), builders);
        ChannelsApi asBuilder = channelsApi(builder);
        int before = visibleIds(owner).size();
        assertApiError(400, "invalid_required_role",
            () -> channels.createChannel(newChannel(ChannelTypeDto.TEXT, "x").requiredRoleIds(List.of(UUID.randomUUID()))));
        // Nobody but the owner can lock themselves out.
        assertApiError(400, "required_role_not_held",
            () -> asBuilder.createChannel(newChannel(ChannelTypeDto.TEXT, "x").requiredRoleIds(List.of(crew))));
        assertEquals(before, visibleIds(owner).size(), "rejected creations leave no channel behind");

        UUID channel = asBuilder.createChannel(newChannel(ChannelTypeDto.TEXT, "builders").requiredRoleIds(List.of(builders))).getId();
        assertApiError(400, "required_role_not_held",
            () -> asBuilder.updateChannel(channel, new ChannelUpdateDto().requiredRoleIds(List.of(crew))));
        ChannelDto shared = asBuilder.updateChannel(channel, new ChannelUpdateDto().requiredRoleIds(List.of(crew, builders)));
        assertEquals(Set.of(crew, builders), Set.copyOf(shared.getRequiredRoleIds()));

        // Changing channels needs MANAGE_CHANNELS.
        ChannelsApi asMember = channelsApi(TestUsers.register());
        assertApiError(403, "forbidden", () -> asMember.updateChannel(GENERAL_TEXT, new ChannelUpdateDto().topic("nope")));
        assertApiError(403, "forbidden", () -> asMember.deleteChannel(GENERAL_TEXT));
    }

    @Test
    void rolesThatChannelsRequireCannotBeDeleted() {
        UUID crew = data.createRole("Crew");
        UUID channel = channelsApi(owner).createChannel(newChannel(ChannelTypeDto.TEXT, "crew").requiredRoleIds(List.of(crew))).getId();
        assertApiError(409, "role_in_use", () -> data.deleteRole(crew));
        channelsApi(owner).updateChannel(channel, new ChannelUpdateDto().requiredRoleIds(List.of()));
        data.deleteRole(crew);
    }

    @Test
    void administratorsHoldEverythingButServerSettings() {
        TestUsers.User admin = TestUsers.register();
        data.assignRole(admin.id(), TestDataService.ADMIN_ROLE);
        PermissionSetDto permissions = rolesApi(admin).getMyPermissions();
        assertFalse(permissions.getOwner());
        assertTrue(permissions.getPermissions().containsAll(
            List.of(MANAGE_ROLES, MANAGE_CHANNELS, MANAGE_MESSAGES, TIMEOUT_MEMBERS, BAN_MEMBERS)));
        assertFalse(permissions.getPermissions().contains(MANAGE_SERVER));
        assertApiError(403, "forbidden", () -> serverApi(admin).getServerSettings());
        assertApiError(403, "forbidden", () -> serverApi(admin).updateServerSettings(new ServerSettingsUpdateDto().name("taken over")));

        channelsApi(admin).createChannel(newChannel(ChannelTypeDto.TEXT, "admin made"));

        // Admins may create further admin-level roles, but not hand out server settings.
        rolesApi(admin).createRole(new RoleCreateDto().name("Deputy").permissions(permissions.getPermissions()));
        assertApiError(403, "permission_escalation",
            () -> rolesApi(admin).createRole(new RoleCreateDto().name("Settings").permissions(List.of(MANAGE_SERVER))));
    }
}
