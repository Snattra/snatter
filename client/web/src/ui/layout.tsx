import { createContext, type ReactNode, useContext } from "react";
import type { Channel } from "../api/types";
import { classes } from "./classes";
import { IconButton } from "./controls";
import { ChannelIcon, Icon, type IconName } from "./icons";
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
  /** Under the body: the Composer. */
  footer?: ReactNode;
}

/**
 * The four columns, left to right: server rail, channels, the channel, members.
 * Collapsing the sidebar and closing the member list animate the columns; the
 * member list stays mounted, inert, so it keeps its place.
 */
export function AppShell(props: AppShellProps) {
  const { rail, sidebar, banner, header, members, collapsed, membersOpen, children, footer } = props;
  return (
    <div className={classes("sn-shell", collapsed && "sn-shell-collapsed", !membersOpen && "sn-shell-members-closed")}>
      {rail}
      {sidebar}
      <main className="sn-shell-content">
        {banner}
        {header}
        <div className="sn-shell-body">{children}</div>
        {footer}
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

/** Whether the channel sidebar around this is narrowed to icons, so rows can give their name as a tooltip. */
export function useCollapsed(): boolean {
  return useContext(Collapsed);
}

interface SidebarProps {
  /** The community name in the header. */
  name: ReactNode;
  collapsed: boolean;
  onToggle?: () => void;
  /** IconButtons beside the name, such as Server settings; they fold away with it when collapsed. */
  actions?: ReactNode;
  /** A ChannelList. */
  children: ReactNode;
  /** A UserPanel. */
  footer?: ReactNode;
}

/** The channel pane. Collapsed, its labels fade while icons and avatars stay where they are. */
export function Sidebar({ name, collapsed, onToggle, actions, children, footer }: SidebarProps) {
  return (
    <Collapsed.Provider value={collapsed}>
      <aside className={classes("sn-sidebar", collapsed && "sn-collapsed")}>
        <header className="sn-pane-header">
          <span className="sn-pane-header-name sn-truncate sn-collapse-fade">{name}</span>
          {actions && <span className="sn-pane-header-actions sn-collapse-fade">{actions}</span>}
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

interface ChannelItemProps {
  channel: Channel;
  selected: boolean;
  /** Has messages the member has not read: bold, with a small pill at the pane's edge. */
  unread: boolean;
  onClick: () => void;
}

export function ChannelItem({ channel, selected, unread, onClick }: ChannelItemProps) {
  const collapsed = useContext(Collapsed);
  return (
    <button
      type="button"
      className={classes("sn-channel", unread && "sn-channel-unread")}
      aria-current={selected ? "page" : undefined}
      title={collapsed ? channel.name : undefined}
      onClick={onClick}
    >
      <ChannelIcon channel={channel} />
      <span className="sn-channel-name sn-truncate sn-collapse-fade">{channel.name}</span>
      {unread && <span className="sn-visually-hidden">, unread</span>}
    </button>
  );
}

interface ChannelActionProps {
  icon: IconName;
  /** Also the tooltip while the sidebar is collapsed. */
  label: string;
  onClick: () => void;
}

/** An action at the end of the channel list, such as Create channel: a row that opens a modal. */
export function ChannelAction({ icon, label, onClick }: ChannelActionProps) {
  const collapsed = useContext(Collapsed);
  return (
    <button
      type="button"
      className="sn-channel sn-channel-action"
      aria-haspopup="dialog"
      title={collapsed ? label : undefined}
      onClick={onClick}
    >
      <Icon name={icon} />
      <span className="sn-channel-name sn-truncate sn-collapse-fade">{label}</span>
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
