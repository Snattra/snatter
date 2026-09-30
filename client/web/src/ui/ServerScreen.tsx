import { type ReactNode, useRef, useState } from "react";
import type { Account, Channel } from "../api/types";
import { platform } from "../platform/platform";
import type { ServerConnection } from "../servers/ServerConnection";
import {
  channelMention,
  matchingChannels,
  matchingMembers,
  type MentionQuery,
  type Mentionables,
  memberMention,
  withTokens,
} from "../state/mentions";
import { isBanned, isTimedOut } from "../state/moderation";
import { type Compatibility, compatibility } from "../state/protocol";
import { can, canInvite, canSend, hasUnread, sortedChannels, typingIn } from "../state/serverView";
import type { ServerEntry } from "../state/store";
import { ChannelView } from "./ChannelView";
import { ChannelSettingsModal, CreateChannelModal } from "./channelSettings";
import { Button, IconButton } from "./controls";
import { useNow, usePreference } from "./hooks";
import { InviteButton } from "./invites";
import {
  AppShell,
  ChannelAction,
  ChannelHeader,
  ChannelItem,
  ChannelList,
  RailServer,
  ServerRail,
  Sidebar,
} from "./layout";
import { ChannelIcon } from "./icons";
import { Composer, type Suggestion } from "./messages";
import { LinkModal } from "./messageText";
import { Avatar, MemberList, TypingIndicator, UserPanel } from "./people";
import { ProfileModal } from "./profile";
import { ServerSettingsModal } from "./serverSettings";
import { Banner, Skeleton } from "./surfaces";
import { aheadTime } from "./time";

/** How often the member's typing is announced again while they keep typing, as the contract asks. */
const TYPING_REPEAT_MS = 8_000;

/** The modal open over the screen, if any. */
type Dialog =
  | { kind: "server-settings" }
  | { kind: "create-channel" }
  | { kind: "channel-settings"; channelId: string }
  | { kind: "profile"; accountId: string }
  | { kind: "link"; url: string };

/**
 * The main screen, laid out left to right: the server rail, the channels of
 * the current server (collapsible to icons), the channel itself, and the
 * member list (which can be closed).
 */
