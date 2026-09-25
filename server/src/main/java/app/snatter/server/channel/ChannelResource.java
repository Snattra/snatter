package app.snatter.server.channel;

import app.snatter.api.ChannelsApi;
import app.snatter.api.model.ChannelCreateDto;
import app.snatter.api.model.ChannelDto;
import app.snatter.api.model.ChannelTypeDto;
import app.snatter.api.model.ChannelUpdateDto;
import app.snatter.api.model.PermissionOverwriteDto;
import app.snatter.api.model.PermissionOverwriteUpdateDto;
import app.snatter.api.model.PermissionSetDto;
import app.snatter.server.account.AccountId;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.Permission;
import app.snatter.server.role.PermissionDtos;
import app.snatter.server.role.RoleId;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Response;
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
        List<PermissionOverwrite> overwrites = body.getOverwrites() == null
            ? List.of()
            : body.getOverwrites().stream().map(ChannelResource::fromDto).toList();
        Channel channel = channels.create(actor(), fromDto(body.getType()), body.getName(), body.getTopic(),
            body.getBitrate(), body.getUserLimit(), overwrites);
        return RestResponse.status(Response.Status.CREATED, toDto(channel));
    }

    @Override
    public RestResponse<ChannelDto> getChannel(ChannelId id) {
        return RestResponse.ok(toDto(channels.get(actor(), id)));
    }

    @Override
    public RestResponse<ChannelDto> updateChannel(ChannelId id, ChannelUpdateDto body) {
        ChannelService.Changes changes = new ChannelService.Changes(
            body.getName(), body.getTopic(), body.getBitrate(), body.getUserLimit(), body.getPosition());
        return RestResponse.ok(toDto(channels.update(actor(), id, changes)));
    }

    @Override
    public RestResponse<Void> deleteChannel(ChannelId id) {
        channels.delete(actor(), id);
        return RestResponse.noContent();
    }

    @Override
    public RestResponse<PermissionSetDto> getMyChannelPermissions(ChannelId id) {
        AccountPrincipal actor = actor();
        return RestResponse.ok(new PermissionSetDto()
            .owner(actor.owner())
            .permissions(PermissionDtos.toDto(channels.permissionsIn(actor, id))));
    }

    @Override
    public RestResponse<ChannelDto> setRoleOverwrite(ChannelId id, RoleId roleId, PermissionOverwriteUpdateDto body) {
        PermissionOverwrite overwrite = PermissionOverwrite.forRole(roleId, allow(body), deny(body));
        return RestResponse.ok(toDto(channels.setOverwrite(actor(), id, overwrite)));
    }

    @Override
    public RestResponse<Void> removeRoleOverwrite(ChannelId id, RoleId roleId) {
        channels.setOverwrite(actor(), id, PermissionOverwrite.forRole(roleId, Set.of(), Set.of()));
        return RestResponse.noContent();
    }

    @Override
    public RestResponse<ChannelDto> setAccountOverwrite(ChannelId id, AccountId accountId, PermissionOverwriteUpdateDto body) {
        PermissionOverwrite overwrite = PermissionOverwrite.forAccount(accountId, allow(body), deny(body));
        return RestResponse.ok(toDto(channels.setOverwrite(actor(), id, overwrite)));
    }

    @Override
    public RestResponse<Void> removeAccountOverwrite(ChannelId id, AccountId accountId) {
        channels.setOverwrite(actor(), id, PermissionOverwrite.forAccount(accountId, Set.of(), Set.of()));
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
            .overwrites(channel.overwrites().stream().map(ChannelResource::toDto).toList())
            .createdAt(channel.createdAt());
    }

    private static PermissionOverwriteDto toDto(PermissionOverwrite overwrite) {
        return new PermissionOverwriteDto()
            .roleId(overwrite.roleId())
            .accountId(overwrite.accountId())
            .allow(PermissionDtos.toDto(overwrite.allow()))
            .deny(PermissionDtos.toDto(overwrite.deny()));
    }

    private static PermissionOverwrite fromDto(PermissionOverwriteDto dto) {
        if ((dto.getRoleId() == null) == (dto.getAccountId() == null)) {
            throw ApiException.badRequest("invalid_overwrite", "Each overwrite needs exactly one of roleId and accountId");
        }
        return new PermissionOverwrite(dto.getRoleId(), dto.getAccountId(),
            PermissionDtos.fromDto(dto.getAllow()), PermissionDtos.fromDto(dto.getDeny()));
    }

    private static ChannelType fromDto(ChannelTypeDto type) {
        return ChannelType.fromDbValue(type.toString());
    }

    private static Set<Permission> allow(PermissionOverwriteUpdateDto body) {
        return PermissionDtos.fromDto(body.getAllow());
    }

    private static Set<Permission> deny(PermissionOverwriteUpdateDto body) {
        return PermissionDtos.fromDto(body.getDeny());
    }
}
