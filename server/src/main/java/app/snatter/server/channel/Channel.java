package app.snatter.server.channel;

import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.RoleId;
import java.time.Instant;
import java.util.Set;

/**
 * A text or voice channel.
 *
 * @param topic           free text shown with the channel, or null
 * @param position        place in the channel list, 0 at the top
 * @param voice           voice settings, present exactly for the types with voice
 * @param requiredRoleIds empty for a public channel; otherwise only members
 *                        holding at least one of these roles see it
 */
public record Channel(
        ChannelId id,
        ChannelType type,
        String name,
        String topic,
        int position,
        VoiceSettings voice,
        Set<RoleId> requiredRoleIds,
        Instant createdAt,
        Instant updatedAt) {

    public Channel {
        if (type.hasVoice() != (voice != null)) {
            throw new IllegalArgumentException(type + " channels " + (type.hasVoice() ? "need" : "have no") + " voice settings");
        }
        requiredRoleIds = Set.copyOf(requiredRoleIds);
    }

    /** The owner sees every channel; everyone else public ones and those they hold a required role for. */
    public boolean isVisibleTo(AccountPrincipal member) {
        return member.owner() || requiredRoleIds.isEmpty() || member.hasAnyRole(requiredRoleIds);
    }

    public Channel withName(String name) {
        return new Channel(id, type, name, topic, position, voice, requiredRoleIds, createdAt, updatedAt);
    }

    public Channel withTopic(String topic) {
        return new Channel(id, type, name, topic, position, voice, requiredRoleIds, createdAt, updatedAt);
    }

    public Channel withVoice(VoiceSettings voice) {
        return new Channel(id, type, name, topic, position, voice, requiredRoleIds, createdAt, updatedAt);
    }

    public Channel withRequiredRoleIds(Set<RoleId> requiredRoleIds) {
        return new Channel(id, type, name, topic, position, voice, requiredRoleIds, createdAt, updatedAt);
    }
}
