package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import java.time.Instant;

/**
 * A message in a channel: a member's {@link UserMessage}, the server's
 * {@link SystemMessage}, or the {@link DeletedMessage} left where a member's
 * message was deleted.
 */
public sealed interface Message permits UserMessage, SystemMessage, DeletedMessage {

    MessageId id();

    ChannelId channelId();

    /** The writer, or for a notice the member who caused it; null once the account is deleted. */
    AccountId authorId();

    Instant createdAt();

    default boolean isBy(AccountId accountId) {
        return authorId() != null && authorId().equals(accountId);
    }
}
