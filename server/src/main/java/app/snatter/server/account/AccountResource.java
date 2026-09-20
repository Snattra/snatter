package app.snatter.server.account;

import static app.snatter.server.account.AccountDtos.toDto;

import app.snatter.api.AccountsApi;
import app.snatter.api.model.AccountDto;
import app.snatter.server.api.ApiException;
import app.snatter.server.auth.AccountPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import java.io.InputStream;
import org.jboss.resteasy.reactive.RestResponse;

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
    public RestResponse<AccountDto> getCurrentAccount() {
        Account account = accounts.findById(self())
            .orElseThrow(() -> new IllegalStateException("authenticated account no longer exists"));
        return RestResponse.ok(toDto(account));
    }

    @Override
    public RestResponse<AccountDto> getAccount(AccountId id) {
        Account account = accounts.findById(id)
            .orElseThrow(() -> ApiException.notFound("account_not_found", "No such account"));
        return RestResponse.ok(toDto(account));
    }

    @Override
    public RestResponse<AccountDto> setAvatar(InputStream image) {
        return RestResponse.ok(toDto(avatars.set(self(), image)));
    }

    @Override
    public RestResponse<AccountDto> clearAvatar() {
        return RestResponse.ok(toDto(avatars.clear(self())));
    }

    private AccountId self() {
        return ((AccountPrincipal) identity.getPrincipal()).accountId();
    }
}
