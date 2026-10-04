package app.snatter.server.testing;

import static app.snatter.server.testing.ApiClientFactory.messagesApi;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.snatter.client.model.DeletedMessageDto;
import app.snatter.client.model.MessageCreateDto;
import app.snatter.client.model.MessageDto;
import app.snatter.client.model.SystemMessageDto;
import app.snatter.client.model.UserMessageDto;
import java.util.UUID;

/**
 * Shortcuts for messages. The kinds of message share no generated type
 * that has their common fields, so the id of any message comes from here.
 */
public final class Messages {

    private Messages() {
    }

    /** Sends a message as the author and returns it. */
    public static UserMessageDto send(TestUsers.User author, UUID channel, String content) {
        return send(author, channel, new MessageCreateDto().content(content));
    }

    public static UserMessageDto send(TestUsers.User author, UUID channel, MessageCreateDto message) {
        return assertInstanceOf(UserMessageDto.class, messagesApi(author).createMessage(channel, message));
    }

    /** The id of a message of any kind. */
    public static UUID id(MessageDto message) {
        return switch (message) {
            case UserMessageDto user -> user.getId();
            case SystemMessageDto system -> system.getId();
            case DeletedMessageDto deleted -> deleted.getId();
            default -> throw new IllegalArgumentException("A kind of message the contract does not have: " + message);
        };
    }

    /** The channel of a message of any kind. */
    public static UUID channelId(MessageDto message) {
        return switch (message) {
            case UserMessageDto user -> user.getChannelId();
            case SystemMessageDto system -> system.getChannelId();
            case DeletedMessageDto deleted -> deleted.getChannelId();
            default -> throw new IllegalArgumentException("A kind of message the contract does not have: " + message);
        };
    }
}
