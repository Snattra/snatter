package app.snatter.server.invite;

import app.snatter.server.account.AccountId;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.role.Permission;
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
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteRepository invites) {
        this.invites = invites;
    }

    /** @param lifetime null for never expiring; @param maxUses null for unlimited */
    @Transactional
    public Invite create(AccountId creator, Duration lifetime, Integer maxUses) {
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

    /** Everything for members with MANAGE_INVITES, otherwise the caller's own invites. */
    public List<Invite> list(AccountPrincipal caller) {
        return caller.has(Permission.MANAGE_INVITES) ? invites.findAll() : invites.findByCreator(caller.accountId());
    }

    public Optional<Invite> find(InviteCode code) {
        return invites.find(code);
    }

    @Transactional
    public void revoke(InviteCode code, AccountPrincipal caller) {
        Invite invite = invites.find(code)
            .orElseThrow(() -> ApiException.notFound("invite_not_found", "No such invite"));
        boolean creator = invite.createdBy() != null && invite.createdBy().equals(caller.accountId());
        if (!creator && !caller.has(Permission.MANAGE_INVITES)) {
            throw new ApiException(403, "forbidden", "Only the creator or a member with MANAGE_INVITES may revoke this invite");
        }
        invites.revoke(code, Instant.now());
    }

    /**
     * Checks, without using it, that the invite could admit a registration
     * now, so a registration with a bad invite fails before the expensive
     * work. {@link #redeem} decides for certain.
     *
     * @throws ApiException {@code invite_invalid} if it cannot be used
     */
    public void requireUsable(InviteCode code) {
        if (invites.find(code).filter(i -> i.isUsable(Instant.now())).isEmpty()) {
            throw invalid();
        }
    }

    /**
     * Consumes one use of the invite for a registration. Must run inside the
     * registration's transaction so a failed registration gives the use back.
     *
     * @throws ApiException {@code invite_invalid} if it cannot be used
     */
    public Invite redeem(InviteCode code) {
        return invites.redeem(code, Instant.now()).orElseThrow(InviteService::invalid);
    }

    private static ApiException invalid() {
        return new ApiException(403, "invite_invalid", "That invite is unknown, revoked, expired or used up");
    }
}
