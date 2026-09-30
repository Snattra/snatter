import type { GatewayClientFrame, GatewayCloseReason, GatewayServerFrame } from "../api/types";
import { PROTOCOL_VERSION } from "../state/protocol";
import { closeAction } from "./closeCodes";

export interface GatewayListener {
  /**
   * A frame in order; `ready` starts over after every reconnect. The type
   * may be one from a newer server that the contract here does not know.
   */
  frame(frame: GatewayServerFrame): void;
  /** The connection dropped and will be retried; what the client knows is going stale. */
  reconnecting(): void;
  /** The server ended the session or refused it, for a reason this client may not know by name; no more retries. */
  ended(reason: GatewayCloseReason | null): void;
}

/** Closed by the client itself when frames arrive out of order, so it resyncs. */
const SEQ_GAP = 3000;
const MAX_RETRY_DELAY_MS = 30_000;

/**
 * One gateway connection that keeps itself open: it identifies with the
 * session token and the client's protocol version, checks that frames are
 * numbered without gaps, and after losing the connection retries with
 * growing, jittered delays until the next `ready`.
 */
export class Gateway {
  private socket: WebSocket | null = null;
  private seq = 0;
  private attempt = 0;
  private retry: ReturnType<typeof setTimeout> | null = null;
  private stopped = false;

  constructor(
    private readonly url: string,
    private readonly token: string,
    private readonly listener: GatewayListener,
  ) {}

  /** The gateway URL of a server, from its HTTP origin. */
  static urlFor(origin: string): string {
    return origin.replace(/^http/, "ws") + "/api/v1/gateway";
  }

  start(): void {
    this.open();
  }

  stop(): void {
    this.stopped = true;
    if (this.retry !== null) {
      clearTimeout(this.retry);
    }
    this.socket?.close(1000);
    this.socket = null;
  }

  /** Sends a frame if the connection is open; dropped otherwise, as frames are only hints. */
  send(frame: GatewayClientFrame): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(frame));
    }
  }

  private open(): void {
    this.seq = 0;
    const socket = new WebSocket(this.url);
    this.socket = socket;
    socket.onopen = () => {
      socket.send(
        JSON.stringify({ type: "identify", token: this.token, protocol: PROTOCOL_VERSION } satisfies GatewayClientFrame),
      );
    };
    socket.onmessage = (event: MessageEvent<string>) => {
      const frame = JSON.parse(event.data) as GatewayServerFrame;
      if (frame.seq !== this.seq + 1) {
        socket.close(SEQ_GAP, "seq_gap");
        return;
      }
      this.seq = frame.seq;
      if (frame.type === "ready") {
        this.attempt = 0;
      }
      this.listener.frame(frame);
    };
    socket.onclose = (event) => {
      if (this.stopped || this.socket !== socket) {
        return;
      }
      this.socket = null;
      const action = closeAction(event.code);
      if (action.kind === "end") {
        this.stopped = true;
        this.listener.ended(action.reason);
      } else {
        this.listener.reconnecting();
        this.scheduleRetry();
      }
    };
  }

  private scheduleRetry(): void {
    const ceiling = Math.min(MAX_RETRY_DELAY_MS, 1000 * 2 ** this.attempt);
    this.attempt++;
    this.retry = setTimeout(() => {
      this.retry = null;
      this.open();
    }, ceiling / 2 + Math.random() * (ceiling / 2));
  }
}
