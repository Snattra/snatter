import type {
  Account,
  Channel,
  GatewayServerFrame,
  PermissionSet,
  ReadState,
  Role,
  ServerInfo,
} from "../api/types";
import { isAfter, later } from "./ids";

type Ready = Extract<GatewayServerFrame, { type: "ready" }>;
type Event = Exclude<GatewayServerFrame, Ready>;

/** How long another member shows as typing after their last `typing_started`. */
export const TYPING_SHOWN_MS = 10_000;

/** How far the member has read a channel, and its newest message; null ids mean none. */
export interface Reading {
  lastRead: string | null;
  last: string | null;
}

/**
 * One server as the signed-in member sees it: built from `ready` and kept
 * current by the gateway's events. Pure data, so it is replaced, not mutated.
 */
export interface ServerView {
  account: Account;
  permissions: PermissionSet;
  info: ServerInfo;
  roles: Record<string, Role>;
  members: Record<string, Account>;
  online: Record<string, true>;
  channels: Record<string, Channel>;
  /** Channel id to typing account id to when its indicator ends, in epoch milliseconds. */
  typing: Record<string, Record<string, number>>;
  /** By channel id, for the channels that keep messages. */
  reading: Record<string, Reading>;
}

export function fromReady(ready: Ready): ServerView {
  return {
    account: ready.account,
    permissions: ready.permissions,
    info: ready.server,
    roles: byId(ready.roles),
    members: byId(ready.members),
    online: Object.fromEntries(
      ready.presences.filter((p) => p.status === "online").map((p) => [p.accountId, true as const]),
    ),
    channels: byId(ready.channels),
    typing: {},
    reading: Object.fromEntries(ready.readStates.map((state) => [state.channelId, fromReadState(state)])),
  };
}

function fromReadState(state: ReadState): Reading {
  return { lastRead: state.lastReadMessageId ?? null, last: state.lastMessageId ?? null };
}

/** The view after one event; {@code now} is the current time in epoch milliseconds. */
export function applyFrame(view: ServerView, frame: Event, now: number): ServerView {
  switch (frame.type) {
    case "server_updated":
      return { ...view, info: frame.server };
    case "permissions_changed":
      return { ...view, permissions: frame.permissions };
    case "member_joined":
    case "member_updated": {
      const member = frame.member;
      const account = member.id === view.account.id ? member : view.account;
      return { ...view, account, members: { ...view.members, [member.id]: member } };
    }
    case "presence_updated": {
      const { accountId, status } = frame.presence;
      const online = { ...view.online };
      if (status === "online") {
        online[accountId] = true;
      } else {
        delete online[accountId];
      }
      return { ...view, online };
    }
    case "role_created":
    case "role_updated":
      return { ...view, roles: { ...view.roles, [frame.role.id]: frame.role } };
    case "role_deleted":
      return { ...view, roles: without(view.roles, frame.roleId) };
    case "channel_created":
    case "channel_updated":
      return { ...view, channels: { ...view.channels, [frame.channel.id]: frame.channel } };
    case "channel_deleted":
      return {
        ...view,
        channels: without(view.channels, frame.channelId),
        typing: without(view.typing, frame.channelId),
        reading: without(view.reading, frame.channelId),
      };
    case "typing_started": {
      const current = Object.entries(view.typing[frame.channelId] ?? {}).filter(([, until]) => until > now);
      const inChannel = { ...Object.fromEntries(current), [frame.accountId]: now + TYPING_SHOWN_MS };
      return { ...view, typing: { ...view.typing, [frame.channelId]: inChannel } };
    }
    case "message_created": {
      const { id, channelId, authorId } = frame.message;
      // The member's own messages are read as they are sent.
      const before = view.reading[channelId] ?? { lastRead: null, last: null };
      const reading = {
        ...view.reading,
        [channelId]: {
          lastRead: authorId === view.account.id ? later(before.lastRead, id) : before.lastRead,
          last: later(before.last, id),
        },
      };
      // A message ends its author's typing there.
      const inChannel = view.typing[channelId];
      if (authorId == null || inChannel?.[authorId] === undefined) {
        return { ...view, reading };
      }
      return { ...view, reading, typing: { ...view.typing, [channelId]: without(inChannel, authorId) } };
    }
    case "read_state_updated": {
      const state = fromReadState(frame.readState);
      const before = view.reading[frame.readState.channelId];
      const merged = before
        ? { lastRead: later(before.lastRead, state.lastRead), last: later(before.last, state.last) }
        : state;
      return { ...view, reading: { ...view.reading, [frame.readState.channelId]: merged } };
    }
    case "message_updated":
    case "message_deleted":
      return view;
  }
}

/** The channels in list order, top first. */
export function sortedChannels(view: ServerView): Channel[] {
  return Object.values(view.channels).sort((a, b) => a.position - b.position);
}

/** Whether the channel has messages newer than the member has read. */
export function hasUnread(view: ServerView, channelId: string): boolean {
  const reading = view.reading[channelId];
  return reading?.last != null && isAfter(reading.last, reading.lastRead);
}

/** Whether the member may send messages; a timeout empties their permissions. */
export function canSend(view: ServerView): boolean {
  return view.permissions.owner || view.permissions.permissions.includes("SEND_MESSAGES");
}

/** Who is typing in a channel right now, other than the member themselves. */
export function typingIn(view: ServerView, channelId: string, now: number): Account[] {
  return Object.entries(view.typing[channelId] ?? {})
    .filter(([id, until]) => until > now && id !== view.account.id)
    .flatMap(([id]) => view.members[id] ?? []);
}

function byId<T extends { id: string }>(items: T[]): Record<string, T> {
  return Object.fromEntries(items.map((item) => [item.id, item]));
}

function without<T>(record: Record<string, T>, key: string): Record<string, T> {
  const copy = { ...record };
  delete copy[key];
  return copy;
}
