package app.snatter.server.role;

import app.snatter.server.account.AccountId;

/** Something that happened to roles, fired inside the transaction that did it. */
public sealed interface RoleEvent {

    RoleId roleId();

    /** Who made the change. */
    AccountId actor();

    record Created(RoleId roleId, AccountId actor) implements RoleEvent {
    }

    /** Name, colour, permissions or position changed; moving one role can shift others. */
    record Updated(RoleId roleId, AccountId actor) implements RoleEvent {
    }

    record Deleted(RoleId roleId, AccountId actor) implements RoleEvent {
    }

    record Assigned(RoleId roleId, AccountId accountId, AccountId actor) implements RoleEvent {
    }

    record Unassigned(RoleId roleId, AccountId accountId, AccountId actor) implements RoleEvent {
    }
}
