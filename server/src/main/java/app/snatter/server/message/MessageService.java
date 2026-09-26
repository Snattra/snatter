package app.snatter.server.message;

import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.channel.Channel;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.channel.ChannelService;
import app.snatter.server.role.Permission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Messages in channels and how far members have read them. Reading needs
 * the channel to be visible, sending needs {@code SEND_MESSAGES}. Authors
 * edit and delete their own messages; {@code MANAGE_MESSAGES} deletes any.
 */
@ApplicationScoped
public class MessageService {

    private final MessageRepository messages;
    private final ReadStateRepository readStates;
    private final ChannelService channels;
    private final Event<MessageEvent> events;
    private final Event<ReadStateEvent> readEvents;

    public MessageService(MessageRepository messages, ReadStateRepository readStates, ChannelService channels,
                          Event<MessageEvent> events, Event<ReadStateEvent> readEvents) {
        this.messages = messages;
        this.readStates = readStates;
        this.channels = channels;
        this.events = events;
        this.readEvents = readEvents;
    }

    /**
     * A page of messages, oldest first: the latest ones, those just before
     * {@code before}, or those just after {@code after}.
     */
    public List<Message> list(AccountPrincipal member, ChannelId channelId, MessageId before, MessageId after, int limit) {
        requireReadable(member, channelId);
        if (before != null && after != null) {
            throw ApiException.badRequest("invalid_paging", "Give either before or after, not both");
        }
        return after != null
            ? messages.findAfter(channelId, after, limit)
            : messages.findLatest(channelId, before, limit);
    }

    public Message get(AccountPrincipal member, ChannelId channelId, MessageId id) {
        requireReadable(member, channelId);
        return require(channelId, id);
    }

    /** @param nonce the client's, handed back to the sending session; may be null */
    @Transactional
    public UserMessage send(AccountPrincipal author, ChannelId channelId, String content, MessageId replyToId, String nonce) {
        requireReadable(author, channelId);
        if (!author.has(Permission.SEND_MESSAGES)) {
            throw new ApiException(403, "forbidden", "You cannot send messages in this channel");
        }
        UserMessage.Reference replyTo = replyToId == null ? null : requireRepliable(channelId, replyToId);
        UserMessage message = UserMessage.create(channelId, author.accountId(), content.strip(), replyTo);
        messages.insert(message);
        events.fire(new MessageEvent.Created(message, author.sessionId(), nonce));
        // Their own message is never unread to the author.
        advance(author, channelId, message.id());
        return message;
    }

    /** Moves the member's read marker in the channel forward to one of its messages. */
    @Transactional
    public ReadState markRead(AccountPrincipal member, ChannelId channelId, MessageId lastReadId) {
        requireReadable(member, channelId);
        require(channelId, lastReadId);
        return advance(member, channelId, lastReadId);
    }

    private ReadState advance(AccountPrincipal member, ChannelId channelId, MessageId lastReadId) {
        boolean moved = readStates.advance(member.accountId(), channelId, lastReadId);
        ReadState state = readStates.find(member.accountId(), channelId);
        if (moved) {
            readEvents.fire(new ReadStateEvent(member.accountId(), state));
        }
        return state;
    }

    @Transactional
    public UserMessage edit(AccountPrincipal author, ChannelId channelId, MessageId id, String content) {
        requireReadable(author, channelId);
        if (!(require(channelId, id) instanceof UserMessage original) || !original.isBy(author.accountId())) {
            throw new ApiException(403, "forbidden", "You can only edit your own messages");
        }
        UserMessage edited = original.edited(content.strip());
        messages.updateContent(edited);
        events.fire(new MessageEvent.Updated(edited));
        return edited;
    }

    @Transactional
    public void delete(AccountPrincipal member, ChannelId channelId, MessageId id) {
        requireReadable(member, channelId);
        Message message = require(channelId, id);
        boolean own = message instanceof UserMessage && message.isBy(member.accountId());
        if (!own && !member.has(Permission.MANAGE_MESSAGES)) {
            throw new ApiException(403, "forbidden", "You need MANAGE_MESSAGES to delete this message");
        }
        messages.delete(id);
        events.fire(new MessageEvent.Deleted(channelId, id));
    }

    /** A visible channel that keeps messages. */
    private Channel requireReadable(AccountPrincipal member, ChannelId channelId) {
        Channel channel = channels.get(member, channelId);
        if (!channel.type().hasMessages()) {
            throw ApiException.badRequest("voice_only_channel", "Voice-only channels have no messages");
        }
        return channel;
    }

    /** Only a member's message in the same channel can be replied to. */
    private UserMessage.Reference requireRepliable(ChannelId channelId, MessageId id) {
        if (messages.find(channelId, id).orElse(null) instanceof UserMessage original) {
            return original.reference();
        }
        throw ApiException.badRequest("invalid_reply", "Replies must refer to a member's message in the same channel");
    }

    private Message require(ChannelId channelId, MessageId id) {
        return messages.find(channelId, id)
            .orElseThrow(() -> ApiException.notFound("message_not_found", "No such message"));
    }
}
