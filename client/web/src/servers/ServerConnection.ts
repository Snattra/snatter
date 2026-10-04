import { type Api, ApiRequestError, createApi, unwrap } from "../api/client";
import type {
  Ban,
  Channel,
  ChannelCreate,
  ChannelUpdate,
  GatewayCloseReason,
  GatewayServerFrame,
  Invite,
  Message,
  ServerSettings,
  ServerSettingsUpdate,
} from "../api/types";
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
  withPurged,
  withoutPending,
  withUpdated,
} from "../state/channelLog";
import { isAfter, later } from "../state/ids";
import { INVITE_LIFETIME_SECONDS, reusableInvite } from "../state/invites";
import { isTimedOut } from "../state/moderation";
import { type Outdated, compatibility, isOutdated } from "../state/protocol";
import { type ServerView, applyFrame, fromReady } from "../state/serverView";
import { type ServerEntry, blank, useServers } from "../state/store";
import { type LocalVoice, endNotice, micOff, noVoice, refusalNotice } from "../state/voice";
import { describeError } from "../ui/errors";

export interface Registration {
  username: string;
  password: string;
  displayName?: string;
  inviteCode?: string;
}

/** The most messages fetched to catch a channel up after reconnecting; beyond that it starts over from the newest. */
const CATCH_UP_LIMIT = 100;

