package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A message written by a member.
 *
 * @param mentions  the members the content mentions, each once, in order of first mention
 * @param replyToId the message this replies to, kept after that message is deleted
 * @param replyTo   preview of that message while it exists, otherwise null
 * @param editedAt  when the content last changed, or null
 */
public record UserMessage(
        MessageId id,
        ChannelId channelId,
        AccountId authorId,
        String content,
        List<AccountId> mentions,
        MessageId replyToId,
        Reference replyTo,
        Instant createdAt,
        Instant editedAt) implements Message {

    /** Enough of a replied-to message to show a preview. */
    public record Reference(MessageId id, AccountId authorId, String content) {
    }

    /** A new message; timestamps are cut to the database's microsecond precision. */
    public static UserMessage create(ChannelId channelId, AccountId authorId, String content, List<AccountId> mentions,
                                     Reference replyTo) {
        return new UserMessage(MessageId.newId(), channelId, authorId, content, List.copyOf(mentions),
            replyTo == null ? null : replyTo.id(), replyTo, Instant.now().truncatedTo(ChronoUnit.MICROS), null);
    }

    public UserMessage edited(String content, List<AccountId> mentions) {
        return new UserMessage(id, channelId, authorId, content, List.copyOf(mentions), replyToId, replyTo, createdAt,
            Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    public Reference reference() {
        return new Reference(id, authorId, content);
    }
}
