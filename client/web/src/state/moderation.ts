import type { Account, Permission, Role } from "../api/types";
import { type ServerView, can } from "./serverView";

/** How long a timeout can be, up to the 28 days the server allows. */
export const TIMEOUT_LENGTHS: { seconds: number; label: string }[] = [
  { seconds: 60, label: "1 minute" },
  { seconds: 5 * 60, label: "5 minutes" },
  { seconds: 60 * 60, label: "1 hour" },
  { seconds: 24 * 60 * 60, label: "1 day" },
  { seconds: 7 * 24 * 60 * 60, label: "1 week" },
  { seconds: 28 * 24 * 60 * 60, label: "28 days" },
];

/** What the member looking at someone's profile may do to them. */
export interface Moderation {
  editRoles: boolean;
  timeOut: boolean;
  endTimeout: boolean;
  ban: boolean;
  liftBan: boolean;
  /** Turn their microphone off for everyone, or back on. */
  mute: boolean;
  unmute: boolean;
  /** Delete what they sent recently, in the channels the viewer sees. Anyone's, as deleting a single message is. */
  deleteMessages: boolean;
}

/** How far back deleting a member's messages can reach, offered in the profile. */
export const PURGE_LENGTHS: { seconds: number; label: string }[] = [
  { seconds: 60 * 60, label: "The last hour" },
  { seconds: 24 * 60 * 60, label: "The last day" },
  { seconds: 7 * 24 * 60 * 60, label: "The last week" },
];

export function isOwner(view: ServerView, member: Account): boolean {
  return view.info.ownerId != null && view.info.ownerId === member.id;
}

export function isBanned(member: Pick<Account, "bannedAt">): boolean {
  return member.bannedAt != null;
}

/** Whether a moderator turned the member's microphone off. */
export function isMuted(member: Pick<Account, "mutedAt">): boolean {
  return member.mutedAt != null;
}

/** Whether a timeout is running; the view keeps its end after it passes. {@code now} is in epoch milliseconds. */
export function isTimedOut(member: Pick<Account, "timedOutUntil">, now: number): boolean {
  return member.timedOutUntil != null && Date.parse(member.timedOutUntil) > now;
}

/**
 * What the viewer's roles grant, which rank and role management go by as the
 * server's do: a timeout or mute takes nothing off it.
 */
function granted(view: ServerView): Set<Permission> {
  return new Set(view.account.roleIds.flatMap((id) => view.roles[id]?.permissions ?? []));
}

/**
 * Whether the viewer may time out, mute or ban the member, as the server
 * decides: never themselves or the owner, and otherwise only someone whose
 * roles grant nothing the viewer's roles lack. The owner outranks everyone
 * else, and a timeout or mute lowers no one's rank, as it is the roles that
 * count.
 */
export function outranks(view: ServerView, member: Account): boolean {
  if (member.id === view.account.id || isOwner(view, member)) {
    return false;
  }
  if (view.permissions.owner) {
    return true;
  }
  const held = granted(view);
  return member.roleIds.every((id) => (view.roles[id]?.permissions ?? []).every((permission) => held.has(permission)));
}

/** Whether the viewer may give or take away the role: with `MANAGE_ROLES`, and their roles granting all it grants unless they are the owner. */
export function canAssign(view: ServerView, role: Role): boolean {
  if (!can(view, "MANAGE_ROLES")) {
    return false;
  }
  const held = granted(view);
  return view.permissions.owner || role.permissions.every((permission) => held.has(permission));
}

/** The actions to offer on a member's profile. {@code now} is in epoch milliseconds. */
export function moderationOf(view: ServerView, member: Account, now: number): Moderation {
  const rank = outranks(view, member);
  const banned = isBanned(member);
  const timedOut = isTimedOut(member, now);
  const timeouts = rank && can(view, "TIMEOUT_MEMBERS") && !banned;
  const mutes = rank && can(view, "MUTE_MEMBERS") && !banned;
  return {
    editRoles: Object.values(view.roles).some((role) => canAssign(view, role)),
    timeOut: timeouts && !timedOut,
    endTimeout: timeouts && timedOut,
    ban: rank && can(view, "BAN_MEMBERS") && !banned,
    liftBan: can(view, "BAN_MEMBERS") && banned,
    mute: mutes && !isMuted(member),
    unmute: mutes && isMuted(member),
    deleteMessages: can(view, "MANAGE_MESSAGES"),
  };
}

/** The roles to give and to take away so the member holds exactly the chosen ones. */
export function roleChanges(member: Account, chosen: string[]): { add: string[]; remove: string[] } {
  return {
    add: chosen.filter((id) => !member.roleIds.includes(id)),
    remove: member.roleIds.filter((id) => !chosen.includes(id)),
  };
}
