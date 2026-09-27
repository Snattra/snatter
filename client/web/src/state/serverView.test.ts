import { describe, expect, it } from "vitest";
import type { Account, Channel, GatewayServerFrame, Message } from "../api/types";
import {
  TYPING_SHOWN_MS,
  applyFrame,
  canInvite,
  fromReady,
  hasUnread,
  sortedChannels,
  sortedRoles,
  typingIn,
} from "./serverView";

type Ready = Extract<GatewayServerFrame, { type: "ready" }>;
type Event = Exclude<GatewayServerFrame, Ready>;

const me = account("me", "Me");
const bob = account("bob", "Bob");

function account(id: string, displayName: string): Account {
  return { id, username: id, displayName, roleIds: [], createdAt: "2026-01-01T00:00:00Z" };
}

function channel(id: string, position: number): Channel {
  return { id, type: "text", name: id, position, requiredRoleIds: [], createdAt: "2026-01-01T00:00:00Z" };
}

/** Message ids that order by their number, as the server's do by time. */
function messageId(n: number): string {
  return `00000000-0000-7000-8000-${String(n).padStart(12, "0")}`;
}

function message(n: number, channelId: string, authorId: string): Message {
  return { kind: "user", id: messageId(n), channelId, authorId, content: "hi", createdAt: "2026-01-01T00:00:00Z" };
}

function ready(): Ready {
  return {
    type: "ready",
    seq: 1,
    account: me,
    permissions: { owner: false, permissions: ["SEND_MESSAGES"] },
    server: {
      name: "Snatter",
      version: "0.1.0",
      apiVersion: 1,
      community: { name: "Test" },
      registration: { mode: "open", challengeRequired: false, setupRequired: false },
      voice: { defaultBitrate: 64000, maxBitrate: 256000 },
    },
    roles: [],
    members: [me, bob],
    presences: [{ accountId: "me", status: "online" }],
    channels: [channel("b", 1), channel("a", 0)],
    readStates: [
      { channelId: "a", lastReadMessageId: messageId(2), lastMessageId: messageId(2) },
      { channelId: "b", lastReadMessageId: messageId(1), lastMessageId: messageId(3) },
    ],
  };
}

function apply(...frames: Event[]) {
  return frames.reduce((view, frame) => applyFrame(view, frame, 1_000), fromReady(ready()));
}

describe("serverView", () => {
  it("starts from ready", () => {
    const view = fromReady(ready());
    expect(view.account.id).toBe("me");
    expect(Object.keys(view.members)).toEqual(["me", "bob"]);
    expect(view.online).toEqual({ me: true });
    expect(sortedChannels(view).map((c) => c.id)).toEqual(["a", "b"]);
  });

  it("follows channels", () => {
    const view = apply(
      { type: "channel_created", seq: 2, channel: channel("c", 2) },
      { type: "channel_updated", seq: 3, channel: { ...channel("c", 0), name: "renamed" } },
      { type: "channel_deleted", seq: 4, channelId: "a" },
    );
    expect(sortedChannels(view).map((c) => c.name)).toEqual(["renamed", "b"]);
    expect(view.reading["a"]).toBeUndefined();
  });

  it("lists roles highest first", () => {
    const role = (id: string, position: number) => ({
      id,
      name: id,
      position,
      permissions: [],
      createdAt: "2026-01-01T00:00:00Z",
    });
    const view = apply(
      { type: "role_created", seq: 2, role: role("user", 0) },
      { type: "role_created", seq: 3, role: role("admin", 2) },
      { type: "role_created", seq: 4, role: role("moderator", 1) },
    );
    expect(sortedRoles(view).map((r) => r.id)).toEqual(["admin", "moderator", "user"]);
  });

  it("follows presence", () => {
    const view = apply(
      { type: "presence_updated", seq: 2, presence: { accountId: "bob", status: "online" } },
      { type: "presence_updated", seq: 3, presence: { accountId: "me", status: "offline" } },
    );
    expect(view.online).toEqual({ bob: true });
  });

  it("updates the member themselves along with the member list", () => {
    const view = apply({ type: "member_updated", seq: 2, member: { ...me, displayName: "Renamed" } });
    expect(view.account.displayName).toBe("Renamed");
    expect(view.members["me"]?.displayName).toBe("Renamed");
  });

  it("shows typing until it expires or a message arrives", () => {
    const typing = apply({ type: "typing_started", seq: 2, channelId: "a", accountId: "bob" });
    expect(typingIn(typing, "a", 1_000).map((m) => m.id)).toEqual(["bob"]);
    expect(typingIn(typing, "a", 1_000 + TYPING_SHOWN_MS)).toEqual([]);

    const sent = applyFrame(typing, { type: "message_created", seq: 3, message: message(4, "a", "bob") }, 1_000);
    expect(typingIn(sent, "a", 1_000)).toEqual([]);
  });

  it("knows which channels have unread messages", () => {
    const view = fromReady(ready());
    expect(hasUnread(view, "a")).toBe(false);
    expect(hasUnread(view, "b")).toBe(true);

    const others = apply({ type: "message_created", seq: 2, message: message(4, "a", "bob") });
    expect(hasUnread(others, "a")).toBe(true);

    // The member's own messages are read as they arrive.
    const own = apply({ type: "message_created", seq: 2, message: message(4, "a", "me") });
    expect(hasUnread(own, "a")).toBe(false);
  });

  it("moves read markers forward only", () => {
    const read = apply({
      type: "read_state_updated",
      seq: 2,
      readState: { channelId: "b", lastReadMessageId: messageId(3), lastMessageId: messageId(3) },
    });
    expect(hasUnread(read, "b")).toBe(false);

    const stale = applyFrame(
      read,
      { type: "read_state_updated", seq: 3, readState: { channelId: "b", lastReadMessageId: messageId(1), lastMessageId: messageId(3) } },
      1_000,
    );
    expect(stale.reading["b"]).toEqual({ lastRead: messageId(3), last: messageId(3) });
  });

  it("starts reading a channel that becomes visible", () => {
    const view = apply(
      { type: "channel_created", seq: 2, channel: channel("c", 2) },
      { type: "read_state_updated", seq: 3, readState: { channelId: "c", lastReadMessageId: null, lastMessageId: null } },
      { type: "message_created", seq: 4, message: message(5, "c", "bob") },
    );
    expect(hasUnread(view, "c")).toBe(true);
  });

  it("offers invites only while registration is invite only, to members allowed to create them", () => {
    const open = fromReady(ready());
    const inviteOnly = { ...open.info, registration: { ...open.info.registration, mode: "invite_only" as const } };
    const closed = applyFrame(open, { type: "server_updated", seq: 2, server: inviteOnly }, 1_000);
    const allowed = applyFrame(
      closed,
      { type: "permissions_changed", seq: 3, permissions: { owner: false, permissions: ["CREATE_INVITE"] } },
      1_000,
    );
    const owner = applyFrame(closed, { type: "permissions_changed", seq: 3, permissions: { owner: true, permissions: [] } }, 1_000);
    const reopened = applyFrame(allowed, { type: "server_updated", seq: 4, server: open.info }, 1_000);

    expect(canInvite(closed)).toBe(false);
    expect(canInvite(allowed)).toBe(true);
    expect(canInvite(owner)).toBe(true);
    expect(canInvite(reopened)).toBe(false);
  });
});
