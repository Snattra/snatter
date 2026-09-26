import type * as React from "react";

export type IconName = "hash" | "speaker" | "members" | "chevron-left" | "chevron-right" | "plus" | "close" | "send" | "arrow-right" | "arrow-up" | "person-add";
export type ChannelType = "text" | "voice" | "voice_text";
export type Presence = "online" | "offline";

/* ---------- Foundations ---------- */

export interface IconProps {
  name: IconName;
  /** Pixel size; defaults to the `icon-size` token (18px). */
  size?: number;
  className?: string;
}
/** A 24px-grid glyph that takes the text colour. Always decorative (aria-hidden); label the control that holds it. */
export declare function Icon(props: IconProps): React.ReactElement;
/** `hash` for a text channel, `speaker` for voice and voice-and-text channels. */
export declare function ChannelIcon(props: { type: ChannelType }): React.ReactElement;
/** A 14px ring in the current colour. */
export declare function Spinner(): React.ReactElement;

/* ---------- Actions ---------- */

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** primary: the one action of a screen; secondary (default); danger: ban, kick, delete; link: quiet text actions such as Sign out. */
  variant?: "primary" | "secondary" | "danger" | "link";
  size?: "md" | "sm";
  /** Shows a spinner and disables the button while an action runs. */
  busy?: boolean;
  /** Full width, as in the sign-in card. */
  block?: boolean;
}
export declare function Button(props: ButtonProps): React.ReactElement;

export interface IconButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  /** Required: becomes aria-label and the native tooltip. */
  label: string;
  icon?: IconName;
  /** For toggles such as the member-list button: sets aria-pressed. */
  pressed?: boolean;
  children?: React.ReactNode;
}
export declare function IconButton(props: IconButtonProps): React.ReactElement;

export interface TabsProps {
  tabs: { id: string; label: React.ReactNode }[];
  value: string;
  onChange?: (id: string) => void;
  /** aria-label of the tab list. */
  label?: string;
  className?: string;
}
/** A segmented control; the selected tab's fill slides to the new choice. Arrow keys move between tabs. */
export declare function Tabs(props: TabsProps): React.ReactElement;

/* ---------- Forms ---------- */

export interface FieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: React.ReactNode;
  /** Helper copy under the input, hidden while there is an error. */
  hint?: React.ReactNode;
  /** Marks the field invalid, shakes it once, and replaces the hint. Each new message shakes again. */
  error?: React.ReactNode;
  /** Appends "(optional)" to the label. */
  optional?: boolean;
}
export declare function Field(props: FieldProps): React.ReactElement;

export interface ComposerProps {
  placeholder?: string;
  /** Accessible name; defaults to the placeholder. */
  label?: string;
  /** Called with the trimmed text on Enter or the send button; the composer then clears. Shift+Enter adds a line. */
  onSend?: (text: string) => void;
  /** Shows the attach button when given. */
  onAttach?: () => void;
  /** Controlled use; leave both out to let the composer keep its own text. */
  value?: string;
  onChange?: (text: string) => void;
  disabled?: boolean;
  /** The line under the field, usually a TypingIndicator. */
  footer?: React.ReactNode;
}
export declare function Composer(props: ComposerProps): React.ReactElement;

/* ---------- Surfaces ---------- */

export interface CardProps {
  title?: React.ReactNode;
  titleAs?: "h1" | "h2" | "h3";
  description?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
}
/** A `panel` card that rises in on mount; its body stacks children with a 12px gap. */
export declare function Card(props: CardProps): React.ReactElement;
/** The full-screen `bg` ground with the faint accent glow, centring a Card. */
export declare function Backdrop(props: { children?: React.ReactNode; className?: string }): React.ReactElement;

export interface CalloutProps {
  title?: React.ReactNode;
  tone?: "accent" | "warning";
  children?: React.ReactNode;
  className?: string;
}
export declare function Callout(props: CalloutProps): React.ReactElement;

export interface TooltipProps {
  label: React.ReactNode;
  side?: "top" | "right" | "bottom";
  /** Exactly one focusable element; it gets aria-describedby. */
  children: React.ReactElement;
}
export declare function Tooltip(props: TooltipProps): React.ReactElement;

export interface PopoverProps {
  /** Give the same id to the control as `popovertarget`; generated when left out. */
  id?: string;
  title: React.ReactNode;
  description?: React.ReactNode;
  /** The control it opens from; the right edges line up. */
  anchorRef: React.RefObject<HTMLElement | null>;
  /** Above the control (the default) or below it. */
  side?: "top" | "bottom";
  /** Called as it opens and closes, including light dismiss and Escape. */
  onToggle?: (open: boolean) => void;
  className?: string;
  children?: React.ReactNode;
}
/** A small task on `floating` beside the control that opened it, in the top layer. Closes on Escape or a click outside. */
export declare function Popover(props: PopoverProps): React.ReactElement;

