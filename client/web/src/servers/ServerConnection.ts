import { type Api, ApiRequestError, createApi, unwrap } from "../api/client";
import type { GatewayCloseReason, GatewayServerFrame, Message } from "../api/types";
import { solveChallenge } from "../auth/altcha";
import { Gateway } from "../gateway/Gateway";
import { platform } from "../platform/platform";
import {
  type ChannelLog,
  PAGE_SIZE,
  emptyLog,
  withDeleted,
  withFailed,
  withLatest,
  withMessage,
  withOlder,
  withPending,
  withoutPending,
  withUpdated,
} from "../state/channelLog";
import { isAfter, later } from "../state/ids";
import { applyFrame, fromReady } from "../state/serverView";
import { type ServerEntry, blank, useServers } from "../state/store";
import { describeError } from "../ui/errors";

export interface Registration {
  username: string;
  password: string;
  displayName?: string;
  inviteCode?: string;
}

/** The most messages fetched to catch a channel up after reconnecting; beyond that it starts over from the newest. */
const CATCH_UP_LIMIT = 100;

/**
 * Everything the app does with one server: its session, its REST API and its
 * gateway connection, feeding that server's entry in the store.
 */
export class ServerConnection {
  readonly api: Api;
  private token: string | null = null;
  private gateway: Gateway | null = null;
  /** Channels with a page on its way, so scrolling does not ask twice. */
  private readonly fetching = new Set<string>();

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

  // --- Messages ---------------------------------------------------------------

  /** Loads the newest messages of a channel the first time it is shown, or after that failed. */
  async open(channelId: string): Promise<void> {
    if (this.entry().logs[channelId]?.loaded !== true) {
      await this.loadLatest(channelId);
    }
  }

  /** Loads the page before the oldest message held, if there is one. */
  async loadOlder(channelId: string): Promise<void> {
    const log = this.entry().logs[channelId];
    const first = log?.messages[0];
    if (!log?.hasOlder || first === undefined || this.fetching.has(channelId)) {
      return;
    }
    this.fetching.add(channelId);
    try {
      const page = await this.page(channelId, { before: first.id, limit: PAGE_SIZE });
      this.updateLog(channelId, (current) => withOlder(current, page, PAGE_SIZE));
    } finally {
      this.fetching.delete(channelId);
    }
  }

  /** Shows the message as pending at once and sends it; a failure stays in the list to retry. */
  async send(channelId: string, content: string): Promise<void> {
    const nonce = crypto.randomUUID();
    this.updateLog(channelId, (log) =>
      withPending(log, { nonce, content, createdAt: new Date().toISOString(), error: null }),
    );
    await this.post(channelId, nonce, content);
  }

  async retry(channelId: string, nonce: string): Promise<void> {
    const pending = this.entry().logs[channelId]?.pending.find((p) => p.nonce === nonce);
    if (pending === undefined) {
      return;
    }
    this.updateLog(channelId, (log) => withPending(log, { ...pending, error: null }));
    await this.post(channelId, nonce, pending.content);
  }

  discard(channelId: string, nonce: string): void {
    this.updateLog(channelId, (log) => withoutPending(log, nonce));
  }

  /**
   * Moves the read marker forward to a message. The view follows at once, so
   * the channel stops showing as unread; the server confirms over the gateway.
   */
  async markRead(channelId: string, messageId: string): Promise<void> {
    const reading = this.entry().view?.reading[channelId];
    if (reading !== undefined && !isAfter(messageId, reading.lastRead)) {
      return;
    }
    useServers.getState().update(this.origin, (entry) => {
      const view = entry.view;
      const before = view?.reading[channelId];
      if (view === null || before === undefined) {
        return entry;
      }
      const reading = { ...before, lastRead: later(before.lastRead, messageId) };
      return { ...entry, view: { ...view, reading: { ...view.reading, [channelId]: reading } } };
    });
    try {
      unwrap(
        await this.api.PUT("/api/v1/channels/{id}/read-state", {
          params: { path: { id: channelId } },
          body: { lastReadMessageId: messageId },
        }),
      );
    } catch (e) {
      // The marker stays where the server has it; the next read moves it again.
      if (!(e instanceof ApiRequestError)) {
        throw e;
      }
    }
  }

  /** Tells the others the member is typing; the caller repeats it every few seconds. */
  typing(channelId: string): void {
    this.gateway?.send({ type: "typing", channelId });
  }

