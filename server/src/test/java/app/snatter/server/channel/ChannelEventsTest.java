package app.snatter.server.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.snatter.server.account.AccountId;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.auth.SessionId;
import app.snatter.server.role.Permission;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChannelEventsTest {

    private static final AccountPrincipal OWNER = new AccountPrincipal(AccountId.newId(), "owner",
        new SessionId(UUID.randomUUID()), true, Permission.all(), Integer.MAX_VALUE, Set.of());

    @Inject
    ChannelService channels;

    @Inject
    RecordedChannelEvents recorded;

    @Test
    void changesThatBecomeNoticesAreFired() {
        AccountId actor = OWNER.accountId();
        Channel channel = channels.create(OWNER, ChannelType.TEXT, "events", null, null, null, List.of());
        ChannelId id = channel.id();

        channels.update(OWNER, id, new ChannelService.Changes("renamed", "a topic", null, null, null));
        channels.update(OWNER, id, new ChannelService.Changes("renamed", "", null, null, 0));
        channels.delete(OWNER, id);

        assertEquals(List.of(
            new ChannelEvent.Created(id, actor, ChannelType.TEXT, "events"),
            new ChannelEvent.Renamed(id, actor, "events", "renamed"),
            new ChannelEvent.TopicChanged(id, actor, null, "a topic"),
            new ChannelEvent.TopicChanged(id, actor, "a topic", null),
            new ChannelEvent.Deleted(id, actor, "renamed")), recorded.about(id));
    }

    @Test
    void rejectedChangesFireNothing() {
        Channel channel = channels.create(OWNER, ChannelType.VOICE, "quiet", null, null, null, List.of());
        try {
            assertThrows(ApiException.class, () ->
                channels.update(OWNER, channel.id(), new ChannelService.Changes("loud", null, 999_000, null, null)));
            assertEquals(List.of(new ChannelEvent.Created(channel.id(), OWNER.accountId(), ChannelType.VOICE, "quiet")),
                recorded.about(channel.id()));
        } finally {
            channels.delete(OWNER, channel.id());
        }
    }
}
