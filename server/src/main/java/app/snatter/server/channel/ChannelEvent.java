package app.snatter.server.channel;

import app.snatter.server.account.AccountId;
import app.snatter.server.role.RoleId;
import java.util.Set;

/**
 * Something that happened to a channel, fired synchronously as a CDI event
 * inside the transaction that made the change. Observers that write within
 * the transaction, such as system notices in the channel's messages, commit
 * or roll back together with the change.
 */
public sealed interface ChannelEvent {

    ChannelId channelId();

    /** Who made the change. */
    AccountId actor();

    record Created(ChannelId channelId, AccountId actor, ChannelType type, String name) implements ChannelEvent {
    }

    record Renamed(ChannelId channelId, AccountId actor, String from, String to) implements ChannelEvent {
    }

    /** {@code from} and {@code to} are null when there was or is no topic. */
    record TopicChanged(ChannelId channelId, AccountId actor, String from, String to) implements ChannelEvent {
    }

    record Moved(ChannelId channelId, AccountId actor, int from, int to) implements ChannelEvent {
    }

    record VoiceSettingsChanged(ChannelId channelId, AccountId actor, VoiceSettings from, VoiceSettings to) implements ChannelEvent {
    }

    /** Who may see the channel changed; an empty set means everyone. */
    record RequiredRolesChanged(ChannelId channelId, AccountId actor, Set<RoleId> from, Set<RoleId> to) implements ChannelEvent {
    }

    record Deleted(ChannelId channelId, AccountId actor, String name) implements ChannelEvent {
    }
}
