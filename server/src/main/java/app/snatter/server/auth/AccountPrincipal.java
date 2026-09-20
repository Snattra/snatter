package app.snatter.server.auth;

import java.security.Principal;
import java.util.UUID;

/** The authenticated account behind a request, available from {@code SecurityIdentity}. */
public record AccountPrincipal(UUID accountId, String username, UUID sessionId) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
