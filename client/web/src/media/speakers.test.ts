import { describe, expect, it } from "vitest";
import { type Player, Speakers } from "./speakers";

function fakes(): { created: { track: MediaStreamTrack; player: Player & { playing: boolean } }[]; speakers: Speakers } {
  const created: { track: MediaStreamTrack; player: Player & { playing: boolean } }[] = [];
  const speakers = new Speakers((track) => {
    const player = {
      muted: false,
      playing: false,
      async play() {
        player.playing = true;
      },
      pause() {
        player.playing = false;
      },
    };
    created.push({ track, player });
    return player;
  });
  return { created, speakers };
}

const track = (id: string) => ({ id }) as MediaStreamTrack;

describe("Speakers", () => {
  it("plays each line once, even when tracks share an id", () => {
    const { created, speakers } = fakes();
    speakers.play("1", track("voice"));
    speakers.play("2", track("voice"));
    // The server reused line 1 for someone else: same line, same track, same player.
    speakers.play("1", track("voice"));
    expect(created.map((c) => c.player.playing)).toEqual([true, true]);
  });

  it("mutes those playing and those to come, and stops them all", () => {
    const { created, speakers } = fakes();
    speakers.play("1", track("a"));
    speakers.setMuted(true);
    speakers.play("2", track("b"));
    expect(created.map((c) => c.player.muted)).toEqual([true, true]);
    speakers.setMuted(false);
    expect(created.map((c) => c.player.muted)).toEqual([false, false]);
    speakers.stop();
    expect(created.map((c) => c.player.playing)).toEqual([false, false]);
  });
});
