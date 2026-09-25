import { useState } from "react";
import type { Account, Channel } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { type ServerView, sortedChannels, typingIn } from "../state/serverView";
import type { ServerEntry } from "../state/store";
import { useNow, usePreference } from "./hooks";
import { ChannelIcon, CollapseIcon, MembersIcon } from "./icons";

/**
 * The main screen, laid out left to right: the server rail, the channels of
 * the current server (collapsible to icons), the channel itself, and the
 * member list (which can be closed).
 */
export function ServerScreen({ connection, entry }: { connection: ServerConnection; entry: ServerEntry }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [channelsCollapsed, setChannelsCollapsed] = usePreference("channelsCollapsed", false);
  const [membersOpen, setMembersOpen] = usePreference("membersOpen", true);
  const view = entry.view;

  const channels = view === null ? [] : sortedChannels(view);
  const selected = channels.find((c) => c.id === selectedId) ?? channels[0] ?? null;
  const someoneTyping = view !== null && selected !== null && Object.keys(view.typing[selected.id] ?? {}).length > 0;
  const now = useNow(someoneTyping ? 1000 : null);

  if (view === null) {
    return <div className="center">Connecting…</div>;
  }

  const typing = new Set(selected === null ? [] : typingIn(view, selected.id, now).map((m) => m.id));

  return (
    <div className={`layout${channelsCollapsed ? " channels-collapsed" : ""}${membersOpen ? "" : " members-closed"}`}>
      <nav className="rail" aria-label="Servers">
        <button className="rail-server" aria-current="true" title={view.info.community.name}>
          {initials(view.info.community.name)}
        </button>
      </nav>

      <aside className="sidebar">
        <header className="community">
          {!channelsCollapsed && <span className="community-name">{view.info.community.name}</span>}
          <button
            className="icon-button"
            onClick={() => setChannelsCollapsed(!channelsCollapsed)}
            aria-label={channelsCollapsed ? "Expand channels" : "Collapse channels"}
            title={channelsCollapsed ? "Expand channels" : "Collapse channels"}
          >
            <CollapseIcon collapsed={channelsCollapsed} />
          </button>
        </header>
        <nav className="channels" aria-label="Channels">
          {channels.map((channel) => (
            <button
              key={channel.id}
              className="channel"
              aria-current={channel.id === selected?.id ? "page" : undefined}
              title={channelsCollapsed ? channel.name : undefined}
              onClick={() => setSelectedId(channel.id)}
            >
              <ChannelIcon channel={channel} />
              {!channelsCollapsed && <span className="channel-name">{channel.name}</span>}
            </button>
          ))}
        </nav>
        <footer className="me">
          <Avatar origin={connection.origin} account={view.account} online />
          {!channelsCollapsed && (
            <>
              <span className="me-name">{view.account.displayName}</span>
              <button className="link" onClick={() => void connection.logOut()}>
                Sign out
              </button>
            </>
          )}
        </footer>
      </aside>

      <main className="content">
        {entry.status === "reconnecting" && <div className="banner">Reconnecting…</div>}
        <header className="channel-header">
          {selected && <ChannelHeading channel={selected} />}
          <button
            className="icon-button"
            aria-pressed={membersOpen}
            onClick={() => setMembersOpen(!membersOpen)}
            aria-label={membersOpen ? "Hide members" : "Show members"}
            title={membersOpen ? "Hide members" : "Show members"}
          >
            <MembersIcon />
          </button>
        </header>
        <section className="channel-body" />
      </main>

      {membersOpen && <MemberList origin={connection.origin} view={view} typing={typing} />}
    </div>
  );
}

function ChannelHeading({ channel }: { channel: Channel }) {
  return (
    <div className="channel-heading">
      <ChannelIcon channel={channel} />
      <strong>{channel.name}</strong>
      {channel.topic && <span className="muted topic">{channel.topic}</span>}
    </div>
  );
}

function MemberList({ origin, view, typing }: { origin: string; view: ServerView; typing: Set<string> }) {
  const members = Object.values(view.members).sort((a, b) => a.displayName.localeCompare(b.displayName));
  const online = members.filter((m) => view.online[m.id]);
  const offline = members.filter((m) => !view.online[m.id]);
  return (
    <aside className="members" aria-label="Members">
      <MemberGroup title="Online" origin={origin} members={online} online typing={typing} />
      <MemberGroup title="Offline" origin={origin} members={offline} online={false} typing={typing} />
    </aside>
  );
}

function MemberGroup(props: { title: string; origin: string; members: Account[]; online: boolean; typing: Set<string> }) {
  const { title, origin, members, online, typing } = props;
  if (members.length === 0) {
    return null;
  }
  return (
    <section>
      <h2>
        {title} — {members.length}
      </h2>
      <ul>
        {members.map((m) => (
          <li key={m.id} className={online ? "member" : "member offline"}>
            <Avatar origin={origin} account={m} online={online} />
            <span className="member-name">{m.displayName}</span>
            {typing.has(m.id) && <span className="typing">typing…</span>}
          </li>
        ))}
      </ul>
    </section>
  );
}

function Avatar({ origin, account, online }: { origin: string; account: Account; online: boolean }) {
  return (
    <span className="avatar" style={account.avatarId ? undefined : { background: colourFor(account.id) }}>
      {account.avatarId ? (
        <img src={`${origin}/api/v1/blobs/${account.avatarId}`} alt="" />
      ) : (
        initials(account.displayName)
      )}
      <span className={online ? "status online" : "status"} aria-label={online ? "Online" : "Offline"} />
    </span>
  );
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return words
    .slice(0, 2)
    .map((w) => [...w][0] ?? "")
    .join("")
    .toUpperCase();
}

/** A steady colour per account for avatars without a picture. */
function colourFor(id: string): string {
  let hash = 0;
  for (const char of id) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  return `hsl(${Math.abs(hash) % 360} 45% 45%)`;
}
