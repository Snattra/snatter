/** The demo server, offered to anyone adding their first server. */
export const DEMO_ORIGIN = "https://demo.snatter.app";

/** Hosts on this very machine, which browsers reach over plain HTTP even from HTTPS pages. */
function isLocal(hostname: string): boolean {
  return (
    hostname === "localhost" ||
    hostname.endsWith(".localhost") ||
    /^127(\.\d+){3}$/.test(hostname) ||
    hostname === "[::1]"
  );
}

/**
 * The server a typed address names, as an origin: `demo.snatter.app` is
 * `https://demo.snatter.app`, and an address on this machine such as
 * `localhost:8080` is plain `http://`. Anything after the host, such as a
 * path, is left out, since a server is at its origin. Null when it names no
 * http(s) server.
 */
export function serverOrigin(typed: string): string | null {
  const text = typed.trim();
  if (text === "" || /\s/.test(text)) {
    return null;
  }
  const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(text) ? text : `${guessScheme(text)}://${text}`;
  let url: URL;
  try {
    url = new URL(withScheme);
  } catch {
    return null;
  }
  if (
    (url.protocol !== "https:" && url.protocol !== "http:") ||
    url.hostname === "" ||
    url.username !== "" ||
    url.password !== ""
  ) {
    return null;
  }
  return url.origin;
}

function guessScheme(text: string): "http" | "https" {
  try {
    return isLocal(new URL(`http://${text}`).hostname) ? "http" : "https";
  } catch {
    return "https";
  }
}

/**
 * Whether the browser will refuse to reach the server from this page: a
 * plain HTTP server from an HTTPS page, unless it is on this machine.
 */
export function blockedAsInsecure(origin: string, pageProtocol: string): boolean {
  const url = new URL(origin);
  return pageProtocol === "https:" && url.protocol === "http:" && !isLocal(url.hostname);
}