export function ServerScreen({ connection, entry }: { connection: ServerConnection; entry: ServerEntry }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  // The member's own message being edited, in the channel it is in.
  const [editingMessage, setEditingMessage] = useState<{ channelId: string; messageId: string } | null>(null);
  const [channelsCollapsed, setChannelsCollapsed] = usePreference("channelsCollapsed", false);
  const [membersOpen, setMembersOpen] = usePreference("membersOpen", true);
  const view = entry.view;

  const channels = view === null ? [] : sortedChannels(view);
  const selected = channels.find((c) => c.id === selectedId) ?? channels[0] ?? null;
  const someoneTyping = view !== null && selected !== null && Object.keys(view.typing[selected.id] ?? {}).length > 0;
  const now = useNow(someoneTyping ? 1000 : null);

  const membersToggle = (
    <IconButton
      icon="members"
      label={membersOpen ? "Hide members" : "Show members"}
      pressed={membersOpen}
      onClick={() => setMembersOpen(!membersOpen)}
    />
  );

  if (view === null) {
    return <Connecting collapsed={channelsCollapsed} membersOpen={membersOpen} membersToggle={membersToggle} />;
  }

  const typists = selected === null ? [] : typingIn(view, selected.id, now);
  const members = Object.values(view.members).sort((a, b) => a.displayName.localeCompare(b.displayName));
  // Banned members stay members, listed last.
  const present = members.filter((m) => !isBanned(m));
  const community = view.info.community.name;
  const hasMessages = selected !== null && selected.type !== "voice";
  // Each modal shows only while the member may use it, and channel settings only while the channel is there.
  const manageServer = can(view, "MANAGE_SERVER");
  const manageChannels = can(view, "MANAGE_CHANNELS");
  const editing = dialog?.kind === "channel-settings" ? view.channels[dialog.channelId] : undefined;
  const profile = dialog?.kind === "profile" ? view.members[dialog.accountId] : undefined;
  const mentionables = { members: present, channels };
  const suggest = suggester(connection.origin, mentionables);
  const editingId =
    editingMessage !== null && editingMessage.channelId === selected?.id ? editingMessage.messageId : null;
  const closeDialog = () => setDialog(null);
  const openProfile = (accountId: string) => setDialog({ kind: "profile", accountId });

  return (
    <>
      <AppShell
        collapsed={channelsCollapsed}
        membersOpen={membersOpen}
        rail={
          <ServerRail>
            <RailServer name={community} selected />
          </ServerRail>
        }
        sidebar={
          <Sidebar
            name={community}
            collapsed={channelsCollapsed}
            onToggle={() => setChannelsCollapsed(!channelsCollapsed)}
            actions={
              manageServer && (
                <IconButton
                  icon="settings"
                  label="Server settings"
                  aria-haspopup="dialog"
                  onClick={() => setDialog({ kind: "server-settings" })}
                />
              )
            }
            footer={
              <UserPanel
                origin={connection.origin}
                account={view.account}
                action={
                  <Button variant="link" onClick={() => void connection.logOut()}>
                    Sign out
                  </Button>
                }
              />
            }
          >
            <ChannelList>
              {channels.map((channel) => (
                <ChannelItem
                  key={channel.id}
                  channel={channel}
                  selected={channel.id === selected?.id}
                  unread={channel.id !== selected?.id && hasUnread(view, channel.id)}
                  onClick={() => setSelectedId(channel.id)}
                />
              ))}
              {manageChannels && (
                <ChannelAction
                  icon="plus"
                  label="Create channel"
                  onClick={() => setDialog({ kind: "create-channel" })}
                />
              )}
            </ChannelList>
          </Sidebar>
        }
        banner={
          <StatusBanner
            reconnecting={entry.status === "reconnecting"}
            account={view.account}
            compatible={compatibility(view.info.protocol)}
            manageServer={manageServer}
          />
        }
        header={
          <ChannelHeader channel={selected}>
            {manageChannels && selected !== null && (
              <IconButton
                icon="settings"
                label="Channel settings"
                aria-haspopup="dialog"
                onClick={() => setDialog({ kind: "channel-settings", channelId: selected.id })}
              />
            )}
            {membersToggle}
          </ChannelHeader>
        }
        footer={
          hasMessages && (
            <ChannelComposer
              key={selected.id}
              connection={connection}
              channel={selected}
              mentionables={mentionables}
              suggest={suggest}
              onEditLast={() => {
                // The member's newest message here that can still be edited.
                const last = entry.logs[selected.id]?.messages.findLast(
                  (m) => m.kind === "user" && m.authorId === view.account.id,
                );
                if (last !== undefined) {
                  setEditingMessage({ channelId: selected.id, messageId: last.id });
                }
              }}
              allowed={canSend(view)}
              timedOut={isTimedOut(view.account, Date.now())}
              typists={typists}
            />
          )
        }
        members={
          <MemberList
            origin={connection.origin}
            online={present.filter((m) => view.online[m.id])}
            offline={present.filter((m) => !view.online[m.id])}
            banned={members.filter(isBanned)}
            typing={new Set(typists.map((m) => m.id))}
            onOpen={(member) => openProfile(member.id)}
            footer={canInvite(view) && <InviteButton connection={connection} community={community} />}
          />
        }
      >
        {selected !== null &&
          (hasMessages ? (
            <ChannelView
              key={selected.id}
              connection={connection}
              view={view}
              channel={selected}
              log={entry.logs[selected.id]}
              onOpenProfile={openProfile}
              onOpenLink={(url) => setDialog({ kind: "link", url })}
              onOpenChannel={setSelectedId}
              mentionables={mentionables}
              suggest={suggest}
              editingId={editingId}
              onEdit={(messageId) => setEditingMessage(messageId === null ? null : { channelId: selected.id, messageId })}
            />
          ) : (
            <p className="sn-channel-note">This is a voice channel. It has no messages, and voice is not built yet.</p>
          ))}
      </AppShell>
      {dialog?.kind === "server-settings" && manageServer && (
        <ServerSettingsModal connection={connection} view={view} onClose={closeDialog} />
      )}
      {dialog?.kind === "create-channel" && manageChannels && (
        <CreateChannelModal
          connection={connection}
          view={view}
          onClose={closeDialog}
          onCreated={(channel) => {
            setSelectedId(channel.id);
            closeDialog();
          }}
        />
      )}
      {profile !== undefined && (
        <ProfileModal key={profile.id} connection={connection} view={view} member={profile} onClose={closeDialog} />
      )}
      {dialog?.kind === "link" && (
        <LinkModal
          url={dialog.url}
          onOpen={() => {
            void platform.openLink(dialog.url);
            closeDialog();
          }}
          onClose={closeDialog}
        />
      )}
      {editing !== undefined && manageChannels && (
        <ChannelSettingsModal
          key={editing.id}
          connection={connection}
          view={view}
          channel={editing}
          onClose={closeDialog}
        />
      )}
    </>
  );
}

