import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { Channel, SystemMessage, UserMessage } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import type { ChannelLog } from "../state/channelLog";
import { isAfter, timeOfId } from "../state/ids";
import type { MentionQuery, Mentionables } from "../state/mentions";
import { type ServerView, can } from "../state/serverView";
import { IconButton } from "./controls";
import { useAttention } from "./hooks";
import { layoutRows, unreadAfter } from "./messageLayout";
import { DeleteMessageModal, MessageEditor } from "./messageActions";
import {
  DeletedMessageRow,
  MessageList,
  NewMessagesDivider,
  PendingRow,
  type Suggestion,
  SystemMessageRow,
  UnreadBar,
  UserMessageRow,
} from "./messages";
import type { TextContext } from "./messageText";
import { Skeleton } from "./surfaces";
import { sinceTime } from "./time";

/** How close to the newest message still counts as being at the end, in pixels. */
const AT_END_SLACK = 8;
/** How long the member must stay at the end before it counts as read. */
const READ_DELAY_MS = 250;

interface ChannelViewProps {
  connection: ServerConnection;
  view: ServerView;
  channel: Channel;
  /** Undefined until the channel is first opened. */
  log: ChannelLog | undefined;
  /** Opens a member's profile, from their name or avatar. */
  onOpenProfile: (accountId: string) => void;
  /** Asks before opening a link in a message. */
  onOpenLink: (url: string) => void;
  /** Switches to a channel a message mentions. */
  onOpenChannel: (channelId: string) => void;
  mentionables: Mentionables;
  suggest: (query: MentionQuery) => Suggestion[];
  /** The member's own message being edited here, if any. */
  editingId: string | null;
  /** Starts editing a message, or with null stops. */
  onEdit: (messageId: string | null) => void;
}

/**
 * A channel's messages, newest at the bottom. Mount one per channel (keyed
 * by its id): the divider above the first unread message is placed when the
 * channel opens and stays for as long as it is shown.
 *
 * The member is reading while the page has focus and the view is at the
 * newest message; then the read marker follows the newest message. When they
 * stop reading, the divider is set after what they last saw, so whatever
 * arrives meanwhile shows as new when they come back.
 */
