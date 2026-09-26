import type { Channel } from "../api/types";

// Filled glyphs on a 24px grid; they take the text colour.

const paths = {
  hash: "M10 3 8.5 9H4v2h4l-1 4H3v2h3.5L5 21h2l1.5-4h4L11 21h2l1.5-4H19v-2h-4l1-4h4V9h-3.5L18 3h-2l-1.5 6h-4L12 3h-2zm0 8h4l-1 4H9l1-4z",
  speaker: "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.5 4.5 0 0 0 16.5 12z",
  members:
    "M16 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm-8 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
  "chevron-left": "M15 6l1.4 1.4-4.6 4.6 4.6 4.6L15 18l-6-6z",
  "chevron-right": "M9 6l6 6-6 6-1.4-1.4 4.6-4.6-4.6-4.6z",
} as const;

export type IconName = keyof typeof paths;

export function Icon({ name }: { name: IconName }) {
  return (
    <svg className="sn-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="currentColor" d={paths[name]} />
    </svg>
  );
}

/** `hash` for a text channel, `speaker` for voice and voice-and-text channels. */
export function ChannelIcon({ channel }: { channel: Channel }) {
  return <Icon name={channel.type === "text" ? "hash" : "speaker"} />;
}
