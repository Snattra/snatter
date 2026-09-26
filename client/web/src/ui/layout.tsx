import { createContext, type ReactNode, useContext } from "react";
import type { Channel } from "../api/types";
import { classes } from "./classes";
import { IconButton } from "./controls";
import { ChannelIcon } from "./icons";
import { initials } from "./people";
import { Tooltip } from "./surfaces";

interface AppShellProps {
  rail: ReactNode;
  sidebar: ReactNode;
  /** Above the header, while the connection is degraded. */
  banner?: ReactNode;
  header: ReactNode;
  members: ReactNode;
  /** The channel sidebar narrowed to icons; pass the same to the Sidebar. */
  collapsed: boolean;
  membersOpen: boolean;
  /** The channel body, which scrolls. */
  children?: ReactNode;
}

/**
 * The four columns, left to right: server rail, channels, the channel, members.
 * Collapsing the sidebar and closing the member list animate the columns; the
 * member list stays mounted, inert, so it keeps its place.
 */
export function AppShell(props: AppShellProps) {
  const { rail, sidebar, banner, header, members, collapsed, membersOpen, children } = props;
  return (
    <div className={classes("sn-shell", collapsed && "sn-shell-collapsed", !membersOpen && "sn-shell-members-closed")}>
      {rail}
      {sidebar}
      <main className="sn-shell-content">
        {banner}
        {header}
        <div className="sn-shell-body">{children}</div>
      </main>
      <div className="sn-shell-members" inert={!membersOpen}>
        {members}
      </div>
    </div>
  );
}

export function ServerRail({ children }: { children?: ReactNode }) {
  return (
    <nav className="sn-rail" aria-label="Servers">
      {children}
    </nav>
  );
}

/** A server icon: a circle that settles into a rounded square, with the pill at the rail's edge. */
export function RailServer({ name, selected = false, onClick }: { name: string; selected?: boolean; onClick?: () => void }) {
  return (
    <div className={classes("sn-rail-item", selected && "sn-rail-item-selected")}>
      <span className="sn-rail-pill" aria-hidden="true" />
      <Tooltip label={name} side="right">
        <button type="button" className="sn-rail-server" aria-label={name} aria-current={selected || undefined} onClick={onClick}>
          {initials(name)}
        </button>
      </Tooltip>
    </div>
  );
}

const Collapsed = createContext(false);

interface SidebarProps {
  /** The community name in the header. */
  name: ReactNode;
  collapsed: boolean;
  onToggle?: () => void;
  /** A ChannelList. */
  children: ReactNode;
  /** A UserPanel. */
  footer?: ReactNode;
}

/** The channel pane. Collapsed, its labels fade while icons and avatars stay where they are. */
export function Sidebar({ name, collapsed, onToggle, children, footer }: SidebarProps) {
  return (
    <Collapsed.Provider value={collapsed}>
      <aside className={classes("sn-sidebar", collapsed && "sn-collapsed")}>
        <header className="sn-pane-header">
          <span className="sn-truncate sn-collapse-fade">{name}</span>
          {onToggle && (
            <IconButton
              icon={collapsed ? "chevron-right" : "chevron-left"}
              label={collapsed ? "Expand channels" : "Collapse channels"}
              onClick={onToggle}
            />
          )}
        </header>
        {children}
        {footer}
      </aside>
    </Collapsed.Provider>
  );
}

export function ChannelList({ children }: { children: ReactNode }) {
  return (
    <nav className="sn-channels" aria-label="Channels">
      {children}
    </nav>
  );
}

export function ChannelItem({ channel, selected, onClick }: { channel: Channel; selected: boolean; onClick: () => void }) {
  const collapsed = useContext(Collapsed);
  return (
    <button
      type="button"
      className="sn-channel"
      aria-current={selected ? "page" : undefined}
      title={collapsed ? channel.name : undefined}
      onClick={onClick}
    >
      <ChannelIcon channel={channel} />
      <span className="sn-channel-name sn-truncate sn-collapse-fade">{channel.name}</span>
    </button>
  );
}

/** The bar over a channel: its icon, name and topic, and actions on the right. */
export function ChannelHeader({ channel, children }: { channel: Channel | null; children: ReactNode }) {
  return (
    <header className="sn-channel-header">
      {channel && (
        <div className="sn-channel-heading">
          <ChannelIcon channel={channel} />
          <strong>{channel.name}</strong>
          {channel.topic && <span className="sn-topic sn-truncate">{channel.topic}</span>}
        </div>
      )}
      <div className="sn-header-actions">{children}</div>
    </header>
  );
}
