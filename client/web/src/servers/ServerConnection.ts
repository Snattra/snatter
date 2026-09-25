import { type Api, ApiRequestError, createApi, unwrap } from "../api/client";
import type { GatewayCloseReason } from "../api/types";
import { solveChallenge } from "../auth/altcha";
import { Gateway } from "../gateway/Gateway";
import { platform } from "../platform/platform";
import { applyFrame, fromReady } from "../state/serverView";
import { type ServerEntry, useServers } from "../state/store";

export interface Registration {
  username: string;
  password: string;
  displayName?: string;
  inviteCode?: string;
}

/**
 * Everything the app does with one server: its session, its REST API and its
 * gateway connection, feeding that server's entry in the store.
 */
export class ServerConnection {
  readonly api: Api;
  private token: string | null = null;
  private gateway: Gateway | null = null;

  constructor(readonly origin: string) {
    this.api = createApi(origin, () => this.token);
  }

  /** Picks up the session kept from last time, if any. */
  async resume(): Promise<void> {
    const token = await platform.secrets.get(this.secretKey);
    if (token === null) {
      this.update({ status: "signed_out" });
    } else {
      this.start(token);
    }
  }

  async logIn(username: string, password: string): Promise<void> {
    const auth = unwrap(await this.api.POST("/api/v1/auth/login", { body: { username, password } }));
    await this.signedIn(auth.token);
  }

  /** Solves the registration challenge first when the server asks for one. */
  async register(registration: Registration, challengeRequired: boolean): Promise<void> {
    let altcha: string | undefined;
    if (challengeRequired) {
      altcha = await solveChallenge(unwrap(await this.api.GET("/api/v1/auth/challenge")));
    }
    const auth = unwrap(await this.api.POST("/api/v1/auth/register", { body: { ...registration, altcha } }));
    await this.signedIn(auth.token);
  }

  async logOut(): Promise<void> {
    try {
      unwrap(await this.api.POST("/api/v1/auth/logout"));
    } catch (e) {
      // Signed out locally all the same; the session ends on the server by itself.
      if (!(e instanceof ApiRequestError)) {
        throw e;
      }
    } finally {
      await this.signOut(null);
    }
  }

  private get secretKey(): string {
    return `session:${this.origin}`;
  }

  private async signedIn(token: string): Promise<void> {
    await platform.secrets.set(this.secretKey, token);
    this.start(token);
  }

  private start(token: string): void {
    this.gateway?.stop();
    this.token = token;
    this.update({ status: "connecting", notice: null });
    this.gateway = new Gateway(Gateway.urlFor(this.origin), token, {
      frame: (frame) =>
        useServers.getState().update(this.origin, (entry) => {
          if (frame.type === "ready") {
            return { ...entry, status: "connected", view: fromReady(frame) };
          }
          return entry.view === null ? entry : { ...entry, view: applyFrame(entry.view, frame, Date.now()) };
        }),
      reconnecting: () => this.update({ status: "reconnecting" }),
      ended: (reason) => void this.signOut(endedNotice(reason)),
    });
    this.gateway.start();
  }

  private async signOut(notice: string | null): Promise<void> {
    this.gateway?.stop();
    this.gateway = null;
    this.token = null;
    await platform.secrets.delete(this.secretKey);
    this.update({ status: "signed_out", view: null, notice });
  }

  private update(patch: Partial<Omit<ServerEntry, "origin">>): void {
    useServers.getState().update(this.origin, (entry) => ({ ...entry, ...patch }));
  }
}

function endedNotice(reason: GatewayCloseReason): string {
  switch (reason) {
    case "banned":
      return "You were banned from this server. Signing in again shows why.";
    case "session_ended":
    case "authentication_failed":
      return "Your session has ended. Please sign in again.";
    default:
      return `The connection was closed (${reason}). Please sign in again.`;
  }
}

const connections = new Map<string, ServerConnection>();

/** The one connection for a server, created on first use. */
export function connectionTo(origin: string): ServerConnection {
  let connection = connections.get(origin);
  if (connection === undefined) {
    connection = new ServerConnection(origin);
    connections.set(origin, connection);
  }
  return connection;
}
