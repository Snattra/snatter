import type { ServerInfo } from "../api/types";

/** The protocol version this client speaks: `info.version` of the contract it is built from, which a test checks. */
export const PROTOCOL_VERSION = "1.0";

/**
 * The oldest server protocol version this client works with. Clients keep
 * working with servers of the previous major version, since members cannot
 * update the servers they use; when the major goes up, this becomes the
 * first version of the one before.
 */
export const OLDEST_SERVER_PROTOCOL = "1.0";

/** Why the app and a server cannot talk: which of the two is too old for the other. */
export type Outdated = "client_outdated" | "server_outdated";

/**
 * - `current`: both speak the same version
 * - `update_available`: the server speaks a newer version, which this client still works with
 * - `server_older`: the server speaks an older version this client still works with; some features may be missing
 */
export type Compatibility = "current" | "update_available" | "server_older" | Outdated;

type Version = readonly [major: number, minor: number];

function parse(text: string | undefined): Version | null {
  const match = text === undefined ? null : /^(\d+)\.(\d+)$/.exec(text);
  return match === null ? null : [Number(match[1]), Number(match[2])];
}

function compare(a: Version, b: Version): number {
  return a[0] - b[0] || a[1] - b[1];
}

const CLIENT = parse(PROTOCOL_VERSION) as Version;
const OLDEST_SERVER = parse(OLDEST_SERVER_PROTOCOL) as Version;

/**
 * How this client and a server stand, from what the server says of itself.
 * A server that states no version predates versions altogether.
 */
export function compatibility(protocol: ServerInfo["protocol"] | undefined): Compatibility {
  const server = parse(protocol?.version);
  if (server === null || compare(server, OLDEST_SERVER) < 0) {
    return "server_outdated";
  }
  const minClient = parse(protocol?.minClient);
  if (minClient !== null && compare(CLIENT, minClient) < 0) {
    return "client_outdated";
  }
  const order = compare(CLIENT, server);
  return order < 0 ? "update_available" : order > 0 ? "server_older" : "current";
}

export function isOutdated(compatibility: Compatibility): compatibility is Outdated {
  return compatibility === "client_outdated" || compatibility === "server_outdated";
}
