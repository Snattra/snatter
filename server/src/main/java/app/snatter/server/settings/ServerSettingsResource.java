package app.snatter.server.settings;

import app.snatter.api.ServerApi;
import app.snatter.api.model.CommunityDto;
import app.snatter.api.model.RateLimitPolicyDto;
import app.snatter.api.model.RateLimitsDto;
import app.snatter.api.model.RegistrationInfoDto;
import app.snatter.api.model.RegistrationModeDto;
import app.snatter.api.model.ServerInfoDto;
import app.snatter.api.model.ServerSettingsDto;
import app.snatter.api.model.ServerSettingsUpdateDto;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse;

/** The {@code server} tag: public server description and owner-only settings. */
public class ServerSettingsResource implements ServerApi {

    /** Bumped whenever the HTTP or WebSocket API changes incompatibly. */
    public static final int API_VERSION = 1;

    private final String version;
    private final ServerSettingsService settings;
    private final SecurityIdentity identity;

    public ServerSettingsResource(
            @ConfigProperty(name = "quarkus.application.version") String version,
            ServerSettingsService settings,
            SecurityIdentity identity) {
        this.version = version;
        this.settings = settings;
        this.identity = identity;
    }

    @Override
    public RestResponse<ServerInfoDto> getServerInfo() {
        ServerSettings s = settings.current();
        return RestResponse.ok(new ServerInfoDto()
            .name("Snatter")
            .version(version)
            .apiVersion(API_VERSION)
            .community(new CommunityDto().name(s.name()).description(s.description()))
            .registration(new RegistrationInfoDto()
                .mode(toDto(s.registrationMode()))
                .challengeRequired(s.challengeRequired())));
    }

    @Override
    @Authenticated
    public RestResponse<ServerSettingsDto> getServerSettings() {
        requireOwner();
        return RestResponse.ok(toDto(settings.current()));
    }

    @Override
    @Authenticated
    public RestResponse<ServerSettingsDto> updateServerSettings(ServerSettingsUpdateDto update) {
        requireOwner();
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
        if (update.getMembersCanInvite() != null) {
            s = s.withMembersCanInvite(update.getMembersCanInvite());
        }
        if (update.getRateLimits() != null) {
            s = s.withRateLimits(fromDto(update.getRateLimits()));
        }
        return RestResponse.ok(toDto(settings.update(s)));
    }

    private void requireOwner() {
        AccountPrincipal principal = (AccountPrincipal) identity.getPrincipal();
        if (!settings.current().isOwner(principal.accountId())) {
            throw new ApiException(403, "forbidden", "Only the server owner may do this");
        }
    }

    static ServerSettingsDto toDto(ServerSettings s) {
        return new ServerSettingsDto()
            .name(s.name())
            .description(s.description())
            .publicUrl(s.publicUrl())
            .registrationMode(toDto(s.registrationMode()))
            .challengeRequired(s.challengeRequired())
            .membersCanInvite(s.membersCanInvite())
            .rateLimits(new RateLimitsDto()
                .enabled(s.rateLimits().enabled())
                .login(toDto(s.rateLimits().login()))
                .register(toDto(s.rateLimits().register()))
                .challenge(toDto(s.rateLimits().challenge()))
                .invite(toDto(s.rateLimits().invite())));
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
