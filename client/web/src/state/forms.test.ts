import { describe, expect, it } from "vitest";
import { sameData, sameMembers, wholeNumber } from "./forms";

describe("sameData", () => {
  it("compares plain data deeply, whatever the order of keys", () => {
    expect(sameData({ a: 1, b: { c: ["x"] } }, { b: { c: ["x"] }, a: 1 })).toBe(true);
    expect(sameData({ a: 1 }, { a: 1, b: undefined })).toBe(false);
    expect(sameData({ a: ["x", "y"] }, { a: ["y", "x"] })).toBe(false);
    expect(sameData([], {})).toBe(false);
  });
});

describe("sameMembers", () => {
  it("ignores order", () => {
    expect(sameMembers(["a", "b"], ["b", "a"])).toBe(true);
    expect(sameMembers(["a"], ["a", "b"])).toBe(false);
  });
});

describe("wholeNumber", () => {
  it("reads plain digits only", () => {
    expect(wholeNumber(" 42 ")).toBe(42);
    expect(wholeNumber("0")).toBe(0);
    for (const text of ["", "1.5", "1e3", "-2", "0x10", "four"]) {
      expect(wholeNumber(text)).toBeNull();
    }
  });
});
