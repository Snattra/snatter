package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import java.time.Instant;

/**
 * What is left of a member's message once deleted: its place in the channel
 * and who wrote it, without anything they wrote.
 *
 * @param removedByModerator deleted by someone other than the author
 */
public record DeletedMessage(
        MessageId id,
        ChannelId channelId,
        AccountId authorId,
        Instant createdAt,
        Instant deletedAt,
        boolean removedByModerator) implements Message {
}
