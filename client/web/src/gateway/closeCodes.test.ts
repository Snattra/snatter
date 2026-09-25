import { describe, expect, it } from "vitest";
import { closeAction } from "./closeCodes";

describe("closeAction", () => {
  it("reconnects after network trouble and recoverable closes", () => {
    for (const code of [1001, 1006, 3000, 4002, 4006]) {
      expect(closeAction(code)).toEqual({ kind: "reconnect" });
    }
  });

  it("ends the session when the server refuses it", () => {
    expect(closeAction(4005)).toEqual({ kind: "end", reason: "session_ended" });
    expect(closeAction(4003)).toEqual({ kind: "end", reason: "authentication_failed" });
    expect(closeAction(4007)).toEqual({ kind: "end", reason: "banned" });
  });

  it("does not retry protocol errors a reconnect would repeat", () => {
    expect(closeAction(4000)).toEqual({ kind: "end", reason: "invalid_frame" });
  });
});
