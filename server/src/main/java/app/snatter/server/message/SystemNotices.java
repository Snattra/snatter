package app.snatter.server.message;

import app.snatter.server.account.AccountEvent;
import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelEvent;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.channel.ChannelRepository;
import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;

/**
 * Turns events into system messages. Observers run inside the transaction
 * that fired the event, so a notice is stored exactly when its change is.
 * Channel notices go to the channel concerned, server-wide ones to the
 * system channel; channels without messages, or no system channel, get none.
 */
@ApplicationScoped
public class SystemNotices {

    private final MessageRepository messages;
    private final ChannelRepository channels;
    private final ServerSettingsService settings;
    private final Event<MessageEvent> events;

    public SystemNotices(MessageRepository messages, ChannelRepository channels, ServerSettingsService settings,
                         Event<MessageEvent> events) {
        this.messages = messages;
        this.channels = channels;
        this.settings = settings;
        this.events = events;
    }

    void onChannelEvent(@Observes ChannelEvent event) {
        SystemNotice notice = switch (event) {
            case ChannelEvent.Created _ -> new SystemNotice.ChannelCreated();
            case ChannelEvent.Renamed renamed -> new SystemNotice.ChannelRenamed(renamed.from(), renamed.to());
            case ChannelEvent.TopicChanged topic -> new SystemNotice.ChannelTopicChanged(topic.from(), topic.to());
            case ChannelEvent.Deleted _, ChannelEvent.Moved _, ChannelEvent.VoiceSettingsChanged _,
                 ChannelEvent.RequiredRolesChanged _ -> null;
        };
        if (notice != null) {
            post(event.channelId(), event.actor(), notice);
        }
    }

    void onSettingsChanged(@Observes ServerSettingsService.Changed changed) {
        ServerSettings before = changed.before();
        ServerSettings after = changed.after();
        if (!before.name().equals(after.name())) {
            post(after.systemChannelId(), changed.actor(), new SystemNotice.ServerRenamed(before.name(), after.name()));
        }
        if (before.registrationMode() != after.registrationMode()) {
            post(after.systemChannelId(), changed.actor(),
                new SystemNotice.RegistrationModeChanged(before.registrationMode(), after.registrationMode()));
        }
    }

    void onAccountEvent(@Observes AccountEvent event) {
        switch (event) {
            case AccountEvent.Registered registered ->
                post(settings.current().systemChannelId(), registered.accountId(), new SystemNotice.MemberJoined());
            case AccountEvent.Updated _, AccountEvent.Banned _, AccountEvent.Unbanned _, AccountEvent.TimeoutChanged _,
                 AccountEvent.MuteChanged _ -> {
            }
        }
    }

    private void post(ChannelId channelId, AccountId actor, SystemNotice notice) {
        if (channelId == null) {
            return;
        }
        channels.find(channelId)
            .filter(channel -> channel.type().hasMessages())
            .ifPresent(channel -> {
                SystemMessage message = SystemMessage.create(channel.id(), actor, notice);
                messages.insert(message);
                events.fire(new MessageEvent.Created(message, null, null));
            });
    }
}
