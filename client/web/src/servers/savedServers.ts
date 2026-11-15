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

/**
 * Keeping the list, one save after the other in the order of the changes,
 * so that where storage answers later, as on the desktop, an older list
 * never lands last.
 */
let saving: Promise<void> = Promise.resolve();

/** The servers added to the app, as the multi-server apps keep them. */
export const useSavedServers = create<SavedServersState>()((set, get) => ({
  ...emptyList,
  loaded: false,
  change: (next) => {
    const { servers, selected } = get();
    const list = next({ servers, selected });
    set(list);
    const text = JSON.stringify(list);
    saving = saving
      .then(() => platform.settings.set(KEY, text))
      .catch(() => {
        // Not kept this time; the next change keeps the whole list again.
      });
  },
}));

/** The one look at what was kept, however often it is asked for, so a late answer cannot undo changes since. */
let loading: Promise<void> | null = null;

/** Reads the list kept from last time. */
export function loadSavedServers(): Promise<void> {
  loading ??= platform.settings.get(KEY).then((text) => {
    useSavedServers.setState({ ...parseServerList(text), loaded: true });
  });
  return loading;
}
