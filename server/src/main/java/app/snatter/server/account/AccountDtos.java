package app.snatter.server.account;

import app.snatter.api.model.AccountDto;
import java.time.Instant;

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
            .timedOutUntil(account.isTimedOut(Instant.now()) ? account.timedOutUntil() : null)
            .mutedAt(account.mutedAt())
            .bannedAt(account.bannedAt())
            .createdAt(account.createdAt());
    }
}
