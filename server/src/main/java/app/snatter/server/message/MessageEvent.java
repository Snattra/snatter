package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import app.snatter.server.auth.SessionId;
import app.snatter.server.channel.ChannelId;
import java.time.Instant;

/** Something that happened to a message, fired inside the transaction that did it. */
public sealed interface MessageEvent {

    ChannelId channelId();

    /**
     * A message or notice was stored. For a user message, {@code origin} is
     * the session that sent it and {@code nonce} the sender's, if any; both
     * are null for notices.
     */
    record Created(Message message, SessionId origin, String nonce) implements MessageEvent {
        @Override
        public ChannelId channelId() {
            return message.channelId();
        }
    }

    /** A user message was edited, or deleted and is now a {@link DeletedMessage}. */
    record Updated(Message message) implements MessageEvent {
        @Override
        public ChannelId channelId() {
            return message.channelId();
        }
    }

    /** A notice was removed entirely. */
    record Deleted(ChannelId channelId, MessageId messageId) implements MessageEvent {
    }

    /**
     * The author's user messages in the channel from {@code fromId} to
     * {@code toId} were deleted at once, all at {@code deletedAt}.
     */
    record Purged(ChannelId channelId, AccountId authorId, MessageId fromId, MessageId toId, Instant deletedAt,
                  boolean removedByModerator) implements MessageEvent {
    }
}
