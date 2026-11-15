/** A server added to the app, under the name it had when last seen. */
export interface SavedServer {
  origin: string;
  name: string;
}

/** The servers added, in the order they were added, and the one open. */
export interface ServerList {
  servers: SavedServer[];
  /** The origin of the server open, or null when none is. */
  selected: string | null;
}

export const emptyList: ServerList = { servers: [], selected: null };

/** The list as kept in storage, forgiving anything that is not one; a list with nothing usable is empty. */
export function parseServerList(text: string | null): ServerList {
  if (text === null) {
    return emptyList;
  }
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    return emptyList;
  }
  if (typeof data !== "object" || data === null || !Array.isArray((data as ServerList).servers)) {
    return emptyList;
  }
  const seen = new Set<string>();
  const servers: SavedServer[] = [];
  for (const item of (data as ServerList).servers as unknown[]) {
    if (typeof item !== "object" || item === null) {
      continue;
    }
    const { origin, name } = item as Partial<SavedServer>;
    if (typeof origin === "string" && isOrigin(origin) && typeof name === "string" && !seen.has(origin)) {
      seen.add(origin);
      servers.push({ origin, name });
    }
  }
  const selected = (data as ServerList).selected;
  return {
    servers,
    selected: servers.some((server) => server.origin === selected)
      ? (selected as string)
      : (servers[0]?.origin ?? null),
  };
}

/** Whether the text is an http(s) origin as the app keeps them, and nothing more. */
function isOrigin(text: string): boolean {
  try {
    const url = new URL(text);
    return (url.protocol === "https:" || url.protocol === "http:") && url.origin === text;
  } catch {
    return false;
  }
}

/** Adds the server, or renames it if it is there already, and opens it. */
export function withServer(list: ServerList, server: SavedServer): ServerList {
  const servers = list.servers.some((s) => s.origin === server.origin)
    ? list.servers.map((s) => (s.origin === server.origin ? server : s))
    : [...list.servers, server];
  return { servers, selected: server.origin };
}

/** Takes the server off the list; if it was open, the one after it opens, or else the one before. */
export function withoutServer(list: ServerList, origin: string): ServerList {
  const index = list.servers.findIndex((s) => s.origin === origin);
  if (index < 0) {
    return list;
  }
  const servers = list.servers.filter((s) => s.origin !== origin);
  const selected =
    list.selected === origin ? (servers[Math.min(index, servers.length - 1)]?.origin ?? null) : list.selected;
  return { servers, selected };
}

/** Opens a server on the list; any other origin leaves the list as it is. */
export function selecting(list: ServerList, origin: string): ServerList {
  return list.servers.some((s) => s.origin === origin) ? { ...list, selected: origin } : list;
}

/** Renames a server on the list, as when it turns out to be called something else now. */
export function renamed(list: ServerList, origin: string, name: string): ServerList {
  return list.servers.some((s) => s.origin === origin && s.name !== name)
    ? { ...list, servers: list.servers.map((s) => (s.origin === origin ? { ...s, name } : s)) }
    : list;
}
