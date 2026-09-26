import { create } from "zustand";
import type { ChannelLog } from "./channelLog";
import type { ServerView } from "./serverView";

/**
 * - `unknown`: the stored session has not been looked at yet
 * - `signed_out`: no session; the sign-in screen shows
 * - `connecting`: signed in, waiting for `ready`
 * - `connected`: the view is live
 * - `reconnecting`: the view is stale until the next `ready`
 */
export type ConnectionStatus = "unknown" | "signed_out" | "connecting" | "connected" | "reconnecting";

export interface ServerEntry {
  origin: string;
  status: ConnectionStatus;
  view: ServerView | null;
  /**
   * Messages of the channels opened so far, by channel id. Kept across
   * reconnects, which catch them up, so they outlive the view.
   */
  logs: Record<string, ChannelLog>;
  /** Why the server ended the last session, to show when signing in again. */
  notice: string | null;
}

interface ServersState {
  /** Every server the app knows, by origin; one for now, many in the multi-server apps. */
  servers: Record<string, ServerEntry>;
  update(origin: string, change: (entry: ServerEntry) => ServerEntry): void;
}

export const useServers = create<ServersState>()((set) => ({
  servers: {},
  update: (origin, change) =>
    set((state) => ({
      servers: { ...state.servers, [origin]: change(state.servers[origin] ?? blank(origin)) },
    })),
}));

export function useServer(origin: string): ServerEntry {
  return useServers((state) => state.servers[origin]) ?? blank(origin);
}

export function blank(origin: string): ServerEntry {
  return { origin, status: "unknown", view: null, logs: {}, notice: null };
}
