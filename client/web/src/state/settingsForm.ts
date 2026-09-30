import type { RateLimits, RegistrationMode, ServerSettings, ServerSettingsUpdate } from "../api/types";
import { MAX_BITRATE, MIN_BITRATE, kbps } from "./channelForm";
import { sameData, wholeNumber } from "./forms";

/** The rate limit policies, in the order the settings list them. */
export const RATE_LIMIT_POLICIES = ["message", "login", "register", "challenge", "invite"] as const;
export type RateLimitPolicyName = (typeof RATE_LIMIT_POLICIES)[number];

/** The contract's limits on a policy. */
const MAX_LIMIT = 100_000;
const MAX_PERIOD_SECONDS = 86_400;

/** The contract's limit on how long sessions last. */
const MAX_SESSION_LIFETIME_DAYS = 365;

/** Bot check difficulties to pick from, as the largest number the puzzle may hide. */
export const CHALLENGE_DIFFICULTIES = [
  { maxNumber: 25_000, label: "Light" },
  { maxNumber: 100_000, label: "Normal" },
  { maxNumber: 400_000, label: "Strong" },
] as const;

/** An http(s) address, as the contract has it for `publicUrl`. */
const PUBLIC_URL = /^https?:\/\/[^/\s]+(\/\S*)?$/;

/** A policy as typed: how many tries every how many seconds. */
export interface PolicyForm {
  limit: string;
  periodSeconds: string;
}

/** The server settings as a form holds them. An empty id means none. */
export interface SettingsForm {
  name: string;
  description: string;
  systemChannelId: string;
  registrationMode: RegistrationMode;
  challengeRequired: boolean;
  /** The difficulty, as the largest number in digits. */
  challengeMaxNumber: string;
  newMemberRoleId: string;
  publicUrl: string;
  sessionLifetimeDays: string;
  /** In kilobits per second. */
  voiceDefaultBitrate: string;
  rateLimitsEnabled: boolean;
  rateLimits: Record<RateLimitPolicyName, PolicyForm>;
}

export type SettingsField =
  | "name"
  | "publicUrl"
  | "sessionLifetimeDays"
  | "voiceDefaultBitrate"
  | RateLimitPolicyName;
export type SettingsErrors = Partial<Record<SettingsField, string>>;

export function settingsForm(settings: ServerSettings): SettingsForm {
  const policy = (name: RateLimitPolicyName): PolicyForm => ({
    limit: String(settings.rateLimits[name].limit),
    periodSeconds: String(settings.rateLimits[name].periodSeconds),
  });
  return {
    name: settings.name,
    description: settings.description ?? "",
    systemChannelId: settings.systemChannelId ?? "",
    registrationMode: settings.registrationMode,
    challengeRequired: settings.challengeRequired,
    challengeMaxNumber: String(settings.challengeMaxNumber),
    newMemberRoleId: settings.newMemberRoleId ?? "",
    publicUrl: settings.publicUrl ?? "",
    sessionLifetimeDays: String(settings.sessionLifetimeDays),
    voiceDefaultBitrate: String(kbps(settings.voice.defaultBitrate)),
    rateLimitsEnabled: settings.rateLimits.enabled,
    rateLimits: {
      message: policy("message"),
      login: policy("login"),
      register: policy("register"),
      challenge: policy("challenge"),
      invite: policy("invite"),
    },
  };
}

/** What the server would refuse, by field; empty when the form can be saved. */
export function settingsErrors(form: SettingsForm): SettingsErrors {
  const errors: SettingsErrors = {};
  if (form.name.trim() === "") {
    errors.name = "Give the server a name.";
  }
  const publicUrl = form.publicUrl.trim();
  if (publicUrl !== "" && !PUBLIC_URL.test(publicUrl)) {
    errors.publicUrl = "Use a whole address, such as https://chat.example.com.";
  }
  const days = wholeNumber(form.sessionLifetimeDays);
  if (days === null || days < 1 || days > MAX_SESSION_LIFETIME_DAYS) {
    errors.sessionLifetimeDays = "Choose from 1 to 365 days.";
  }
  if (bitrateIn(form.voiceDefaultBitrate) === null) {
    errors.voiceDefaultBitrate = `Choose from ${kbps(MIN_BITRATE)} to ${kbps(MAX_BITRATE)} kbps.`;
  }
  for (const name of RATE_LIMIT_POLICIES) {
    if (policyIn(form.rateLimits[name]) === null) {
      errors[name] = "Allow 1 to 100,000, every 1 to 86,400 seconds.";
    }
  }
  return errors;
}

