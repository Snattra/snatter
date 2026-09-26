import { describe, expect, it } from "vitest";
import { untilText } from "./time";

const now = new Date("2026-09-27T12:00:00Z");

function later(ms: number): Date {
  return new Date(now.getTime() + ms);
}

describe("untilText", () => {
  it("rounds to the largest unit that fits", () => {
    expect(untilText(later(7 * 24 * 3_600_000 - 1_000), now)).toBe("7 days");
    expect(untilText(later(26 * 3_600_000), now)).toBe("1 day");
    expect(untilText(later(5 * 3_600_000), now)).toBe("5 hours");
    expect(untilText(later(61 * 60_000), now)).toBe("1 hour");
    expect(untilText(later(12 * 60_000), now)).toBe("12 minutes");
  });

  it("never says less than a minute", () => {
    expect(untilText(later(10_000), now)).toBe("1 minute");
    expect(untilText(later(-60_000), now)).toBe("1 minute");
  });
});
