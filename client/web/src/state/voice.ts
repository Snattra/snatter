import type { Channel, VoiceEndReason, VoiceRefusal, VoiceState } from "../api/types";

/**
 * Voice as this app asked for it. The server confirms it over the gateway,
 * where the member's own `VoiceState` says where they really are.
 */
export interface LocalVoice {
  /** The voice channel this app is in or joining; null when out of voice. */
  channelId: string | null;
  /** The member's own choice for their microphone, kept while deafened so undeafening restores it. */
  selfMuted: boolean;
  selfDeafened: boolean;
  /** Why voice was refused or ended, until dismissed or the next join. */
  notice: string | null;
}

export const noVoice: LocalVoice = { channelId: null, selfMuted: false, selfDeafened: false, notice: null };

/**
 * - `out`: not in voice here
 * - `joining`: asked to join and waiting for the server, also after reconnecting
 * - `connected`: in the channel asked for
 */
export type VoiceStatus = "out" | "joining" | "connected";

export function voiceStatus(local: LocalVoice, own: VoiceState | undefined): VoiceStatus {
  if (local.channelId === null) {
    return "out";
  }
  return own?.channelId === local.channelId ? "connected" : "joining";
}

/** Whether the member's microphone is off, by their choice: muted, or deafened, which mutes too. */
export function micOff(local: LocalVoice): boolean {
  return local.selfMuted || local.selfDeafened;
}

/** What to tell the member when the server would not let them join or move. */
export function refusalNotice(reason: VoiceRefusal, channel: Channel | undefined, timedOut: boolean): string {
  switch (reason) {
    case "channel_full":
      return `${channel?.name ?? "That channel"} is full.`;
    case "forbidden":
      return timedOut ? "You can't join voice while you're timed out." : "You don't have permission to join voice.";
    case "not_a_voice_channel":
      return "That channel has no voice.";
    case "channel_not_found":
      return "That voice channel is gone.";
    default:
      return "You couldn't join voice.";
  }
}

/** What to tell the member when the server took them out of voice. */
export function endNotice(reason: VoiceEndReason, timedOut: boolean): string {
  switch (reason) {
    case "joined_elsewhere":
      return "You joined voice on another device.";
    case "channel_unavailable":
      return "You left voice: the channel is gone.";
    case "forbidden":
      return timedOut ? "You left voice because you were timed out." : "You left voice: you no longer have permission.";
    default:
      return "You were disconnected from voice.";
  }
}

export type VoiceSound = "join" | "leave";

/** The voice channel this app is connected to, if any, and the others in it. */
export interface VoiceScene {
  channelId: string | null;
  others: string[];
}

export const quietScene: VoiceScene = { channelId: null, others: [] };

/**
 * The sound for going from one scene to the next: joining or moving to a
 * channel, someone arriving, leaving voice, or someone leaving. Those
 * already in a channel when the member arrives are simply there, and the
 * caller keeps the last connected scene while reconnecting, so coming back
 * to the same people is quiet.
 */
export function voiceSound(before: VoiceScene, after: VoiceScene): VoiceSound | null {
  if (after.channelId !== before.channelId) {
    return after.channelId === null ? "leave" : "join";
  }
  if (after.others.some((id) => !before.others.includes(id))) {
    return "join";
  }
  if (before.others.some((id) => !after.others.includes(id))) {
    return "leave";
  }
  return null;
}
