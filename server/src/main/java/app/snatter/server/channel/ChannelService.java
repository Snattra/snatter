package app.snatter.server.channel;

import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.Permission;
import app.snatter.server.role.Role;
import app.snatter.server.role.RoleRepository;
import app.snatter.server.role.RoleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Channels and their overwrites. Channels a member cannot view behave as if
 * they did not exist. Changing a channel needs {@code MANAGE_CHANNELS} in it;
 * changing its overwrites needs {@code MANAGE_ROLES} in it and follows the
 * role rules: only roles and members below your highest role, and only
 * permissions you hold in the channel. The owner is exempt from those rules.
 */
@ApplicationScoped
public class ChannelService {

    /** Requested changes to a channel; null fields stay unchanged, an empty topic clears it. */
    public record Changes(String name, String topic, Integer bitrate, Integer userLimit, Integer position) {
    }

    private final ChannelRepository channels;
    private final RoleRepository roles;
    private final RoleService roleService;
    private final AccountRepository accounts;
    private final VoiceConfig voice;
    private final Event<ChannelEvent> events;

    public ChannelService(ChannelRepository channels, RoleRepository roles, RoleService roleService,
                          AccountRepository accounts, VoiceConfig voice, Event<ChannelEvent> events) {
        this.channels = channels;
        this.roles = roles;
        this.roleService = roleService;
        this.accounts = accounts;
        this.voice = voice;
        this.events = events;
    }

    /** The channels the member can see, top first. */
    public List<Channel> list(AccountPrincipal member) {
        return channels.findAll().stream().filter(c -> ChannelPermissions.canView(member, c)).toList();
    }

    public Channel get(AccountPrincipal member, ChannelId id) {
        return requireVisible(member, id);
    }

    public Set<Permission> permissionsIn(AccountPrincipal member, ChannelId id) {
        return ChannelPermissions.of(member, requireVisible(member, id));
    }

    /**
     * Needs server-level {@code MANAGE_CHANNELS}, checked by the caller. The
     * overwrites are stored in the same transaction, so the channel is never
     * visible without them. Each is judged against the actor's permissions in
     * the new channel before any overwrite applies, so that denying everyone
     * a permission does not stop the actor from granting it to others.
     */
    @Transactional
    public Channel create(AccountPrincipal actor, ChannelType type, String name, String topic, Integer bitrate,
                          Integer userLimit, List<PermissionOverwrite> overwrites) {
        requireVoiceSettingsFit(type, bitrate, userLimit);
        Instant now = Instant.now();
        Channel channel = new Channel(
            ChannelId.newId(),
            type,
            name.strip(),
            blankToNull(topic),
            0,
            type.hasVoice()
                ? new VoiceSettings(Objects.requireNonNullElse(bitrate, voice.newChannelBitrate()), Objects.requireNonNullElse(userLimit, 0))
                : null,
            List.of(),
            now,
            now);

        Set<Permission> held = ChannelPermissions.of(actor, channel);
        Set<PermissionOverwrite> targets = new HashSet<>();
        for (PermissionOverwrite overwrite : overwrites) {
            if (!targets.add(overwrite.cleared())) {
                throw ApiException.badRequest("invalid_overwrite", "Each role or member may have only one overwrite");
            }
            requireMayChange(actor, held, overwrite.cleared(), overwrite);
        }

        channel = channels.insertLast(channel);
        for (PermissionOverwrite overwrite : overwrites) {
            channels.saveOverwrite(channel.id(), overwrite);
        }
        events.fire(new ChannelEvent.Created(channel.id(), actor.accountId(), channel.type(), channel.name()));
        return overwrites.isEmpty() ? channel : requireExisting(channel.id());
    }

    @Transactional
    public Channel update(AccountPrincipal actor, ChannelId id, Changes changes) {
        Channel before = requireVisible(actor, id);
        requireInChannel(actor, before, Permission.MANAGE_CHANNELS);
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
        if (!after.equals(before)) {
            channels.update(after);
        }
        if (changes.position() != null && changes.position() != before.position()) {
            channels.moveTo(id, changes.position());
        }

        if (!after.name().equals(before.name())) {
            events.fire(new ChannelEvent.Renamed(id, actor.accountId(), before.name(), after.name()));
        }
        if (!Objects.equals(after.topic(), before.topic())) {
            events.fire(new ChannelEvent.TopicChanged(id, actor.accountId(), before.topic(), after.topic()));
        }
        return requireExisting(id);
    }

