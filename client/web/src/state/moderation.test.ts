import { describe, expect, it } from "vitest";
import type { Account, Permission, Role } from "../api/types";
import { canAssign, isTimedOut, moderationOf, outranks, roleChanges } from "./moderation";
import type { ServerView } from "./serverView";

const now = Date.parse("2026-09-27T12:00:00Z");

function role(id: string, permissions: Permission[]): Role {
  return { id, name: id, position: 0, permissions, createdAt: "2026-01-01T00:00:00Z" };
}

const roles = {
  user: role("user", ["SEND_MESSAGES", "CREATE_INVITE"]),
  mod: role("mod", ["SEND_MESSAGES", "CREATE_INVITE", "TIMEOUT_MEMBERS", "BAN_MEMBERS"]),
  admin: role("admin", ["SEND_MESSAGES", "CREATE_INVITE", "TIMEOUT_MEMBERS", "BAN_MEMBERS", "MANAGE_ROLES"]),
};

function account(id: string, roleIds: string[], changes: Partial<Account> = {}): Account {
  return { id, username: id, displayName: id, roleIds, createdAt: "2026-01-01T00:00:00Z", ...changes };
}

/** The view of `me`, holding the permissions of their roles, or all of them as the owner. */
function viewOf(me: Account, owner = false): ServerView {
  const permissions = [...new Set(me.roleIds.flatMap((id) => roles[id as keyof typeof roles].permissions))];
  return {
    account: me,
    permissions: { owner, permissions },
    info: {
      name: "Snatter",
      version: "0.1.0",
      protocol: { version: "1.0", minClient: "1.0" },
      community: { name: "Test" },
      registration: { mode: "open", challengeRequired: false, setupRequired: false },
      voice: { defaultBitrate: 64000 },
      ownerId: "boss",
    },
    roles,
    members: {},
    online: {},
    channels: {},
    typing: {},
    reading: {},
  };
}

const mod = viewOf(account("mod", ["mod"]));
const admin = viewOf(account("admin", ["admin"]));
const boss = viewOf(account("boss", ["user"]), true);

describe("outranks", () => {
  it("lets a member act on those whose roles grant nothing they lack", () => {
    expect(outranks(mod, account("member", ["user"]))).toBe(true);
    expect(outranks(mod, account("nobody", []))).toBe(true);
    expect(outranks(mod, account("peer", ["mod"]))).toBe(true);
    expect(outranks(mod, account("above", ["admin"]))).toBe(false);
  });

  it("never on themselves or the owner, while the owner outranks everyone else", () => {
    expect(outranks(mod, mod.account)).toBe(false);
    expect(outranks(admin, account("boss", ["user"]))).toBe(false);
    expect(outranks(boss, account("above", ["admin"]))).toBe(true);
    expect(outranks(boss, boss.account)).toBe(false);
  });

  it("counts roles, not a timeout", () => {
    const timedOut = account("above", ["admin"], { timedOutUntil: "2026-09-28T00:00:00Z" });
    expect(outranks(mod, timedOut)).toBe(false);
  });
});

describe("canAssign", () => {
  it("needs MANAGE_ROLES and everything the role grants, unless the owner", () => {
    expect(canAssign(mod, roles.user)).toBe(false);
    expect(canAssign(admin, roles.mod)).toBe(true);
    expect(canAssign(admin, role("server", ["MANAGE_SERVER"]))).toBe(false);
    expect(canAssign(boss, role("server", ["MANAGE_SERVER"]))).toBe(true);
  });
});

describe("moderationOf", () => {
  it("lets MANAGE_MESSAGES delete anyone's recent messages, rank aside", () => {
    const cleaner: ServerView = {
      ...viewOf(account("cleaner", [])),
      permissions: { owner: false, permissions: ["MANAGE_MESSAGES"] },
    };
    expect(moderationOf(cleaner, account("above", ["admin"]), now).deleteMessages).toBe(true);
    expect(moderationOf(boss, boss.account, now).deleteMessages).toBe(true);
    expect(moderationOf(mod, account("member", ["user"]), now).deleteMessages).toBe(false);
  });

  it("offers what the viewer may do", () => {
    expect(moderationOf(mod, account("member", ["user"]), now)).toEqual({
      editRoles: false,
      timeOut: true,
      endTimeout: false,
      ban: true,
      liftBan: false,
      deleteMessages: false,
    });
    expect(moderationOf(viewOf(account("plain", ["user"])), account("member", ["user"]), now)).toEqual({
      editRoles: false,
      timeOut: false,
      endTimeout: false,
      ban: false,
      liftBan: false,
      deleteMessages: false,
    });
  });

  it("offers ending a running timeout instead of starting one", () => {
    const timedOut = account("member", ["user"], { timedOutUntil: "2026-09-27T13:00:00Z" });
    expect(moderationOf(mod, timedOut, now)).toMatchObject({ timeOut: false, endTimeout: true });
    // The view keeps a timeout's end after it passes.
    expect(moderationOf(mod, timedOut, Date.parse("2026-09-27T14:00:00Z"))).toMatchObject({
      timeOut: true,
      endTimeout: false,
    });
  });

  it("offers lifting a ban, whoever was banned, and nothing else of the kind", () => {
    const banned = account("above", ["admin"], { bannedAt: "2026-09-27T11:00:00Z" });
    expect(moderationOf(mod, banned, now)).toMatchObject({
      timeOut: false,
      endTimeout: false,
      ban: false,
      liftBan: true,
    });
  });
});

describe("isTimedOut", () => {
  it("ends when the time passes", () => {
    expect(isTimedOut(account("m", [], { timedOutUntil: "2026-09-27T12:00:01Z" }), now)).toBe(true);
    expect(isTimedOut(account("m", [], { timedOutUntil: "2026-09-27T12:00:00Z" }), now)).toBe(false);
    expect(isTimedOut(account("m", []), now)).toBe(false);
  });
});

describe("roleChanges", () => {
  it("gives what is missing and takes away what was not chosen", () => {
    expect(roleChanges(account("m", ["user", "mod"]), ["user", "admin"])).toEqual({ add: ["admin"], remove: ["mod"] });
    expect(roleChanges(account("m", ["user"]), ["user"])).toEqual({ add: [], remove: [] });
  });
});
