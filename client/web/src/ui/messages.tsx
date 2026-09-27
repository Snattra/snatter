import { type ReactNode, type Ref, useRef, useState } from "react";
import type { Account, SystemMessage as SystemMessageData, UserMessage } from "../api/types";
import type { Pending } from "../state/channelLog";
import { classes } from "./classes";
import { Button, IconButton } from "./controls";
import { Icon } from "./icons";
import { Avatar } from "./people";
import { longTime, shortTime } from "./time";

export function MessageList({ busy = false, children }: { busy?: boolean; children: ReactNode }) {
  return (
    <div className="sn-messages" role="log" aria-label="Messages" aria-busy={busy || undefined}>
      {children}
    </div>
  );
}

interface MessageProps {
  origin: string;
  /** Undefined when the author's account is gone or unknown. */
  author: Account | undefined;
  createdAt: string;
  /** Starts a group with avatar, name and time; follow-ups show their time in the gutter on hover. */
  head: boolean;
  /** Rises in; only for messages that arrive while the channel is open. */
  isNew?: boolean;
  state?: "sent" | "pending" | "failed";
  /** Under a failed message: what went wrong and what to do. */
  error?: ReactNode;
  /** Opens the author's profile from their name, and their avatar for the mouse. */
  onAuthor?: () => void;
  children: ReactNode;
}

/** One message in a channel. */
export function Message(props: MessageProps) {
  const { origin, author, createdAt, head, isNew = false, state = "sent", error, onAuthor, children } = props;
  const at = new Date(createdAt);
  const name = author?.displayName ?? "Deleted account";
  return (
    <div
      className={classes(
        "sn-message",
        head && "sn-message-head",
        isNew && "sn-message-new",
        state === "pending" && "sn-message-pending",
        state === "failed" && "sn-message-failed",
      )}
      role="article"
      aria-label={name}
    >
      {head &&
        author &&
        (onAuthor ? (
          // Keyboards reach the profile through the name, so the avatar adds no stop of its own.
          <button type="button" className="sn-message-avatar" tabIndex={-1} aria-hidden="true" onClick={onAuthor}>
            <Avatar origin={origin} account={author} size="lg" />
          </button>
        ) : (
          <Avatar origin={origin} account={author} size="lg" />
        ))}
      {head ? (
        <div className="sn-message-meta">
          {onAuthor ? (
            <button
              type="button"
              className="sn-message-author sn-name-button"
              aria-haspopup="dialog"
              onClick={onAuthor}
            >
              {name}
            </button>
          ) : (
            <span className="sn-message-author">{name}</span>
          )}
          <time className="sn-message-time" dateTime={createdAt}>
            {longTime(at, new Date())}
          </time>
        </div>
      ) : (
        <time className="sn-message-gutter-time" dateTime={createdAt}>
          {shortTime(at)}
        </time>
      )}
      <div className="sn-message-content">{children}</div>
      {state === "failed" && <span className="sn-message-error">{error}</span>}
    </div>
  );
}

export function UserMessageRow(props: {
  origin: string;
  message: UserMessage;
  author: Account | undefined;
  head: boolean;
  isNew: boolean;
  onAuthor: (() => void) | undefined;
}) {
  const { origin, message, author, head, isNew, onAuthor } = props;
  return (
    <Message
      origin={origin}
      author={author}
      createdAt={message.createdAt}
      head={head}
      isNew={isNew}
      onAuthor={onAuthor}
    >
      {message.content}
    </Message>
  );
}

/** A message on its way, or one that failed with the choice to send it again or let it go. */
export function PendingRow(props: {
  origin: string;
  pending: Pending;
  author: Account;
  head: boolean;
  onAuthor: (() => void) | undefined;
  onRetry: () => void;
  onDiscard: () => void;
}) {
  const { origin, pending, author, head, onAuthor, onRetry, onDiscard } = props;
  const failed = pending.error !== null;
  return (
    <Message
      origin={origin}
      author={author}
      createdAt={pending.createdAt}
      head={head}
      isNew
      state={failed ? "failed" : "pending"}
      onAuthor={onAuthor}
      error={
        <>
          Not sent: {pending.error}
          <Button variant="link" onClick={onRetry}>
            Try again
          </Button>
          <Button variant="link" onClick={onDiscard}>
            Discard
          </Button>
        </>
      }
    >
      {pending.content}
    </Message>
  );
}

