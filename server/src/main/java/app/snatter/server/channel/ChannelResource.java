package app.snatter.server.channel;

import app.snatter.api.ChannelsApi;
import app.snatter.api.model.ChannelCreateDto;
import app.snatter.api.model.ChannelDto;
import app.snatter.api.model.ChannelTypeDto;
import app.snatter.api.model.ChannelUpdateDto;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.RoleId;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.jboss.resteasy.reactive.RestResponse;

@Authenticated
public class ChannelResource implements ChannelsApi {

    private final ChannelService channels;
    private final SecurityIdentity identity;

    public ChannelResource(ChannelService channels, SecurityIdentity identity) {
        this.channels = channels;
        this.identity = identity;
    }

    @Override
    public RestResponse<List<ChannelDto>> listChannels() {
        return RestResponse.ok(channels.list(actor()).stream().map(ChannelResource::toDto).toList());
    }

    @Override
    @PermissionsAllowed("MANAGE_CHANNELS")
    public RestResponse<ChannelDto> createChannel(ChannelCreateDto body) {
        Channel channel = channels.create(actor(), fromDto(body.getType()), body.getName(), body.getTopic(),
            body.getBitrate(), body.getUserLimit(), roleIds(body.getRequiredRoleIds()));
        return RestResponse.status(Response.Status.CREATED, toDto(channel));
    }

    @Override
    public RestResponse<ChannelDto> getChannel(ChannelId id) {
        return RestResponse.ok(toDto(channels.get(actor(), id)));
    }

    @Override
    @PermissionsAllowed("MANAGE_CHANNELS")
    public RestResponse<ChannelDto> updateChannel(ChannelId id, ChannelUpdateDto body) {
        ChannelService.Changes changes = new ChannelService.Changes(
            body.getName(), body.getTopic(), body.getBitrate(), body.getUserLimit(), body.getPosition(),
            body.getRequiredRoleIds() == null ? null : roleIds(body.getRequiredRoleIds()));
        return RestResponse.ok(toDto(channels.update(actor(), id, changes)));
    }

    @Override
    @PermissionsAllowed("MANAGE_CHANNELS")
    public RestResponse<Void> deleteChannel(ChannelId id) {
        channels.delete(actor(), id);
        return RestResponse.noContent();
    }

    private AccountPrincipal actor() {
        return (AccountPrincipal) identity.getPrincipal();
    }

    public static ChannelDto toDto(Channel channel) {
        return new ChannelDto()
            .id(channel.id())
            .type(ChannelTypeDto.fromValue(channel.type().dbValue()))
            .name(channel.name())
            .topic(channel.topic())
            .position(channel.position())
            .bitrate(channel.voice() == null ? null : channel.voice().bitrate())
            .userLimit(channel.voice() == null ? null : channel.voice().userLimit())
            .requiredRoleIds(channel.requiredRoleIds().stream().sorted(Comparator.comparing(RoleId::value)).toList())
            .createdAt(channel.createdAt());
    }

    private static ChannelType fromDto(ChannelTypeDto type) {
        return ChannelType.fromDbValue(type.toString());
    }

    private static Set<RoleId> roleIds(List<RoleId> ids) {
        return ids == null ? Set.of() : Set.copyOf(ids);
    }
}
