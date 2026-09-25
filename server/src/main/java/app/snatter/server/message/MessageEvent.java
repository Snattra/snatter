package app.snatter.server.message;

import app.snatter.server.auth.SessionId;
import app.snatter.server.channel.ChannelId;

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

    record Updated(UserMessage message) implements MessageEvent {
        @Override
        public ChannelId channelId() {
            return message.channelId();
        }
    }

    record Deleted(ChannelId channelId, MessageId messageId) implements MessageEvent {
    }
}
