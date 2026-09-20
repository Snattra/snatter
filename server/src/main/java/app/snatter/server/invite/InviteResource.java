package app.snatter.server.invite;

import app.snatter.api.InvitesApi;
import app.snatter.api.model.CommunityDto;
import app.snatter.api.model.InviteCreateDto;
import app.snatter.api.model.InviteDto;
import app.snatter.api.model.InvitePreviewDto;
import app.snatter.server.account.AccountDtos;
import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.ratelimit.RateLimited;
import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;

public class InviteResource implements InvitesApi {

    private final InviteService invites;
    private final AccountRepository accounts;
    private final ServerSettingsService settings;
    private final SecurityIdentity identity;
    private final UriInfo uriInfo;

    public InviteResource(InviteService invites, AccountRepository accounts, ServerSettingsService settings,
                          SecurityIdentity identity, @Context UriInfo uriInfo) {
        this.invites = invites;
        this.accounts = accounts;
        this.settings = settings;
        this.identity = identity;
        this.uriInfo = uriInfo;
    }

    @Override
    @PermissionsAllowed("CREATE_INVITE")
    public RestResponse<InviteDto> createInvite(InviteCreateDto body) {
        Duration lifetime = body.getExpiresInSeconds() == null ? null : Duration.ofSeconds(body.getExpiresInSeconds());
        Invite invite = invites.create(self(), lifetime, body.getMaxUses());
        return RestResponse.status(Response.Status.CREATED, toDto(invite));
    }

    @Override
    @Authenticated
    public RestResponse<List<InviteDto>> listInvites() {
        return RestResponse.ok(invites.list(principal()).stream().map(this::toDto).toList());
    }

    @Override
    @Authenticated
    public RestResponse<Void> revokeInvite(InviteCode code) {
        invites.revoke(code, principal());
        return RestResponse.noContent();
    }

    @Override
    @RateLimited("invite")
    public RestResponse<InvitePreviewDto> previewInvite(InviteCode code) {
        Invite invite = invites.find(code)
            .filter(i -> !i.isRevoked())
            .orElseThrow(() -> ApiException.notFound("invite_not_found", "No such invite"));
        if (!invite.isUsable(Instant.now())) {
            throw new ApiException(410, "invite_unusable", "That invite has expired or has no uses left");
        }
        ServerSettings s = settings.current();
        InvitePreviewDto preview = new InvitePreviewDto()
            .code(invite.code())
            .community(new CommunityDto().name(s.name()).description(s.description()))
            .expiresAt(invite.expiresAt());
        if (invite.createdBy() != null) {
            accounts.findById(invite.createdBy()).ifPresent(a -> preview.inviter(AccountDtos.toDto(a)));
        }
        return RestResponse.ok(preview);
    }

    private InviteDto toDto(Invite invite) {
        return new InviteDto()
            .code(invite.code())
            .url(linkFor(invite.code()))
            .createdBy(invite.createdBy())
            .createdAt(invite.createdAt())
            .expiresAt(invite.expiresAt())
            .maxUses(invite.maxUses())
            .uses(invite.uses())
            .revoked(invite.isRevoked());
    }

    /** Builds the shareable link from the configured public URL, or from this request's address. */
    private String linkFor(InviteCode code) {
        String publicUrl = settings.current().publicUrl();
        String base = publicUrl != null ? publicUrl : uriInfo.getBaseUri().toString().replaceAll("/+$", "");
        return base + "/invite/" + code;
    }

    private AccountPrincipal principal() {
        return (AccountPrincipal) identity.getPrincipal();
    }

    private AccountId self() {
        return principal().accountId();
    }
}
