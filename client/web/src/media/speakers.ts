/** What plays one voice: an `<audio>` element in the app, a fake in tests. */
export interface Player {
  muted: boolean;
  play(): Promise<void>;
  pause(): void;
}

/**
 * The others' voices, one player for each line the server sends one on.
 * Keyed by the line, not the track's id, which browsers may take from the
 * offer and need not differ between lines; a line the server reuses for
 * someone else keeps its track, and its player.
 */
export class Speakers {
  private readonly players = new Map<string, Player>();
  private muted = false;

  constructor(private readonly create: (track: MediaStreamTrack) => Player) {}

  play(line: string, track: MediaStreamTrack): void {
    if (this.players.has(line)) {
      return;
    }
    const player = this.create(track);
    player.muted = this.muted;
    this.players.set(line, player);
    player.play().catch((e: unknown) => console.warn("Could not play a voice", e));
  }

  setMuted(muted: boolean): void {
    this.muted = muted;
    for (const player of this.players.values()) {
      player.muted = muted;
    }
  }

  stop(): void {
    for (const player of this.players.values()) {
      player.pause();
    }
    this.players.clear();
  }
}