/* ---------- Invite ---------- */

export interface InviteButtonProps {
  /** The id of the Popover it opens. */
  popoverId: string;
  buttonRef?: React.Ref<HTMLButtonElement>;
  /** While its popover is open. */
  expanded?: boolean;
  /** Defaults to "Invite people". */
  label?: string;
}
/** A row at the foot of the member list that opens the invite popover. */
export declare function InviteButton(props: InviteButtonProps): React.ReactElement;

export interface InviteLinkProps {
  /** The link to share; leave out while it is being created. */
  url?: string;
  /** "Expires in 7 days." */
  expiry?: string;
  /** Why no link could be made; replaces everything else. */
  error?: string;
  onRetry?: () => void;
}
/** The link in a well, when it expires, and a Copy button that confirms. Goes in a Popover. */
export declare function InviteLink(props: InviteLinkProps): React.ReactElement;

/* ---------- Feedback ---------- */

export interface BannerProps {
  tone?: "warning" | "danger" | "accent";
  /** Adds a spinner, for states that resolve on their own (Reconnecting…). */
  busy?: boolean;
  children?: React.ReactNode;
}
/** A full-width strip at the top of the channel pane that slides down when it mounts. */
export declare function Banner(props: BannerProps): React.ReactElement;

export interface SkeletonProps {
  variant?: "line" | "circle" | "message";
  /** CSS width of a line, or of a message's second line. */
  width?: string;
}
export declare function Skeleton(props: SkeletonProps): React.ReactElement;

export interface TypingIndicatorProps {
  /** Display names of who is typing; empty renders the empty live region. */
  names?: string[];
  /** Dots only, for a member row. */
  compact?: boolean;
}
export declare function TypingIndicator(props: TypingIndicatorProps): React.ReactElement;

/* ---------- People ---------- */

export interface AvatarProps {
  name: string;
  /** Picture URL; without it the initials sit on a steady fill from `seed`. */
  src?: string;
  /** Usually the account id; defaults to the name. */
  seed?: string;
  /** Shows the presence dot. A change to online pings twice. */
  status?: Presence;
  size?: "sm" | "md" | "lg";
  /** The surface token the dot's ring cuts into; defaults to `panel`. */
  ring?: "bg" | "panel" | "panel-raised";
  className?: string;
}
export declare function Avatar(props: AvatarProps): React.ReactElement;
/** Up to two initials, as the app computes them. */
export declare function initials(name: string): string;
/** The avatar fill for a seed: the app's hash, as oklch(50% 0.06 hue). */
export declare function colourFor(seed: string): string;

export interface UserPanelProps {
  name: string;
  src?: string;
  seed?: string;
  status?: Presence;
  /** Replaces the "Online"/"Offline" line. */
  statusText?: React.ReactNode;
  /** Right-hand action, usually <Button variant="link">Sign out</Button>. */
  action?: React.ReactNode;
}
/** The signed-in account at the foot of the Sidebar. */
export declare function UserPanel(props: UserPanelProps): React.ReactElement;

export interface MemberProps {
  id: string;
  name: string;
  src?: string;
  seed?: string;
  online?: boolean;
  typing?: boolean;
  /** Role colour for the name, #rrggbb; shown only while online. */
  color?: string;
}
export declare function Member(props: MemberProps): React.ReactElement;

export interface MemberListProps {
  /** Rendered in order; an empty group is skipped. Headings read "Online — 3". */
  groups: { title: string; online?: boolean; members: MemberProps[] }[];
  /** Pinned under the list, such as an InviteButton. */
  footer?: React.ReactNode;
  label?: string;
  className?: string;
}
export declare function MemberList(props: MemberListProps): React.ReactElement;

/* ---------- Navigation ---------- */

export declare function ServerRail(props: { label?: string; children?: React.ReactNode }): React.ReactElement;
export declare function RailDivider(): React.ReactElement;
export interface RailServerProps {
  name: string;
  src?: string;
  /** An icon instead of initials, for actions such as adding a server. */
  icon?: IconName;
  /** action: an accent icon that fills with the sheen on hover, without the selection pill. */
  variant?: "server" | "action";
  selected?: boolean;
  unread?: boolean;
  onClick?: () => void;
}
/** A server icon: a circle at rest that settles into a rounded square, with the pill at the rail's edge (8px unread, 20px hover, 40px selected). */
export declare function RailServer(props: RailServerProps): React.ReactElement;

export interface SidebarProps {
  /** The community name in the 48px header. */
  name: React.ReactNode;
  /** Icons only, 56px. Pass the same value to AppShell. */
  collapsed?: boolean;
  /** Shows the collapse chevron. */
  onToggle?: () => void;
  /** A ChannelList. */
  children?: React.ReactNode;
  /** A UserPanel. */
  footer?: React.ReactNode;
  className?: string;
}
export declare function Sidebar(props: SidebarProps): React.ReactElement;
export declare function ChannelList(props: { label?: string; children?: React.ReactNode }): React.ReactElement;
export interface ChannelItemProps {
  name: string;
  type: ChannelType;
  selected?: boolean;
  unread?: boolean;
  onClick?: () => void;
}
export declare function ChannelItem(props: ChannelItemProps): React.ReactElement;

