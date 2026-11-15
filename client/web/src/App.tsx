import { useEffect, useState } from "react";
import { resolveMode } from "./config";
import { loadSavedServers, useSavedServers } from "./servers/savedServers";
import { connectionTo, forgetServer } from "./servers/ServerConnection";
import { hostedInviteIn, inviteCodeIn } from "./state/invites";
import { type SavedServer, renamed, selecting, withServer, withoutServer } from "./state/serverList";
import { useServer, useServers } from "./state/store";
import { AddServerModal, RemoveServerModal, WelcomeScreen } from "./ui/AddServer";
import { AuthScreen } from "./ui/AuthScreen";
import { Spinner } from "./ui/controls";
import { RailAdd, RailProvider, RailServer, Railed, ServerRail } from "./ui/layout";
import { OutdatedScreen } from "./ui/OutdatedScreen";
import { ServerScreen } from "./ui/ServerScreen";
import { VoiceSounds } from "./ui/voice";

const mode = resolveMode();

export function App() {
  if (mode.kind === "multi") {
    return <MultiServerApp />;
  }
  return (
    <>
      <ServerApp origin={mode.origin} invite={inviteCodeIn(window.location.pathname)} />
      <VoiceSounds origin={mode.origin} />
    </>
  );
}

/**
 * The hosted app and the desktop app: the servers added, on the rail, and
 * the one open beside it. With none added yet, adding the first.
 */
function MultiServerApp() {
  const { servers, selected, loaded, change } = useSavedServers();
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<SavedServer | null>(null);
  // The names the servers go by now, for the rail.
  const names = useServers((state) => state.servers);
  // The page may have been opened from an invite link, /invite#{server}/{code}:
  // taken up until someone is signed in there, or adding the server is let be.
  const [invite, setInvite] = useState(() => hostedInviteIn(window.location.pathname, window.location.hash));
  const invitedServerAdded = invite !== null && servers.some((server) => server.origin === invite.origin);
  const dropInvite = () => {
    setInvite(null);
    window.history.replaceState(null, "", "/");
  };

  useEffect(() => {
    void loadSavedServers();
  }, []);

  // An invite to a server added already opens it.
  useEffect(() => {
    if (loaded && invite !== null && invitedServerAdded) {
      change((list) => selecting(list, invite.origin));
    }
  }, [loaded, invite, invitedServerAdded, change]);

  // A server renamed since it was added is kept under its new name.
  useEffect(() => {
    for (const server of servers) {
      const name = names[server.origin]?.view?.info.community.name;
      if (name !== undefined && name !== server.name) {
        change((list) => renamed(list, server.origin, name));
      }
    }
  }, [names, servers, change]);

  const add = (server: SavedServer) => {
    change((list) => withServer(list, server));
    setAdding(false);
    // Another server than the one invited to: the invite is let be.
    if (invite !== null && server.origin !== invite.origin) {
      dropInvite();
    }
  };
  // Off the list at once; signing out of it may take a moment more.
  const remove = async (server: SavedServer) => {
    const leaving = forgetServer(server.origin);
    change((list) => withoutServer(list, server.origin));
    setRemoving(null);
    await leaving;
  };

  if (!loaded) {
    return <Loading />;
  }
  const open = servers.find((server) => server.origin === selected) ?? servers[0];
  if (open === undefined) {
    return <WelcomeScreen onAdd={add} address={invite?.origin} />;
  }

  // An invite to a server not added yet starts adding it.
  const inviteToAdd = invite !== null && !invitedServerAdded ? invite.origin : undefined;

  const rail = (
    <ServerRail>
      {servers.map((server) => (
        <RailServer
          key={server.origin}
          name={server.name}
          selected={server.origin === open.origin}
          onClick={() => change((list) => selecting(list, server.origin))}
          onMenu={() => setRemoving(server)}
          inVoice={names[server.origin]?.voice.channelId != null}
        />
      ))}
      <RailAdd onClick={() => setAdding(true)} />
    </ServerRail>
  );

  return (
    <RailProvider value={rail}>
      <ServerApp
        key={open.origin}
        origin={open.origin}
        invite={invite?.origin === open.origin ? invite.code : null}
        onInviteUsed={dropInvite}
        onRemove={() => setRemoving(open)}
      />
      {/* Outside the open server's screen, so a call elsewhere is still heard. */}
      {servers.map((server) => (
        <VoiceSounds key={server.origin} origin={server.origin} />
      ))}
      {(adding || inviteToAdd !== undefined) && (
        <AddServerModal
          known={servers}
          address={inviteToAdd}
          onAdd={add}
          onClose={() => {
            setAdding(false);
            if (inviteToAdd !== undefined) {
              dropInvite();
            }
          }}
        />
      )}
      {removing && (
        <RemoveServerModal server={removing} onRemove={() => remove(removing)} onClose={() => setRemoving(null)} />
      )}
    </RailProvider>
  );
}

interface ServerAppProps {
  origin: string;
  /** The code of an invite to this server the page was opened from, for signing up with. */
  invite?: string | null;
  /** Called once the invite has done its job, someone being signed in. */
  onInviteUsed?: () => void;
  /** Takes the server off the app's list, for the screens before signing in. */
  onRemove?: () => void;
}

function ServerApp({ origin, invite: invited = null, onInviteUsed, onRemove }: ServerAppProps) {
  const connection = connectionTo(origin);
  const entry = useServer(origin);
  const [invite, setInvite] = useState(invited);
  const signedIn = entry.status !== "unknown" && entry.status !== "signed_out";

  useEffect(() => {
    void connection.resume();
  }, [connection]);

  // Once in, the invite has done its job, and the address goes back to the app's own.
  useEffect(() => {
    if (signedIn && invite !== null) {
      setInvite(null);
      window.history.replaceState(null, "", "/");
      onInviteUsed?.();
    }
  }, [signedIn, invite, onInviteUsed]);

  switch (entry.status) {
    case "unknown":
      return (
        <Railed>
          <Loading />
        </Railed>
      );
    case "signed_out":
      return (
        <Railed>
          <AuthScreen connection={connection} notice={entry.notice} invite={invite} onRemove={onRemove} />
        </Railed>
      );
    case "client_outdated":
    case "server_outdated":
      return (
        <Railed>
          <OutdatedScreen outdated={entry.status} />
        </Railed>
      );
    default:
      return <ServerScreen connection={connection} entry={entry} />;
  }
}

function Loading() {
  return (
    <div className="sn-center" role="status">
      <Spinner />
      Loading…
    </div>
  );
}
