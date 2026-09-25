import { type FormEvent, useEffect, useState } from "react";
import { unwrap } from "../api/client";
import type { ServerInfo } from "../api/types";
import type { ServerConnection } from "../servers/ServerConnection";
import { describeError } from "./errors";

type Tab = "log_in" | "register";

export function AuthScreen({ connection, notice }: { connection: ServerConnection; notice: string | null }) {
  const [info, setInfo] = useState<ServerInfo | null>(null);
  const [tab, setTab] = useState<Tab>("log_in");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    connection.api
      .GET("/api/v1/server-info")
      .then(unwrap)
      .then(setInfo, (e: unknown) => setError(describeError(e)));
  }, [connection]);

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
    const displayName = text(form, "displayName").trim();
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
    <div className="center">
      <div className="card auth">
        <h1>{info?.community.name ?? "Snatter"}</h1>
        {info?.community.description && <p className="muted">{info.community.description}</p>}
        {notice && <p className="notice">{notice}</p>}

        {setup ? (
          <div className="setup">
            <h2>Set up your server</h2>
            <p>
              This server has no accounts yet. The account you create now becomes its owner, with full control over
              the server and its settings.
            </p>
          </div>
        ) : (
          <div className="tabs" role="tablist">
            <button role="tab" aria-selected={tab === "log_in"} onClick={() => setTab("log_in")}>
              Sign in
            </button>
            <button role="tab" aria-selected={tab === "register"} onClick={() => setTab("register")}>
              Create account
            </button>
          </div>
        )}

        {tab === "log_in" && !setup ? (
          <form onSubmit={logIn}>
            <label>
              Username
              <input name="username" autoComplete="username" required />
            </label>
            <label>
              Password
              <input name="password" type="password" autoComplete="current-password" required />
            </label>
            <button type="submit" disabled={busy !== null}>
              Sign in
            </button>
          </form>
        ) : (
          <form onSubmit={register}>
            <label>
              Username
              <input name="username" autoComplete="username" required minLength={3} maxLength={32} pattern="[A-Za-z0-9_.]+" />
            </label>
            <label>
              Display name <span className="muted">(optional)</span>
              <input name="displayName" autoComplete="nickname" maxLength={64} />
            </label>
            <label>
              Password
              <input name="password" type="password" autoComplete="new-password" required minLength={8} maxLength={128} />
            </label>
            {info?.registration.mode === "invite_only" && !setup && (
              <label>
                Invite code
                <input name="inviteCode" required pattern="[A-Za-z0-9]{8}" />
              </label>
            )}
            <button type="submit" disabled={busy !== null || info === null}>
              Create account
            </button>
          </form>
        )}

        {busy && <p className="muted">{busy}</p>}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
      </div>
    </div>
  );
}

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}