/**
 * The update that turns the settings into what the form holds, with only the
 * fields that differ; empty when nothing does. Check the form with
 * {@link settingsErrors} first. The rate limits go as a whole or not at all.
 */
export function settingsChanges(settings: ServerSettings, form: SettingsForm): ServerSettingsUpdate {
  const update: ServerSettingsUpdate = {};
  const name = form.name.trim();
  if (name !== settings.name) {
    update.name = name;
  }
  const description = form.description.trim();
  if (description !== (settings.description ?? "")) {
    update.description = description;
  }
  // The server keeps the address without trailing slashes.
  const publicUrl = form.publicUrl.trim().replace(/\/+$/, "");
  if (publicUrl !== (settings.publicUrl ?? "")) {
    update.publicUrl = publicUrl;
  }
  if (form.registrationMode !== settings.registrationMode) {
    update.registrationMode = form.registrationMode;
  }
  if (form.challengeRequired !== settings.challengeRequired) {
    update.challengeRequired = form.challengeRequired;
  }
  const challengeMaxNumber = wholeNumber(form.challengeMaxNumber);
  if (challengeMaxNumber !== null && challengeMaxNumber !== settings.challengeMaxNumber) {
    update.challengeMaxNumber = challengeMaxNumber;
  }
  const sessionLifetimeDays = wholeNumber(form.sessionLifetimeDays);
  if (sessionLifetimeDays !== null && sessionLifetimeDays !== settings.sessionLifetimeDays) {
    update.sessionLifetimeDays = sessionLifetimeDays;
  }
  const defaultBitrate = bitrateIn(form.voiceDefaultBitrate);
  if (defaultBitrate !== null && defaultBitrate !== settings.voice.defaultBitrate) {
    update.voice = { defaultBitrate };
  }
  if (form.systemChannelId !== (settings.systemChannelId ?? "")) {
    update.systemChannelId = form.systemChannelId;
  }
  if (form.newMemberRoleId !== (settings.newMemberRoleId ?? "")) {
    update.newMemberRoleId = form.newMemberRoleId;
  }
  const rateLimits = rateLimitsIn(form);
  if (rateLimits !== null && !sameData(rateLimits, settings.rateLimits)) {
    update.rateLimits = rateLimits;
  }
  return update;
}

function rateLimitsIn(form: SettingsForm): RateLimits | null {
  const [message, login, register, challenge, invite] = RATE_LIMIT_POLICIES.map((name) => policyIn(form.rateLimits[name]));
  if (message == null || login == null || register == null || challenge == null || invite == null) {
    return null;
  }
  return { enabled: form.rateLimitsEnabled, login, register, challenge, invite, message };
}

/** Bits per second from kilobits as typed, within what Opus supports; null for anything else. */
function bitrateIn(text: string): number | null {
  const trimmed = text.trim();
  const value = Math.round(Number(trimmed) * 1000);
  return trimmed === "" || !Number.isFinite(value) || value < MIN_BITRATE || value > MAX_BITRATE ? null : value;
}

function policyIn(policy: PolicyForm): { limit: number; periodSeconds: number } | null {
  const limit = wholeNumber(policy.limit);
  const periodSeconds = wholeNumber(policy.periodSeconds);
  if (limit === null || periodSeconds === null) {
    return null;
  }
  return limit >= 1 && limit <= MAX_LIMIT && periodSeconds >= 1 && periodSeconds <= MAX_PERIOD_SECONDS
    ? { limit, periodSeconds }
    : null;
}
