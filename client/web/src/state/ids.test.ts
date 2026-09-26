import { describe, expect, it } from "vitest";
import { isAfter, later, timeOfId } from "./ids";

describe("ids", () => {
  it("orders message ids", () => {
    const a = "0192a0b4-1c2d-7000-8000-000000000001";
    const b = "0192a0b4-1c2d-7001-8000-000000000000";
    expect(isAfter(b, a)).toBe(true);
    expect(isAfter(a, b)).toBe(false);
    expect(isAfter(a, null)).toBe(true);
    expect(later(a, b)).toBe(b);
    expect(later(null, a)).toBe(a);
    expect(later(a, null)).toBe(a);
  });

  it("reads the creation time of a version 7 id", () => {
    const at = Date.UTC(2026, 8, 27, 18, 31);
    const id = `${at.toString(16).padStart(12, "0").replace(/^(.{8})(.{4})$/, "$1-$2")}-7abc-8000-000000000000`;
    expect(timeOfId(id)).toBe(at);
  });
});
