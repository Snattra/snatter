import { describe, expect, it } from "vitest";
import type { Channel } from "../api/types";
import {
  type ChannelForm,
  type ChannelRules,
  blankChannelForm,
  channelChanges,
  channelForm,
  creation,
  creationErrors,
  toggledRole,
  updateErrors,
} from "./channelForm";

const member: ChannelRules = { maxBitrate: 256_000, owner: false, heldRoleIds: ["user"] };
const owner: ChannelRules = { ...member, owner: true, heldRoleIds: [] };

function channel(changes: Partial<Channel> = {}): Channel {
  return {
    id: "c1",
    type: "text",
    name: "general",
    position: 0,
    requiredRoleIds: [],
    createdAt: "2026-09-27T00:00:00Z",
    ...changes,
  };
}

const lounge = channel({ type: "voice_text", name: "Lounge", bitrate: 64_000, userLimit: 0 });

function edited(from: Channel, changes: Partial<ChannelForm>): ChannelForm {
  return { ...channelForm(from), ...changes };
}

describe("channelForm", () => {
  it("shows the bitrate in kilobits and a public channel as not private", () => {
    expect(channelForm(lounge)).toEqual({
      name: "Lounge",
      topic: "",
      bitrate: "64",
      userLimit: "0",
      private: false,
      roleIds: [],
    });
    expect(channelForm(channel({ topic: "News", requiredRoleIds: ["mod", "admin"] }))).toMatchObject({
      topic: "News",
      private: true,
      roleIds: ["admin", "mod"],
    });
  });
});

describe("toggledRole", () => {
  it("keeps the roles sorted, so ticking one off and on again is no change", () => {
    expect(toggledRole(["b"], "a")).toEqual(["a", "b"]);
    expect(toggledRole(toggledRole(["a", "b"], "a"), "a")).toEqual(["a", "b"]);
  });
});

describe("creationErrors", () => {
  it("wants a name", () => {
    expect(creationErrors({ ...blankChannelForm, name: "   " }, member)).toEqual({ name: "Give the channel a name." });
    expect(creationErrors({ ...blankChannelForm, name: "x".repeat(101) }, member).name).toBeDefined();
    expect(creationErrors({ ...blankChannelForm, name: "patch-notes" }, member)).toEqual({});
  });

  it("wants a private channel to name its roles, one of them the member's own", () => {
    const secret = { ...blankChannelForm, name: "secret", private: true };
    expect(creationErrors(secret, member).roles).toBe("Choose at least one role that can see it.");
    expect(creationErrors({ ...secret, roleIds: ["mod"] }, member).roles).toBe(
      "Include a role you have, or you could not see it yourself.",
    );
    expect(creationErrors({ ...secret, roleIds: ["mod", "user"] }, member)).toEqual({});
  });

  it("lets the owner, who sees every channel, choose roles they lack", () => {
    expect(creationErrors({ ...blankChannelForm, name: "secret", private: true, roleIds: ["mod"] }, owner)).toEqual({});
  });

  it("ignores ticked roles while the channel is public", () => {
    expect(creationErrors({ ...blankChannelForm, name: "open", roleIds: ["mod"] }, member)).toEqual({});
  });
});

describe("updateErrors", () => {
  it("checks voice settings against the server's limits", () => {
    expect(updateErrors(lounge, edited(lounge, { bitrate: "300" }), member)).toEqual({
      bitrate: "Choose from 8 to 256 kbps.",
    });
    expect(updateErrors(lounge, edited(lounge, { bitrate: "" }), member).bitrate).toBeDefined();
    expect(updateErrors(lounge, edited(lounge, { bitrate: "7.9" }), member).bitrate).toBeDefined();
    expect(updateErrors(lounge, edited(lounge, { bitrate: "96.5" }), member)).toEqual({});
    expect(updateErrors(lounge, edited(lounge, { userLimit: "100" }), member)).toEqual({
      userLimit: "Use a whole number from 0 to 99.",
    });
    expect(updateErrors(lounge, edited(lounge, { userLimit: "2.5" }), member).userLimit).toBeDefined();
  });

  it("leaves alone what did not change, as only changes are sent", () => {
    const loud = { ...lounge, bitrate: 384_000 };
    expect(updateErrors(loud, edited(loud, { name: "Loud" }), member)).toEqual({});

    // A private channel the member sees only through a role they no longer hold.
    const hidden = channel({ requiredRoleIds: ["mod"] });
    expect(updateErrors(hidden, edited(hidden, { topic: "psst" }), member)).toEqual({});
    expect(updateErrors(hidden, edited(hidden, { roleIds: ["admin", "mod"] }), member).roles).toBeDefined();
  });

  it("checks making a channel private without roles", () => {
    expect(updateErrors(lounge, edited(lounge, { private: true }), member).roles).toBeDefined();
  });
});

describe("creation", () => {
  it("sends the trimmed name, and roles only for a private channel", () => {
    expect(creation("voice", { ...blankChannelForm, name: "  Lounge ", roleIds: ["mod"] })).toEqual({
      type: "voice",
      name: "Lounge",
    });
    expect(creation("text", { ...blankChannelForm, name: "mods", private: true, roleIds: ["mod"] })).toEqual({
      type: "text",
      name: "mods",
      requiredRoleIds: ["mod"],
    });
  });
});

describe("channelChanges", () => {
  it("is empty when nothing differs, surrounding whitespace included", () => {
    expect(channelChanges(lounge, channelForm(lounge))).toEqual({});
    expect(channelChanges(lounge, edited(lounge, { name: " Lounge ", topic: "  " }))).toEqual({});
  });

  it("holds only what differs", () => {
    expect(channelChanges(lounge, edited(lounge, { topic: " Hang out ", bitrate: "96", userLimit: "8" }))).toEqual({
      topic: "Hang out",
      bitrate: 96_000,
      userLimit: 8,
    });
  });

  it("clears the topic with an empty string", () => {
    const withTopic = channel({ topic: "News" });
    expect(channelChanges(withTopic, edited(withTopic, { topic: "" }))).toEqual({ topic: "" });
  });

  it("never sends voice settings for a text channel", () => {
    const text = channel();
    expect(channelChanges(text, edited(text, { bitrate: "64", userLimit: "5" }))).toEqual({});
  });

  it("makes a channel private, and public again with no roles", () => {
    const open = channel();
    expect(channelChanges(open, edited(open, { private: true, roleIds: ["mod"] }))).toEqual({
      requiredRoleIds: ["mod"],
    });
    const secret = channel({ requiredRoleIds: ["mod"] });
    expect(channelChanges(secret, edited(secret, { private: false }))).toEqual({ requiredRoleIds: [] });
    expect(channelChanges(secret, edited(secret, { roleIds: ["mod"] }))).toEqual({});
  });
});
