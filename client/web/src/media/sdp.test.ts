import { describe, expect, it } from "vitest";
import { membersOf, sessionOf } from "./sdp";

describe("sessionOf", () => {
  it("reads the session id of the o= line", () => {
    expect(sessionOf("v=0\r\no=- 4611731400430051336 3 IN IP4 0.0.0.0\r\ns=-\r\n")).toBe("4611731400430051336");
    expect(sessionOf("v=0\r\ns=-\r\n")).toBeNull();
  });
});

describe("membersOf", () => {
  it("reads the member of each line the server sends on, by mid", () => {
    const offer = [
      "v=0",
      "o=- 1 3 IN IP4 0.0.0.0",
      "a=group:BUNDLE 0 1 2 3",
      "m=audio 9 UDP/TLS/RTP/SAVPF 111",
      "a=mid:0",
      "a=recvonly",
      "m=audio 9 UDP/TLS/RTP/SAVPF 111",
      "a=mid:1",
      "a=sendonly",
      "a=msid:alice voice-1",
      "a=ssrc:1 cname:alice",
      "m=audio 9 UDP/TLS/RTP/SAVPF 111",
      "a=mid:2",
      "a=inactive",
      "m=audio 9 UDP/TLS/RTP/SAVPF 111",
      "a=mid:3",
      "a=sendonly",
      "a=msid:bob voice-3",
      "",
    ].join("\r\n");
    expect(membersOf(offer)).toEqual(new Map([["1", "alice"], ["3", "bob"]]));
  });
});
