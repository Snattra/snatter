package app.snatter.server.auth;

import app.snatter.server.account.AccountId;
import java.security.Principal;

/** The authenticated account behind a request, available from {@code SecurityIdentity}. */
public record AccountPrincipal(AccountId accountId, String username, SessionId sessionId) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
