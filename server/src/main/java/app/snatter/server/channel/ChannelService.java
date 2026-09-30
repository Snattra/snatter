package app.snatter.server.channel;

import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.RoleId;
import app.snatter.server.role.RoleRepository;
import app.snatter.server.settings.ServerSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

/**
 * Channels. A channel with required roles is private to the members holding
 * at least one of them; channels a member cannot see behave as if they did
 * not exist. Names are unique regardless of case, so a message can name a
 * channel; that holds for channels the actor cannot see too. Changing channels needs {@code MANAGE_CHANNELS}, checked by the
 * caller, and only reaches channels the actor can see.
 */
@ApplicationScoped
public class ChannelService {

    private static final String UNIQUE_VIOLATION = "23505";

    /** Requested changes to a channel; null fields stay unchanged, an empty topic clears it. */
    public record Changes(String name, String topic, Integer bitrate, Integer userLimit, Integer position,
                          Set<RoleId> requiredRoleIds) {
    }

    private final ChannelRepository channels;
    private final RoleRepository roles;
    private final ServerSettingsService settings;
    private final Event<ChannelEvent> events;

    public ChannelService(ChannelRepository channels, RoleRepository roles, ServerSettingsService settings,
                          Event<ChannelEvent> events) {
        this.channels = channels;
        this.roles = roles;
        this.settings = settings;
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
        requireFreeName(name.strip(), null);
        Instant now = Instant.now();
        Channel channel = nameChecked(() -> channels.insertLast(new Channel(
            ChannelId.newId(),
            type,
            name.strip(),
            blankToNull(topic),
            0,
            type.hasVoice()
                ? new VoiceSettings(Objects.requireNonNullElse(bitrate, settings.current().voiceDefaultBitrate()),
                    Objects.requireNonNullElse(userLimit, 0))
                : null,
            requiredRoleIds,
            now,
            now)));
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
            requireFreeName(after.name(), id);
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
            Channel changed = after;
            nameChecked(() -> {
                channels.update(changed);
                return changed;
            });
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

    private void requireFreeName(String name, ChannelId except) {
        if (channels.nameTaken(name, except)) {
            throw nameTaken();
        }
    }

    /** Runs a write that may lose a race with another channel taking the same name. */
    private static <T> T nameChecked(Supplier<T> write) {
        try {
            return write.get();
        } catch (UnableToExecuteStatementException e) {
            if (e.getCause() instanceof java.sql.SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                throw nameTaken();
            }
            throw e;
        }
    }

    private static ApiException nameTaken() {
        return ApiException.conflict("channel_name_taken", "Another channel already has that name");
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
