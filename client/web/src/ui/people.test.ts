import { describe, expect, it } from "vitest";
import { colourFor, initials } from "./people";

describe("initials", () => {
  it("takes the first letter of at most two words", () => {
    expect(initials("mallard duck jones")).toBe("MD");
  });

  it("ignores extra whitespace", () => {
    expect(initials("  Teal  ")).toBe("T");
  });

  it("keeps a character outside the basic plane whole", () => {
    expect(initials("🦆 Pond")).toBe("🦆P");
  });
});

describe("colourFor", () => {
  it("gives an account the same colour every time", () => {
    expect(colourFor("01J9ZQ6M3S")).toBe(colourFor("01J9ZQ6M3S"));
  });

  it("varies only the hue, so initials read on every fill", () => {
    expect(colourFor("01J9ZQ6M3S")).toMatch(/^oklch\(50% 0\.06 \d{1,3}\)$/);
  });
});
