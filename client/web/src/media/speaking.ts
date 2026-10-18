/**
 * WebRTC's audio level, from 0 to 1, a voice coming in must reach to count
 * as speech. A sender's shut gate sends silence, which is 0.
 */
const SPEAKING_LEVEL = 0.01;
/** How long someone still counts as speaking after their voice drops, so the ring doesn't flicker between syllables. */
const HOLD_MS = 300;

/**
 * Who is speaking, from the levels of the voices this app receives. Only
 * those it hears can light up, so it is always someone in the member's own
 * channel, in step with what plays.
 */
export class SpeakingTracker {
  /** When each member in the channel was last loud enough. */
  private readonly heardAt = new Map<string, number>();

  /** Takes each member's level just measured and returns who is speaking now; members not measured have left. */
  update(levels: ReadonlyMap<string, number>, now: number): Set<string> {
    for (const member of this.heardAt.keys()) {
      if (!levels.has(member)) {
        this.heardAt.delete(member);
      }
    }
    for (const [member, level] of levels) {
      if (level >= SPEAKING_LEVEL) {
        this.heardAt.set(member, now);
      }
    }
    return new Set([...this.heardAt].filter(([, at]) => now - at < HOLD_MS).map(([member]) => member));
  }
}

export function sameMembers(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  return a.size === b.size && [...a].every((member) => b.has(member));
}
