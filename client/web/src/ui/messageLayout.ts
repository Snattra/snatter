import type { DeletedMessage, SystemMessage, UserMessage } from "../api/types";
import type { ChannelLog, Pending } from "../state/channelLog";
import { isAfter } from "../state/ids";
import { sameDay } from "./time";

/** Messages from one author closer together than this share one head. */
const GROUP_GAP_MS = 7 * 60_000;

export type Row =
  | { kind: "divider" }
  | { kind: "message"; message: UserMessage; head: boolean }
  /**
   * Where one or more of a member's messages in a row were deleted, the same
   * way (all by them, or all by a moderator), oldest first. It groups with
   * its author's messages like any other.
   */
  | { kind: "deleted"; messages: [DeletedMessage, ...DeletedMessage[]]; head: boolean }
  | { kind: "system"; message: SystemMessage }
  | { kind: "pending"; pending: Pending; head: boolean };

/**
 * The rows of a channel, oldest first: its messages, the new-messages
 * divider above {@code firstUnreadId}, then the member's messages still on
 * their way. A message starts a new group, with avatar and name, unless it
 * follows one from the same author on the same day within a few minutes.
 * Deleted messages that follow each other, from one author and deleted the
 * same way, share one row however far apart they were sent, so a purge
 * leaves one line instead of a column of them.
 */
export function layoutRows(log: ChannelLog, me: string, firstUnreadId: string | null): Row[] {
  const rows: Row[] = [];
  let previous: { authorId: string; at: Date } | null = null;
  const follows = (authorId: string | null | undefined, at: Date) =>
    previous !== null &&
    authorId != null &&
    previous.authorId === authorId &&
    at.getTime() - previous.at.getTime() < GROUP_GAP_MS &&
    sameDay(previous.at, at);

  for (const message of log.messages) {
    if (message.id === firstUnreadId) {
      rows.push({ kind: "divider" });
      previous = null;
    }
    if (message.kind === "system") {
      rows.push({ kind: "system", message });
      previous = null;
      continue;
    }
    const at = new Date(message.createdAt);
    const last = rows.at(-1);
    if (
      message.kind === "deleted" &&
      last?.kind === "deleted" &&
      last.messages[0].authorId === message.authorId &&
      last.messages[0].removedByModerator === message.removedByModerator
    ) {
      last.messages.push(message);
      previous = message.authorId == null ? null : { authorId: message.authorId, at };
      continue;
    }
    const head = !follows(message.authorId, at);
    rows.push(
      message.kind === "deleted" ? { kind: "deleted", messages: [message], head } : { kind: "message", message, head },
    );
    previous = message.authorId == null ? null : { authorId: message.authorId, at };
  }
  for (const pending of log.pending) {
    const at = new Date(pending.createdAt);
    rows.push({ kind: "pending", pending, head: !follows(me, at) });
    previous = { authorId: me, at };
  }
  return rows;
}

export interface Unread {
  /** The first held message after the anchor that someone else wrote, if any. */
  firstId: string | null;
  /** How many of those are held. */
  count: number;
  /** False when older messages that may be unread are not held yet. */
  complete: boolean;
}

/**
 * What is unread after {@code anchor}, the newest message read when the
 * member last caught up (null: nothing read). The member's own messages are
 * never unread.
 */
export function unreadAfter(log: ChannelLog, anchor: string | null, me: string): Unread {
  const unread = log.messages.filter((m) => isAfter(m.id, anchor) && m.authorId !== me);
  const first = log.messages[0];
  const complete = !log.hasOlder || (first !== undefined && anchor !== null && !isAfter(first.id, anchor));
  return { firstId: complete ? (unread[0]?.id ?? null) : null, count: unread.length, complete };
}
