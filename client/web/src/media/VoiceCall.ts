/**
 * The member's side of voice: their microphone, and the WebRTC connection
 * that carries it to the server. The server offers and this answers, once
 * for every time the member comes into voice. Muting turns the microphone's
 * track off, so the connection stays up.
 */
export class VoiceCall {
  private peer: RTCPeerConnection | null = null;

  private constructor(
    private readonly microphone: MediaStream,
    private readonly connectedChanged: (connected: boolean) => void,
  ) {}

  /** Asks for the microphone first, which fails when the member refuses or has none. */
  static async start(connectedChanged: (connected: boolean) => void): Promise<VoiceCall> {
    const microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
    return new VoiceCall(microphone, connectedChanged);
  }

  /** Answers the server's offer on a new connection, replacing any earlier one, and returns the answer. */
  async answer(offer: string): Promise<string> {
    this.disconnect();
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
    await peer.setRemoteDescription({ type: "offer", sdp: offer });
    // Takes the line the offer made for the member's voice, which the answer then sends on.
    for (const track of this.microphone.getAudioTracks()) {
      peer.addTrack(track, this.microphone);
    }
    await peer.setLocalDescription();
    const answer = peer.localDescription;
    if (answer === null) {
      throw new Error("No answer was made");
    }
    return answer.sdp;
  }

  setMuted(muted: boolean): void {
    for (const track of this.microphone.getAudioTracks()) {
      track.enabled = !muted;
    }
  }

  /** Closes the connection but keeps the microphone, to answer again after the gateway reconnects. */
  disconnect(): void {
    if (this.peer !== null) {
      this.peer.onconnectionstatechange = null;
      this.peer.close();
      this.peer = null;
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
