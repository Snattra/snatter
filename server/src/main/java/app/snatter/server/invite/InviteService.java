package app.snatter.server.invite;

import app.snatter.server.account.AccountId;
import app.snatter.server.api.ApiException;
import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InviteService {

    private final InviteRepository invites;
    private final ServerSettingsService settings;
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteRepository invites, ServerSettingsService settings) {
        this.invites = invites;
        this.settings = settings;
    }

    /** @param lifetime null for never expiring; @param maxUses null for unlimited */
    @Transactional
    public Invite create(AccountId creator, Duration lifetime, Integer maxUses) {
        ServerSettings s = settings.current();
        if (!s.membersCanInvite() && !s.isOwner(creator)) {
            throw new ApiException(403, "forbidden", "Only the server owner may create invites on this server");
        }
        Instant now = Instant.now();
        Instant expiresAt = lifetime == null ? null : now.plus(lifetime);
        for (int attempt = 0; attempt < 5; attempt++) {
            Invite invite = new Invite(InviteCode.random(random), creator, now, expiresAt, maxUses, 0, null);
            if (invites.insert(invite)) {
                return invite;
            }
        }
        throw new IllegalStateException("could not generate a unique invite code");
    }

    public List<Invite> list(AccountId caller) {
        return settings.current().isOwner(caller) ? invites.findAll() : invites.findByCreator(caller);
    }

    public Optional<Invite> find(InviteCode code) {
        return invites.find(code);
    }

    @Transactional
    public void revoke(InviteCode code, AccountId caller) {
        Invite invite = invites.find(code)
            .orElseThrow(() -> ApiException.notFound("invite_not_found", "No such invite"));
        boolean creator = invite.createdBy() != null && invite.createdBy().equals(caller);
        if (!creator && !settings.current().isOwner(caller)) {
            throw new ApiException(403, "forbidden", "Only the creator or the server owner may revoke this invite");
        }
        invites.revoke(code, Instant.now());
    }

    /**
     * Consumes one use of the invite for a registration. Must run inside the
     * registration's transaction so a failed registration gives the use back.
     *
     * @throws ApiException {@code invite_invalid} if it cannot be used
     */
    public Invite redeem(InviteCode code) {
        return invites.redeem(code, Instant.now())
            .orElseThrow(() -> new ApiException(403, "invite_invalid", "That invite is unknown, revoked, expired or used up"));
    }
}
