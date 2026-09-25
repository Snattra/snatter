package app.snatter.server.moderation;

import app.snatter.api.ModerationApi;
import app.snatter.api.model.AccountDto;
import app.snatter.api.model.BanCreateDto;
import app.snatter.api.model.BanDto;
import app.snatter.api.model.TimeoutCreateDto;
import app.snatter.server.account.AccountDtos;
import app.snatter.server.account.AccountId;
import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import java.time.Duration;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;

public class ModerationResource implements ModerationApi {

    private final ModerationService moderation;
    private final SecurityIdentity identity;

    public ModerationResource(ModerationService moderation, SecurityIdentity identity) {
        this.moderation = moderation;
        this.identity = identity;
    }

    @Override
    @PermissionsAllowed("BAN_MEMBERS")
    public RestResponse<List<BanDto>> listBans() {
        return RestResponse.ok(moderation.bans().stream().map(ModerationResource::toDto).toList());
    }

    @Override
    @PermissionsAllowed("BAN_MEMBERS")
    public RestResponse<BanDto> banMember(AccountId accountId, BanCreateDto body) {
        return RestResponse.ok(toDto(moderation.ban(actor(), accountId, body.getReason())));
    }

    @Override
    @PermissionsAllowed("BAN_MEMBERS")
    public RestResponse<Void> unbanMember(AccountId accountId) {
        moderation.unban(accountId);
        return RestResponse.noContent();
    }

    @Override
    @PermissionsAllowed("TIMEOUT_MEMBERS")
    public RestResponse<AccountDto> timeOutMember(AccountId accountId, TimeoutCreateDto body) {
        return RestResponse.ok(AccountDtos.toDto(moderation.timeOut(actor(), accountId, Duration.ofSeconds(body.getDurationSeconds()))));
    }

    @Override
    @PermissionsAllowed("TIMEOUT_MEMBERS")
    public RestResponse<Void> endTimeout(AccountId accountId) {
        moderation.endTimeout(actor(), accountId);
        return RestResponse.noContent();
    }

    private AccountPrincipal actor() {
        return (AccountPrincipal) identity.getPrincipal();
    }

    private static BanDto toDto(Ban ban) {
        return new BanDto()
            .accountId(ban.accountId())
            .reason(ban.reason())
            .bannedBy(ban.bannedBy())
            .createdAt(ban.createdAt());
    }
}
