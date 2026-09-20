package app.snatter.server.account;

import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

    private final SecurityIdentity identity;
    private final AccountRepository accounts;

    public AccountResource(SecurityIdentity identity, AccountRepository accounts) {
        this.identity = identity;
        this.accounts = accounts;
    }

    /** The account that owns the current session. */
    @GET
    @Path("/me")
    @Authenticated
    public Account me() {
        AccountPrincipal principal = (AccountPrincipal) identity.getPrincipal();
        return accounts.findById(principal.accountId())
            .orElseThrow(() -> new IllegalStateException("authenticated account no longer exists"));
    }
}
