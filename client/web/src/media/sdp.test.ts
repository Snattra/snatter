import { describe, expect, it } from "vitest";
import { sessionOf } from "./sdp";

describe("sessionOf", () => {
  it("reads the session id of the o= line", () => {
    expect(sessionOf("v=0\r\no=- 4611731400430051336 3 IN IP4 0.0.0.0\r\ns=-\r\n")).toBe("4611731400430051336");
    expect(sessionOf("v=0\r\ns=-\r\n")).toBeNull();
  });
});