export function ChannelView(props: ChannelViewProps) {
  const { connection, view, channel, log, onOpenProfile, onOpenLink, onOpenChannel, mentionables, suggest, editingId, onEdit } =
    props;
  const me = view.account.id;
  const [deleting, setDeleting] = useState<UserMessage | SystemMessage | null>(null);
  // The author's profile, for a message whose author is still a member.
  const opener = (authorId: string | null | undefined) =>
    authorId != null && view.members[authorId] !== undefined ? () => onOpenProfile(authorId) : undefined;
  // What mentions in messages name, as the view knows it now.
  const text: TextContext = {
    member: (id) => view.members[id],
    channel: (id) => view.channels[id],
    openProfile: onOpenProfile,
    openChannel: onOpenChannel,
    openLink: onOpenLink,
  };
  // Authors edit and delete their own messages; MANAGE_MESSAGES deletes any, notices included.
  const manage = can(view, "MANAGE_MESSAGES");
  const actionsFor = (message: UserMessage | SystemMessage) => {
    const own = message.kind === "user" && message.authorId === me;
    if (!own && !manage) {
      return undefined;
    }
    return (
      <>
        {own && <IconButton icon="edit" label="Edit" onClick={() => onEdit(message.id)} />}
        <IconButton
          icon="delete"
          label="Delete"
          className="sn-message-action-danger"
          onClick={(event) => {
            if (!event.shiftKey) {
              setDeleting(message);
              return;
            }
            // Shift skips the question; if it fails, the question comes up to say why.
            connection.delete(message).catch(() => setDeleting(message));
          }}
        />
      </>
    );
  };
  const reading = view.reading[channel.id];
  const scroller = useRef<HTMLDivElement>(null);
  const divider = useRef<HTMLDivElement>(null);
  const top = useRef<HTMLDivElement>(null);

  // The newest message read when the member last caught up; undefined: no divider.
  const [anchor, setAnchor] = useState<string | null | undefined>(() =>
    reading?.last != null && isAfter(reading.last, reading.lastRead) ? reading.lastRead : undefined,
  );
  const [atEnd, setAtEnd] = useState(true);
  const [dividerAbove, setDividerAbove] = useState(false);
  // Once the divider has been on screen, the unread bar has done its job for that divider.
  const [dividerSeen, setDividerSeen] = useState(false);
  const [jumping, setJumping] = useState(false);
  const attentive = useAttention();

  const loaded = log?.loaded === true;
  const isReading = loaded && atEnd && attentive;
  const latest = log?.messages.at(-1)?.id ?? null;
  const unread = log && anchor !== undefined ? unreadAfter(log, anchor, me) : null;
  const hasUnread = unread !== null && (unread.firstId !== null || !unread.complete);

  useEffect(() => {
    connection.open(channel.id).catch((e: unknown) => console.warn(`Could not load ${channel.name}`, e));
  }, [connection, channel.id, channel.name]);

  // Stopping reading puts the divider after what was on screen; resuming with nothing new takes it away.
  // Opening a channel with nothing unread counts as having read it up to then.
  const wasReading = useRef(true);
  useEffect(() => {
    if (!loaded || wasReading.current === isReading) {
      return;
    }
    wasReading.current = isReading;
    if (!isReading && anchor === undefined) {
      setAnchor(latest);
      setDividerSeen(false);
    } else if (isReading && anchor !== undefined && !hasUnread) {
      setAnchor(undefined);
    }
  }, [loaded, isReading, anchor, latest, hasUnread]);

  // Reading moves the marker to the newest message.
  const lastRead = reading?.lastRead ?? null;
  useEffect(() => {
    if (!isReading || latest === null || !isAfter(latest, lastRead)) {
      return;
    }
    const timer = setTimeout(() => void connection.markRead(channel.id, latest), READ_DELAY_MS);
    return () => clearTimeout(timer);
  }, [connection, channel.id, isReading, latest, lastRead]);

  // Sending goes back to the end, where the new message is.
  const pendingCount = log?.pending.length ?? 0;
  const previousPending = useRef(pendingCount);
  useLayoutEffect(() => {
    if (pendingCount > previousPending.current) {
      scroller.current?.scrollTo({ top: 0 });
    }
    previousPending.current = pendingCount;
  }, [pendingCount]);

  // Older pages load as the top comes near.
  const hasOlder = log?.hasOlder === true;
  useEffect(() => {
    const target = top.current;
    if (!loaded || !hasOlder || target === null) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          connection.loadOlder(channel.id).catch((e: unknown) => console.warn("Could not load older messages", e));
        }
      },
      { root: scroller.current, rootMargin: "400px 0px 0px 0px" },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [connection, channel.id, loaded, hasOlder, log?.messages.length]);

  // The unread bar shows while the divider is above the view, or not loaded yet.
  const firstUnreadId = unread?.firstId ?? null;
  const unreadNotHeld = unread !== null && !unread.complete;
  useEffect(() => {
    const target = divider.current;
    if (target === null) {
      setDividerAbove(unreadNotHeld);
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry !== undefined) {
          setDividerAbove(!entry.isIntersecting && entry.boundingClientRect.bottom <= (entry.rootBounds?.top ?? 0));
          if (entry.isIntersecting) {
            setDividerSeen(true);
          }
        }
      },
      { root: scroller.current },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [firstUnreadId, unreadNotHeld]);

  // Jumping loads older pages until the first unread message is held, then scrolls to it.
  const held = log?.messages.length ?? 0;
  useEffect(() => {
    if (!jumping) {
      return;
    }
    if (unreadNotHeld) {
      connection.loadOlder(channel.id).catch((e: unknown) => {
        console.warn("Could not load older messages", e);
        setJumping(false);
      });
      return;
    }
    divider.current?.scrollIntoView({ block: "center" });
    setJumping(false);
  }, [connection, channel.id, jumping, unreadNotHeld, held]);

  function markAllRead() {
    setAnchor(undefined);
    if (latest !== null) {
      void connection.markRead(channel.id, latest);
    }
  }

  const rows = log === undefined ? [] : layoutRows(log, me, firstUnreadId);

  return (
    <>
      {unread !== null && hasUnread && dividerAbove && !dividerSeen && (
        <UnreadBar
          count={unread.complete ? unread.count : null}
          since={anchor ? sinceTime(new Date(timeOfId(anchor)), new Date()) : null}
          onJump={() => setJumping(true)}
          onMarkRead={markAllRead}
        />
      )}
      <div
        ref={scroller}
        className="sn-channel-scroll"
        onScroll={(event) => setAtEnd(Math.abs(event.currentTarget.scrollTop) <= AT_END_SLACK)}
      >
        <div>
          <div ref={top} />
          {loaded && !hasOlder && (
            <p className="sn-channel-start">
              <strong>Welcome to {channel.name}</strong>
              This is the start of the channel.
            </p>
          )}
          {!loaded ? (
            <div className="sn-shell-loading-messages" aria-busy="true">
              <span className="sn-visually-hidden" role="status">
                Loading messages…
              </span>
              <Skeleton variant="message" />
              <Skeleton variant="message" width="64%" />
              <Skeleton variant="message" width="82%" />
            </div>
          ) : (
            <MessageList>
              {rows.map((row) => {
                switch (row.kind) {
                  case "divider":
                    return <NewMessagesDivider key="divider" ref={divider} />;
                  case "system":
                    return (
                      <SystemMessageRow
                        key={row.message.id}
                        message={row.message}
                        author={row.message.authorId ? view.members[row.message.authorId] : undefined}
                        isNew={log?.live[row.message.id] === true}
                        onAuthor={opener(row.message.authorId)}
                        actions={manage ? actionsFor(row.message) : undefined}
                      />
                    );
                  case "deleted":
                    return (
                      <DeletedMessageRow
                        key={row.messages[0].id}
                        origin={connection.origin}
                        messages={row.messages}
                        author={row.messages[0].authorId ? view.members[row.messages[0].authorId] : undefined}
                        head={row.head}
                        onAuthor={opener(row.messages[0].authorId)}
                      />
                    );
                  case "message":
                    return (
                      <UserMessageRow
                        // Keyed by nonce where there is one, so the confirmed message replaces its pending copy in place.
                        key={row.message.nonce ?? row.message.id}
                        origin={connection.origin}
                        message={row.message}
                        author={row.message.authorId ? view.members[row.message.authorId] : undefined}
                        head={row.head}
                        isNew={log?.live[row.message.id] === true}
                        mentioned={row.message.mentions.includes(me)}
                        onAuthor={opener(row.message.authorId)}
                        text={text}
                        actions={actionsFor(row.message)}
                        editor={
                          editingId === row.message.id && row.message.authorId === me ? (
                            <MessageEditor
                              connection={connection}
                              message={row.message}
                              mentionables={mentionables}
                              suggest={suggest}
                              onDone={() => onEdit(null)}
                            />
                          ) : undefined
                        }
                      />
                    );
                  case "pending":
                    return (
                      <PendingRow
                        key={row.pending.nonce}
                        origin={connection.origin}
                        pending={row.pending}
                        author={view.account}
                        head={row.head}
                        onAuthor={opener(me)}
                        text={text}
                        onRetry={() => void connection.retry(channel.id, row.pending.nonce)}
                        onDiscard={() => connection.discard(channel.id, row.pending.nonce)}
                      />
                    );
                }
              })}
            </MessageList>
          )}
        </div>
      </div>
      {deleting !== null && (
        <DeleteMessageModal
          connection={connection}
          message={deleting}
          author={deleting.authorId ? view.members[deleting.authorId] : undefined}
          own={deleting.authorId === me}
          text={text}
          onClose={() => setDeleting(null)}
        />
      )}
    </>
  );
}
