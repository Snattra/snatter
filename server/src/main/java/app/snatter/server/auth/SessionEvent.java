package app.snatter.server.auth;

import app.snatter.server.account.AccountId;

/** Something that happened to a session, fired inside the transaction that did it. */
public sealed interface SessionEvent {

    SessionId sessionId();

    /** The session can no longer be used: logged out, revoked or expired. */
    record Ended(SessionId sessionId, AccountId accountId) implements SessionEvent {
    }
}
