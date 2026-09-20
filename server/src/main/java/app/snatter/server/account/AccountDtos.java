package app.snatter.server.account;

import app.snatter.api.model.AccountDto;

/** Maps the domain {@link Account} onto the contract's {@code Account} schema. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public static AccountDto toDto(Account account) {
        return new AccountDto()
            .id(account.id())
            .username(account.username())
            .displayName(account.displayName())
            .avatarId(account.avatarId())
            .roleIds(account.roleIds())
            .createdAt(account.createdAt());
    }
}
