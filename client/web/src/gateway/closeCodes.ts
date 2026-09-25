import type { GatewayCloseReason } from "../api/types";

/** The close codes of the contract's {@code GatewayCloseReason}; adding one there fails the build here. */
const CODES = {
  invalid_frame: 4000,
  not_identified: 4001,
  identify_timeout: 4002,
  authentication_failed: 4003,
  already_identified: 4004,
  session_ended: 4005,
  too_slow: 4006,
  banned: 4007,
} as const satisfies Record<GatewayCloseReason, number>;

const REASONS = new Map<number, GatewayCloseReason>(
  Object.entries(CODES).map(([reason, code]) => [code, reason as GatewayCloseReason]),
);

export type CloseAction = { kind: "reconnect" } | { kind: "end"; reason: GatewayCloseReason };

/**
 * What to do when the connection closes. Network trouble, a server going
 * away, falling behind or a slow identify are worth a reconnect. The session
 * being gone or refused ends it; so do protocol errors, which a reconnect
 * would only repeat.
 */
export function closeAction(code: number): CloseAction {
  const reason = REASONS.get(code);
  if (reason === undefined || reason === "identify_timeout" || reason === "too_slow") {
    return { kind: "reconnect" };
  }
  return { kind: "end", reason };
}
