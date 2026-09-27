package app.snatter.server.account;

/**
 * Something that happened to an account, fired synchronously as a CDI event
 * inside the transaction that made the change.
 */
public sealed interface AccountEvent {

    AccountId accountId();

    /** A new member joined the server. */
    record Registered(AccountId accountId) implements AccountEvent {
    }

    /** The member's profile changed, for example their avatar. */
    record Updated(AccountId accountId) implements AccountEvent {
    }

    /** The member was banned; their sessions are already gone. */
    record Banned(AccountId accountId, AccountId actor) implements AccountEvent {
    }

    /** The member's ban was lifted, so they can log in again. */
    record Unbanned(AccountId accountId, AccountId actor) implements AccountEvent {
    }

    /** A timeout started, changed or was lifted early. One running out on its own fires nothing. */
    record TimeoutChanged(AccountId accountId, AccountId actor) implements AccountEvent {
    }
}
