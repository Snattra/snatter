import { create } from "zustand";
import { platform } from "../platform/platform";
import { type ServerList, emptyList, parseServerList } from "../state/serverList";

const KEY = "servers";

interface SavedServersState extends ServerList {
  /** Whether the list has been read from storage yet. */
  loaded: boolean;
  /** Changes the list and keeps the change. */
  change(next: (list: ServerList) => ServerList): void;
}

/** The servers added to the app, as the multi-server apps keep them. */
export const useSavedServers = create<SavedServersState>()((set, get) => ({
  ...emptyList,
  loaded: false,
  change: (next) => {
    const { servers, selected } = get();
    const list = next({ servers, selected });
    set(list);
    void platform.settings.set(KEY, JSON.stringify(list));
  },
}));

/** Reads the list kept from last time. */
export async function loadSavedServers(): Promise<void> {
  const list = parseServerList(await platform.settings.get(KEY));
  useSavedServers.setState({ ...list, loaded: true });
}
