import { type ReactNode, type Ref, useId, useLayoutEffect, useRef, useState } from "react";
import type { Account, DeletedMessage, SystemMessage as SystemMessageData, UserMessage } from "../api/types";
import type { Pending } from "../state/channelLog";
import { insertMention, type MentionQuery, queryAt } from "../state/mentions";
import { classes } from "./classes";
import { Button, IconButton } from "./controls";
import { Icon } from "./icons";
import { MessageText, type TextContext } from "./messageText";
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
  /** Warmed with the mention gradient: it mentions the member reading it. */
  mentioned?: boolean;
  state?: "sent" | "pending" | "failed";
  /** Under a failed message: what went wrong and what to do. */
  error?: ReactNode;
  /** Opens the author's profile from their name, and their avatar for the mouse. */
  onAuthor?: () => void;
  /** Icon buttons for what the member may do to it, shown on hover and focus. */
  actions?: ReactNode;
  children: ReactNode;
}

/** One message in a channel. */
export function Message(props: MessageProps) {
  const { origin, author, createdAt, head, isNew = false, mentioned = false, state = "sent", error, onAuthor, actions, children } =
    props;
  const at = new Date(createdAt);
  const name = author?.displayName ?? "Deleted account";
  return (
    <div
      className={classes(
        "sn-message",
        head && "sn-message-head",
        isNew && "sn-message-new",
        mentioned && "sn-message-mentioned",
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
      {actions && (
        <div className="sn-message-actions" role="toolbar" aria-label="Message actions">
          {actions}
        </div>
      )}
    </div>
  );
}

export function UserMessageRow(props: {
  origin: string;
  message: UserMessage;
  author: Account | undefined;
  head: boolean;
  isNew: boolean;
  mentioned: boolean;
  onAuthor: (() => void) | undefined;
  text: TextContext;
  actions?: ReactNode;
  /** Takes the place of the text while the member edits it. */
  editor?: ReactNode;
}) {
  const { origin, message, author, head, isNew, mentioned, onAuthor, text, actions, editor } = props;
  return (
    <Message
      origin={origin}
      author={author}
      createdAt={message.createdAt}
      head={head}
      isNew={isNew}
      mentioned={mentioned && !editor}
      onAuthor={onAuthor}
      actions={editor ? undefined : actions}
    >
      {editor ?? (
        <MessageText
          content={message.content}
          context={text}
          suffix={
            message.editedAt && (
              <time className="sn-message-edited" dateTime={message.editedAt} title={longTime(new Date(message.editedAt), new Date())}>
                (edited)
              </time>
            )
          }
        />
      )}
    </Message>
  );
}

/**
 * Where a member's messages were deleted, one or several in a row: the
 * author and the time of the first stay, and a muted line says what happened.
 */
export function DeletedMessageRow(props: {
  origin: string;
  messages: [DeletedMessage, ...DeletedMessage[]];
  author: Account | undefined;
  head: boolean;
  onAuthor: (() => void) | undefined;
}) {
  const { origin, messages, author, head, onAuthor } = props;
  const [first] = messages;
  return (
    <Message origin={origin} author={author} createdAt={first.createdAt} head={head} onAuthor={onAuthor}>
      <span className="sn-message-deleted">
        <Icon name="delete" />
        {deletedText(messages.length, first.removedByModerator)}
      </span>
    </Message>
  );
}

function deletedText(count: number, byModerator: boolean): string {
  if (count === 1) {
    return byModerator ? "Removed by a moderator." : "This message was deleted.";
  }
  return byModerator ? `${count} messages removed by a moderator.` : `${count} messages deleted.`;
}

/** A message on its way, or one that failed with the choice to send it again or let it go. */
export function PendingRow(props: {
  origin: string;
  pending: Pending;
  author: Account;
  head: boolean;
  onAuthor: (() => void) | undefined;
  text: TextContext;
  onRetry: () => void;
  onDiscard: () => void;
}) {
  const { origin, pending, author, head, onAuthor, text, onRetry, onDiscard } = props;
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
      <MessageText content={pending.content} context={text} />
    </Message>
  );
}

/** A notice the server wrote, as one muted line behind an accent arrow. */
export function SystemMessageRow(props: {
  message: SystemMessageData;
  author: Account | undefined;
  isNew: boolean;
  onAuthor: (() => void) | undefined;
  actions?: ReactNode;
}) {
  const { message, author, isNew, onAuthor, actions } = props;
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
      {actions && (
        <div className="sn-message-actions" role="toolbar" aria-label="Message actions">
          {actions}
        </div>
      )}
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

/** Something the composer offers to mention while `@` or `#` is being typed. */
export interface Suggestion {
  id: string;
  /** The member's avatar or the channel's icon. */
  leading: ReactNode;
  label: string;
  /** After the label in `muted`, such as the username. */
  detail?: string;
  /** What replaces the query in the text, such as `@wigeon`. */
  insert: string;
}

interface ComposerProps {
  placeholder: string;
  disabled?: boolean;
  /**
   * `send` at the foot of a channel. `edit` in place of a message's text:
   * it starts with `initialText` and focus, keeps its text after
   * `onSend` (so a failed save loses nothing), and Escape calls `onCancel`.
   */
  mode?: "send" | "edit";
  initialText?: string;
  onCancel?: () => void;
  /** Up in the empty field, to edit the member's last message; `send` mode only. */
  onEditLast?: () => void;
  /** Called with the trimmed text; in `send` mode the composer then clears. */
  onSend: (text: string) => void;
  /** Called as the member types, with the current text. */
  onChange?: (text: string) => void;
  /** What to offer for the mention being typed, best first; none hides the list. */
  suggest?: (query: MentionQuery) => Suggestion[];
  /** The line under the field: the TypingIndicator. */
  footer: ReactNode;
}

/**
 * The message field at the foot of a channel. Enter sends, Shift+Enter adds
 * a line, and composing with an IME never sends. The send button springs in
 * once there is something to send.
 *
 * Typing `@` or `#` at the start of a word lists what `suggest` offers
 * above the field. The arrow keys move through it, Enter or Tab puts the
 * choice in the text, and Escape closes it until the next mention.
 */
export function Composer(props: ComposerProps) {
  const { placeholder, disabled = false, mode = "send", initialText = "", onCancel, onEditLast, onSend, onChange, suggest, footer } =
    props;
  const editing = mode === "edit";
  const [text, setText] = useState(initialText);
  const [caret, setCaret] = useState(initialText.length);
  const [focused, setFocused] = useState(false);
  const [active, setActive] = useState(0);
  // Where the mention closed with Escape starts; a new one opens the list again.
  const [dismissed, setDismissed] = useState<number | null>(null);
  const field = useRef<HTMLTextAreaElement>(null);
  const nextCaret = useRef<number | null>(null);
  const listId = useId();
  const ready = text.trim() !== "" && !disabled;

  const query = suggest && focused ? queryAt(text, caret) : null;
  const options = query && suggest ? suggest(query) : [];
  const open = query !== null && options.length > 0 && dismissed !== query.start;
  const current = Math.min(active, options.length - 1);

  // An editor opens ready to type at the end of the text.
  useLayoutEffect(() => {
    if (editing && field.current !== null) {
      field.current.focus();
      field.current.setSelectionRange(field.current.value.length, field.current.value.length);
    }
  }, [editing]);

  // Put the caret after a mention just chosen, once the text holds it.
  useLayoutEffect(() => {
    if (nextCaret.current !== null && field.current !== null) {
      field.current.setSelectionRange(nextCaret.current, nextCaret.current);
      setCaret(nextCaret.current);
      nextCaret.current = null;
    }
  }, [text]);

  function edit(next: string, at: number) {
    setText(next);
    setCaret(at);
    setActive(0);
    if (queryAt(next, at)?.start !== dismissed) {
      setDismissed(null);
    }
    onChange?.(next);
  }

  function choose(option: Suggestion, at: MentionQuery) {
    const inserted = insertMention(text, at, option.insert);
    nextCaret.current = inserted.caret;
    edit(inserted.text, inserted.caret);
  }

  function send() {
    const content = text.trim();
    if (content === "" || disabled) {
      return;
    }
    onSend(content);
    if (!editing) {
      setText("");
      setCaret(0);
      setDismissed(null);
    }
    field.current?.focus();
  }

  return (
    <div className={classes("sn-composer-area", editing && "sn-composer-area-edit")}>
      {open && (
        <div className="sn-suggestions" role="listbox" id={listId} aria-label={query.sigil === "@" ? "Members" : "Channels"}>
          <div className="sn-suggestions-title" aria-hidden="true">
            {query.sigil === "@" ? "Members" : "Channels"}
          </div>
          {options.map((option, i) => (
            <div
              key={option.id}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === current}
              className="sn-suggestion"
              // Keeps focus in the field.
              onMouseDown={(event) => event.preventDefault()}
              onMouseMove={() => setActive(i)}
              onClick={() => choose(option, query)}
            >
              {option.leading}
              <span className="sn-suggestion-label">{option.label}</span>
              {option.detail && <span className="sn-suggestion-detail">{option.detail}</span>}
            </div>
          ))}
        </div>
      )}
      <div className={classes("sn-composer", ready && "sn-composer-ready")}>
        <textarea
          ref={field}
          rows={1}
          value={text}
          placeholder={placeholder}
          aria-label={placeholder}
          disabled={disabled}
          maxLength={4000}
          role={suggest ? "combobox" : undefined}
          aria-autocomplete={suggest ? "list" : undefined}
          aria-expanded={suggest ? open : undefined}
          aria-controls={open ? listId : undefined}
          aria-activedescendant={open ? `${listId}-${current}` : undefined}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onSelect={(event) => setCaret(event.currentTarget.selectionStart)}
          onChange={(event) => edit(event.target.value, event.target.selectionStart)}
          onKeyDown={(event) => {
            if (event.nativeEvent.isComposing) {
              return;
            }
            if (open) {
              const option = options[current];
              if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                event.preventDefault();
                const step = event.key === "ArrowDown" ? 1 : options.length - 1;
                setActive((current + step) % options.length);
                return;
              }
              if ((event.key === "Enter" && !event.shiftKey) || event.key === "Tab") {
                event.preventDefault();
                if (option !== undefined) {
                  choose(option, query);
                }
                return;
              }
              if (event.key === "Escape") {
                event.preventDefault();
                setDismissed(query.start);
                return;
              }
            }
            if (event.key === "Escape" && editing) {
              event.preventDefault();
              onCancel?.();
            } else if (event.key === "ArrowUp" && text === "" && onEditLast && !editing) {
              event.preventDefault();
              onEditLast();
            } else if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send();
            }
          }}
        />
        <IconButton
          icon={editing ? "check" : "send"}
          label={editing ? "Save" : "Send"}
          className="sn-composer-send"
          tabIndex={ready ? 0 : -1}
          onClick={send}
        />
      </div>
      <div className="sn-composer-foot">{footer}</div>
    </div>
  );
}
