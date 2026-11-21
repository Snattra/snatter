import { createHash, randomBytes } from "node:crypto";
import { describe, expect, it } from "vitest";
import { sha256Hex } from "./sha256";

const bytes = (text: string) => new TextEncoder().encode(text);

describe("sha256Hex", () => {
  it("gives the standard test vectors", () => {
    expect(sha256Hex(bytes(""))).toBe("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    expect(sha256Hex(bytes("abc"))).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    expect(sha256Hex(bytes("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))).toBe(
      "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
    );
  });

  it("agrees with the platform's for every length around the block boundaries, and beyond", () => {
    for (const length of [1, 54, 55, 56, 57, 63, 64, 65, 119, 120, 128, 1000]) {
      const data = new Uint8Array(randomBytes(length));
      expect(sha256Hex(data), `${length} bytes`).toBe(createHash("sha256").update(data).digest("hex"));
    }
  });
});
