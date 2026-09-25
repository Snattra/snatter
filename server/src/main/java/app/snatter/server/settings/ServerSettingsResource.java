package app.snatter.server.settings;

import app.snatter.api.ServerApi;
import app.snatter.api.model.RateLimitPolicyDto;
import app.snatter.api.model.RateLimitsDto;
import app.snatter.api.model.RegistrationModeDto;
import app.snatter.api.model.ServerInfoDto;
import app.snatter.api.model.ServerSettingsDto;
import app.snatter.api.model.ServerSettingsUpdateDto;
import app.snatter.server.account.AccountId;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.role.RoleId;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.PermissionsAllowed;
import java.time.Duration;
import org.jboss.resteasy.reactive.RestResponse;

/** The {@code server} tag: public server description and owner-only settings. */
public class ServerSettingsResource implements ServerApi {

    private final ServerSettingsService settings;
    private final ServerInfoDtos serverInfo;
    private final SecurityIdentity identity;

    public ServerSettingsResource(ServerSettingsService settings, ServerInfoDtos serverInfo, SecurityIdentity identity) {
        this.settings = settings;
        this.serverInfo = serverInfo;
        this.identity = identity;
    }

    private AccountId actor() {
        return ((AccountPrincipal) identity.getPrincipal()).accountId();
    }

    @Override
    public RestResponse<ServerInfoDto> getServerInfo() {
        return RestResponse.ok(serverInfo.toDto(settings.current()));
    }

    @Override
    @PermissionsAllowed("MANAGE_SERVER")
    public RestResponse<ServerSettingsDto> getServerSettings() {
        return RestResponse.ok(toDto(settings.current()));
    }

    @Override
    @PermissionsAllowed("MANAGE_SERVER")
    public RestResponse<ServerSettingsDto> updateServerSettings(ServerSettingsUpdateDto update) {
        ServerSettings s = settings.current();
        if (update.getName() != null) {
            s = s.withName(update.getName().strip());
        }
        if (update.getDescription() != null) {
            s = s.withDescription(update.getDescription().isBlank() ? null : update.getDescription().strip());
        }
        if (update.getPublicUrl() != null) {
            s = s.withPublicUrl(update.getPublicUrl().isBlank() ? null : update.getPublicUrl().strip().replaceAll("/+$", ""));
        }
        if (update.getRegistrationMode() != null) {
            s = s.withRegistrationMode(fromDto(update.getRegistrationMode()));
        }
        if (update.getChallengeRequired() != null) {
            s = s.withChallengeRequired(update.getChallengeRequired());
        }
        if (update.getRateLimits() != null) {
            s = s.withRateLimits(fromDto(update.getRateLimits()));
        }
        if (update.getSystemChannelId() != null) {
            s = s.withSystemChannelId(update.getSystemChannelId().isEmpty() ? null : ChannelId.fromString(update.getSystemChannelId()));
        }
        if (update.getNewMemberRoleId() != null) {
            s = s.withNewMemberRoleId(update.getNewMemberRoleId().isEmpty() ? null : RoleId.fromString(update.getNewMemberRoleId()));
        }
        return RestResponse.ok(toDto(settings.update(actor(), s)));
    }

    static ServerSettingsDto toDto(ServerSettings s) {
        return new ServerSettingsDto()
            .name(s.name())
            .description(s.description())
            .publicUrl(s.publicUrl())
            .registrationMode(toDto(s.registrationMode()))
            .challengeRequired(s.challengeRequired())
            .rateLimits(new RateLimitsDto()
                .enabled(s.rateLimits().enabled())
                .login(toDto(s.rateLimits().login()))
                .register(toDto(s.rateLimits().register()))
                .challenge(toDto(s.rateLimits().challenge()))
                .invite(toDto(s.rateLimits().invite())))
            .systemChannelId(s.systemChannelId())
            .newMemberRoleId(s.newMemberRoleId());
    }

    private static RateLimitPolicyDto toDto(RateLimitPolicy p) {
        return new RateLimitPolicyDto().limit(p.limit()).periodSeconds((int) p.period().toSeconds());
    }

    private static RegistrationModeDto toDto(RegistrationMode mode) {
        return RegistrationModeDto.fromValue(mode.dbValue());
    }

    private static RegistrationMode fromDto(RegistrationModeDto mode) {
        return RegistrationMode.fromDbValue(mode.toString());
    }

    private static RateLimits fromDto(RateLimitsDto d) {
        return new RateLimits(d.getEnabled(), fromDto(d.getLogin()), fromDto(d.getRegister()), fromDto(d.getChallenge()), fromDto(d.getInvite()));
    }

    private static RateLimitPolicy fromDto(RateLimitPolicyDto d) {
        return new RateLimitPolicy(d.getLimit(), Duration.ofSeconds(d.getPeriodSeconds()));
    }
}
