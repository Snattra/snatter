import { describe, expect, it } from "vitest";
import type { ServerSettings } from "../api/types";
import { type SettingsForm, settingsChanges, settingsErrors, settingsForm } from "./settingsForm";

const policy = { limit: 10, periodSeconds: 60 };

const settings: ServerSettings = {
  name: "Pondside",
  description: null,
  publicUrl: "https://chat.example.com",
  registrationMode: "invite_only",
  challengeRequired: true,
  challengeMaxNumber: 100_000,
  rateLimits: { enabled: true, login: policy, register: policy, challenge: policy, invite: policy, message: policy },
  sessionLifetimeDays: 30,
  voice: { defaultBitrate: 64_000 },
  systemChannelId: "general",
  newMemberRoleId: null,
};

function edited(changes: Partial<SettingsForm>): SettingsForm {
  return { ...settingsForm(settings), ...changes };
}

describe("settingsForm", () => {
  it("holds nulls as empty text and numbers as typed", () => {
    const form = settingsForm(settings);
    expect(form.description).toBe("");
    expect(form.newMemberRoleId).toBe("");
    expect(form.systemChannelId).toBe("general");
    expect(form.rateLimits.login).toEqual({ limit: "10", periodSeconds: "60" });
  });

  it("holds the bitrate in kilobits", () => {
    expect(settingsForm(settings).voiceDefaultBitrate).toBe("64");
  });
});

describe("settingsErrors", () => {
  it("accepts the settings as they are", () => {
    expect(settingsErrors(settingsForm(settings))).toEqual({});
  });

  it("wants a name and a whole address", () => {
    expect(settingsErrors(edited({ name: " " }))).toEqual({ name: "Give the server a name." });
    expect(settingsErrors(edited({ publicUrl: "chat.example.com" })).publicUrl).toBeDefined();
    expect(settingsErrors(edited({ publicUrl: "https://chat.example.com/snatter" }))).toEqual({});
    expect(settingsErrors(edited({ publicUrl: "" }))).toEqual({});
  });

  it("checks each rate limit on its own", () => {
    const form = settingsForm(settings);
    const errors = settingsErrors({
      ...form,
      rateLimits: {
        ...form.rateLimits,
        register: { limit: "0", periodSeconds: "60" },
        invite: { limit: "5", periodSeconds: "" },
      },
    });
    expect(Object.keys(errors)).toEqual(["register", "invite"]);
  });

  it("keeps sessions from 1 to 365 days", () => {
    expect(settingsErrors(edited({ sessionLifetimeDays: "0" })).sessionLifetimeDays).toBeDefined();
    expect(settingsErrors(edited({ sessionLifetimeDays: "366" })).sessionLifetimeDays).toBeDefined();
    expect(settingsErrors(edited({ sessionLifetimeDays: "7.5" })).sessionLifetimeDays).toBeDefined();
    expect(settingsErrors(edited({ sessionLifetimeDays: "365" }))).toEqual({});
  });

  it("keeps the bitrate within what Opus supports", () => {
    expect(settingsErrors(edited({ voiceDefaultBitrate: "600" })).voiceDefaultBitrate).toBe("Choose from 8 to 510 kbps.");
    expect(settingsErrors(edited({ voiceDefaultBitrate: "4" })).voiceDefaultBitrate).toBeDefined();
    expect(settingsErrors(edited({ voiceDefaultBitrate: "510" }))).toEqual({});
  });
});

describe("settingsChanges", () => {
  it("is empty when nothing differs", () => {
    expect(settingsChanges(settings, settingsForm(settings))).toEqual({});
    expect(settingsChanges(settings, edited({ name: " Pondside ", publicUrl: "https://chat.example.com/" }))).toEqual(
      {},
    );
  });

  it("clears text and ids with empty strings", () => {
    expect(settingsChanges(settings, edited({ publicUrl: "", systemChannelId: "" }))).toEqual({
      publicUrl: "",
      systemChannelId: "",
    });
  });

  it("holds only what differs", () => {
    expect(
      settingsChanges(
        settings,
        edited({
          description: " Thursdays ",
          registrationMode: "open",
          challengeRequired: false,
          newMemberRoleId: "user",
        }),
      ),
    ).toEqual({
      description: "Thursdays",
      registrationMode: "open",
      challengeRequired: false,
      newMemberRoleId: "user",
    });
  });

  it("sends the rate limits whole when any of them changed", () => {
    const form = settingsForm(settings);
    expect(settingsChanges(settings, { ...form, rateLimitsEnabled: false }).rateLimits).toEqual({
      ...settings.rateLimits,
      enabled: false,
    });
    const update = settingsChanges(settings, {
      ...form,
      rateLimits: { ...form.rateLimits, login: { limit: "5", periodSeconds: "60" } },
    });
    expect(update).toEqual({ rateLimits: { ...settings.rateLimits, login: { limit: 5, periodSeconds: 60 } } });
  });

  it("sends the numbers that changed, the bitrate in bits per second", () => {
    expect(
      settingsChanges(settings, edited({ challengeMaxNumber: "25000", sessionLifetimeDays: "7", voiceDefaultBitrate: "96" })),
    ).toEqual({
      challengeMaxNumber: 25_000,
      sessionLifetimeDays: 7,
      voice: { defaultBitrate: 96_000 },
    });
  });
});
