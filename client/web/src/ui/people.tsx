import { type ReactNode, useEffect, useState } from "react";
import type { Account } from "../api/types";
import { classes } from "./classes";

interface AvatarProps {
  origin: string;
  account: Account;
  /** Shows the presence dot; leave out where presence does not matter, as beside a message. */
  online?: boolean;
  /** Pings the presence dot once as it mounts, for someone who has just come online. */
  arrived?: boolean;
  /** `lg` heads a message group. */
  size?: "md" | "lg";
}

export function Avatar({ origin, account, online, arrived = false, size = "md" }: AvatarProps) {
  return (
    <span className={classes("sn-avatar", size === "lg" && "sn-avatar-lg", online === false && "sn-avatar-offline")}>
      <span className="sn-avatar-face" style={account.avatarId ? undefined : { background: colourFor(account.id) }}>
        {account.avatarId ? <img src={`${origin}/api/v1/blobs/${account.avatarId}`} alt="" /> : initials(account.displayName)}
      </span>
      {online !== undefined && (
        <span
          className={classes("sn-status", online && "sn-status-online", online && arrived && "sn-status-announce")}
          role="img"
          aria-label={online ? "Online" : "Offline"}
        />
      )}
    </span>
  );
}

/**
 * Who is typing in the channel, under the composer: "**Teal** is typing…".
 * It stays mounted while nobody types so screen readers hear the next change.
 */
export function TypingIndicator({ names }: { names: string[] }) {
  if (names.length === 0) {
    return <span className="sn-typing" role="status" aria-live="polite" />;
  }
  return (
    <span className="sn-typing" role="status" aria-live="polite">
      <span className="sn-typing-dots" aria-hidden="true">
        <span className="sn-typing-dot" />
        <span className="sn-typing-dot" />
        <span className="sn-typing-dot" />
      </span>
      <span>{typingText(names)}</span>
    </span>
  );
}

function typingText(names: string[]): ReactNode {
  const [a, b, c] = names;
  switch (names.length) {
    case 1:
      return (
        <>
          <strong>{a}</strong> is typing…
        </>
      );
    case 2:
      return (
        <>
          <strong>{a}</strong> and <strong>{b}</strong> are typing…
        </>
      );
    case 3:
      return (
        <>
          <strong>{a}</strong>, <strong>{b}</strong> and <strong>{c}</strong> are typing…
        </>
      );
    default:
      return "Several people are typing…";
  }
}

/** Three dots in the sheen, bouncing in turn, for someone typing in the current channel. */
export function TypingDots() {
  return (
    <span className="sn-typing" role="img" aria-label="typing">
      <span className="sn-typing-dots" aria-hidden="true">
        <span className="sn-typing-dot" />
        <span className="sn-typing-dot" />
        <span className="sn-typing-dot" />
      </span>
    </span>
  );
}

interface MemberListProps {
  origin: string;
  online: Account[];
  offline: Account[];
  typing: Set<string>;
  /** Pinned under the list, such as the InviteButton. */
  footer?: ReactNode;
}

/** Members under "Online" and "Offline" headings; someone coming online moves up with a ping. */
export function MemberList({ origin, online, offline, typing, footer }: MemberListProps) {
  // Members on the list when it first renders are simply there; only later arrivals ping.
  const [settled, setSettled] = useState(false);
  useEffect(() => setSettled(true), []);
  return (
    <aside className="sn-members" aria-label="Members">
      <div className="sn-members-list">
        <MemberGroup title="Online" origin={origin} members={online} online typing={typing} settled={settled} />
        <MemberGroup title="Offline" origin={origin} members={offline} online={false} typing={typing} settled={settled} />
      </div>
      {footer && <div className="sn-members-foot">{footer}</div>}
    </aside>
  );
}

function MemberGroup(props: {
  title: string;
  origin: string;
  members: Account[];
  online: boolean;
  typing: Set<string>;
  settled: boolean;
}) {
  const { title, origin, members, online, typing, settled } = props;
  if (members.length === 0) {
    return null;
  }
  return (
    <section className="sn-member-group">
      <h2 className="sn-member-group-title">
        {title} — {members.length}
      </h2>
      <ul>
        {members.map((m) => (
          <Member key={m.id} origin={origin} account={m} online={online} typing={typing.has(m.id)} arrived={settled && online} />
        ))}
      </ul>
    </section>
  );
}

function Member(props: { origin: string; account: Account; online: boolean; typing: boolean; arrived: boolean }) {
  const { origin, account, online, typing } = props;
  // Fixed when the row mounts: a row that moved into Online after the first render pings once.
  const [arrived] = useState(props.arrived);
  return (
    <li className={classes("sn-member", !online && "sn-member-offline")}>
      <Avatar origin={origin} account={account} online={online} arrived={arrived} />
      <span className="sn-member-name sn-truncate">{account.displayName}</span>
      {typing && <TypingDots />}
    </li>
  );
}

/** The signed-in account at the foot of the channel sidebar. */
export function UserPanel({ origin, account, action }: { origin: string; account: Account; action: ReactNode }) {
  return (
    <footer className="sn-user-panel">
      <Avatar origin={origin} account={account} online />
      <span className="sn-user-panel-text sn-collapse-fade">
        <span className="sn-user-panel-name sn-truncate">{account.displayName}</span>
        <span className="sn-user-panel-status">Online</span>
      </span>
      <span className="sn-collapse-fade">{action}</span>
    </footer>
  );
}

export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return words
    .slice(0, 2)
    .map((w) => [...w][0] ?? "")
    .join("")
    .toUpperCase();
}

/**
 * A steady colour per account for avatars without a picture: a hue from the
 * id, at one perceived lightness so the initials read on every hue.
 */
export function colourFor(id: string): string {
  let hash = 0;
  for (const char of id) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  return `oklch(50% 0.06 ${Math.abs(hash) % 360})`;
}
