package app.snatter.server.channel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A text or voice channel.
 *
 * @param topic     free text shown with the channel, or null
 * @param position  place in the channel list, 0 at the top
 * @param bitrate   audio bitrate in bits per second; null exactly for text channels
 * @param userLimit most members connected at once, 0 for no limit; null exactly for text channels
 */
public record Channel(
        ChannelId id,
        ChannelType type,
        String name,
        String topic,
        int position,
        Integer bitrate,
        Integer userLimit,
        List<PermissionOverwrite> overwrites,
        Instant createdAt,
        Instant updatedAt) {

    public Channel {
        overwrites = List.copyOf(overwrites);
    }

    /** The overwrite for the same role or account as {@code target}, if the channel has one. */
    public Optional<PermissionOverwrite> overwriteFor(PermissionOverwrite target) {
        return overwrites.stream().filter(o -> o.sameTarget(target)).findFirst();
    }

    public Channel withName(String name) {
        return new Channel(id, type, name, topic, position, bitrate, userLimit, overwrites, createdAt, updatedAt);
    }

    public Channel withTopic(String topic) {
        return new Channel(id, type, name, topic, position, bitrate, userLimit, overwrites, createdAt, updatedAt);
    }

    public Channel withBitrate(Integer bitrate) {
        return new Channel(id, type, name, topic, position, bitrate, userLimit, overwrites, createdAt, updatedAt);
    }

    public Channel withUserLimit(Integer userLimit) {
        return new Channel(id, type, name, topic, position, bitrate, userLimit, overwrites, createdAt, updatedAt);
    }
}