/**
 * The composer for one channel, announcing typing while the member writes. It
 * suggests members and channels to mention, and sends their names as tokens.
 */
function ChannelComposer(props: {
  connection: ServerConnection;
  channel: Channel;
  mentionables: Mentionables;
  suggest: (query: MentionQuery) => Suggestion[];
  /** Up in the empty field: edit the member's last message here. */
  onEditLast: () => void;
  allowed: boolean;
  timedOut: boolean;
  typists: Account[];
}) {
  const { connection, channel, mentionables, suggest, onEditLast, allowed, timedOut, typists } = props;
  const lastAnnounced = useRef(0);
  return (
    <Composer
      placeholder={
        allowed
          ? `Message #${channel.name}`
          : timedOut
            ? "You can't send messages while you're timed out"
            : "You cannot send messages in this channel"
      }
      disabled={!allowed}
      onChange={(text) => {
        const now = Date.now();
        if (text.trim() !== "" && now - lastAnnounced.current >= TYPING_REPEAT_MS) {
          lastAnnounced.current = now;
          connection.typing(channel.id);
        }
      }}
      suggest={suggest}
      onEditLast={onEditLast}
      onSend={(text) => {
        lastAnnounced.current = 0;
        void connection.send(channel.id, withTokens(text, mentionables.members, mentionables.channels));
      }}
      footer={<TypingIndicator names={typists.map((m) => m.displayName)} />}
    />
  );
}

/** Members and channels to offer while a mention is typed, in the composer and while editing. */
function suggester(origin: string, { members, channels }: Mentionables) {
  return (at: MentionQuery): Suggestion[] =>
    at.sigil === "@"
      ? matchingMembers(members, at.query).map((member) => ({
          id: member.id,
          leading: <Avatar origin={origin} account={member} size="sm" />,
          label: member.displayName,
          detail: member.username,
          insert: memberMention(member),
        }))
      : matchingChannels(channels, at.query).map((c) => ({
          id: c.id,
          leading: <ChannelIcon channel={c} />,
          label: c.name,
          insert: channelMention(c),
        }));
}

/**
 * Across the top of the channel, one at a time and the most pressing first:
 * the connection coming back; the member's own timeout, which the gateway
 * ends in the view when it runs out; a newer version of the app, which the
 * server serves; and, for those who run the server, the server being older
 * than the app.
 */
function StatusBanner(props: {
  reconnecting: boolean;
  account: Account;
  compatible: Compatibility;
  manageServer: boolean;
}) {
  const { reconnecting, account, compatible, manageServer } = props;
  if (reconnecting) {
    return <Banner busy>Reconnecting…</Banner>;
  }
  if (account.timedOutUntil != null && isTimedOut(account, Date.now())) {
    return (
      <Banner>
        You're timed out until {aheadTime(new Date(account.timedOutUntil), new Date())}. You can read, but not write.
      </Banner>
    );
  }
  if (compatible === "update_available") {
    return <Banner tone="accent">Snatter has been updated. Reload the page to get the new version.</Banner>;
  }
  if (compatible === "server_older" && manageServer) {
    return (
      <Banner tone="accent">
        This server runs an older version of Snatter than this app, so some features are missing until it's updated.
      </Banner>
    );
  }
  return null;
}

/** The shell in the shape it is about to take, while the first `ready` is on its way. */
function Connecting(props: { collapsed: boolean; membersOpen: boolean; membersToggle: ReactNode }) {
  const { collapsed, membersOpen, membersToggle } = props;
  return (
    <AppShell
      collapsed={collapsed}
      membersOpen={membersOpen}
      rail={<ServerRail />}
      sidebar={
        <Sidebar name={<Skeleton width="120px" />} collapsed={collapsed}>
          <div className="sn-shell-loading" aria-hidden="true">
            {["70%", "55%", "80%", "45%"].map((width) => (
              <Skeleton key={width} width={collapsed ? "100%" : width} />
            ))}
          </div>
        </Sidebar>
      }
      header={<ChannelHeader channel={null}>{membersToggle}</ChannelHeader>}
      members={
        <aside className="sn-members" aria-hidden="true">
          <div className="sn-shell-loading">
            {["60%", "75%", "50%"].map((width) => (
              <Skeleton key={width} width={width} />
            ))}
          </div>
        </aside>
      }
    >
      <div className="sn-shell-loading-messages" aria-busy="true">
        <span className="sn-visually-hidden" role="status">
          Connecting…
        </span>
        <Skeleton variant="message" />
        <Skeleton variant="message" width="64%" />
        <Skeleton variant="message" width="82%" />
      </div>
    </AppShell>
  );
}