/** A notice the server wrote, as one muted line behind an accent arrow. */
export function SystemMessageRow(props: {
  message: SystemMessageData;
  author: Account | undefined;
  isNew: boolean;
  onAuthor: (() => void) | undefined;
}) {
  const { message, author, isNew, onAuthor } = props;
  const who =
    author && onAuthor ? (
      <button type="button" className="sn-name-button" aria-haspopup="dialog" onClick={onAuthor}>
        {author.displayName}
      </button>
    ) : (
      <strong>{author?.displayName ?? "Someone"}</strong>
    );
  return (
    <div className={classes("sn-message sn-message-system", isNew && "sn-message-new")} role="article">
      <Icon name="arrow-right" />
      <span>{noticeText(message, who)}</span>
      <time className="sn-message-time" dateTime={message.createdAt}>
        {longTime(new Date(message.createdAt), new Date())}
      </time>
    </div>
  );
}

function noticeText(message: SystemMessageData, who: ReactNode): ReactNode {
  const notice = message.notice;
  switch (notice.type) {
    case "channel_created":
      return <>{who} created the channel.</>;
    case "channel_renamed":
      return (
        <>
          {who} renamed the channel from <strong>{notice.from}</strong> to <strong>{notice.to}</strong>.
        </>
      );
    case "channel_topic_changed":
      return notice.to ? (
        <>
          {who} set the topic to <strong>{notice.to}</strong>.
        </>
      ) : (
        <>{who} cleared the topic.</>
      );
    case "member_joined":
      return <>{who} joined the server.</>;
    case "server_renamed":
      return (
        <>
          {who} renamed the server to <strong>{notice.to}</strong>.
        </>
      );
    case "registration_mode_changed":
      return notice.to === "open" ? <>{who} opened registration to everyone.</> : <>{who} made registration invite only.</>;
    default:
      // A notice type newer than this app.
      return <>{who} changed something.</>;
  }
}

/** The line above the first unread message. */
export function NewMessagesDivider({ ref }: { ref?: Ref<HTMLDivElement> }) {
  return (
    <div ref={ref} className="sn-new-divider" role="separator" aria-label="New messages">
      New
    </div>
  );
}

interface UnreadBarProps {
  /** Null when not every unread message is held. */
  count: number | null;
  since: string | null;
  onJump: () => void;
  onMarkRead: () => void;
}

/** Over the top of the channel while the first unread message is scrolled out of view above. */
export function UnreadBar({ count, since, onJump, onMarkRead }: UnreadBarProps) {
  return (
    <div className="sn-unread-bar" role="status">
      <span className="sn-unread-bar-text">
        {count === null ? (
          "New messages"
        ) : (
          <>
            <strong>{count}</strong> {count === 1 ? "new message" : "new messages"}
          </>
        )}
        {since && ` since ${since}`}
      </span>
      <Button variant="link" size="sm" onClick={onMarkRead}>
        Mark as read
      </Button>
      <Button size="sm" onClick={onJump}>
        <Icon name="arrow-up" />
        Jump to first unread
      </Button>
    </div>
  );
}

interface ComposerProps {
  placeholder: string;
  disabled?: boolean;
  /** Called with the trimmed text; the composer then clears. */
  onSend: (text: string) => void;
  /** Called as the member types, with the current text. */
  onChange?: (text: string) => void;
  /** The line under the field: the TypingIndicator. */
  footer: ReactNode;
}

/**
 * The message field at the foot of a channel. Enter sends, Shift+Enter adds
 * a line, and composing with an IME never sends. The send button springs in
 * once there is something to send.
 */
export function Composer({ placeholder, disabled = false, onSend, onChange, footer }: ComposerProps) {
  const [text, setText] = useState("");
  const field = useRef<HTMLTextAreaElement>(null);
  const ready = text.trim() !== "" && !disabled;

  function send() {
    const content = text.trim();
    if (content === "" || disabled) {
      return;
    }
    onSend(content);
    setText("");
    field.current?.focus();
  }

  return (
    <div className="sn-composer-area">
      <div className={classes("sn-composer", ready && "sn-composer-ready")}>
        <textarea
          ref={field}
          rows={1}
          value={text}
          placeholder={placeholder}
          aria-label={placeholder}
          disabled={disabled}
          maxLength={4000}
          onChange={(event) => {
            setText(event.target.value);
            onChange?.(event.target.value);
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
              event.preventDefault();
              send();
            }
          }}
        />
        <IconButton icon="send" label="Send" className="sn-composer-send" tabIndex={ready ? 0 : -1} onClick={send} />
      </div>
      <div className="sn-composer-foot">{footer}</div>
    </div>
  );
}
