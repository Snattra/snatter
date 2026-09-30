import type { Outdated } from "../state/protocol";
import { Button } from "./controls";
import { Backdrop, Card } from "./surfaces";

/**
 * Instead of the app, when it and the server are too far apart in version to
 * talk. The server serves this app, so reloading fetches the version it
 * needs; an outdated server is its owner's to update.
 */
export function OutdatedScreen({ outdated }: { outdated: Outdated }) {
  const reload = () => window.location.reload();
  if (outdated === "client_outdated") {
    return (
      <Backdrop>
        <Card
          title="Snatter has been updated"
          description="This server needs a newer version of the app than the one open here. Reload the page to get it."
        >
          <Button variant="primary" block onClick={reload}>
            Reload
          </Button>
        </Card>
      </Backdrop>
    );
  }
  return (
    <Backdrop>
      <Card
        title="This server needs an update"
        description="It runs a version of Snatter that is too old for this app. Ask the server's owner to update it, then try again."
      >
        <Button block onClick={reload}>
          Try again
        </Button>
      </Card>
    </Backdrop>
  );
}