/** How long to wait for the gateway to bring a change into the view, as when the connection is down meanwhile. */
const VIEW_WAIT_MS = 5_000;

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
  /** The voice channel the server confirmed this connection is in, to fall back to when a move is refused. */
  private voiceHeld: string | null = null;

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

  // --- Invites ----------------------------------------------------------------

  /**
   * A link for inviting people: the member's newest invite that can be
   * handed out again, so asking twice does not pile up invites, or else a new
   * one that works for a week.
   */
  async inviteLink(): Promise<Invite> {
    const account = this.entry().view?.account;
    if (account !== undefined) {
      const reusable = reusableInvite(unwrap(await this.api.GET("/api/v1/invites")), account.id, Date.now());
      if (reusable !== null) {
        return reusable;
      }
    }
    return unwrap(await this.api.POST("/api/v1/invites", { body: { expiresInSeconds: INVITE_LIFETIME_SECONDS } }));
  }

  // --- Channels ---------------------------------------------------------------

  /**
   * Creates a channel and waits for the gateway to announce it, so it can be
   * shown as soon as this returns.
   */
  async createChannel(create: ChannelCreate): Promise<Channel> {
    const channel = unwrap(await this.api.POST("/api/v1/channels", { body: create }));
    await this.untilView((view) => view.channels[channel.id] !== undefined);
    return channel;
  }

  /** Changes a channel; the view follows when the gateway sends the result. */
  async updateChannel(channelId: string, update: ChannelUpdate): Promise<Channel> {
    return unwrap(
      await this.api.PATCH("/api/v1/channels/{id}", { params: { path: { id: channelId } }, body: update }),
    );
  }

  async deleteChannel(channelId: string): Promise<void> {
    unwrap(await this.api.DELETE("/api/v1/channels/{id}", { params: { path: { id: channelId } } }));
  }

  // --- Server settings --------------------------------------------------------

  async serverSettings(): Promise<ServerSettings> {
    return unwrap(await this.api.GET("/api/v1/server-settings"));
  }

  /** Changes the settings; what members see of them follows over the gateway. */
  async updateServerSettings(update: ServerSettingsUpdate): Promise<ServerSettings> {
    return unwrap(await this.api.PATCH("/api/v1/server-settings", { body: update }));
  }

  // --- Moderation -------------------------------------------------------------
  // Each change resolves once the gateway shows it, so the profile that made it shows it at once.

  /** Gives and takes away roles, one at a time; a refusal stops there. */
  async changeRoles(accountId: string, add: string[], remove: string[]): Promise<void> {
    for (const roleId of add) {
      unwrap(
        await this.api.PUT("/api/v1/accounts/{id}/roles/{roleId}", { params: { path: { id: accountId, roleId } } }),
      );
    }
    for (const roleId of remove) {
      unwrap(
        await this.api.DELETE("/api/v1/accounts/{id}/roles/{roleId}", { params: { path: { id: accountId, roleId } } }),
      );
    }
    await this.untilView((view) => {
      const roleIds = view.members[accountId]?.roleIds ?? [];
      return add.every((id) => roleIds.includes(id)) && !remove.some((id) => roleIds.includes(id));
    });
  }

  async timeOut(accountId: string, durationSeconds: number): Promise<void> {
    unwrap(
      await this.api.PUT("/api/v1/timeouts/{accountId}", {
        params: { path: { accountId } },
        body: { durationSeconds },
      }),
    );
    await this.untilView((view) => isTimedOut(view.members[accountId] ?? { timedOutUntil: null }, Date.now()));
  }

  async endTimeout(accountId: string): Promise<void> {
    unwrap(await this.api.DELETE("/api/v1/timeouts/{accountId}", { params: { path: { accountId } } }));
    await this.untilView((view) => !isTimedOut(view.members[accountId] ?? { timedOutUntil: null }, Date.now()));
  }

  /** Bans a member; a blank reason means none. */
  async ban(accountId: string, reason: string): Promise<void> {
    const body = reason.trim() === "" ? {} : { reason: reason.trim() };
    unwrap(await this.api.PUT("/api/v1/bans/{accountId}", { params: { path: { accountId } }, body }));
    await this.untilView((view) => view.members[accountId]?.bannedAt != null);
  }

  async liftBan(accountId: string): Promise<void> {
    unwrap(await this.api.DELETE("/api/v1/bans/{accountId}", { params: { path: { accountId } } }));
    await this.untilView((view) => view.members[accountId]?.bannedAt == null);
  }

  /** Turns a member's microphone off for everyone, until unmuted. */
  async mute(accountId: string): Promise<void> {
    unwrap(await this.api.PUT("/api/v1/mutes/{accountId}", { params: { path: { accountId } } }));
    await this.untilView((view) => view.members[accountId]?.mutedAt != null);
  }

  async unmute(accountId: string): Promise<void> {
    unwrap(await this.api.DELETE("/api/v1/mutes/{accountId}", { params: { path: { accountId } } }));
    await this.untilView((view) => view.members[accountId]?.mutedAt == null);
  }

  /** Every ban with its reason, for members holding `BAN_MEMBERS`. */
  async bans(): Promise<Ban[]> {
    return unwrap(await this.api.GET("/api/v1/bans"));
  }

  // --- Voice ------------------------------------------------------------------
  // The store shows what was asked for at once; the member's own voice state
  // in the view says when the server has done it.

  /** Joins a voice channel, or moves there from the one the member is in. */
  joinVoice(channelId: string): void {
    this.updateVoice((voice) => ({ ...voice, channelId, notice: null }));
    this.sendVoice();
  }

  leaveVoice(): void {
    this.voiceHeld = null;
    this.updateVoice((voice) => ({ ...voice, channelId: null, notice: null }));
    this.gateway?.send({ type: "voice_state", channelId: null, selfMuted: false, selfDeafened: false });
  }

  /** Turns the member's microphone off or on; turning it on while deafened turns sound back on too. */
  setSelfMuted(selfMuted: boolean): void {
    this.updateVoice((voice) => ({ ...voice, selfMuted, selfDeafened: selfMuted && voice.selfDeafened }));
    this.sendVoice();
  }

  /** Turns sound off, which mutes too, or back on, with the microphone as the member left it. */
  setSelfDeafened(selfDeafened: boolean): void {
    this.updateVoice((voice) => ({ ...voice, selfDeafened }));
    this.sendVoice();
  }

  dismissVoiceNotice(): void {
    this.updateVoice((voice) => ({ ...voice, notice: null }));
  }

  /** Tells the server where the member wants to be, if anywhere. */
  private sendVoice(): void {
    const voice = this.entry().voice;
    if (voice.channelId !== null) {
      this.gateway?.send({
        type: "voice_state",
        channelId: voice.channelId,
        selfMuted: micOff(voice),
        selfDeafened: voice.selfDeafened,
      });
    }
  }

  private applyToVoice(frame: GatewayServerFrame): void {
    const { view, voice } = this.entry();
    const timedOut = view !== null && isTimedOut(view.account, Date.now());
    switch (frame.type) {
      case "voice_state_updated":
        if (frame.voiceState.accountId === view?.account.id && frame.voiceState.channelId === voice.channelId) {
          this.voiceHeld = voice.channelId;
        }
        break;
      case "voice_refused":
        if (frame.channelId === voice.channelId) {
          const notice = refusalNotice(frame.reason, view?.channels[frame.channelId], timedOut);
          this.updateVoice((current) => ({ ...current, channelId: this.voiceHeld, notice }));
        }
        break;
      case "voice_ended":
        this.voiceHeld = null;
        this.updateVoice((current) => ({ ...current, channelId: null, notice: endNotice(frame.reason, timedOut) }));
        break;
      default:
        break;
    }
  }

  private updateVoice(change: (voice: LocalVoice) => LocalVoice): void {
    useServers.getState().update(this.origin, (entry) => ({ ...entry, voice: change(entry.voice) }));
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

  /** Replaces the text of one of the member's messages; the log shows it once the server has it. */
  async edit(channelId: string, messageId: string, content: string): Promise<void> {
    const message = unwrap(
      await this.api.PATCH("/api/v1/channels/{id}/messages/{messageId}", {
        params: { path: { id: channelId, messageId } },
        body: { content },
      }),
    );
    this.updateHeldLog(channelId, (log) => withUpdated(log, message));
  }

  /**
   * Deletes a message. It turns into what the server will announce straight
   * away: a deleted member's message, or nothing for a notice.
   */
  async delete(message: Message): Promise<void> {
    unwrap(
      await this.api.DELETE("/api/v1/channels/{id}/messages/{messageId}", {
        params: { path: { id: message.channelId, messageId: message.id } },
      }),
    );
    const me = this.entry().view?.account.id;
    this.updateHeldLog(message.channelId, (log) =>
      message.kind === "system"
        ? withDeleted(log, message.id)
        : withUpdated(log, {
            kind: "deleted",
            id: message.id,
            channelId: message.channelId,
            authorId: message.authorId,
            createdAt: message.createdAt,
            deletedAt: new Date().toISOString(),
            removedByModerator: message.authorId !== me,
          }),
    );
  }

  /** Deletes everything a member sent since a time, in the channels the member can see; resolves with how many. */
  async purge(accountId: string, since: Date): Promise<number> {
    const result = unwrap(
      await this.api.DELETE("/api/v1/accounts/{id}/messages", {
        params: { path: { id: accountId }, query: { since: since.toISOString() } },
      }),
    );
    return result.removed;
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
      case "messages_purged":
        this.updateHeldLog(frame.channelId, (log) => withPurged(log, frame));
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
    this.voiceHeld = null;
    this.update({ status: "connecting", notice: null, voice: noVoice });
    this.gateway = new Gateway(Gateway.urlFor(this.origin), token, {
      frame: (frame) => {
        // Checked on every ready, since the server may have been upgraded while the app was away.
        if (frame.type === "ready") {
          const compatible = compatibility(frame.server.protocol);
          if (isOutdated(compatible)) {
            this.stopOutdated(compatible);
            return;
          }
        }
        useServers.getState().update(this.origin, (entry) => {
          if (frame.type === "ready") {
            return { ...entry, status: "connected", view: fromReady(frame) };
          }
          return entry.view === null ? entry : { ...entry, view: applyFrame(entry.view, frame, Date.now()) };
        });
        if (frame.type === "ready") {
          // The server took the member out of voice when the last connection closed, so join again.
          this.voiceHeld = null;
          this.sendVoice();
          void this.catchUp();
        } else {
          this.applyToLogs(frame);
          this.applyToVoice(frame);
        }
      },
      reconnecting: () => this.update({ status: "reconnecting" }),
      ended: (reason) => {
        if (reason === "client_outdated") {
          this.stopOutdated(reason);
        } else {
          void this.signOut(endedNotice(reason));
        }
      },
    });
    this.gateway.start();
  }

  /** Stops talking to the server, keeping the session for when the app or the server has been updated. */
  private stopOutdated(outdated: Outdated): void {
    this.gateway?.stop();
    this.gateway = null;
    this.update({ status: outdated, view: null, voice: noVoice });
  }

  private async signOut(notice: string | null): Promise<void> {
    this.gateway?.stop();
    this.gateway = null;
    this.token = null;
    await platform.secrets.delete(this.secretKey);
    this.update({ status: "signed_out", view: null, logs: {}, notice, voice: noVoice });
  }

  /**
   * Resolves once the view passes the test, which it does as soon as the
   * gateway delivers the change, or the view is gone, or after
   * {@link VIEW_WAIT_MS} in case the change is held up (the next `ready`
   * brings it).
   */
  private untilView(test: (view: ServerView) => boolean): Promise<void> {
    const passes = () => {
      const view = this.entry().view;
      return view === null || test(view);
    };
    if (passes()) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      const done = () => {
        clearTimeout(timer);
        unsubscribe();
        resolve();
      };
      const timer = setTimeout(done, VIEW_WAIT_MS);
      const unsubscribe = useServers.subscribe(() => {
        if (passes()) {
          done();
        }
      });
    });
  }

  private entry(): ServerEntry {
    return useServers.getState().servers[this.origin] ?? blank(this.origin);
  }

  private update(patch: Partial<Omit<ServerEntry, "origin">>): void {
    useServers.getState().update(this.origin, (entry) => ({ ...entry, ...patch }));
  }
}

function endedNotice(reason: GatewayCloseReason | null): string {
  switch (reason) {
    case "banned":
      return "You were banned from this server. Signing in again shows why.";
    case "session_ended":
    case "authentication_failed":
      return "Your session has ended. Please sign in again.";
    case null:
      return "The server closed the connection. Please sign in again.";
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