  private async post(channelId: string, nonce: string, content: string): Promise<void> {
    try {
      const message = unwrap(
        await this.api.POST("/api/v1/channels/{id}/messages", {
          params: { path: { id: channelId } },
          body: { content, nonce },
        }),
      );
      this.updateLog(channelId, (log) => withMessage(log, message, true));
    } catch (e) {
      this.updateLog(channelId, (log) => withFailed(log, nonce, describeError(e)));
    }
  }

  /** Starts holding the channel at once, so messages arriving during the fetch are kept. */
  private async loadLatest(channelId: string): Promise<void> {
    if (this.fetching.has(channelId)) {
      return;
    }
    this.fetching.add(channelId);
    try {
      this.updateLog(channelId, (log) => log);
      const page = await this.page(channelId, { limit: PAGE_SIZE });
      this.updateLog(channelId, (log) => withLatest(log, page, PAGE_SIZE));
    } finally {
      this.fetching.delete(channelId);
    }
  }

  /**
   * After reconnecting, fetches what each held channel missed. A channel
   * that missed more than one catch-up page starts over from its newest
   * messages instead. Edits and deletions made meanwhile are not replayed.
   */
  private async catchUp(): Promise<void> {
    const logs = this.entry().logs;
    const channels = this.entry().view?.channels ?? {};
    for (const [channelId, log] of Object.entries(logs)) {
      if (channels[channelId] === undefined) {
        this.dropLog(channelId);
        continue;
      }
      const last = log.messages.at(-1);
      try {
        if (last === undefined) {
          await this.loadLatest(channelId);
          continue;
        }
        const missed = await this.page(channelId, { after: last.id, limit: CATCH_UP_LIMIT });
        if (missed.length === CATCH_UP_LIMIT) {
          const page = await this.page(channelId, { limit: PAGE_SIZE });
          this.updateLog(channelId, (current) => withLatest({ ...emptyLog, pending: current.pending }, page, PAGE_SIZE));
        } else {
          this.updateLog(channelId, (current) => missed.reduce((next, m) => withMessage(next, m, false), current));
        }
      } catch (e) {
        console.warn(`Could not catch up channel ${channelId}`, e);
      }
    }
  }

  private async page(
    channelId: string,
    query: { before?: string; after?: string; limit: number },
  ): Promise<Message[]> {
    return unwrap(
      await this.api.GET("/api/v1/channels/{id}/messages", { params: { path: { id: channelId }, query } }),
    );
  }

  private applyToLogs(frame: GatewayServerFrame): void {
    switch (frame.type) {
      case "message_created":
        this.updateHeldLog(frame.message.channelId, (log) => withMessage(log, frame.message, true));
        break;
      case "message_updated":
        this.updateHeldLog(frame.message.channelId, (log) => withUpdated(log, frame.message));
        break;
      case "message_deleted":
        this.updateHeldLog(frame.channelId, (log) => withDeleted(log, frame.messageId));
        break;
      case "channel_deleted":
        this.dropLog(frame.channelId);
        break;
      default:
        break;
    }
  }

  /** Changes a channel's log, starting an empty one if there is none. */
  private updateLog(channelId: string, change: (log: ChannelLog) => ChannelLog): void {
    useServers.getState().update(this.origin, (entry) => ({
      ...entry,
      logs: { ...entry.logs, [channelId]: change(entry.logs[channelId] ?? emptyLog) },
    }));
  }

  /** Changes a channel's log if the app holds one. */
  private updateHeldLog(channelId: string, change: (log: ChannelLog) => ChannelLog): void {
    if (this.entry().logs[channelId] !== undefined) {
      this.updateLog(channelId, change);
    }
  }

  private dropLog(channelId: string): void {
    useServers.getState().update(this.origin, (entry) => {
      const logs = { ...entry.logs };
      delete logs[channelId];
      return { ...entry, logs };
    });
  }

  // --- Session ----------------------------------------------------------------

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
      frame: (frame) => {
        useServers.getState().update(this.origin, (entry) => {
          if (frame.type === "ready") {
            return { ...entry, status: "connected", view: fromReady(frame) };
          }
          return entry.view === null ? entry : { ...entry, view: applyFrame(entry.view, frame, Date.now()) };
        });
        if (frame.type === "ready") {
          void this.catchUp();
        } else {
          this.applyToLogs(frame);
        }
      },
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
    this.update({ status: "signed_out", view: null, logs: {}, notice });
  }

  private entry(): ServerEntry {
    return useServers.getState().servers[this.origin] ?? blank(this.origin);
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
