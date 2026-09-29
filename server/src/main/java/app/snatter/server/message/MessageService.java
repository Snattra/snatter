package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.channel.Channel;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.channel.ChannelService;
import app.snatter.server.role.Permission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Messages in channels and how far members have read them. Reading needs
 * the channel to be visible, sending needs {@code SEND_MESSAGES}. Authors
 * edit and delete their own messages; {@code MANAGE_MESSAGES} deletes any,
 * one at a time or all of a member's recent ones. A deleted user message
 * stays as a {@link DeletedMessage}; a deleted notice is removed.
 */
@ApplicationScoped
public class MessageService {

    private final MessageRepository messages;
    private final ReadStateRepository readStates;
    private final AccountRepository accounts;
    private final ChannelService channels;
    private final Event<MessageEvent> events;
    private final Event<ReadStateEvent> readEvents;

    public MessageService(MessageRepository messages, ReadStateRepository readStates, AccountRepository accounts,
                          ChannelService channels, Event<MessageEvent> events, Event<ReadStateEvent> readEvents) {
        this.messages = messages;
        this.readStates = readStates;
        this.accounts = accounts;
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
        String text = content.strip();
        UserMessage message = UserMessage.create(channelId, author.accountId(), text, mentionsIn(text), replyTo);
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
        String text = content.strip();
        UserMessage edited = original.edited(text, mentionsIn(text));
        messages.updateContent(edited);
        events.fire(new MessageEvent.Updated(edited));
        return edited;
    }

    @Transactional
    public void delete(AccountPrincipal member, ChannelId channelId, MessageId id) {
        requireReadable(member, channelId);
        Message message = require(channelId, id);
        if (message instanceof DeletedMessage) {
            return;
        }
        boolean own = message instanceof UserMessage && message.isBy(member.accountId());
        if (!own && !member.has(Permission.MANAGE_MESSAGES)) {
            throw new ApiException(403, "forbidden", "You need MANAGE_MESSAGES to delete this message");
        }
        switch (message) {
            case UserMessage user -> events.fire(new MessageEvent.Updated(messages.markDeleted(user, member.accountId(), now())));
            case SystemMessage notice -> {
                messages.delete(notice.id());
                events.fire(new MessageEvent.Deleted(channelId, id));
            }
            case DeletedMessage _ -> throw new IllegalStateException("handled above");
        }
    }

    /**
     * Deletes the member's user messages sent since {@code since}, in the
     * channels the moderator can see.
     *
     * @return how many were deleted
     */
    @Transactional
    public int purge(AccountPrincipal moderator, AccountId authorId, Instant since) {
        if (!moderator.has(Permission.MANAGE_MESSAGES)) {
            throw new ApiException(403, "forbidden", "You need MANAGE_MESSAGES to delete a member's messages");
        }
        if (accounts.findById(authorId).isEmpty()) {
            throw ApiException.notFound("account_not_found", "No such account");
        }
        Instant at = now();
        if (since.isAfter(at)) {
            throw ApiException.badRequest("invalid_since", "Choose a time that has passed");
        }
        List<ChannelId> readable = channels.list(moderator).stream()
            .filter(c -> c.type().hasMessages())
            .map(Channel::id)
            .toList();
        List<MessageRepository.Purged> purged = messages.purge(authorId, since, readable, moderator.accountId(), at);
        boolean byModerator = !authorId.equals(moderator.accountId());
        // Oldest first, so each channel's first and last are its range.
        purged.stream()
            .collect(Collectors.groupingBy(MessageRepository.Purged::channelId, LinkedHashMap::new,
                Collectors.mapping(MessageRepository.Purged::id, Collectors.toList())))
            .forEach((channelId, ids) -> events.fire(
                new MessageEvent.Purged(channelId, authorId, ids.getFirst(), ids.getLast(), at, byModerator)));
        return purged.size();
    }

    /** Now, at the database's microsecond precision. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * The existing accounts the text mentions. A token for an account that
     * does not exist stays in the text but is no mention.
     */
    private List<AccountId> mentionsIn(String content) {
        Set<AccountId> mentioned = Mentions.in(content);
        if (mentioned.size() > Mentions.MAX) {
            throw ApiException.badRequest("too_many_mentions",
                "A message can mention at most " + Mentions.MAX + " members");
        }
        Set<AccountId> existing = accounts.existing(mentioned);
        return mentioned.stream().filter(existing::contains).toList();
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
