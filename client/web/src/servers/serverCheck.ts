import type { ServerInfo } from "../api/types";
import { blockedAsInsecure } from "../state/serverAddress";
import { type Compatibility, type Outdated, compatibility, isOutdated } from "../state/protocol";

/** How long a server gets to answer before it counts as unreachable. */
const CHECK_TIMEOUT_MS = 10_000;

/**
 * What asking a server about itself found, before it is added:
 * - `ok`: a Snatter server this app can talk to
 * - `outdated`: a Snatter server too far from this app in version
 * - `insecure`: plain HTTP, which the browser will not reach from this page
 * - `unreachable`: no answer, or one the browser would not let the app read,
 *   as when the server does not let this app connect
 * - `not_snatter`: an answer, but not from a Snatter server
 */
export type ServerCheck =
  | { kind: "ok"; origin: string; info: ServerInfo; compatibility: Exclude<Compatibility, Outdated> }
  | { kind: "outdated"; origin: string; info: ServerInfo; outdated: Outdated }
  | { kind: "insecure"; origin: string }
  | { kind: "unreachable"; origin: string }
  | { kind: "not_snatter"; origin: string };

export interface CheckEnvironment {
  fetch: typeof fetch;
  /** The page's own `location.protocol`. */
  pageProtocol: string;
}

const browser: CheckEnvironment = {
  fetch: (input, init) => fetch(input, init),
  pageProtocol: typeof location === "undefined" ? "https:" : location.protocol,
};

/** Asks the server at the origin what it is, as the app does before adding it. */
export async function checkServer(origin: string, environment: CheckEnvironment = browser): Promise<ServerCheck> {
  if (blockedAsInsecure(origin, environment.pageProtocol)) {
    return { kind: "insecure", origin };
  }
  let response: Response;
  try {
    response = await environment.fetch(`${origin}/api/v1/server-info`, {
      credentials: "omit",
      signal: AbortSignal.timeout(CHECK_TIMEOUT_MS),
    });
  } catch {
    return { kind: "unreachable", origin };
  }
  if (!response.ok) {
    return { kind: "not_snatter", origin };
  }
  let info: unknown;
  try {
    info = await response.json();
  } catch {
    return { kind: "not_snatter", origin };
  }
  if (!isServerInfo(info)) {
    return { kind: "not_snatter", origin };
  }
  const compatible = compatibility(info.protocol);
  if (isOutdated(compatible)) {
    return { kind: "outdated", origin, info, outdated: compatible };
  }
  return { kind: "ok", origin, info, compatibility: compatible };
}

/** Enough of `ServerInfo` to tell a Snatter server and show it; the rest is taken on trust. */
function isServerInfo(value: unknown): value is ServerInfo {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const info = value as Partial<ServerInfo>;
  return (
    info.name === "Snatter" &&
    typeof info.community === "object" &&
    info.community !== null &&
    typeof info.community.name === "string" &&
    (info.community.description === undefined ||
      info.community.description === null ||
      typeof info.community.description === "string") &&
    typeof info.registration === "object" &&
    info.registration !== null
  );
}
