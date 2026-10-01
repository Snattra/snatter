package app.snatter.server.message;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

/** Members' read markers, one row per member and channel they have seen. */
@ApplicationScoped
public class ReadStateRepository {

    private static final RowMapper<ReadState> MAPPER = (rs, ctx) -> new ReadState(
        new ChannelId(uuid(rs, "channel_id")),
        id(rs, "last_read_id", MessageId::new),
        id(rs, "last_message_id", MessageId::new));

    private static final String LATEST = "(SELECT m.id FROM message m WHERE m.channel_id = c.id ORDER BY m.id DESC LIMIT 1)";

    private final Jdbi jdbi;

    public ReadStateRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /**
     * Gives the member a marker at the newest message in each of the channels
     * that has none yet, so what is already there counts as read. The
     * gateway calls this outside any transaction, so it brings its own.
     */
    @Transactional
    public void startReading(AccountId accountId, Collection<ChannelId> channelIds) {
        if (channelIds.isEmpty()) {
            return;
        }
        jdbi.useHandle(h -> h
            .createUpdate("""
                INSERT INTO read_state (account_id, channel_id, last_read_id)
                SELECT :accountId, c.id, %s FROM channel c WHERE c.id IN (<channelIds>)
                ON CONFLICT (account_id, channel_id) DO NOTHING
                """.formatted(LATEST))
            .bind("accountId", accountId)
            .bindList("channelIds", List.copyOf(channelIds))
            .execute());
    }

    /** The member's markers in those channels, with each channel's newest message. */
    public List<ReadState> find(AccountId accountId, Collection<ChannelId> channelIds) {
        if (channelIds.isEmpty()) {
            return List.of();
        }
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT c.id AS channel_id, r.last_read_id, %s AS last_message_id
                FROM channel c
                LEFT JOIN read_state r ON r.channel_id = c.id AND r.account_id = :accountId
                WHERE c.id IN (<channelIds>)
                """.formatted(LATEST))
            .bind("accountId", accountId)
            .bindList("channelIds", List.copyOf(channelIds))
            .map(MAPPER)
            .list());
    }

    public ReadState find(AccountId accountId, ChannelId channelId) {
        return find(accountId, List.of(channelId)).getFirst();
    }

    /**
     * Moves the marker forward to {@code messageId}; a marker already there or
     * past it stays.
     *
     * @return whether the marker moved
     */
    public boolean advance(AccountId accountId, ChannelId channelId, MessageId messageId) {
        return jdbi.withHandle(h -> h
            .createUpdate("""
                INSERT INTO read_state (account_id, channel_id, last_read_id)
                VALUES (:accountId, :channelId, :messageId)
                ON CONFLICT (account_id, channel_id) DO UPDATE SET last_read_id = EXCLUDED.last_read_id
                WHERE read_state.last_read_id IS NULL OR read_state.last_read_id < EXCLUDED.last_read_id
                """)
            .bind("accountId", accountId)
            .bind("channelId", channelId)
            .bind("messageId", messageId)
            .execute()) > 0;
    }
}
