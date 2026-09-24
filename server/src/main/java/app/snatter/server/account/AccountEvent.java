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
}
