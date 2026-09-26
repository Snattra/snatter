import { useEffect, useId, useRef, useState } from "react";
import type { Invite } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { stillReusable } from "../state/invites";
import { Button, Spinner } from "./controls";
import { describeError } from "./errors";
import { Icon } from "./icons";
import { Popover } from "./surfaces";
import { untilText } from "./time";

/** How long Copy reads "Copied". */
const COPIED_SHOWN_MS = 2_000;

type LinkState = { status: "creating" } | { status: "ready"; invite: Invite } | { status: "failed"; error: string };

/**
 * "Invite people" at the foot of the member list, opening the invite link in
 * a popover above it. The link is fetched as the popover opens, and kept for
 * the next time while it can still be handed out.
 */
export function InviteButton({ connection, community }: { connection: ServerConnection; community: string }) {
  const id = useId();
  const anchor = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [link, setLink] = useState<LinkState>({ status: "creating" });

  function load() {
    setLink({ status: "creating" });
    connection.inviteLink().then(
      (invite) => setLink({ status: "ready", invite }),
      (e: unknown) => setLink({ status: "failed", error: describeError(e) }),
    );
  }

  function toggled(isOpen: boolean) {
    setOpen(isOpen);
    if (isOpen && !(link.status === "ready" && stillReusable(link.invite, Date.now()))) {
      load();
    }
  }

  return (
    <>
      <button ref={anchor} type="button" className="sn-invite-button" popoverTarget={id} aria-expanded={open}>
        <Icon name="person-add" />
        Invite people
      </button>
      <Popover
        id={id}
        anchor={anchor}
        title={`Invite people to ${community}`}
        description="Anyone with this link can create an account and join."
        onToggle={toggled}
      >
        <InviteLink link={link} onRetry={load} />
      </Popover>
    </>
  );
}

/** The link in a well, when it expires, and a Copy button that confirms. */
function InviteLink({ link, onRetry }: { link: LinkState; onRetry: () => void }) {
  const url = useRef<HTMLParagraphElement>(null);
  const [copy, setCopy] = useState<"idle" | "copied" | "blocked">("idle");

  useEffect(() => {
    if (copy !== "copied") {
      return;
    }
    const timer = setTimeout(() => setCopy("idle"), COPIED_SHOWN_MS);
    return () => clearTimeout(timer);
  }, [copy]);

  async function copyLink(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopy("copied");
    } catch {
      // No clipboard outside a secure context, or the browser said no: select it for copying by hand.
      if (url.current !== null) {
        window.getSelection()?.selectAllChildren(url.current);
      }
      setCopy("blocked");
    }
  }

  switch (link.status) {
    case "creating":
      return (
        <p className="sn-invite-status" role="status">
          <Spinner />
          Creating a link…
        </p>
      );
    case "failed":
      return (
        <div className="sn-invite-foot">
          <p className="sn-invite-status sn-invite-status-error" role="alert">
            {link.error}
          </p>
          <Button size="sm" onClick={onRetry}>
            Try again
          </Button>
        </div>
      );
    case "ready": {
      const { url: href, expiresAt } = link.invite;
      const expiry =
        expiresAt == null ? "This link never expires." : `Expires in ${untilText(new Date(expiresAt), new Date())}.`;
      return (
        <>
          <p ref={url} className="sn-invite-url">
            {href}
          </p>
          <div className="sn-invite-foot">
            <span>{copy === "blocked" ? "Your browser blocked copying, so the link is selected instead." : expiry}</span>
            <Button size="sm" variant={copy === "copied" ? "secondary" : "primary"} onClick={() => void copyLink(href)}>
              {copy === "copied" ? "Copied" : "Copy"}
            </Button>
          </div>
          <span className="sn-visually-hidden" role="status">
            {copy === "copied" ? "Link copied" : ""}
          </span>
        </>
      );
    }
  }
}
