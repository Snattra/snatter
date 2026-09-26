import type { Message } from "../api/types";
import { isAfter } from "./ids";

/** How many messages one page fetches. */
export const PAGE_SIZE = 50;

/** A message the member sent that the server has not confirmed yet. */
export interface Pending {
  nonce: string;
  content: string;
  createdAt: string;
  /** Why sending failed, or null while it is on its way. */
  error: string | null;
}

/**
 * The messages the app holds for one channel: the newest ones without gaps,
 * oldest first, and the member's messages still on their way. Pure data, so
 * it is replaced, not mutated.
 */
export interface ChannelLog {
  /** False until the first page arrives; messages that come live meanwhile are kept. */
  loaded: boolean;
  messages: Message[];
  /** Ids of messages that arrived while the channel was held, which rise in when shown. */
  live: Record<string, true>;
  pending: Pending[];
  /** Whether the server has messages before the first one here. */
  hasOlder: boolean;
}

export const emptyLog: ChannelLog = { loaded: false, messages: [], live: {}, pending: [], hasOlder: false };

/** The newest page arrived; anything that came live while it loaded stays. */
export function withLatest(log: ChannelLog, page: Message[], limit: number): ChannelLog {
  return { ...log, loaded: true, messages: merge(page, log.messages), hasOlder: page.length === limit };
}

/** The page just before the first message arrived. */
export function withOlder(log: ChannelLog, page: Message[], limit: number): ChannelLog {
  return { ...log, messages: merge(page, log.messages), hasOlder: page.length === limit };
}

/**
 * A message the server stored or sent again. It replaces the member's
 * pending copy with the same nonce, and a message already held. One from
 * before the held messages is left out, so the log keeps no gaps.
 */
export function withMessage(log: ChannelLog, message: Message, live: boolean): ChannelLog {
  const nonce = message.kind === "user" ? message.nonce : null;
  const pending = nonce ? log.pending.filter((p) => p.nonce !== nonce) : log.pending;
  const first = log.messages[0];
  const known = log.messages.some((m) => m.id === message.id);
  if (!known && log.hasOlder && first !== undefined && isAfter(first.id, message.id)) {
    return pending === log.pending ? log : { ...log, pending };
  }
  return {
    ...log,
    messages: merge(log.messages, [message]),
    live: live && !known ? { ...log.live, [message.id]: true } : log.live,
    pending,
  };
}

/** A held message changed; one that is not held stays unknown. */
export function withUpdated(log: ChannelLog, message: Message): ChannelLog {
  if (!log.messages.some((m) => m.id === message.id)) {
    return log;
  }
  return { ...log, messages: log.messages.map((m) => (m.id === message.id ? message : m)) };
}

export function withDeleted(log: ChannelLog, messageId: string): ChannelLog {
  if (!log.messages.some((m) => m.id === messageId)) {
    return log;
  }
  return { ...log, messages: log.messages.filter((m) => m.id !== messageId) };
}

export function withPending(log: ChannelLog, pending: Pending): ChannelLog {
  return { ...log, pending: [...log.pending.filter((p) => p.nonce !== pending.nonce), pending] };
}

/** Sending failed; the message stays, marked, until it is sent again or discarded. */
export function withFailed(log: ChannelLog, nonce: string, error: string): ChannelLog {
  return { ...log, pending: log.pending.map((p) => (p.nonce === nonce ? { ...p, error } : p)) };
}

export function withoutPending(log: ChannelLog, nonce: string): ChannelLog {
  return { ...log, pending: log.pending.filter((p) => p.nonce !== nonce) };
}

/** Both lists by id, oldest first, the second one's copy winning. */
function merge(a: Message[], b: Message[]): Message[] {
  const byId = new Map<string, Message>();
  for (const message of [...a, ...b]) {
    byId.set(message.id, message);
  }
  return [...byId.values()].sort((x, y) => (x.id === y.id ? 0 : isAfter(x.id, y.id) ? 1 : -1));
}
