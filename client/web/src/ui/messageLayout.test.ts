import { describe, expect, it } from "vitest";
import type { Message } from "../api/types";
import { type ChannelLog, emptyLog } from "../state/channelLog";
import { layoutRows, unreadAfter } from "./messageLayout";

function messageId(n: number): string {
  return `00000000-0000-7000-8000-${String(n).padStart(12, "0")}`;
}

function user(n: number, authorId: string, minute: number): Message {
  const createdAt = new Date(2026, 8, 27, 18, minute).toISOString();
  return { kind: "user", id: messageId(n), channelId: "c", authorId, content: `m${n}`, mentions: [], createdAt };
}

function log(messages: Message[], hasOlder = false): ChannelLog {
  return { ...emptyLog, loaded: true, messages, hasOlder };
}

const shape = (rows: ReturnType<typeof layoutRows>) =>
  rows.map((row) => (row.kind === "divider" ? "divider" : row.kind === "system" ? "system" : row.head ? "head" : "follow"));

describe("layoutRows", () => {
  it("groups one author's messages until someone else, a pause or a notice", () => {
    const notice: Message = {
      kind: "system",
      id: messageId(5),
      channelId: "c",
      authorId: "bob",
      notice: { type: "channel_renamed", from: "a", to: "b" },
      createdAt: new Date(2026, 8, 27, 18, 30).toISOString(),
    };
    const rows = layoutRows(
      log([user(1, "bob", 0), user(2, "bob", 1), user(3, "teal", 2), user(4, "teal", 20), notice, user(6, "teal", 31)]),
      "me",
      null,
    );
    expect(shape(rows)).toEqual(["head", "follow", "head", "head", "system", "head"]);
  });

  it("starts a group under the divider and puts pending messages last", () => {
    const channel = {
      ...log([user(1, "bob", 0), user(2, "bob", 1)]),
      pending: [{ nonce: "n", content: "hi", createdAt: new Date(2026, 8, 27, 18, 2).toISOString(), error: null }],
    };
    expect(shape(layoutRows(channel, "me", messageId(2)))).toEqual(["head", "divider", "head", "head"]);
  });
});

describe("layoutRows with deleted messages", () => {
  it("keeps a deleted message in its author's group", () => {
    const ghost: Message = {
      kind: "deleted",
      id: messageId(2),
      channelId: "c",
      authorId: "ann",
      createdAt: new Date(2026, 8, 27, 18, 1).toISOString(),
      deletedAt: new Date(2026, 8, 27, 18, 5).toISOString(),
      removedByModerator: false,
    };
    const rows = layoutRows(log([user(1, "ann", 0), ghost, user(3, "ann", 2)]), "me", null);
    expect(rows.map((row) => row.kind)).toEqual(["message", "deleted", "message"]);
    expect(shape(rows)).toEqual(["head", "follow", "follow"]);
  });
});

describe("layoutRows folding deleted messages", () => {
  function ghost(n: number, authorId: string, minute: number, removedByModerator = true): Message {
    const createdAt = new Date(2026, 8, 27, 18, minute).toISOString();
    return { kind: "deleted", id: messageId(n), channelId: "c", authorId, createdAt, deletedAt: createdAt, removedByModerator };
  }
  const folded = (rows: ReturnType<typeof layoutRows>) =>
    rows.map((row) => (row.kind === "deleted" ? row.messages.length : row.kind));

  it("puts one author's deleted messages in a row together, however far apart", () => {
    const rows = layoutRows(log([ghost(1, "ann", 0), ghost(2, "ann", 30), ghost(3, "ann", 59)]), "me", null);
    expect(folded(rows)).toEqual([3]);
  });

  it("keeps apart different authors, different ways of deleting, and anything in between", () => {
    const rows = layoutRows(
      log([
        ghost(1, "ann", 0),
        ghost(2, "ann", 1, false),
        ghost(3, "bob", 2),
        ghost(4, "bob", 3),
        user(5, "bob", 4),
        ghost(6, "bob", 5),
        ghost(7, "bob", 6),
      ]),
      "me",
      messageId(7),
    );
    expect(folded(rows)).toEqual([1, 1, 2, "message", 1, "divider", 1]);
  });
});

describe("unreadAfter", () => {
  it("counts others' messages after the anchor", () => {
    const channel = log([user(1, "bob", 0), user(2, "me", 1), user(3, "bob", 2), user(4, "bob", 3)]);
    expect(unreadAfter(channel, messageId(1), "me")).toEqual({ firstId: messageId(3), count: 2, complete: true });
    expect(unreadAfter(channel, messageId(4), "me")).toEqual({ firstId: null, count: 0, complete: true });
    expect(unreadAfter(channel, null, "me").firstId).toBe(messageId(1));
  });

  it("knows when unread messages may be older than those held", () => {
    const channel = log([user(5, "bob", 0), user(6, "bob", 1)], true);
    expect(unreadAfter(channel, messageId(2), "me")).toEqual({ firstId: null, count: 2, complete: false });
    expect(unreadAfter(channel, null, "me").complete).toBe(false);
    expect(unreadAfter(channel, messageId(5), "me")).toEqual({ firstId: messageId(6), count: 1, complete: true });
  });
});
