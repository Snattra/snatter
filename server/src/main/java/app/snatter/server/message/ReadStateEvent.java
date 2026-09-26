package app.snatter.server.message;

import app.snatter.server.account.AccountId;

/** A member's read marker moved, fired inside the transaction that moved it. */
public record ReadStateEvent(AccountId accountId, ReadState readState) {
}
