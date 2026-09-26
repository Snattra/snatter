import { type ReactNode, useState } from "react";
import type { ServerConnection } from "../servers/ServerConnection";
import { sortedChannels, typingIn } from "../state/serverView";
import type { ServerEntry } from "../state/store";
import { Button, IconButton } from "./controls";
import { useNow, usePreference } from "./hooks";
import { AppShell, ChannelHeader, ChannelItem, ChannelList, RailServer, ServerRail, Sidebar } from "./layout";
import { MemberList, UserPanel } from "./people";
import { Banner, Skeleton } from "./surfaces";

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

  const typing = new Set(selected === null ? [] : typingIn(view, selected.id, now).map((m) => m.id));
  const members = Object.values(view.members).sort((a, b) => a.displayName.localeCompare(b.displayName));
  const community = view.info.community.name;

  return (
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
                onClick={() => setSelectedId(channel.id)}
              />
            ))}
          </ChannelList>
        </Sidebar>
      }
      banner={entry.status === "reconnecting" && <Banner busy>Reconnecting…</Banner>}
      header={<ChannelHeader channel={selected}>{membersToggle}</ChannelHeader>}
      members={
        <MemberList
          origin={connection.origin}
          online={members.filter((m) => view.online[m.id])}
          offline={members.filter((m) => !view.online[m.id])}
          typing={typing}
        />
      }
    />
  );
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
