import { type FormEvent, type ReactNode, useEffect, useRef, useState } from "react";
import { type ServerCheck, checkServer } from "../servers/serverCheck";
import { DEMO_ORIGIN, serverOrigin } from "../state/serverAddress";
import type { SavedServer } from "../state/serverList";
import { Button, Field } from "./controls";
import { describeError } from "./errors";
import { Backdrop, Callout, Card, Modal } from "./surfaces";

type Found = Extract<ServerCheck, { kind: "ok" }>;

interface AddServerParts {
  /** The address, or the server found there. */
  body: ReactNode;
  /** The primary action: look the server up, then add it. */
  submit: () => void;
  submitLabel: string;
  busy: boolean;
  /** Back to the address from the server found, or null on the first step. */
  back: (() => void) | null;
  /** The step shown, for a modal to focus anew. */
  step: "address" | "found";
}

/**
 * Adding a server in two steps: its address, checked by asking the server
 * what it is, then what was found there, to add it. The demo server is a
 * click away until it has been added.
 */
function useAddServer(known: SavedServer[], onAdd: (server: SavedServer) => void, initial?: string): AddServerParts {
  const [address, setAddress] = useState(initial ?? "");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [found, setFound] = useState<Found | null>(null);

  async function look(typed: string) {
    const origin = serverOrigin(typed);
    if (origin === null) {
      setProblem("That isn't a server address. Try one like chat.example.com.");
      return;
    }
    setBusy(true);
    setProblem(null);
    const check = await checkServer(origin);
    setBusy(false);
    if (check.kind === "ok") {
      setFound(check);
    } else {
      setProblem(problemWith(check));
    }
  }

  // An address to start from, as from an invite link, is looked up at once.
  const lookedUp = useRef(false);
  useEffect(() => {
    if (initial !== undefined && !lookedUp.current) {
      lookedUp.current = true;
      void look(initial);
    }
    // Once, for the address the screen opened with.
  }, []);

  const add = () => found !== null && onAdd({ origin: found.origin, name: found.info.community.name });

  if (found !== null) {
    return {
      body: <FoundServer found={found} added={known.some((server) => server.origin === found.origin)} />,
      submit: add,
      submitLabel: known.some((server) => server.origin === found.origin) ? "Open server" : "Add server",
      busy: false,
      back: () => setFound(null),
      step: "found",
    };
  }

  const demoAdded = known.some((server) => server.origin === DEMO_ORIGIN);
  return {
    body: (
      <>
        <Field
          label="Server address"
          name="address"
          placeholder="chat.example.com"
          autoComplete="url"
          inputMode="url"
          spellCheck={false}
          data-autofocus
          hint="The address its owner gave you."
          value={address}
          error={problem ?? undefined}
          onChange={(event) => {
            setAddress(event.target.value);
            setProblem(null);
          }}
        />
        {!demoAdded && (
          <p className="sn-add-server-demo">
            Just looking?{" "}
            <Button
              variant="link"
              disabled={busy}
              onClick={() => {
                setAddress(new URL(DEMO_ORIGIN).host);
                void look(DEMO_ORIGIN);
              }}
            >
              Try the demo server
            </Button>
          </p>
        )}
      </>
    ),
    submit: () => void look(address),
    submitLabel: busy ? "Looking for the server…" : "Continue",
    busy,
    back: null,
    step: "address",
  };
}

/** Why a server cannot be added, for whoever typed its address. */
function problemWith(check: Exclude<ServerCheck, { kind: "ok" }>): string {
  switch (check.kind) {
    case "insecure":
      return "This page is secure and that server isn't, so the browser won't connect to it. Use its https:// address.";
    case "unreachable":
      return "No Snatter server answered there. Check the address, or ask the server's owner whether it lets this app connect.";
    case "not_snatter":
      return "Something answered there, but it isn't a Snatter server.";
    case "outdated":
      return check.outdated === "server_outdated"
        ? `${check.info.community.name} runs a version of Snatter that is too old for this app. Ask its owner to update it.`
        : `${check.info.community.name} needs a newer version of this app.`;
  }
}

function FoundServer({ found, added }: { found: Found; added: boolean }) {
  const { info, origin } = found;
  return (
    <div className="sn-add-server-found">
      <div>
        <strong className="sn-add-server-name">{info.community.name}</strong>
        <span className="sn-add-server-address">{new URL(origin).host}</span>
      </div>
      {info.community.description && <p className="sn-add-server-description">{info.community.description}</p>}
      <p className="sn-add-server-joining">
        {info.registration.setupRequired
          ? "It has no accounts yet: the first one created becomes its owner."
          : info.registration.mode === "open"
            ? "Anyone can create an account here."
            : "Creating an account here takes an invite."}
      </p>
      {found.compatibility === "server_older" && (
        <Callout title="An older server" tone="warning">
          It runs an older version of Snatter than this app, so some features are missing until it's updated.
        </Callout>
      )}
      {added && <p className="sn-add-server-joining">You have added it already.</p>}
    </div>
  );
}

/** What the app shows before any server is added. */
export function WelcomeScreen({ onAdd, address }: { onAdd: (server: SavedServer) => void; address?: string }) {
  const parts = useAddServer([], onAdd, address);
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    parts.submit();
  };
  return (
    <Backdrop>
      <Card
        title="Welcome to Snatter"
        description="Snatter runs on servers that people host for their own communities. Add one to get started; you can add more later."
      >
        <form className="sn-form" onSubmit={submit} noValidate>
          {parts.body}
          <Button variant="primary" type="submit" block busy={parts.busy}>
            {parts.submitLabel}
          </Button>
          {parts.back && (
            <Button variant="link" onClick={parts.back}>
              Use another address
            </Button>
          )}
        </form>
      </Card>
    </Backdrop>
  );
}

/** Adding another server, from the rail. */
export function AddServerModal(props: {
  known: SavedServer[];
  onAdd: (server: SavedServer) => void;
  onClose: () => void;
  /** An address to look up at once, as from an invite link. */
  address?: string;
}) {
  const parts = useAddServer(props.known, props.onAdd, props.address);
  return (
    <Modal
      title="Add a server"
      onClose={props.onClose}
      onSubmit={parts.submit}
      step={parts.step}
      footerStart={
        parts.back && (
          <Button variant="link" onClick={parts.back}>
            Use another address
          </Button>
        )
      }
      footer={
        <>
          <Button onClick={props.onClose}>Cancel</Button>
          <Button variant="primary" type="submit" busy={parts.busy}>
            {parts.submitLabel}
          </Button>
        </>
      }
    >
      {parts.body}
    </Modal>
  );
}

/** Taking a server off the list, which signs out of it on this device. */
export function RemoveServerModal(props: { server: SavedServer; onRemove: () => Promise<void>; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal
      title={`Remove ${props.server.name}?`}
      description={new URL(props.server.origin).host}
      onClose={props.onClose}
      error={error}
      footer={
        <>
          <Button onClick={props.onClose}>Cancel</Button>
          <Button
            variant="danger"
            busy={busy}
            data-autofocus
            onClick={() => {
              setBusy(true);
              setError(null);
              props.onRemove().catch((e: unknown) => {
                setBusy(false);
                setError(`It could not be removed: ${describeError(e)}`);
              });
            }}
          >
            Remove server
          </Button>
        </>
      }
    >
      <p>
        It goes from this app, and you're signed out of it here. Your account on the server stays, so you can add it
        again later.
      </p>
    </Modal>
  );
}