export interface ChannelHeaderProps {
  name: string;
  type: ChannelType;
  topic?: string | null;
  /** Header actions, usually IconButtons. */
  children?: React.ReactNode;
}
export declare function ChannelHeader(props: ChannelHeaderProps): React.ReactElement;

/* ---------- Messages ---------- */

export declare function MessageList(props: { label?: string; busy?: boolean; className?: string; children?: React.ReactNode }): React.ReactElement;

export interface MessageProps {
  author: string;
  src?: string;
  seed?: string;
  /** Role colour for the author, #rrggbb. */
  color?: string;
  /** false for a follow-up from the same author: no avatar or name, time in the gutter on hover. */
  head?: boolean;
  /** "Today at 18:31", shown beside the author. */
  time?: string;
  /** "18:31", shown in the gutter of a follow-up. */
  shortTime?: string;
  /** ISO timestamp for the time element. */
  dateTime?: string;
  /** Rises in. Set only for messages that arrive while the channel is open, not for history. */
  isNew?: boolean;
  /** pending: sent optimistically, dimmed until the server confirms; failed: tinted, with `error`. */
  state?: "sent" | "pending" | "failed";
  error?: React.ReactNode;
  /** Mentions the reader: a warning tint. */
  mentioned?: boolean;
  /** Hover toolbar, usually IconButtons. */
  actions?: React.ReactNode;
  children?: React.ReactNode;
}
export declare function Message(props: MessageProps): React.ReactElement;
export declare function SystemMessage(props: { time?: string; dateTime?: string; isNew?: boolean; children?: React.ReactNode }): React.ReactElement;

/* ---------- New messages ---------- */

/** The line above the first unread message. Goes between two rows of a MessageList. */
export declare function NewMessagesDivider(props: { label?: string }): React.ReactElement;

export interface UnreadBarProps {
  /** How many messages are unread, when known; otherwise the bar says "New messages". */
  count?: number | null;
  /** When the member stopped reading, "18:31" or "Yesterday at 18:31". */
  since?: string;
  /** Scrolls to the NewMessagesDivider. */
  onJump: () => void;
  /** Shows "Mark as read" when given. */
  onMarkRead?: () => void;
}
/** Over the top of the channel body while the first unread message is scrolled out of view above. */
export declare function UnreadBar(props: UnreadBarProps): React.ReactElement;

/* ---------- Layout ---------- */

export interface AppShellProps {
  rail?: React.ReactNode;
  sidebar?: React.ReactNode;
  /** Above the header: a Banner. */
  banner?: React.ReactNode;
  header?: React.ReactNode;
  /** Under the scrolling body: a Composer. */
  footer?: React.ReactNode;
  members?: React.ReactNode;
  /** The sidebar at 56px; animates. */
  collapsed?: boolean;
  /** The member list slides shut when false; it stays mounted and inert. */
  membersOpen?: boolean;
  style?: React.CSSProperties;
  className?: string;
  /** The scrolling channel body: a MessageList. */
  children?: React.ReactNode;
}
/** The four columns: rail, channels, channel, members. */
export declare function AppShell(props: AppShellProps): React.ReactElement;

declare global {
  interface Window {
    Snatter: {
      AppShell: typeof AppShell; ServerRail: typeof ServerRail; RailServer: typeof RailServer; RailDivider: typeof RailDivider;
      Sidebar: typeof Sidebar; ChannelList: typeof ChannelList; ChannelItem: typeof ChannelItem; ChannelIcon: typeof ChannelIcon;
      ChannelHeader: typeof ChannelHeader; MessageList: typeof MessageList; Message: typeof Message; SystemMessage: typeof SystemMessage;
      NewMessagesDivider: typeof NewMessagesDivider; UnreadBar: typeof UnreadBar;
      Composer: typeof Composer; TypingIndicator: typeof TypingIndicator; MemberList: typeof MemberList; Member: typeof Member;
      UserPanel: typeof UserPanel; Avatar: typeof Avatar; Button: typeof Button; IconButton: typeof IconButton; Tabs: typeof Tabs;
      Field: typeof Field; Card: typeof Card; Backdrop: typeof Backdrop; Callout: typeof Callout; Banner: typeof Banner;
      Tooltip: typeof Tooltip; Popover: typeof Popover; InviteButton: typeof InviteButton; InviteLink: typeof InviteLink; Skeleton: typeof Skeleton; Spinner: typeof Spinner; Icon: typeof Icon;
      initials: typeof initials; colourFor: typeof colourFor;
    };
  }
}
