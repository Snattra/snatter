package app.snatter.server.channel;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.snatter.server.account.AccountId;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.auth.SessionId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.RoleId;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChannelEventsTest {

    /** The real owner account, since system notices reference their author. */
    private static AccountPrincipal owner() {
        String id = given().header("Authorization", "Bearer " + TestUsers.ownerToken()).get("/api/v1/accounts/me").path("id");
        return new AccountPrincipal(AccountId.fromString(id), TestUsers.OWNER_USERNAME,
            new SessionId(UUID.randomUUID()), true, Permission.all(), Set.of());
    }

    @Inject
    ChannelService channels;

    @Inject
    RecordedChannelEvents recorded;

    @Test
    void changesThatBecomeNoticesAreFired() {
        AccountPrincipal owner = owner();
        AccountId actor = owner.accountId();
        Channel channel = channels.create(owner, ChannelType.TEXT, "events", null, null, null, Set.of());
        ChannelId id = channel.id();

        channels.update(owner, id, new ChannelService.Changes("renamed", "a topic", null, null, null, null));
        channels.update(owner, id, new ChannelService.Changes("renamed", "", null, null, 0, null));
        RoleId staff = RoleId.fromString(TestUsers.createRole("Events staff " + UUID.randomUUID()));
        channels.update(owner, id, new ChannelService.Changes(null, null, null, null, null, Set.of(staff)));
        channels.delete(owner, id);
        TestUsers.deleteRole(staff.toString());

        assertEquals(List.of(
            new ChannelEvent.Created(id, actor, ChannelType.TEXT, "events"),
            new ChannelEvent.Renamed(id, actor, "events", "renamed"),
            new ChannelEvent.TopicChanged(id, actor, null, "a topic"),
            new ChannelEvent.TopicChanged(id, actor, "a topic", null),
            new ChannelEvent.Moved(id, actor, channel.position(), 0),
            new ChannelEvent.RequiredRolesChanged(id, actor, Set.of(), Set.of(staff)),
            new ChannelEvent.Deleted(id, actor, "renamed")), recorded.about(id));
    }

    @Test
    void rejectedChangesFireNothing() {
        AccountPrincipal owner = owner();
        Channel channel = channels.create(owner, ChannelType.VOICE, "quiet", null, null, null, Set.of());
        try {
            assertThrows(ApiException.class, () ->
                channels.update(owner, channel.id(), new ChannelService.Changes("loud", null, 999_000, null, null, null)));
            assertEquals(List.of(new ChannelEvent.Created(channel.id(), owner.accountId(), ChannelType.VOICE, "quiet")),
                recorded.about(channel.id()));
        } finally {
            channels.delete(owner, channel.id());
        }
    }
}
