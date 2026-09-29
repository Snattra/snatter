import type { Channel, ChannelCreate, ChannelType, ChannelUpdate } from "../api/types";
import { sameMembers, wholeNumber } from "./forms";
import type { ServerView } from "./serverView";

/** The contract's limits on a channel; the server may allow a lower bitrate at most. */
const MAX_NAME_LENGTH = 100;
const MIN_BITRATE = 8_000;
const MAX_USER_LIMIT = 99;

/**
 * A channel's settings as a form holds them: text as typed, and the bitrate
 * in kilobits per second. The voice settings only matter for channels with
 * voice.
 */
export interface ChannelForm {
  name: string;
  topic: string;
  bitrate: string;
  userLimit: string;
  /** Private to `roleIds`; while false the channel is public, whichever roles are ticked. */
  private: boolean;
  /** Kept sorted, so ticking a role off and on again is no change. */
  roleIds: string[];
}

export type ChannelFormErrors = Partial<Record<"name" | "bitrate" | "userLimit" | "roles", string>>;

/** What the server checks a channel against, as far as the member's view knows it. */
export interface ChannelRules {
  /** The server's highest bitrate, in bits per second. */
  maxBitrate: number;
  /** The owner sees every channel, so may make one private to roles they do not hold. */
  owner: boolean;
  heldRoleIds: string[];
  /** The channels the member can see; names are unique regardless of case, so a mention names one channel. */
  channels: Pick<Channel, "id" | "name">[];
}

export function rulesFor(view: ServerView): ChannelRules {
  return {
    maxBitrate: view.info.voice.maxBitrate,
    owner: view.permissions.owner,
    heldRoleIds: view.account.roleIds,
    channels: Object.values(view.channels),
  };
}

export const blankChannelForm: ChannelForm = {
  name: "",
  topic: "",
  bitrate: "",
  userLimit: "",
  private: false,
  roleIds: [],
};

export function channelForm(channel: Channel): ChannelForm {
  return {
    name: channel.name,
    topic: channel.topic ?? "",
    bitrate: channel.bitrate == null ? "" : String(channel.bitrate / 1000),
    userLimit: channel.userLimit == null ? "" : String(channel.userLimit),
    private: channel.requiredRoleIds.length > 0,
    roleIds: [...channel.requiredRoleIds].sort(),
  };
}

export function hasVoice(type: ChannelType): boolean {
  return type !== "text";
}

/** The roles with one ticked or unticked, still sorted. */
export function toggledRole(roleIds: string[], roleId: string): string[] {
  return roleIds.includes(roleId) ? roleIds.filter((id) => id !== roleId) : [...roleIds, roleId].sort();
}

/** What the server would refuse about a new channel, by field; empty when it can be sent. */
export function creationErrors(form: ChannelForm, rules: ChannelRules): ChannelFormErrors {
  return { ...nameError(form, rules, null), ...accessError(form, rules) };
}

/**
 * What the server would refuse about a change, by field; empty when it can be
 * sent. Only what changed is checked, as only that is sent: a bitrate over a
 * limit the server has since lowered stays until someone changes it.
 */
export function updateErrors(channel: Channel, form: ChannelForm, rules: ChannelRules): ChannelFormErrors {
  const before = channelForm(channel);
  const voice = hasVoice(channel.type);
  return {
    ...nameError(form, rules, channel.id),
    ...(voice && form.bitrate !== before.bitrate ? bitrateError(form, rules) : {}),
    ...(voice && form.userLimit !== before.userLimit ? userLimitError(form) : {}),
    ...(form.private !== before.private || !sameMembers(form.roleIds, before.roleIds) ? accessError(form, rules) : {}),
  };
}

export function creation(type: ChannelType, form: ChannelForm): ChannelCreate {
  return { type, name: form.name.trim(), ...(form.private ? { requiredRoleIds: form.roleIds } : {}) };
}

/**
 * The update that turns the channel into what the form holds, with only the
 * fields that differ; empty when nothing does. Check it with
 * {@link updateErrors} first: a number that does not parse is left out.
 */
export function channelChanges(channel: Channel, form: ChannelForm): ChannelUpdate {
  const update: ChannelUpdate = {};
  const name = form.name.trim();
  if (name !== channel.name) {
    update.name = name;
  }
  const topic = form.topic.trim();
  if (topic !== (channel.topic ?? "")) {
    update.topic = topic;
  }
  if (hasVoice(channel.type)) {
    const bitrate = bitrateIn(form);
    if (bitrate !== null && bitrate !== channel.bitrate) {
      update.bitrate = bitrate;
    }
    const userLimit = wholeNumber(form.userLimit);
    if (userLimit !== null && userLimit !== channel.userLimit) {
      update.userLimit = userLimit;
    }
  }
  const roleIds = form.private ? form.roleIds : [];
  if (!sameMembers(roleIds, channel.requiredRoleIds)) {
    update.requiredRoleIds = roleIds;
  }
  return update;
}

/** Kilobits per second as shown to people, from bits per second. */
export function kbps(bitsPerSecond: number): number {
  return bitsPerSecond / 1000;
}

/** A channel may keep its own name, in another case too; `self` is null for a new one. */
function nameError(form: ChannelForm, rules: ChannelRules, self: string | null): ChannelFormErrors {
  const name = form.name.trim();
  if (name === "") {
    return { name: "Give the channel a name." };
  }
  if (name.length > MAX_NAME_LENGTH) {
    return { name: `Keep the name to ${MAX_NAME_LENGTH} characters.` };
  }
  const taken = rules.channels.some((c) => c.id !== self && c.name.toLowerCase() === name.toLowerCase());
  return taken ? { name: "Another channel has this name. Choose a different one." } : {};
}

function bitrateError(form: ChannelForm, rules: ChannelRules): ChannelFormErrors {
  const bitrate = bitrateIn(form);
  return bitrate === null || bitrate < MIN_BITRATE || bitrate > rules.maxBitrate
    ? { bitrate: `Choose from ${kbps(MIN_BITRATE)} to ${kbps(rules.maxBitrate)} kbps.` }
    : {};
}

function userLimitError(form: ChannelForm): ChannelFormErrors {
  const userLimit = wholeNumber(form.userLimit);
  return userLimit === null || userLimit > MAX_USER_LIMIT
    ? { userLimit: `Use a whole number from 0 to ${MAX_USER_LIMIT}.` }
    : {};
}

function accessError(form: ChannelForm, rules: ChannelRules): ChannelFormErrors {
  if (!form.private) {
    return {};
  }
  if (form.roleIds.length === 0) {
    return { roles: "Choose at least one role that can see it." };
  }
  if (!rules.owner && !form.roleIds.some((id) => rules.heldRoleIds.includes(id))) {
    return { roles: "Include a role you have, or you could not see it yourself." };
  }
  return {};
}

/** The bitrate in bits per second, from kilobits as typed; null when it is not a number. */
function bitrateIn(form: ChannelForm): number | null {
  const text = form.bitrate.trim();
  const value = Number(text);
  return text === "" || !Number.isFinite(value) ? null : Math.round(value * 1000);
}
