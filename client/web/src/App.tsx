import { useEffect, useState } from "react";
import { resolveMode } from "./config";
import { connectionTo } from "./servers/ServerConnection";
import { inviteCodeIn } from "./state/invites";
import { useServer } from "./state/store";
import { AuthScreen } from "./ui/AuthScreen";
import { Spinner } from "./ui/controls";
import { ServerScreen } from "./ui/ServerScreen";

const mode = resolveMode();

export function App() {
  if (mode.kind === "multi") {
    return <div className="sn-center">Adding servers is not built yet.</div>;
  }
  return <ServerApp origin={mode.origin} />;
}

function ServerApp({ origin }: { origin: string }) {
  const connection = connectionTo(origin);
  const entry = useServer(origin);
  // The page may have been opened from an invite link, /invite/{code}.
  const [invite, setInvite] = useState(() => inviteCodeIn(window.location.pathname));
  const signedIn = entry.status !== "unknown" && entry.status !== "signed_out";

  useEffect(() => {
    void connection.resume();
  }, [connection]);

  // Once in, the invite has done its job, and the address goes back to the app's own.
  useEffect(() => {
    if (signedIn && invite !== null) {
      setInvite(null);
      window.history.replaceState(null, "", "/");
    }
  }, [signedIn, invite]);

  switch (entry.status) {
    case "unknown":
      return (
        <div className="sn-center" role="status">
          <Spinner />
          Loading…
        </div>
      );
    case "signed_out":
      return <AuthScreen connection={connection} notice={entry.notice} invite={invite} />;
    default:
      return <ServerScreen connection={connection} entry={entry} />;
  }
}
