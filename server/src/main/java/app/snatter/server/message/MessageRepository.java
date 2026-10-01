package app.snatter.server.message;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.ids;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.json;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.settings.RegistrationMode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

/**
 * Messages of both kinds in one table. User messages store {@code content};
 * system messages store the notice as {@code system_type} plus its values in
 * {@code system_data}. The type names are the API's {@code SystemNotice}
 * discriminator values.
 */
@ApplicationScoped
public class MessageRepository {

    /** A notice as stored: its type name and its values. */
    private record StoredNotice(String type, String from, String to) {
    }

    private static final RowMapper<Message> MAPPER = (rs, ctx) -> {
        MessageId id = new MessageId(uuid(rs, "id"));
        ChannelId channelId = new ChannelId(uuid(rs, "channel_id"));
        AccountId authorId = id(rs, "author_id", AccountId::new);
        return switch (rs.getString("kind")) {
            case "user" -> {
                MessageId replyId = id(rs, "reply_id", MessageId::new);
                yield new UserMessage(id, channelId, authorId,
                    rs.getString("content"),
                    ids(rs, "mentioned_account_ids", AccountId::new),
                    id(rs, "reply_to_id", MessageId::new),
                    replyId == null ? null : new UserMessage.Reference(
                        replyId, id(rs, "reply_author_id", AccountId::new), rs.getString("reply_content")),
                    instant(rs, "created_at"),
                    instant(rs, "edited_at"));
            }
            case "deleted" -> new DeletedMessage(id, channelId, authorId,
                instant(rs, "created_at"), instant(rs, "deleted_at"), rs.getBoolean("removed_by_moderator"));
            case "system" -> new SystemMessage(id, channelId, authorId,
                load(new StoredNotice(rs.getString("system_type"), rs.getString("system_from"), rs.getString("system_to"))),
                instant(rs, "created_at"));
            default -> throw new IllegalStateException("Unknown message kind: " + rs.getString("kind"));
        };
    };

    /** A message with a preview of the message it replies to, while that is there and not deleted. */
    private static final String SELECT = """
        SELECT m.id, m.channel_id, m.kind, m.author_id, m.content, m.mentioned_account_ids,
               m.system_type, m.system_data ->> 'from' AS system_from, m.system_data ->> 'to' AS system_to,
               m.reply_to_id, m.created_at, m.edited_at, m.deleted_at, m.removed_by_moderator,
               r.id AS reply_id, r.author_id AS reply_author_id, r.content AS reply_content
        FROM message m
        LEFT JOIN message r ON r.id = m.reply_to_id AND r.channel_id = m.channel_id AND r.kind = 'user'
        """;

    private final Jdbi jdbi;

