import { describe, expect, it } from "vitest";
import type { Channel } from "../api/types";
import { endNotice, micOff, noVoice, quietScene, refusalNotice, voiceSound, voiceStatus } from "./voice";

const lounge: Channel = {
  id: "lounge",
  type: "voice",
  name: "Lounge",
  position: 0,
  bitrate: 64000,
  userLimit: 2,
  requiredRoleIds: [],
  createdAt: "2026-01-01T00:00:00Z",
};

describe("voiceStatus", () => {
  it("is connected only once the server has the member in the channel asked for", () => {
    const asked = { ...noVoice, channelId: "lounge" };
    expect(voiceStatus(noVoice, undefined)).toBe("out");
    expect(voiceStatus(asked, undefined)).toBe("joining");
    expect(voiceStatus(asked, { accountId: "me", channelId: "den", selfMuted: false, selfDeafened: false })).toBe("joining");
    expect(voiceStatus(asked, { accountId: "me", channelId: "lounge", selfMuted: false, selfDeafened: false })).toBe(
      "connected",
    );
    // In voice on another device is not in voice here.
    expect(voiceStatus(noVoice, { accountId: "me", channelId: "lounge", selfMuted: false, selfDeafened: false })).toBe("out");
  });
});

describe("micOff", () => {
  it("is off when muted or deafened", () => {
    expect(micOff(noVoice)).toBe(false);
    expect(micOff({ ...noVoice, selfMuted: true })).toBe(true);
    expect(micOff({ ...noVoice, selfDeafened: true })).toBe(true);
  });
});

describe("notices", () => {
  it("say why joining was refused", () => {
    expect(refusalNotice("channel_full", lounge, false)).toBe("Lounge is full.");
    expect(refusalNotice("forbidden", lounge, true)).toBe("You can't join voice while you're timed out.");
    expect(refusalNotice("forbidden", lounge, false)).toBe("You don't have permission to join voice.");
  });

  it("say why voice ended, also for reasons added later", () => {
    expect(endNotice("joined_elsewhere", false)).toBe("You joined voice on another device.");
    expect(endNotice("forbidden", true)).toBe("You left voice because you were timed out.");
    expect(endNotice("kicked" as never, false)).toBe("You were disconnected from voice.");
  });
});

describe("voiceSound", () => {
  const lounge = (...others: string[]) => ({ channelId: "lounge", others });

  it("rings for joining, moving and leaving", () => {
    expect(voiceSound(quietScene, lounge("bob"))).toBe("join");
    expect(voiceSound(lounge("bob"), { channelId: "den", others: [] })).toBe("join");
    expect(voiceSound(lounge("bob"), quietScene)).toBe("leave");
  });

  it("rings for others arriving and leaving, but not for nothing", () => {
    expect(voiceSound(lounge("bob"), lounge("bob", "ann"))).toBe("join");
    expect(voiceSound(lounge("bob", "ann"), lounge("ann"))).toBe("leave");
    expect(voiceSound(lounge("bob"), lounge("bob"))).toBeNull();
    expect(voiceSound(quietScene, quietScene)).toBeNull();
  });
});
