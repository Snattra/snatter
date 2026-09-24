package app.snatter.server.channel;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.instant;
import static app.snatter.server.persistence.Rows.uuid;

import app.snatter.server.account.AccountId;
import app.snatter.server.role.Permission;
import app.snatter.server.role.RoleId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

/**
 * Channels and their overwrites. Operations that change positions lock the
 * table against concurrent writers so positions stay contiguous; run them in
 * a transaction.
 */
@ApplicationScoped
public class ChannelRepository {

    private record OverwriteRow(ChannelId channelId, PermissionOverwrite overwrite) {
    }

    private static final RowMapper<Channel> MAPPER = (rs, ctx) -> new Channel(
        new ChannelId(uuid(rs, "id")),
        ChannelType.fromDbValue(rs.getString("type")),
        rs.getString("name"),
        rs.getString("topic"),
        rs.getInt("position"),
        voice(rs.getObject("bitrate", Integer.class), rs.getObject("user_limit", Integer.class)),
        List.of(),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));

    private static final RowMapper<OverwriteRow> OVERWRITE_MAPPER = (rs, ctx) -> new OverwriteRow(
        new ChannelId(uuid(rs, "channel_id")),
        new PermissionOverwrite(
            id(rs, "role_id", RoleId::new),
            id(rs, "account_id", AccountId::new),
            Permission.fromMask(rs.getLong("allow")),
            Permission.fromMask(rs.getLong("deny"))));

    private static final String SELECT = """
        SELECT id, type, name, topic, position, bitrate, user_limit, created_at, updated_at
        FROM channel
        """;

    private static final String SELECT_OVERWRITES = """
        SELECT channel_id, role_id, account_id, allow, deny
        FROM channel_overwrite
        """;

    private static final String OVERWRITE_ORDER = " ORDER BY role_id NULLS LAST, account_id";

    private final Jdbi jdbi;

    public ChannelRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Every channel with its overwrites, top first. */
    public List<Channel> findAll() {
        return jdbi.withHandle(h -> {
            List<Channel> channels = h.createQuery(SELECT + "ORDER BY position, created_at").map(MAPPER).list();
            Map<ChannelId, List<PermissionOverwrite>> overwrites = new HashMap<>();
            for (OverwriteRow row : h.createQuery(SELECT_OVERWRITES + OVERWRITE_ORDER).map(OVERWRITE_MAPPER).list()) {
                overwrites.computeIfAbsent(row.channelId(), k -> new ArrayList<>()).add(row.overwrite());
            }
            return channels.stream()
                .map(c -> withOverwrites(c, overwrites.getOrDefault(c.id(), List.of())))
                .toList();
        });
    }

    public Optional<Channel> find(ChannelId id) {
        return jdbi.withHandle(h -> h
            .createQuery(SELECT + "WHERE id = :id")
            .bind("id", id)
            .map(MAPPER)
            .findOne()
            .map(c -> withOverwrites(c, h
                .createQuery(SELECT_OVERWRITES + "WHERE channel_id = :id" + OVERWRITE_ORDER)
                .bind("id", id)
                .map(OVERWRITE_MAPPER)
                .map(OverwriteRow::overwrite)
                .list())));
    }

    /** Inserts a channel at the bottom of the list, ignoring {@code channel.position()}. */
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
            return new Channel(channel.id(), channel.type(), channel.name(), channel.topic(), position,
                channel.voice(), List.of(), channel.createdAt(), channel.updatedAt());
        });
    }

    /** Saves name, topic and voice settings. Type, position and overwrites have their own operations. */
    public void update(Channel channel) {
        jdbi.useHandle(h -> h
            .createUpdate("""
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
            .execute());
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

    /** Deletes a channel with its overwrites and closes the gap it leaves. */
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

    /** Replaces the overwrite for the same role or account; an empty overwrite removes it. */
    public void saveOverwrite(ChannelId channelId, PermissionOverwrite overwrite) {
        jdbi.useHandle(h -> {
            h.createUpdate("""
                DELETE FROM channel_overwrite
                WHERE channel_id = :channelId
                  AND role_id IS NOT DISTINCT FROM :roleId
                  AND account_id IS NOT DISTINCT FROM :accountId
                """)
                .bind("channelId", channelId)
                .bind("roleId", overwrite.roleId())
                .bind("accountId", overwrite.accountId())
                .execute();
            if (!overwrite.isEmpty()) {
                h.createUpdate("""
                    INSERT INTO channel_overwrite (channel_id, role_id, account_id, allow, deny)
                    VALUES (:channelId, :roleId, :accountId, :allow, :deny)
                    """)
                    .bind("channelId", channelId)
                    .bind("roleId", overwrite.roleId())
                    .bind("accountId", overwrite.accountId())
                    .bind("allow", Permission.toMask(overwrite.allow()))
                    .bind("deny", Permission.toMask(overwrite.deny()))
                    .execute();
            }
        });
    }

    /** Writers wait for each other so positions are computed from a stable list; readers are not blocked. */
    private static void lockOrdering(Handle h) {
        h.execute("LOCK TABLE channel IN SHARE ROW EXCLUSIVE MODE");
    }

    private static VoiceSettings voice(Integer bitrate, Integer userLimit) {
        return bitrate == null ? null : new VoiceSettings(bitrate, userLimit);
    }

    private static Channel withOverwrites(Channel c, List<PermissionOverwrite> overwrites) {
        return new Channel(c.id(), c.type(), c.name(), c.topic(), c.position(), c.voice(),
            overwrites, c.createdAt(), c.updatedAt());
    }
}
