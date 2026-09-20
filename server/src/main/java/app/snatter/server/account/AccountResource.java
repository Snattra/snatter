package app.snatter.server.account;

import static app.snatter.server.account.AccountDtos.toDto;

import app.snatter.api.AccountsApi;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Response;

@Authenticated
public class AccountResource implements AccountsApi {

    private final SecurityIdentity identity;
    private final AccountRepository accounts;
    private final AvatarService avatars;

    public AccountResource(SecurityIdentity identity, AccountRepository accounts, AvatarService avatars) {
        this.identity = identity;
        this.accounts = accounts;
        this.avatars = avatars;
    }

    @Override
    public Response getCurrentAccount() {
        Account account = accounts.findById(self())
            .orElseThrow(() -> new IllegalStateException("authenticated account no longer exists"));
        return Response.ok(toDto(account)).build();
    }

    @Override
    public Response getAccount(AccountId id) {
        Account account = accounts.findById(id)
            .orElseThrow(() -> ApiException.notFound("account_not_found", "No such account"));
        return Response.ok(toDto(account)).build();
    }

    @Override
    public Response setAvatar(byte[] image) {
        return Response.ok(toDto(avatars.set(self(), image))).build();
    }

    @Override
    public Response clearAvatar() {
        return Response.ok(toDto(avatars.clear(self()))).build();
    }

    private AccountId self() {
        return ((AccountPrincipal) identity.getPrincipal()).accountId();
    }
}
