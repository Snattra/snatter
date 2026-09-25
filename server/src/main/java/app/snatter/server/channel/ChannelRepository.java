package app.snatter.server.channel;

import static app.snatter.server.persistence.Rows.ids;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.role.RoleId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

/**
 * Channels and their required roles. Operations that change positions lock
 * the table against concurrent writers so positions stay contiguous; run them
 * in a transaction.
 */
@ApplicationScoped
public class ChannelRepository {

    private static final RowMapper<Channel> MAPPER = (rs, ctx) -> new Channel(
        new ChannelId(uuid(rs, "id")),
        ChannelType.fromDbValue(rs.getString("type")),
        rs.getString("name"),
        rs.getString("topic"),
        rs.getInt("position"),
        voice(rs.getObject("bitrate", Integer.class), rs.getObject("user_limit", Integer.class)),
        new HashSet<>(ids(rs, "required_role_ids", RoleId::new)),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));

    private static final String SELECT = """
        SELECT c.id, c.type, c.name, c.topic, c.position, c.bitrate, c.user_limit, c.created_at, c.updated_at,
               array_remove(array_agg(r.role_id), NULL) AS required_role_ids
        FROM channel c
        LEFT JOIN channel_required_role r ON r.channel_id = c.id
        """;

    private static final String GROUP = " GROUP BY c.id";

    private final Jdbi jdbi;

    public ChannelRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Every channel, top first. */
    public List<Channel> findAll() {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + GROUP + " ORDER BY c.position, c.created_at")
            .map(MAPPER)
            .list());
    }

    public Optional<Channel> find(ChannelId id) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE c.id = :id" + GROUP)
            .bind("id", id)
            .map(MAPPER)
            .findOne());
    }

    /**
     * Inserts a channel with its required roles at the bottom of the list,
     * ignoring {@code channel.position()}.
     */
    public Channel insertLast(Channel channel) {
        return jdbi.withHandle(h -> {
            lockOrdering(h);
            int position = h.createQuery("SELECT coalesce(max(position) + 1, 0) FROM channel").mapTo(Integer.class).one();
            h.createUpdate("""
                INSERT INTO channel (id, type, name, topic, position, bitrate, user_limit, created_at, updated_at)
                VALUES (:id, :type, :name, :topic, :position, :bitrate, :userLimit, :createdAt, :updatedAt)
                """)
                .bind("id", channel.id())
                .bind("type", channel.type().dbValue())
                .bind("name", channel.name())
                .bind("topic", channel.topic())
                .bind("position", position)
                .bind("bitrate", channel.voice() == null ? null : channel.voice().bitrate())
                .bind("userLimit", channel.voice() == null ? null : channel.voice().userLimit())
                .bind("createdAt", channel.createdAt())
                .bind("updatedAt", channel.updatedAt())
                .execute();
            insertRequiredRoles(h, channel.id(), channel.requiredRoleIds());
            return new Channel(channel.id(), channel.type(), channel.name(), channel.topic(), position,
                channel.voice(), channel.requiredRoleIds(), channel.createdAt(), channel.updatedAt());
        });
    }

    /** Saves name, topic, voice settings and required roles. Type and position have their own operations. */
    public void update(Channel channel) {
        jdbi.useHandle(h -> {
            h.createUpdate("""
                UPDATE channel
                SET name = :name, topic = :topic, bitrate = :bitrate, user_limit = :userLimit, updated_at = :now
                WHERE id = :id
                """)
                .bind("id", channel.id())
                .bind("name", channel.name())
                .bind("topic", channel.topic())
                .bind("bitrate", channel.voice() == null ? null : channel.voice().bitrate())
                .bind("userLimit", channel.voice() == null ? null : channel.voice().userLimit())
                .bind("now", Instant.now())
                .execute();
            h.createUpdate("DELETE FROM channel_required_role WHERE channel_id = :id")
                .bind("id", channel.id())
                .execute();
            insertRequiredRoles(h, channel.id(), channel.requiredRoleIds());
        });
    }

    /** Moves a channel to a position, clamped to the end of the list, and renumbers the rest. */
    public void moveTo(ChannelId id, int position) {
        jdbi.useHandle(h -> {
            lockOrdering(h);
            List<ChannelId> order = new ArrayList<>(h
                .createQuery("SELECT id FROM channel ORDER BY position, created_at")
                .map((rs, ctx) -> new ChannelId(uuid(rs, "id")))
                .list());
            if (!order.remove(id)) {
                return;
            }
            order.add(Math.min(position, order.size()), id);
            var batch = h.prepareBatch("UPDATE channel SET position = :position WHERE id = :id AND position <> :position");
            for (int i = 0; i < order.size(); i++) {
                batch.bind("id", order.get(i)).bind("position", i).add();
            }
            batch.execute();
        });
    }

    /** Deletes a channel with its required roles and closes the gap it leaves. */
    public boolean delete(ChannelId id) {
        return jdbi.withHandle(h -> {
            lockOrdering(h);
            Optional<Integer> position = h.createQuery("DELETE FROM channel WHERE id = :id RETURNING position")
                .bind("id", id)
                .mapTo(Integer.class)
                .findOne();
            position.ifPresent(p -> h.createUpdate("UPDATE channel SET position = position - 1 WHERE position > :position")
                .bind("position", p)
                .execute());
            return position.isPresent();
        });
    }

    private static void insertRequiredRoles(Handle h, ChannelId channelId, Set<RoleId> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        var batch = h.prepareBatch("INSERT INTO channel_required_role (channel_id, role_id) VALUES (:channelId, :roleId)");
        for (RoleId roleId : roleIds) {
            batch.bind("channelId", channelId).bind("roleId", roleId).add();
        }
        batch.execute();
    }

    /** Writers wait for each other so positions are computed from a stable list; readers are not blocked. */
    private static void lockOrdering(Handle h) {
        h.execute("LOCK TABLE channel IN SHARE ROW EXCLUSIVE MODE");
    }

    private static VoiceSettings voice(Integer bitrate, Integer userLimit) {
        return bitrate == null ? null : new VoiceSettings(bitrate, userLimit);
    }
}
