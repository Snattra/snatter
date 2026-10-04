package app.snatter.server.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.snatter.server.account.AccountId;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.auth.SessionId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.RoleId;
import app.snatter.server.testing.TestDataService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChannelEventsTest {

    private final TestDataService data = new TestDataService();

    @BeforeEach
    void setUpServer() {
        data.setUpServer();
    }

    /** The real owner account, since system notices reference their author. */
    private AccountPrincipal owner() {
        return new AccountPrincipal(new AccountId(data.owner().id()), data.owner().username(),
            new SessionId(UUID.randomUUID()), true, Permission.all(), Permission.all(), Set.of(), null);
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
        RoleId staff = new RoleId(data.createRole("Events staff"));
        channels.update(owner, id, new ChannelService.Changes(null, null, null, null, null, Set.of(staff)));
        channels.delete(owner, id);

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
        Channel channel = channels.create(owner, ChannelType.TEXT, "quiet", null, null, null, Set.of());
        // Renamed and given a bitrate, which text channels do not have: all of it is refused.
        assertThrows(ApiException.class, () ->
            channels.update(owner, channel.id(), new ChannelService.Changes("loud", null, 64_000, null, null, null)));
        assertEquals(List.of(new ChannelEvent.Created(channel.id(), owner.accountId(), ChannelType.TEXT, "quiet")),
            recorded.about(channel.id()));
    }
}
