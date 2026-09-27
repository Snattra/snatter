import { type FormEvent, useEffect, useState } from "react";
import { unwrap } from "../api/client";
import type { InvitePreview, ServerInfo } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { USERNAME_PATTERN, displayNameProblem, tidyDisplayName } from "../state/names";
import { Button, Field, Tabs } from "./controls";
import { describeError } from "./errors";
import { Backdrop, Callout, Card } from "./surfaces";

type Tab = "log_in" | "register";

const tabs: { id: Tab; label: string }[] = [
  { id: "log_in", label: "Sign in" },
  { id: "register", label: "Create account" },
];

interface AuthScreenProps {
  connection: ServerConnection;
  notice: string | null;
  /** The code of the invite link the page was opened from. */
  invite: string | null;
}

export function AuthScreen({ connection, notice, invite }: AuthScreenProps) {
  const [info, setInfo] = useState<ServerInfo | null>(null);
  const [preview, setPreview] = useState<InvitePreview | null>(null);
  const [tab, setTab] = useState<Tab>(invite === null ? "log_in" : "register");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    connection.api
      .GET("/api/v1/server-info")
      .then(unwrap)
      .then(setInfo, (e: unknown) => setError(describeError(e)));
  }, [connection]);

  // Who sent the invite, and whether it still works, before anyone fills in the form.
  useEffect(() => {
    if (invite === null) {
      return;
    }
    connection.api
      .GET("/api/v1/invites/{code}", { params: { path: { code: invite } } })
      .then(unwrap)
      .then(setPreview, (e: unknown) => setError(describeError(e)));
  }, [connection, invite]);

  async function run(label: string, action: () => Promise<void>) {
    setBusy(label);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(describeError(e));
    } finally {
      // On success the screen is about to go; on failure the form is usable again.
      setBusy(null);
    }
  }

  function logIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    void run("Signing in…", () => connection.logIn(text(form, "username"), text(form, "password")));
  }

  function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (info === null) {
      return;
    }
    const form = new FormData(event.currentTarget);
    const displayName = tidyDisplayName(text(form, "displayName"));
    const problem = displayNameProblem(displayName);
    if (problem !== null) {
      setError(problem);
      return;
    }
    const inviteCode = text(form, "inviteCode").trim();
    // The first account on a fresh server needs no challenge.
    const challenge = info.registration.challengeRequired && !info.registration.setupRequired;
    void run(challenge ? "Checking you are not a bot…" : "Creating your account…", () =>
      connection.register(
        {
          username: text(form, "username"),
          password: text(form, "password"),
          ...(displayName ? { displayName } : {}),
          ...(inviteCode ? { inviteCode } : {}),
        },
        challenge,
      ),
    );
  }

  const setup = info?.registration.setupRequired === true;

  return (
    <Backdrop>
      <Card title={info?.community.name ?? "Snatter"} description={info?.community.description}>
        {notice && <p className="sn-notice">{notice}</p>}

        {setup ? (
          <Callout title="Set up your server">
            This server has no accounts yet. The account you create now becomes its owner, with full control over the
            server and its settings.
          </Callout>
        ) : (
          <>
            {preview && (
              <Callout title="You have an invite">
                {preview.inviter ? `${preview.inviter.displayName} invited you to join.` : "You are invited to join."} Create
                an account to accept.
              </Callout>
            )}
            <Tabs label="Account" tabs={tabs} value={tab} onChange={setTab} />
          </>
        )}

        {tab === "log_in" && !setup ? (
          <form className="sn-form" onSubmit={logIn}>
            <Field label="Username" name="username" autoComplete="username" required />
            <Field label="Password" name="password" type="password" autoComplete="current-password" required />
            <Button variant="primary" type="submit" block busy={busy !== null}>
              {busy ?? "Sign in"}
            </Button>
          </form>
        ) : (
          <form className="sn-form" onSubmit={register}>
            <Field
              label="Username"
              name="username"
              autoComplete="username"
              required
              minLength={3}
              maxLength={32}
              pattern={USERNAME_PATTERN}
              title="Letters, digits and _ only"
              hint="3 to 32 letters, digits and _."
            />
            <Field
              label="Display name"
              optional
              name="displayName"
              autoComplete="nickname"
              maxLength={64}
              hint="How others see you. Letters, digits, punctuation and spaces; no emoji."
            />
            <Field
              label="Password"
              name="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={128}
            />
            {(info?.registration.mode === "invite_only" || invite !== null) && !setup && (
              <Field
                label="Invite code"
                name="inviteCode"
                required={info?.registration.mode === "invite_only"}
                optional={info?.registration.mode !== "invite_only"}
                pattern="[A-Za-z0-9]{8}"
                defaultValue={invite ?? undefined}
              />
            )}
            <Button variant="primary" type="submit" block busy={busy !== null} disabled={info === null}>
              {busy ?? "Create account"}
            </Button>
          </form>
        )}

        {error && (
          <p className="sn-form-error" role="alert">
            {error}
          </p>
        )}
      </Card>
    </Backdrop>
  );
}

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}