    @Transactional
    public void delete(AccountPrincipal actor, ChannelId id) {
        Channel channel = requireVisible(actor, id);
        requireInChannel(actor, channel, Permission.MANAGE_CHANNELS);
        channels.delete(id);
        events.fire(new ChannelEvent.Deleted(id, actor.accountId(), channel.name()));
    }

    /**
     * Replaces the overwrite for the role or account named in {@code overwrite};
     * an empty overwrite removes it.
     */
    @Transactional
    public Channel setOverwrite(AccountPrincipal actor, ChannelId id, PermissionOverwrite overwrite) {
        Channel channel = requireVisible(actor, id);
        PermissionOverwrite current = channel.overwriteFor(overwrite).orElse(overwrite.cleared());
        requireMayChange(actor, ChannelPermissions.of(actor, channel), current, overwrite);
        channels.saveOverwrite(id, overwrite);
        return requireExisting(id);
    }

    /**
     * Whether the actor, holding {@code held} in the channel, may replace
     * {@code current} with {@code replacement} for the same role or member.
     */
    private void requireMayChange(AccountPrincipal actor, Set<Permission> held,
                                  PermissionOverwrite current, PermissionOverwrite replacement) {
        if (!held.contains(Permission.MANAGE_ROLES)) {
            throw new ApiException(403, "forbidden", "You need MANAGE_ROLES in this channel to change its overwrites");
        }
        requireValid(replacement);
        requireTargetBelow(actor, replacement);
        if (!actor.owner() && !held.containsAll(changed(current, replacement))) {
            throw new ApiException(403, "permission_escalation", "You can only change permissions you hold in this channel");
        }
    }

    private void requireTargetBelow(AccountPrincipal actor, PermissionOverwrite target) {
        int targetPosition;
        if (target.roleId() != null) {
            Role role = roles.find(target.roleId())
                .orElseThrow(() -> ApiException.notFound("role_not_found", "No such role"));
            targetPosition = role.position();
        } else {
            if (accounts.findById(target.accountId()).isEmpty()) {
                throw ApiException.notFound("account_not_found", "No such account");
            }
            if (target.accountId().equals(actor.accountId())) {
                return;
            }
            targetPosition = roleService.resolve(target.accountId()).highestPosition();
        }
        if (!actor.owner() && targetPosition >= actor.highestRolePosition()) {
            throw new ApiException(403, "role_hierarchy", "You can only set overwrites for roles and members below your own highest role");
        }
    }

    private static void requireValid(PermissionOverwrite overwrite) {
        if (!Permission.CHANNEL_SCOPED.containsAll(overwrite.allow()) || !Permission.CHANNEL_SCOPED.containsAll(overwrite.deny())) {
            throw ApiException.badRequest("invalid_overwrite", "Overwrites may only contain channel-scoped permissions");
        }
        if (overwrite.allow().stream().anyMatch(overwrite.deny()::contains)) {
            throw ApiException.badRequest("invalid_overwrite", "A permission cannot be both allowed and denied");
        }
    }

    /** Permissions whose state (allowed, denied or neither) differs between two overwrites. */
    private static Set<Permission> changed(PermissionOverwrite from, PermissionOverwrite to) {
        EnumSet<Permission> changed = EnumSet.noneOf(Permission.class);
        for (Permission p : Permission.values()) {
            if (from.allow().contains(p) != to.allow().contains(p) || from.deny().contains(p) != to.deny().contains(p)) {
                changed.add(p);
            }
        }
        return changed;
    }

    private void requireVoiceSettingsFit(ChannelType type, Integer bitrate, Integer userLimit) {
        if (!type.hasVoice() && (bitrate != null || userLimit != null)) {
            throw ApiException.badRequest("not_a_voice_channel", "Text channels have no bitrate or user limit");
        }
        if (bitrate != null && bitrate > voice.maxBitrate()) {
            throw ApiException.badRequest("bitrate_too_high", "This server allows at most " + voice.maxBitrate() + " bits per second");
        }
    }

    private static void requireInChannel(AccountPrincipal actor, Channel channel, Permission permission) {
        if (!ChannelPermissions.of(actor, channel).contains(permission)) {
            throw new ApiException(403, "forbidden", "You need " + permission + " in this channel");
        }
    }

    private Channel requireVisible(AccountPrincipal member, ChannelId id) {
        return channels.find(id)
            .filter(c -> ChannelPermissions.canView(member, c))
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
