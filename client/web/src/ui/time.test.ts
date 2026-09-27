import { describe, expect, it } from "vitest";
import { aheadTime, dateText, dayText, shortTime, untilText } from "./time";

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

describe("aheadTime", () => {
  it("says today and tomorrow in words, lower case for a sentence", () => {
    const noon = new Date(2026, 8, 27, 12, 0);
    const evening = new Date(2026, 8, 27, 18, 30);
    expect(aheadTime(evening, noon)).toBe(`today at ${shortTime(evening)}`);
    const tomorrow = new Date(2026, 8, 28, 9, 0);
    expect(aheadTime(tomorrow, noon)).toBe(`tomorrow at ${shortTime(tomorrow)}`);
    const nextWeek = new Date(2026, 9, 4, 9, 0);
    expect(aheadTime(nextWeek, noon)).toBe(`${dayText(nextWeek, noon)} at ${shortTime(nextWeek)}`);
  });
});

describe("dayText", () => {
  it("leaves out this year and names any other", () => {
    const noon = new Date(2026, 8, 27, 12, 0);
    expect(dayText(new Date(2026, 2, 14), noon)).not.toContain("2026");
    expect(dayText(new Date(2025, 2, 14), noon)).toContain("2025");
    expect(dateText(new Date(2026, 2, 14))).toContain("2026");
  });
});
