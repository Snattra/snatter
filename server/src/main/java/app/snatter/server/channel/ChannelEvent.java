package app.snatter.server.channel;

import app.snatter.server.account.AccountId;

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

    /** The overwrite for one role or member was set; an empty one means it was removed. */
    record OverwriteChanged(ChannelId channelId, AccountId actor, PermissionOverwrite overwrite) implements ChannelEvent {
    }

    record Deleted(ChannelId channelId, AccountId actor, String name) implements ChannelEvent {
    }
}