    public MessageRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Optional<Message> find(ChannelId channelId, MessageId id) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE m.channel_id = :channelId AND m.id = :id")
            .bind("channelId", channelId)
            .bind("id", id)
            .map(MAPPER)
            .findOne());
    }

    /** The latest {@code limit} messages, or the ones just before {@code before}; oldest first. */
    public List<Message> findLatest(ChannelId channelId, MessageId before, int limit) {
        List<Message> newestFirst = jdbi.withHandle(h -> h
            .createQuery(SELECT + """
                WHERE m.channel_id = :channelId AND (:before IS NULL OR m.id < :before)
                ORDER BY m.id DESC
                LIMIT :limit
                """)
            .bind("channelId", channelId)
            .bind("before", before)
            .bind("limit", limit)
            .map(MAPPER)
            .list());
        return newestFirst.reversed();
    }

    /** The {@code limit} messages just after {@code after}; oldest first. */
    public List<Message> findAfter(ChannelId channelId, MessageId after, int limit) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + """
                WHERE m.channel_id = :channelId AND m.id > :after
                ORDER BY m.id
                LIMIT :limit
                """)
            .bind("channelId", channelId)
            .bind("after", after)
            .bind("limit", limit)
            .map(MAPPER)
            .list());
    }

    public void insert(Message message) {
        switch (message) {
            case UserMessage m -> jdbi.useHandle(h -> h
                .createUpdate("""
                    INSERT INTO message (id, channel_id, kind, author_id, content, mentioned_account_ids, reply_to_id, created_at)
                    VALUES (:id, :channelId, 'user', :authorId, :content, :mentions, :replyToId, :createdAt)
                    """)
                .bind("id", m.id())
                .bind("channelId", m.channelId())
                .bind("authorId", m.authorId())
                .bind("content", m.content())
                .bind("mentions", json(m.mentions()))
                .bind("replyToId", m.replyToId())
                .bind("createdAt", m.createdAt())
                .execute());
            case DeletedMessage m -> throw new IllegalArgumentException("A message is only deleted where it is: " + m.id());
            case SystemMessage m -> {
                StoredNotice notice = store(m.notice());
                jdbi.useHandle(h -> h
                    // Patching an empty object drops the null values, as a JSON merge patch does.
                    .createUpdate("""
                        INSERT INTO message (id, channel_id, kind, author_id, system_type, system_data, created_at)
                        VALUES (:id, :channelId, 'system', :authorId, :type,
                                json_patch('{}', json_object('from', :from, 'to', :to)),
                                :createdAt)
                        """)
                    .bind("id", m.id())
                    .bind("channelId", m.channelId())
                    .bind("authorId", m.authorId())
                    .bind("type", notice.type())
                    .bind("from", notice.from())
                    .bind("to", notice.to())
                    .bind("createdAt", m.createdAt())
                    .execute());
            }
        }
    }

    /** Saves new content, its mentions and the edit time of a user message. */
    public void updateContent(UserMessage message) {
        jdbi.useHandle(h -> h
            .createUpdate("""
                UPDATE message SET content = :content, mentioned_account_ids = :mentions, edited_at = :editedAt
                WHERE id = :id AND kind = 'user'
                """)
            .bind("id", message.id())
            .bind("content", message.content())
            .bind("mentions", json(message.mentions()))
            .bind("editedAt", message.editedAt())
            .execute());
    }

    /** What a user message becomes when deleted; everything its author wrote goes. */
    private static final String DELETE_CONTENT = """
        UPDATE message
        SET kind = 'deleted', content = NULL, mentioned_account_ids = '[]', reply_to_id = NULL, edited_at = NULL,
            deleted_at = :at, deleted_by = :by, removed_by_moderator = :byModerator
        """;

    /** Turns a user message into a {@link DeletedMessage}. */
    public DeletedMessage markDeleted(UserMessage message, AccountId by, Instant at) {
        boolean byModerator = !message.isBy(by);
        jdbi.useHandle(h -> h
            .createUpdate(DELETE_CONTENT + "WHERE id = :id AND kind = 'user'")
            .bind("id", message.id())
            .bind("at", at)
            .bind("by", by)
            .bind("byModerator", byModerator)
            .execute());
        return new DeletedMessage(message.id(), message.channelId(), message.authorId(), message.createdAt(), at, byModerator);
    }

    /** A message a purge deleted. */
    public record Purged(ChannelId channelId, MessageId id) {
    }

    /**
     * Deletes the author's user messages since {@code since} in these
     * channels, as {@link #markDeleted} would; oldest first.
     */
    public List<Purged> purge(AccountId authorId, Instant since, Collection<ChannelId> channelIds, AccountId by, Instant at) {
        if (channelIds.isEmpty()) {
            return List.of();
        }
        List<Purged> purged = jdbi.withHandle(h -> h
            .createQuery(DELETE_CONTENT + """
                WHERE author_id = :authorId AND kind = 'user' AND created_at >= :since AND channel_id IN (<channelIds>)
                RETURNING id, channel_id
                """)
            .bind("authorId", authorId)
            .bind("since", since)
            .bindList("channelIds", List.copyOf(channelIds))
            .bind("at", at)
            .bind("by", by)
            .bind("byModerator", !authorId.equals(by))
            .map((rs, ctx) -> new Purged(new ChannelId(uuid(rs, "channel_id")), new MessageId(uuid(rs, "id"))))
            .list());
        // RETURNING comes in no particular order; ids as stored order the messages.
        return purged.stream().sorted(Comparator.comparing(p -> p.id().value().toString())).toList();
    }

    /** Removes a message entirely; for notices. */
    public void delete(MessageId id) {
        jdbi.useHandle(h -> h
            .createUpdate("DELETE FROM message WHERE id = :id")
            .bind("id", id)
            .execute());
    }

    private static StoredNotice store(SystemNotice notice) {
        return switch (notice) {
            case SystemNotice.ChannelCreated n -> new StoredNotice("channel_created", null, null);
            case SystemNotice.ChannelRenamed n -> new StoredNotice("channel_renamed", n.from(), n.to());
            case SystemNotice.ChannelTopicChanged n -> new StoredNotice("channel_topic_changed", n.from(), n.to());
            case SystemNotice.MemberJoined n -> new StoredNotice("member_joined", null, null);
            case SystemNotice.ServerRenamed n -> new StoredNotice("server_renamed", n.from(), n.to());
            case SystemNotice.RegistrationModeChanged n ->
                new StoredNotice("registration_mode_changed", n.from().dbValue(), n.to().dbValue());
        };
    }

    private static SystemNotice load(StoredNotice stored) {
        return switch (stored.type()) {
            case "channel_created" -> new SystemNotice.ChannelCreated();
            case "channel_renamed" -> new SystemNotice.ChannelRenamed(stored.from(), stored.to());
            case "channel_topic_changed" -> new SystemNotice.ChannelTopicChanged(stored.from(), stored.to());
            case "member_joined" -> new SystemNotice.MemberJoined();
            case "server_renamed" -> new SystemNotice.ServerRenamed(stored.from(), stored.to());
            case "registration_mode_changed" -> new SystemNotice.RegistrationModeChanged(
                RegistrationMode.fromDbValue(stored.from()), RegistrationMode.fromDbValue(stored.to()));
            default -> throw new IllegalStateException("Unknown system notice type: " + stored.type());
        };
    }
}
