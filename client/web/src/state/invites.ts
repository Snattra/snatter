import type { Invite } from "../api/types";

/** How long a new invite link works: a week, so a link shared in passing does not stay open for good. */
export const INVITE_LIFETIME_SECONDS = 7 * 24 * 60 * 60;

/** How long an invite must still run to be handed out again rather than replaced by a new one. */
const REUSE_MIN_REMAINING_MS = 24 * 60 * 60 * 1000;

/** The invite code in an invite link's path (`/invite/k3Jd8QzP`), or null for any other path. */
export function inviteCodeIn(pathname: string): string | null {
  return /^\/invite\/([A-Za-z0-9]{8})\/?$/.exec(pathname)?.[1] ?? null;
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
