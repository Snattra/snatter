/**
 * The session id of an SDP description, from its `o=` line. The server keeps
 * it for every offer of one connection, so a different one means it started
 * over.
 */
export function sessionOf(sdp: string): string | null {
  return /^o=\S+ (\S+) /m.exec(sdp)?.[1] ?? null;
}
