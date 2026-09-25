import type { Channel } from "../api/types";

// Small inline icons; they take the text colour.

export function ChannelIcon({ channel }: { channel: Channel }) {
  if (channel.type === "text") {
    return (
      <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path
          fill="currentColor"
          d="M10 3 8.5 9H4v2h4l-1 4H3v2h3.5L5 21h2l1.5-4h4L11 21h2l1.5-4H19v-2h-4l1-4h4V9h-3.5L18 3h-2l-1.5 6h-4L12 3h-2zm0 8h4l-1 4H9l1-4z"
        />
      </svg>
    );
  }
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.5 4.5 0 0 0 16.5 12z" />
    </svg>
  );
}

export function MembersIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M16 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm-8 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
      />
    </svg>
  );
}

export function CollapseIcon({ collapsed }: { collapsed: boolean }) {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="currentColor" d={collapsed ? "M9 6l6 6-6 6-1.4-1.4 4.6-4.6-4.6-4.6z" : "M15 6l1.4 1.4-4.6 4.6 4.6 4.6L15 18l-6-6z"} />
    </svg>
  );
}
