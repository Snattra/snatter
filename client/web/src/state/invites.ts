import type { Invite } from "../api/types";
import { serverOrigin } from "./serverAddress";

/** How long a new invite link works: a week, so a link shared in passing does not stay open for good. */
export const INVITE_LIFETIME_SECONDS = 7 * 24 * 60 * 60;

/** How long an invite must still run to be handed out again rather than replaced by a new one. */
const REUSE_MIN_REMAINING_MS = 24 * 60 * 60 * 1000;

/** The invite code in an invite link's path (`/invite/k3Jd8QzP`), or null for any other path. */
export function inviteCodeIn(pathname: string): string | null {
  return /^\/invite\/([A-Za-z0-9]{8})\/?$/.exec(pathname)?.[1] ?? null;
}

/** An invite to a server of anyone's, as the hosted app takes them. */
export interface HostedInvite {
  /** The server, as an origin. */
  origin: string;
  code: string;
}

/**
 * The invite of a link to the hosted app, `/invite#demo.snatter.app/k3Jd8QzP`:
 * the server's address, as people type it, then the code. Both are after
 * the `#`, so the host of the app never learns which server or invite. Null
 * for any other address.
 */
export function hostedInviteIn(pathname: string, hash: string): HostedInvite | null {
  if (!/^\/invite\/?$/.test(pathname)) {
    return null;
  }
  const match = /^#([^/\s]+)\/([A-Za-z0-9]{8})\/?$/.exec(hash);
  if (match === null) {
    return null;
  }
  let address: string;
  try {
    address = decodeURIComponent(match[1] ?? "");
  } catch {
    return null;
  }
  const origin = serverOrigin(address);
  return origin === null ? null : { origin, code: match[2] ?? "" };
}

/**
 * Whether an invite can be handed out again: not revoked, without a limit on
 * uses, and running for at least another day. {@code now} is in epoch milliseconds.
 */
export function stillReusable(invite: Invite, now: number): boolean {
  return (
    !invite.revoked &&
    invite.maxUses == null &&
    (invite.expiresAt == null || Date.parse(invite.expiresAt) - now >= REUSE_MIN_REMAINING_MS)
  );
}

/** The newest of the member's own invites that can be handed out again, from a list newest first. */
export function reusableInvite(invites: Invite[], accountId: string, now: number): Invite | null {
  return invites.find((invite) => invite.createdBy === accountId && stillReusable(invite, now)) ?? null;
}
