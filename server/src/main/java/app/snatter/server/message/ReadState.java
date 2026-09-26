package app.snatter.server.message;

import app.snatter.server.channel.ChannelId;

/**
 * How far a member has read a channel.
 *
 * @param lastReadId    the newest message read, or null if none; may be deleted
 * @param lastMessageId the channel's newest message as this was read, or null if it has none
 */
public record ReadState(ChannelId channelId, MessageId lastReadId, MessageId lastMessageId) {
}
