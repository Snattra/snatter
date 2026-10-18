/**
 * The session id of an SDP description, from its `o=` line. The server keeps
 * it for every offer of one connection, so a different one means it started
 * over.
 */
export function sessionOf(sdp: string): string | null {
  return /^o=\S+ (\S+) /m.exec(sdp)?.[1] ?? null;
}

/**
 * The member each line of an offer carries, by the line's mid: the server
 * names them in the line's msid. Lines carrying nobody have none.
 */
export function membersOf(sdp: string): Map<string, string> {
  const members = new Map<string, string>();
  for (const section of sdp.split(/\r?\n(?=m=)/).slice(1)) {
    const mid = /^a=mid:(\S+)/m.exec(section)?.[1];
    const member = /^a=msid:(\S+) /m.exec(section)?.[1];
    if (mid !== undefined && member !== undefined) {
      members.set(mid, member);
    }
  }
  return members;
}
