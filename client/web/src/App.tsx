import { useEffect } from "react";
import { resolveMode } from "./config";
import { connectionTo } from "./servers/ServerConnection";
import { useServer } from "./state/store";
import { AuthScreen } from "./ui/AuthScreen";
import { ServerScreen } from "./ui/ServerScreen";

const mode = resolveMode();

export function App() {
  if (mode.kind === "multi") {
    return <div className="center">Adding servers is not built yet.</div>;
  }
  return <ServerApp origin={mode.origin} />;
}

function ServerApp({ origin }: { origin: string }) {
  const connection = connectionTo(origin);
  const entry = useServer(origin);

  useEffect(() => {
    void connection.resume();
  }, [connection]);

  switch (entry.status) {
    case "unknown":
      return <div className="center">Loading…</div>;
    case "signed_out":
      return <AuthScreen connection={connection} notice={entry.notice} />;
    default:
      return <ServerScreen connection={connection} entry={entry} />;
  }
}
