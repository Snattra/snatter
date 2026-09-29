import { describe, expect, it } from "vitest";
import type { Message } from "../api/types";
import {
  emptyLog,
  withDeleted,
  withFailed,
  withLatest,
  withMessage,
  withOlder,
  withPending,
  withPurged,
  withUpdated,
} from "./channelLog";

function messageId(n: number): string {
  return `00000000-0000-7000-8000-${String(n).padStart(12, "0")}`;
}

function message(n: number, extra: { nonce?: string; content?: string } = {}): Message {
  return {
    kind: "user",
    id: messageId(n),
    channelId: "c",
    authorId: "bob",
    content: extra.content ?? `message ${n}`,
    mentions: [],
    nonce: extra.nonce,
    createdAt: "2026-01-01T00:00:00Z",
  };
}

const ids = (log: { messages: Message[] }) => log.messages.map((m) => Number(m.id.slice(-12)));

describe("channelLog", () => {
  it("keeps messages that arrive while the first page loads", () => {
    const arrived = withMessage(emptyLog, message(4), true);
    const log = withLatest(arrived, [message(2), message(3), message(4)], 3);
    expect(log.loaded).toBe(true);
    expect(ids(log)).toEqual([2, 3, 4]);
    expect(log.hasOlder).toBe(true);
    expect(log.live).toEqual({ [messageId(4)]: true });
  });

  it("pages back until the start", () => {
    const log = withLatest(emptyLog, [message(3), message(4)], 2);
    const older = withOlder(log, [message(1), message(2)], 2);
    expect(ids(older)).toEqual([1, 2, 3, 4]);
    expect(older.hasOlder).toBe(true);
    expect(withOlder(older, [], 2).hasOlder).toBe(false);
  });

  it("marks only messages that arrive live, and treats a repeat as an update", () => {
    const log = withLatest(emptyLog, [message(1)], 50);
    const live = withMessage(log, message(2), true);
    expect(live.live).toEqual({ [messageId(2)]: true });
    const repeated = withMessage(live, message(1, { content: "again" }), true);
    expect(ids(repeated)).toEqual([1, 2]);
    expect(repeated.messages[0]?.kind === "user" && repeated.messages[0].content).toBe("again");
    expect(repeated.live).toEqual({ [messageId(2)]: true });
  });

  it("leaves out a message from before the held ones while older ones are missing", () => {
    const log = withLatest(emptyLog, [message(5), message(6)], 2);
    expect(ids(withMessage(log, message(3), true))).toEqual([5, 6]);
  });

  it("replaces a pending message with the stored one carrying its nonce", () => {
    const log = withPending(withLatest(emptyLog, [], 50), {
      nonce: "n1",
      content: "hello",
      createdAt: "2026-01-01T00:00:00Z",
      error: null,
    });
    const failed = withFailed(log, "n1", "Not sent");
    expect(failed.pending[0]?.error).toBe("Not sent");
    const stored = withMessage(failed, message(1, { nonce: "n1" }), true);
    expect(stored.pending).toEqual([]);
    expect(ids(stored)).toEqual([1]);
  });

  it("applies edits and deletions to held messages only", () => {
    const log = withLatest(emptyLog, [message(1), message(2)], 50);
    const edited = withUpdated(log, message(2, { content: "edited" }));
    expect(edited.messages[1]?.kind === "user" && edited.messages[1].content).toBe("edited");
    expect(withUpdated(log, message(9))).toBe(log);
    expect(ids(withDeleted(log, messageId(1)))).toEqual([2]);
  });
});

describe("withPurged", () => {
  const purge = {
    authorId: "bob",
    fromMessageId: messageId(2),
    toMessageId: messageId(4),
    deletedAt: "2026-01-02T00:00:00Z",
    removedByModerator: true,
  };

  it("turns the author's messages in the range into deleted ones, and nothing else", () => {
    const alices: Message = { ...message(3), id: messageId(3), authorId: "alice" };
    const log = withLatest(emptyLog, [message(1), message(2), alices, message(4), message(5)], 10);
    const purged = withPurged(log, purge);
    expect(purged.messages.map((m) => m.kind)).toEqual(["user", "deleted", "user", "deleted", "user"]);
    expect(purged.messages[1]).toEqual({
      kind: "deleted",
      id: messageId(2),
      channelId: "c",
      authorId: "bob",
      createdAt: "2026-01-01T00:00:00Z",
      deletedAt: "2026-01-02T00:00:00Z",
      removedByModerator: true,
    });
  });

  it("leaves a log without any of them as it is", () => {
    const log = withLatest(emptyLog, [message(7)], 10);
    expect(withPurged(log, purge)).toBe(log);
  });
});
