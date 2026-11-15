/**
 * How the app finds its servers. A server installation serves the app for
 * itself alone ("single"), so the server is wherever the page came from. The
 * hosted app and the desktop app let people add any number of servers
 * ("multi"); Vite's `--mode multi` builds and serves the app that way.
 */
export type ClientMode = { kind: "single"; origin: string } | { kind: "multi" };

export function resolveMode(): ClientMode {
  if (import.meta.env.MODE === "multi") {
    return { kind: "multi" };
  }
  return { kind: "single", origin: window.location.origin };
}
