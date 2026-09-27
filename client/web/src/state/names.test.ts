import { describe, expect, it } from "vitest";
import { USERNAME_PATTERN, displayNameProblem, tidyDisplayName } from "./names";

describe("USERNAME_PATTERN", () => {
  it("allows letters, digits and underscores only", () => {
    const whole = new RegExp(`^${USERNAME_PATTERN}$`);
    expect(whole.test("robin_42")).toBe(true);
    for (const name of ["dotted.name", "dashed-name", "åsa", "two words"]) {
      expect(whole.test(name)).toBe(false);
    }
  });
});

describe("tidyDisplayName", () => {
  it("trims and collapses spaces, and changes nothing else", () => {
    expect(tidyDisplayName("  Quacky    McQuack\n")).toBe("Quacky McQuack");
    expect(tidyDisplayName("André")).toBe("André");
    expect(tidyDisplayName(" \t ")).toBe("");
  });
});

describe("displayNameProblem", () => {
  it("accepts letters of any script, digits, punctuation and symbols", () => {
    for (const name of ["Robin Jönsson", "日本の鴨", "Маллард", "O'Brien-Smith (away)", "© Pond & Co. ♥ €5", "Player #1"]) {
      expect(displayNameProblem(name)).toBeNull();
    }
  });

  it("refuses emoji, flags and skin tones", () => {
    for (const name of ["Mallard 🦆", "🇸🇪", "Wave 👋🏽"]) {
      expect(displayNameProblem(name)).toContain("emoji");
    }
  });

  it("refuses invisible characters, other spaces and combining marks", () => {
    for (const name of ["Rob\nin", "Rob​in", "‮evil", "Rob in", "ㅤ", "André", "Rob‍in"]) {
      expect(displayNameProblem(name)).toContain("invisible");
    }
  });
});
