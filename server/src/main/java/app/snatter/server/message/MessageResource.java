package app.snatter.server.message;

import app.snatter.api.MessagesApi;
import app.snatter.api.model.ChannelCreatedNoticeDto;
import app.snatter.api.model.ChannelRenamedNoticeDto;
import app.snatter.api.model.ChannelTopicChangedNoticeDto;
import app.snatter.api.model.MemberJoinedNoticeDto;
import app.snatter.api.model.MessageCreateDto;
import app.snatter.api.model.MessageDto;
import app.snatter.api.model.MessageReferenceDto;
import app.snatter.api.model.MessageUpdateDto;
import app.snatter.api.model.RegistrationModeChangedNoticeDto;
import app.snatter.api.model.RegistrationModeDto;
import app.snatter.api.model.ServerRenamedNoticeDto;
import app.snatter.api.model.SystemMessageDto;
import app.snatter.api.model.SystemNoticeDto;
import app.snatter.api.model.UserMessageDto;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.settings.RegistrationMode;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * Messages over HTTP. The DTOs mirror the sealed domain types; Jackson writes
 * the {@code kind} and {@code type} discriminators from the DTO classes.
 */
@Authenticated
public class MessageResource implements MessagesApi {

    private final MessageService messages;
    private final SecurityIdentity identity;

    public MessageResource(MessageService messages, SecurityIdentity identity) {
        this.messages = messages;
        this.identity = identity;
    }

    @Override
    public RestResponse<List<MessageDto>> listMessages(ChannelId id, MessageId before, MessageId after, Integer limit) {
        return RestResponse.ok(messages.list(actor(), id, before, after, limit).stream()
            .map(MessageResource::toDto)
            .toList());
    }

    @Override
    public RestResponse<MessageDto> createMessage(ChannelId id, MessageCreateDto body) {
        UserMessage message = messages.send(actor(), id, body.getContent(), body.getReplyToId(), body.getNonce());
        return RestResponse.status(Response.Status.CREATED, toUserDto(message).nonce(body.getNonce()));
    }

    @Override
    public RestResponse<MessageDto> getMessage(ChannelId id, MessageId messageId) {
        return RestResponse.ok(toDto(messages.get(actor(), id, messageId)));
    }

    @Override
    public RestResponse<MessageDto> editMessage(ChannelId id, MessageId messageId, MessageUpdateDto body) {
        return RestResponse.ok(toUserDto(messages.edit(actor(), id, messageId, body.getContent())));
    }

    @Override
    public RestResponse<Void> deleteMessage(ChannelId id, MessageId messageId) {
        messages.delete(actor(), id, messageId);
        return RestResponse.noContent();
    }

    private AccountPrincipal actor() {
        return (AccountPrincipal) identity.getPrincipal();
    }

    public static MessageDto toDto(Message message) {
        return switch (message) {
            case UserMessage m -> toUserDto(m);
            case SystemMessage m -> new SystemMessageDto()
                .id(m.id())
                .channelId(m.channelId())
                .authorId(m.authorId())
                .notice(toDto(m.notice()))
                .createdAt(m.createdAt());
        };
    }

    public static UserMessageDto toUserDto(UserMessage m) {
        return new UserMessageDto()
            .id(m.id())
            .channelId(m.channelId())
            .authorId(m.authorId())
            .content(m.content())
            .replyToId(m.replyToId())
            .replyTo(m.replyTo() == null ? null : new MessageReferenceDto()
                .id(m.replyTo().id())
                .authorId(m.replyTo().authorId())
                .content(m.replyTo().content()))
            .createdAt(m.createdAt())
            .editedAt(m.editedAt());
    }

    private static SystemNoticeDto toDto(SystemNotice notice) {
        return switch (notice) {
            case SystemNotice.ChannelCreated n -> new ChannelCreatedNoticeDto();
            case SystemNotice.ChannelRenamed n -> new ChannelRenamedNoticeDto().from(n.from()).to(n.to());
            case SystemNotice.ChannelTopicChanged n -> new ChannelTopicChangedNoticeDto().from(n.from()).to(n.to());
            case SystemNotice.MemberJoined n -> new MemberJoinedNoticeDto();
            case SystemNotice.ServerRenamed n -> new ServerRenamedNoticeDto().from(n.from()).to(n.to());
            case SystemNotice.RegistrationModeChanged n ->
                new RegistrationModeChangedNoticeDto().from(toDto(n.from())).to(toDto(n.to()));
        };
    }

    private static RegistrationModeDto toDto(RegistrationMode mode) {
        return RegistrationModeDto.fromValue(mode.dbValue());
    }
}
