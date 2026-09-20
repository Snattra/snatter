package app.snatter.server.account;

import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AccountResource {

    private final SecurityIdentity identity;
    private final AccountRepository accounts;
    private final AvatarService avatars;

    public AccountResource(SecurityIdentity identity, AccountRepository accounts, AvatarService avatars) {
        this.identity = identity;
        this.accounts = accounts;
        this.avatars = avatars;
    }

    /** The account that owns the current session. */
    @GET
    @Path("/me")
    public Account me() {
        return accounts.findById(self())
            .orElseThrow(() -> new IllegalStateException("authenticated account no longer exists"));
    }

    /** Any member's public profile. */
    @GET
    @Path("/{id}")
    public Account get(@PathParam("id") AccountId id) {
        return accounts.findById(id)
            .orElseThrow(() -> ApiException.notFound("account_not_found", "No such account"));
    }

    /**
     * Replaces the caller's profile picture. The body is the raw image; the
     * declared Content-Type is ignored in favour of what the bytes contain.
     */
    @PUT
    @Path("/me/avatar")
    @Consumes(MediaType.WILDCARD)
    public Account setAvatar(byte[] image) {
        return avatars.set(self(), image);
    }

    @DELETE
    @Path("/me/avatar")
    public Account clearAvatar() {
        return avatars.clear(self());
    }

    private AccountId self() {
        return ((AccountPrincipal) identity.getPrincipal()).accountId();
    }
}
