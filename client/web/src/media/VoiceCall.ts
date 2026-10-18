import { trustedScriptUrl } from "../trustedTypes";
import type { MicInput } from "./gate";
import { membersOf, sessionOf } from "./sdp";
import { Speakers } from "./speakers";
import { SpeakingTracker, sameMembers } from "./speaking";
import type { GateSettings } from "./voiceGate.worklet";
import gateUrl from "./voiceGate.worklet.ts?worker&url";

/** How often the voices coming in are measured for who is speaking. */
const LISTEN_MS = 100;

/** What a call tells the app as it goes. */
export interface VoiceCallEvents {
  /** The connection carrying audio came up or went down. */
  connected(connected: boolean): void;
  /** How loud the microphone is and whether it is sent, from the gate. */
  input(input: MicInput): void;
  /** Who of those this app hears is speaking, by account id, when that changes. */
  speaking(members: ReadonlySet<string>): void;
}

/**
 * The member's side of voice: their microphone, the WebRTC connection that
 * carries it to the server, and the voices of the others coming back. The
 * server offers and this answers: first when the member comes into voice,
 * then whenever who they hear changes. The microphone goes through the voice
 * gate, so only sound louder than the member's threshold is sent. Muting
 * turns what is sent off and deafening the others' voices, so the
 * connection stays up. Who is speaking comes from the voices coming in.
 */
export class VoiceCall {
  private peer: RTCPeerConnection | null = null;
  /** The session the connection answers the server's offers in. */
  private session: string | null = null;
  /** The member on each line the server sends on, by mid, as the last offer said. */
  private members = new Map<string, string>();
  private readonly tracker = new SpeakingTracker();
  private speaking: ReadonlySet<string> = new Set();
  private listening: ReturnType<typeof setInterval> | null = null;
  /** The others' voices, played by an `<audio>` element each. */
  private readonly speakers = new Speakers((track) => {
    const audio = new Audio();
    audio.srcObject = new MediaStream([track]);
    return audio;
  });

  private constructor(
    private readonly microphone: MediaStream,
    private readonly audio: AudioContext,
    private readonly gate: AudioWorkletNode,
    /** The microphone after the gate: what the server gets. */
    private readonly sent: MediaStream,
    private readonly events: VoiceCallEvents,
  ) {}

  /**
   * Asks for the microphone first, which fails when the member refuses or
   * has none, then puts the gate after it, at the threshold given, in dBFS.
   */
  static async start(threshold: number, events: VoiceCallEvents): Promise<VoiceCall> {
    const microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
    let audio: AudioContext | null = null;
    try {
      // At the browser's default rate, which Firefox also takes microphones in at: it can't
      // connect one to a context at another.
      audio = new AudioContext();
      await audio.audioWorklet.addModule(trustedScriptUrl(gateUrl));
      const gate = new AudioWorkletNode(audio, "voice-gate", {
        numberOfInputs: 1,
        numberOfOutputs: 1,
        channelCount: 1,
        channelCountMode: "explicit",
        outputChannelCount: [1],
        processorOptions: { threshold },
      });
      gate.port.onmessage = (event: MessageEvent<MicInput>) => events.input(event.data);
      const sent = audio.createMediaStreamDestination();
      audio.createMediaStreamSource(microphone).connect(gate).connect(sent);
      // Joining was a click, so the browser lets it run; resuming covers one held back anyway.
      void audio.resume();
      return new VoiceCall(microphone, audio, gate, sent.stream, events);
    } catch (e) {
      void audio?.close();
      for (const track of microphone.getTracks()) {
        track.stop();
      }
      throw e;
    }
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
    if (this.peer === peer) {
      this.members = membersOf(offer);
    }
    if (fresh) {
      // Takes the line the offer made for the member's voice, which the answer then sends on.
      for (const track of this.sent.getAudioTracks()) {
        peer.addTrack(track, this.sent);
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
        this.events.connected(peer.connectionState === "connected");
      }
    };
    peer.ontrack = (event) => {
      if (this.peer === peer) {
        this.speakers.play(event.transceiver.mid ?? event.track.id, event.track);
      }
    };
    let measuring = false;
    this.listening = setInterval(() => {
      if (!measuring) {
        measuring = true;
        void this.listen(peer).finally(() => (measuring = false));
      }
    }, LISTEN_MS);
    return peer;
  }

  /** Measures each voice coming in, by the member on its line, and says when who is speaking changes. */
  private async listen(peer: RTCPeerConnection): Promise<void> {
    const levels = new Map<string, number>();
    for (const transceiver of peer.getTransceivers()) {
      const member = transceiver.mid === null ? undefined : this.members.get(transceiver.mid);
      if (member !== undefined) {
        const stats = await transceiver.receiver.getStats();
        stats.forEach((entry: RTCStats) => {
          if (entry.type === "inbound-rtp") {
            const level = (entry as RTCInboundRtpStreamStats).audioLevel ?? 0;
            levels.set(member, Math.max(levels.get(member) ?? 0, level));
          }
        });
      }
    }
    if (this.peer === peer) {
      this.heard(this.tracker.update(levels, performance.now()));
    }
  }

  private heard(speaking: ReadonlySet<string>): void {
    if (!sameMembers(speaking, this.speaking)) {
      this.speaking = speaking;
      this.events.speaking(speaking);
    }
  }

  /**
   * Turns what is sent off or on. The gate hears of it first: it sends
   * silence until it has dropped what it heard while muted, so unmuting
   * sends none of that.
   */
  setMuted(muted: boolean): void {
    const settings: GateSettings = { muted };
    this.gate.port.postMessage(settings);
    for (const track of this.sent.getAudioTracks()) {
      track.enabled = !muted;
    }
  }

  /** How loud, in dBFS, the microphone must be to be sent. */
  setThreshold(threshold: number): void {
    const settings: GateSettings = { threshold };
    this.gate.port.postMessage(settings);
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
      this.members = new Map();
      if (this.listening !== null) {
        clearInterval(this.listening);
        this.listening = null;
      }
      this.speakers.stop();
      this.heard(new Set());
      this.events.connected(false);
    }
  }

  /** Closes the connection and lets go of the microphone. */
  close(): void {
    this.disconnect();
    this.gate.port.onmessage = null;
    void this.audio.close();
    for (const track of [...this.microphone.getTracks(), ...this.sent.getTracks()]) {
      track.stop();
    }
  }
}
