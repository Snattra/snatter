package app.snatter.server.channel;

import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.RoleId;
import app.snatter.server.role.RoleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Channels. A channel with required roles is private to the members holding
 * at least one of them; channels a member cannot see behave as if they did
 * not exist. Changing channels needs {@code MANAGE_CHANNELS}, checked by the
 * caller, and only reaches channels the actor can see.
 */
@ApplicationScoped
public class ChannelService {

    /** Requested changes to a channel; null fields stay unchanged, an empty topic clears it. */
    public record Changes(String name, String topic, Integer bitrate, Integer userLimit, Integer position,
                          Set<RoleId> requiredRoleIds) {
    }

    private final ChannelRepository channels;
    private final RoleRepository roles;
    private final VoiceConfig voice;
    private final Event<ChannelEvent> events;

    public ChannelService(ChannelRepository channels, RoleRepository roles, VoiceConfig voice, Event<ChannelEvent> events) {
        this.channels = channels;
        this.roles = roles;
        this.voice = voice;
        this.events = events;
    }

    /** The channels the member can see, top first. */
    public List<Channel> list(AccountPrincipal member) {
        return channels.findAll().stream().filter(c -> c.isVisibleTo(member)).toList();
    }

    public Channel get(AccountPrincipal member, ChannelId id) {
        return requireVisible(member, id);
    }

    /** The required roles are stored in the same transaction, so a private channel is never public. */
    @Transactional
    public Channel create(AccountPrincipal actor, ChannelType type, String name, String topic, Integer bitrate,
                          Integer userLimit, Set<RoleId> requiredRoleIds) {
        requireVoiceSettingsFit(type, bitrate, userLimit);
        requireValidRequiredRoles(actor, requiredRoleIds);
        Instant now = Instant.now();
        Channel channel = channels.insertLast(new Channel(
            ChannelId.newId(),
            type,
            name.strip(),
            blankToNull(topic),
            0,
            type.hasVoice()
                ? new VoiceSettings(Objects.requireNonNullElse(bitrate, voice.newChannelBitrate()), Objects.requireNonNullElse(userLimit, 0))
                : null,
            requiredRoleIds,
            now,
            now));
        events.fire(new ChannelEvent.Created(channel.id(), actor.accountId(), channel.type(), channel.name()));
        return channel;
    }

    @Transactional
    public Channel update(AccountPrincipal actor, ChannelId id, Changes changes) {
        Channel before = requireVisible(actor, id);
        requireVoiceSettingsFit(before.type(), changes.bitrate(), changes.userLimit());

        Channel after = before;
        if (changes.name() != null) {
            after = after.withName(changes.name().strip());
        }
        if (changes.topic() != null) {
            after = after.withTopic(blankToNull(changes.topic()));
        }
        if (changes.bitrate() != null) {
            after = after.withVoice(after.voice().withBitrate(changes.bitrate()));
        }
        if (changes.userLimit() != null) {
            after = after.withVoice(after.voice().withUserLimit(changes.userLimit()));
        }
        if (changes.requiredRoleIds() != null) {
            requireValidRequiredRoles(actor, changes.requiredRoleIds());
            after = after.withRequiredRoleIds(changes.requiredRoleIds());
        }
        if (!after.equals(before)) {
            channels.update(after);
        }
        if (changes.position() != null && changes.position() != before.position()) {
            channels.moveTo(id, changes.position());
        }
        Channel updated = requireExisting(id);

        if (!after.name().equals(before.name())) {
            events.fire(new ChannelEvent.Renamed(id, actor.accountId(), before.name(), after.name()));
        }
        if (!Objects.equals(after.topic(), before.topic())) {
            events.fire(new ChannelEvent.TopicChanged(id, actor.accountId(), before.topic(), after.topic()));
        }
        if (!Objects.equals(after.voice(), before.voice())) {
            events.fire(new ChannelEvent.VoiceSettingsChanged(id, actor.accountId(), before.voice(), after.voice()));
        }
        if (!after.requiredRoleIds().equals(before.requiredRoleIds())) {
            events.fire(new ChannelEvent.RequiredRolesChanged(id, actor.accountId(), before.requiredRoleIds(), after.requiredRoleIds()));
        }
        if (updated.position() != before.position()) {
            events.fire(new ChannelEvent.Moved(id, actor.accountId(), before.position(), updated.position()));
        }
        return updated;
    }

    @Transactional
    public void delete(AccountPrincipal actor, ChannelId id) {
        Channel channel = requireVisible(actor, id);
        channels.delete(id);
        events.fire(new ChannelEvent.Deleted(id, actor.accountId(), channel.name()));
    }

    /**
     * Required roles must exist. Unless they are the owner, the actor must
     * hold one of them, so no one locks themselves out of a channel by mistake.
     */
    private void requireValidRequiredRoles(AccountPrincipal actor, Set<RoleId> requiredRoleIds) {
        for (RoleId roleId : requiredRoleIds) {
            if (roles.find(roleId).isEmpty()) {
                throw ApiException.badRequest("invalid_required_role", "No such role: " + roleId);
            }
        }
        if (!actor.owner() && !requiredRoleIds.isEmpty() && !actor.hasAnyRole(requiredRoleIds)) {
            throw ApiException.badRequest("required_role_not_held", "Hold one of the required roles yourself, or you could not see the channel");
        }
    }

    private void requireVoiceSettingsFit(ChannelType type, Integer bitrate, Integer userLimit) {
        if (!type.hasVoice() && (bitrate != null || userLimit != null)) {
            throw ApiException.badRequest("not_a_voice_channel", "Text channels have no bitrate or user limit");
        }
        if (bitrate != null && bitrate > voice.maxBitrate()) {
            throw ApiException.badRequest("bitrate_too_high", "This server allows at most " + voice.maxBitrate() + " bits per second");
        }
    }

    private Channel requireVisible(AccountPrincipal member, ChannelId id) {
        return channels.find(id)
            .filter(c -> c.isVisibleTo(member))
            .orElseThrow(ChannelService::notFound);
    }

    private Channel requireExisting(ChannelId id) {
        return channels.find(id).orElseThrow(ChannelService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("channel_not_found", "No such channel");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
