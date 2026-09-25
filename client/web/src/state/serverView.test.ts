import { describe, expect, it } from "vitest";
import type { Account, Channel, GatewayServerFrame } from "../api/types";
import { TYPING_SHOWN_MS, applyFrame, fromReady, sortedChannels, typingIn } from "./serverView";

type Ready = Extract<GatewayServerFrame, { type: "ready" }>;
type Event = Exclude<GatewayServerFrame, Ready>;

const me = account("me", "Me");
const bob = account("bob", "Bob");

function account(id: string, displayName: string): Account {
  return { id, username: id, displayName, roleIds: [], createdAt: "2026-01-01T00:00:00Z" };
}

function channel(id: string, position: number): Channel {
  return { id, type: "text", name: id, position, requiredRoleIds: [], createdAt: "2026-01-01T00:00:00Z" };
}

function ready(): Ready {
  return {
    type: "ready",
    seq: 1,
    account: me,
    permissions: { owner: false, permissions: ["SEND_MESSAGES"] },
    server: {
      name: "Snatter",
      version: "0.1.0",
      apiVersion: 1,
      community: { name: "Test" },
      registration: { mode: "open", challengeRequired: false, setupRequired: false },
      voice: { defaultBitrate: 64000, maxBitrate: 256000 },
    },
    roles: [],
    members: [me, bob],
    presences: [{ accountId: "me", status: "online" }],
    channels: [channel("b", 1), channel("a", 0)],
  };
}

function apply(...frames: Event[]) {
  return frames.reduce((view, frame) => applyFrame(view, frame, 1_000), fromReady(ready()));
}

describe("serverView", () => {
  it("starts from ready", () => {
    const view = fromReady(ready());
    expect(view.account.id).toBe("me");
    expect(Object.keys(view.members)).toEqual(["me", "bob"]);
    expect(view.online).toEqual({ me: true });
    expect(sortedChannels(view).map((c) => c.id)).toEqual(["a", "b"]);
  });

  it("follows channels", () => {
    const view = apply(
      { type: "channel_created", seq: 2, channel: channel("c", 2) },
      { type: "channel_updated", seq: 3, channel: { ...channel("c", 0), name: "renamed" } },
      { type: "channel_deleted", seq: 4, channelId: "a" },
    );
    expect(sortedChannels(view).map((c) => c.name)).toEqual(["renamed", "b"]);
  });

  it("follows presence", () => {
    const view = apply(
      { type: "presence_updated", seq: 2, presence: { accountId: "bob", status: "online" } },
      { type: "presence_updated", seq: 3, presence: { accountId: "me", status: "offline" } },
    );
    expect(view.online).toEqual({ bob: true });
  });

  it("updates the member themselves along with the member list", () => {
    const view = apply({ type: "member_updated", seq: 2, member: { ...me, displayName: "Renamed" } });
    expect(view.account.displayName).toBe("Renamed");
    expect(view.members["me"]?.displayName).toBe("Renamed");
  });

  it("shows typing until it expires or a message arrives", () => {
    const typing = apply({ type: "typing_started", seq: 2, channelId: "a", accountId: "bob" });
    expect(typingIn(typing, "a", 1_000).map((m) => m.id)).toEqual(["bob"]);
    expect(typingIn(typing, "a", 1_000 + TYPING_SHOWN_MS)).toEqual([]);

    const sent = applyFrame(
      typing,
      {
        type: "message_created",
        seq: 3,
        message: {
          kind: "user",
          id: "m1",
          channelId: "a",
          authorId: "bob",
          content: "hi",
          createdAt: "2026-01-01T00:00:00Z",
        },
      },
      1_000,
    );
    expect(typingIn(sent, "a", 1_000)).toEqual([]);
  });
});
