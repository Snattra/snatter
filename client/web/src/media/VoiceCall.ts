import { sessionOf } from "./sdp";

/**
 * The member's side of voice: their microphone, the WebRTC connection that
 * carries it to the server, and the voices of the others coming back. The
 * server offers and this answers: first when the member comes into voice,
 * then whenever who they hear changes. Muting turns the microphone's track
 * off and deafening the others' voices, so the connection stays up.
 */
export class VoiceCall {
  private peer: RTCPeerConnection | null = null;
  /** The session the connection answers the server's offers in. */
  private session: string | null = null;
  /** What plays each of the others' voices, by track; a line the server reuses keeps its track. */
  private readonly speakers = new Map<string, HTMLAudioElement>();
  private deafened = false;

  private constructor(
    private readonly microphone: MediaStream,
    private readonly connectedChanged: (connected: boolean) => void,
  ) {}

  /** Asks for the microphone first, which fails when the member refuses or has none. */
  static async start(connectedChanged: (connected: boolean) => void): Promise<VoiceCall> {
    const microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
    return new VoiceCall(microphone, connectedChanged);
  }

  /**
   * Answers the server's offer and returns the answer. An offer of the same
   * session changes the connection there is; one of another session, as
   * after the server closed its side, starts a new one.
   */
  async answer(offer: string): Promise<string> {
    const session = sessionOf(offer);
    const fresh = this.peer === null || session !== this.session;
    if (fresh) {
      this.disconnect();
      this.session = session;
    }
    const peer = this.peer ?? this.open();
    await peer.setRemoteDescription({ type: "offer", sdp: offer });
    if (fresh) {
      // Takes the line the offer made for the member's voice, which the answer then sends on.
      for (const track of this.microphone.getAudioTracks()) {
        peer.addTrack(track, this.microphone);
      }
    }
    await peer.setLocalDescription();
    const answer = peer.localDescription;
    if (this.peer !== peer || answer === null) {
      throw new Error("The connection closed while answering");
    }
    return answer.sdp;
  }

  private open(): RTCPeerConnection {
    // The server's offer bundles everything on one transport. It lists every
    // address it can be reached at, and learns the browser's from its checks,
    // so no STUN server is needed.
    const peer = new RTCPeerConnection({ bundlePolicy: "max-bundle", rtcpMuxPolicy: "require" });
    this.peer = peer;
    peer.onconnectionstatechange = () => {
      if (this.peer === peer) {
        this.connectedChanged(peer.connectionState === "connected");
      }
    };
    peer.ontrack = (event) => {
      if (this.peer === peer) {
        this.play(event.track);
      }
    };
    return peer;
  }

  /** Plays another member's voice, once per track, as a line may come back for someone else. */
  private play(track: MediaStreamTrack): void {
    if (this.speakers.has(track.id)) {
      return;
    }
    const speaker = new Audio();
    speaker.srcObject = new MediaStream([track]);
    speaker.muted = this.deafened;
    this.speakers.set(track.id, speaker);
    speaker.play().catch((e: unknown) => console.warn("Could not play a voice", e));
  }

  setMuted(muted: boolean): void {
    for (const track of this.microphone.getAudioTracks()) {
      track.enabled = !muted;
    }
  }

  setDeafened(deafened: boolean): void {
    this.deafened = deafened;
    for (const speaker of this.speakers.values()) {
      speaker.muted = deafened;
    }
  }

  /** Closes the connection but keeps the microphone, to answer again after the gateway reconnects. */
  disconnect(): void {
    if (this.peer !== null) {
      this.peer.onconnectionstatechange = null;
      this.peer.ontrack = null;
      this.peer.close();
      this.peer = null;
      this.session = null;
      for (const speaker of this.speakers.values()) {
        speaker.pause();
        speaker.srcObject = null;
      }
      this.speakers.clear();
      this.connectedChanged(false);
    }
  }

  /** Closes the connection and lets go of the microphone. */
  close(): void {
    this.disconnect();
    for (const track of this.microphone.getTracks()) {
      track.stop();
    }
  }
}
