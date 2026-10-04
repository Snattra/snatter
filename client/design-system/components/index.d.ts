import type * as React from "react";

export type IconName = "hash" | "speaker" | "members" | "chevron-left" | "chevron-right" | "plus" | "close" | "send" | "arrow-right" | "arrow-up" | "person-add" | "settings" | "chevron-down" | "check" | "ban" | "edit" | "delete" | "mic" | "mic-off" | "headset" | "headset-off" | "call-end";
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

export interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: React.ReactNode;
  hint?: React.ReactNode;
  error?: React.ReactNode;
  optional?: boolean;
}
/** A Field around a textarea, three lines tall by default, for descriptions and topics. The member can make it taller. */
export declare function TextArea(props: TextAreaProps): React.ReactElement;

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: React.ReactNode;
  hint?: React.ReactNode;
  error?: React.ReactNode;
  optional?: boolean;
  /** The options. */
  children?: React.ReactNode;
}
/** A Field around a native select, its arrow drawn as `chevron-down`. For one of a list that is too long, or too plain, for Choices. */
export declare function Select(props: SelectProps): React.ReactElement;

export interface ChoiceProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> {
  /** radio: one of a set sharing a `name`; checkbox: on or off by itself. */
  type: "radio" | "checkbox";
  label: React.ReactNode;
  /** One line under the label, in `muted`; screen readers hear it after the label. */
  description?: React.ReactNode;
  /** Beside the label, such as a channel type; it takes the accent when chosen. */
  icon?: IconName;
}
/** A radio or checkbox as a row: a mark that fills with the sheen when chosen, a label and an optional description. */
export declare function Choice(props: ChoiceProps): React.ReactElement;

export interface ChoiceGroupProps {
  /** Names the set, like a field's label. */
  legend: React.ReactNode;
  hint?: React.ReactNode;
  /** Replaces the hint and edges the empty marks in `danger`. */
  error?: React.ReactNode;
  className?: string;
  /** Choices. */
  children?: React.ReactNode;
}
/** A fieldset of Choices under a legend. */
export declare function ChoiceGroup(props: ChoiceGroupProps): React.ReactElement;

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

export interface TagProps {
  /** A role's colour for the dot, #rrggbb; null shows a `muted` dot, and leaving it out shows none. */
  color?: string | null;
  /** `accent-text` on `accent-soft`, for what should stand out without being chosen, such as Owner. */
  accent?: boolean;
  className?: string;
  children?: React.ReactNode;
}
/** A small pill naming something about a person, such as a role. Gather several in a `ul.sn-tags`. */
export declare function Tag(props: TagProps): React.ReactElement;

export interface TooltipProps {
  label: React.ReactNode;
  /** `left` for a mark at the end of a row, such as a banned member's. */
  side?: "top" | "right" | "bottom" | "left";
  /** Exactly one element, usually focusable; it gets aria-describedby. A mark inside a control needs role="img" and an aria-label. */
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

export interface ModalProps {
  id?: string;
  /** In `text-title`, sentence case: "Create channel". */
  title: React.ReactNode;
  /** One line under the title, such as the channel it is about. */
  description?: React.ReactNode;
  /** The close button, Escape and Cancel call this; the owner then unmounts the modal. */
  onClose?: () => void;
  /** Wraps the body and footer in a form, with the browser's own validation off; the footer's submit button sends it. */
  onSubmit?: (event: React.FormEvent<HTMLFormElement>) => void;
  /** Under the title, outside the scrolling body: Tabs. */
  tabs?: React.ReactNode;
  /** What went wrong, in `danger-text` above the footer. */
  error?: React.ReactNode;
  /** Buttons at the right: Cancel, then the primary action. */
  footer?: React.ReactNode;
  /** A destructive action at the footer's other end, such as Delete channel. */
  footerStart?: React.ReactNode;
  /** 560px instead of 480px, for settings with Tabs. */
  wide?: boolean;
  /** Beside the title, such as the member's Avatar in a profile. */
  leading?: React.ReactNode;
  /** The step shown, for a modal whose content changes (a question before banning); a new step focuses its `data-autofocus` control. */
  step?: string;
  className?: string;
  /** The body, which scrolls: Fields, Choices. Put `data-autofocus` on the control to focus first. */
  children?: React.ReactNode;
}
/** A task over the app: a native modal <dialog> on the scrim, open while it is mounted. Escape closes it; a click outside does not. */
export declare function Modal(props: ModalProps): React.ReactElement;

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
  /** Faded, without a presence dot, and marked with `ban`. */
  banned?: boolean;
  /** The ban mark's tooltip; defaults to "Banned". */
  bannedText?: string;
  /** Opens the member's profile. */
  onClick?: () => void;
}
/** A row that opens the member's profile. */
export declare function Member(props: MemberProps): React.ReactElement;

export interface MemberListProps {
  /** Rendered in order; an empty group is skipped. Headings read "Online — 3". */
  groups: { title: string; online?: boolean; banned?: boolean; members: MemberProps[] }[];
  /** Called with a member's id when their row is clicked, to open their profile. */
  onOpen?: (id: string) => void;
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
  /** IconButtons beside the name, such as Server settings; they fold away when collapsed. */
  actions?: React.ReactNode;
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
export interface ChannelActionProps {
  /** "Create channel"; also the tooltip when collapsed. */
  label: string;
  /** Defaults to `plus`. */
  icon?: IconName;
  onClick?: () => void;
}
/** An action at the end of a ChannelList, shaped like a channel row and always `muted`. */
export declare function ChannelAction(props: ChannelActionProps): React.ReactElement;

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
  /** Makes the author's name (and, for the mouse, avatar) open their profile. */
  onAuthor?: () => void;
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
      Sidebar: typeof Sidebar; ChannelList: typeof ChannelList; ChannelItem: typeof ChannelItem; ChannelAction: typeof ChannelAction; ChannelIcon: typeof ChannelIcon;
      ChannelHeader: typeof ChannelHeader; MessageList: typeof MessageList; Message: typeof Message; SystemMessage: typeof SystemMessage;
      NewMessagesDivider: typeof NewMessagesDivider; UnreadBar: typeof UnreadBar;
      Composer: typeof Composer; TypingIndicator: typeof TypingIndicator; MemberList: typeof MemberList; Member: typeof Member;
      UserPanel: typeof UserPanel; Avatar: typeof Avatar; Button: typeof Button; IconButton: typeof IconButton; Tabs: typeof Tabs;
      Field: typeof Field; TextArea: typeof TextArea; Select: typeof Select; Choice: typeof Choice; ChoiceGroup: typeof ChoiceGroup; Card: typeof Card; Backdrop: typeof Backdrop; Callout: typeof Callout; Tag: typeof Tag; Banner: typeof Banner;
      Tooltip: typeof Tooltip; Popover: typeof Popover; Modal: typeof Modal; InviteButton: typeof InviteButton; InviteLink: typeof InviteLink; Skeleton: typeof Skeleton; Spinner: typeof Spinner; Icon: typeof Icon;
      initials: typeof initials; colourFor: typeof colourFor;
    };
  }
}
