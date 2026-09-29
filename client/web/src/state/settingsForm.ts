import type { RateLimits, RegistrationMode, ServerSettings, ServerSettingsUpdate } from "../api/types";
import { sameData, wholeNumber } from "./forms";

/** The rate limit policies, in the order the settings list them. */
export const RATE_LIMIT_POLICIES = ["message", "login", "register", "challenge", "invite"] as const;
export type RateLimitPolicyName = (typeof RATE_LIMIT_POLICIES)[number];

/** The contract's limits on a policy. */
const MAX_LIMIT = 100_000;
const MAX_PERIOD_SECONDS = 86_400;

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
  newMemberRoleId: string;
  publicUrl: string;
  rateLimitsEnabled: boolean;
  rateLimits: Record<RateLimitPolicyName, PolicyForm>;
}

export type SettingsField = "name" | "publicUrl" | RateLimitPolicyName;
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
    newMemberRoleId: settings.newMemberRoleId ?? "",
    publicUrl: settings.publicUrl ?? "",
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
