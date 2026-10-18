import { describe, expect, it } from "vitest";
import { SpeakingTracker, sameMembers } from "./speaking";

const levels = (entries: Record<string, number>) => new Map(Object.entries(entries));

describe("SpeakingTracker", () => {
  it("lights someone up as soon as they are heard, and for a moment after", () => {
    const tracker = new SpeakingTracker();
    expect(tracker.update(levels({ alice: 0, bob: 0.3 }), 0)).toEqual(new Set(["bob"]));
    expect(tracker.update(levels({ alice: 0, bob: 0 }), 200)).toEqual(new Set(["bob"]));
    expect(tracker.update(levels({ alice: 0, bob: 0 }), 300)).toEqual(new Set());
  });

  it("counts quiet speech a gate let through, but not the silence of a shut one", () => {
    const tracker = new SpeakingTracker();
    // About -46 dB, which a gate at its default -50 lets through.
    expect(tracker.update(levels({ alice: 0.005, bob: 0, carol: 0.0005 }), 0)).toEqual(new Set(["alice"]));
  });

  it("drops someone who left at once", () => {
    const tracker = new SpeakingTracker();
    tracker.update(levels({ alice: 0.3, bob: 0.3 }), 0);
    expect(tracker.update(levels({ alice: 0.3 }), 100)).toEqual(new Set(["alice"]));
  });
});

describe("sameMembers", () => {
  it("compares as sets", () => {
    expect(sameMembers(new Set(["a", "b"]), new Set(["b", "a"]))).toBe(true);
    expect(sameMembers(new Set(["a"]), new Set(["a", "b"]))).toBe(false);
    expect(sameMembers(new Set(["a", "c"]), new Set(["a", "b"]))).toBe(false);
  });
});
