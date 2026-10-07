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
  /** Whether the connection carrying the member's audio to the server is up. */
  audioConnected: boolean;
}

export const noVoice: LocalVoice = {
  channelId: null,
  selfMuted: false,
  selfDeafened: false,
  notice: null,
  audioConnected: false,
};

/**
 * - `out`: not in voice here
 * - `joining`: asked to join and waiting for the server or the audio connection, also after reconnecting
 * - `connected`: in the channel asked for, with audio connected
 */
export type VoiceStatus = "out" | "joining" | "connected";

export function voiceStatus(local: LocalVoice, own: VoiceState | undefined): VoiceStatus {
  if (local.channelId === null) {
    return "out";
  }
  return own?.channelId === local.channelId && local.audioConnected ? "connected" : "joining";
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
      return "You left voice because the channel is no longer available.";
    case "forbidden":
      return timedOut ? "You left voice because you were timed out." : "You left voice because you no longer have permission.";
    case "connection_failed":
      return "Voice couldn't connect to the server.";
    default:
      return "You were disconnected from voice.";
  }
}

/**
 * What to tell the member when joining failed because the microphone could
 * not be used. Browsers give it only to secure pages, HTTPS or localhost.
 */
export function microphoneNotice(error: unknown, secure: boolean): string {
  if (!secure) {
    return "Voice needs the app to be opened over HTTPS.";
  }
  switch (error instanceof Error ? error.name : null) {
    case "NotAllowedError":
      return "Allow microphone access to join voice.";
    case "NotFoundError":
      return "No microphone was found.";
    case "NotReadableError":
      return "Your microphone couldn't be started. Another app may be using it.";
    default:
      return "Your microphone couldn't be used.";
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
