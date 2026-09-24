package app.snatter.server.channel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A text or voice channel.
 *
 * @param topic     free text shown with the channel, or null
 * @param position  place in the channel list, 0 at the top
 * @param voice     voice settings, present exactly for the types with voice
 */
public record Channel(
        ChannelId id,
        ChannelType type,
        String name,
        String topic,
        int position,
        VoiceSettings voice,
        List<PermissionOverwrite> overwrites,
        Instant createdAt,
        Instant updatedAt) {

    public Channel {
        if (type.hasVoice() != (voice != null)) {
            throw new IllegalArgumentException(type + " channels " + (type.hasVoice() ? "need" : "have no") + " voice settings");
        }
        overwrites = List.copyOf(overwrites);
    }

    /** The overwrite for the same role or account as {@code target}, if the channel has one. */
    public Optional<PermissionOverwrite> overwriteFor(PermissionOverwrite target) {
        return overwrites.stream().filter(o -> o.sameTarget(target)).findFirst();
    }

    public Channel withName(String name) {
        return new Channel(id, type, name, topic, position, voice, overwrites, createdAt, updatedAt);
    }

    public Channel withTopic(String topic) {
        return new Channel(id, type, name, topic, position, voice, overwrites, createdAt, updatedAt);
    }

    public Channel withVoice(VoiceSettings voice) {
        return new Channel(id, type, name, topic, position, voice, overwrites, createdAt, updatedAt);
    }
}
