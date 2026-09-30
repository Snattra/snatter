import type { GatewayCloseReason } from "../api/types";

/** The close codes of the contract's {@code GatewayCloseReason}; adding one there fails the build here. */
const CODES = {
  invalid_frame: 4000,
  not_identified: 4001,
  authentication_failed: 4002,
  already_identified: 4003,
  session_ended: 4004,
  banned: 4005,
  client_outdated: 4006,
  identify_timeout: 4500,
  too_slow: 4501,
} as const satisfies Record<GatewayCloseReason, number>;

const REASONS = new Map<number, GatewayCloseReason>(
  Object.entries(CODES).map(([reason, code]) => [code, reason as GatewayCloseReason]),
);

/** A reason is null for a code from a newer server that this client does not know by name. */
export type CloseAction = { kind: "reconnect" } | { kind: "end"; reason: GatewayCloseReason | null };

/**
 * What to do when the connection closes, by the contract's ranges, so codes
 * added later are handled too. From 4000 to 4499 the server refused the
 * session or the client broke the protocol, and a reconnect would only
 * repeat that, so it ends. Anything else, such as network trouble, the server
 * going away, falling behind or a slow identify, is worth a reconnect.
 */
export function closeAction(code: number): CloseAction {
  if (code < 4000 || code > 4499) {
    return { kind: "reconnect" };
  }
  return { kind: "end", reason: REASONS.get(code) ?? null };
}
