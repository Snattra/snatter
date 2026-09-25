package app.snatter.server.auth;

import app.snatter.server.account.AccountId;
import app.snatter.server.role.RoleService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Builds the {@link AccountPrincipal} for a session token, for HTTP requests
 * and gateway connections alike, with permissions resolved from the roles.
 */
@ApplicationScoped
public class Principals {

    private final AuthService auth;
    private final RoleService roles;

    public Principals(AuthService auth, RoleService roles) {
        this.auth = auth;
        this.roles = roles;
    }

    /** The principal behind a token, or empty if the token is unknown or its session ended. */
    public Optional<AccountPrincipal> authenticate(String token) {
        return auth.authenticate(token)
            .map(a -> resolve(a.account().id(), a.account().username(), a.session().id()));
    }

    /** The same session with permissions resolved again, after roles or ownership changed. */
    public AccountPrincipal refresh(AccountPrincipal principal) {
        return resolve(principal.accountId(), principal.username(), principal.sessionId());
    }

    private AccountPrincipal resolve(AccountId accountId, String username, SessionId sessionId) {
        RoleService.Resolution resolution = roles.resolve(accountId);
        return new AccountPrincipal(accountId, username, sessionId, resolution.owner(), resolution.permissions(),
            resolution.roleIds());
    }
}
