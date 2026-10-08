import { sessionOf } from "./sdp";
import { Speakers } from "./speakers";

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
  /** The others' voices, played by an `<audio>` element each. */
  private readonly speakers = new Speakers((track) => {
    const audio = new Audio();
    audio.srcObject = new MediaStream([track]);
    return audio;
  });

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
        this.speakers.play(event.transceiver.mid ?? event.track.id, event.track);
      }
    };
    return peer;
  }

  setMuted(muted: boolean): void {
    for (const track of this.microphone.getAudioTracks()) {
      track.enabled = !muted;
    }
  }

  setDeafened(deafened: boolean): void {
    this.speakers.setMuted(deafened);
  }

  /** Closes the connection but keeps the microphone, to answer again after the gateway reconnects. */
  disconnect(): void {
    if (this.peer !== null) {
      this.peer.onconnectionstatechange = null;
      this.peer.ontrack = null;
      this.peer.close();
      this.peer = null;
      this.session = null;
      this.speakers.stop();
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
