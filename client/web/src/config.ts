/**
 * How the app finds its servers. A server installation serves the app for
 * itself alone ("single"), so the server is wherever the page came from. The
 * hosted app and the desktop app will let people add any number of servers
 * ("multi"); not built yet, but all state is kept per server already.
 */
export type ClientMode = { kind: "single"; origin: string } | { kind: "multi" };

export function resolveMode(): ClientMode {
  if (import.meta.env.VITE_SNATTER_MODE === "multi") {
    return { kind: "multi" };
  }
  return { kind: "single", origin: window.location.origin };
}
