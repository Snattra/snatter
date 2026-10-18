// The voice gate in the audio thread, between the microphone and what is
// sent. It runs there so it keeps time while the app is in a background tab,
// where the browser slows the page's own timers down.
import { type MicInput, VoiceGate } from "./gate";

// The audio worklet's globals, which TypeScript has no library for.
declare const sampleRate: number;
declare class AudioWorkletProcessor {
  readonly port: MessagePort;
}
declare function registerProcessor(
  name: string,
  processor: new (options: { processorOptions: { threshold: number } }) => AudioWorkletProcessor,
): void;

/** What the app sends it: a new threshold, in dBFS, or whether the member is muted. */
export type GateSettings = { threshold: number } | { muted: boolean };

class VoiceGateProcessor extends AudioWorkletProcessor {
  private readonly gate: VoiceGate;

  constructor(options: { processorOptions: { threshold: number } }) {
    super();
    this.gate = new VoiceGate(sampleRate, options.processorOptions.threshold, (input: MicInput) =>
      this.port.postMessage(input),
    );
    this.port.onmessage = (event: MessageEvent<GateSettings>) => {
      if ("threshold" in event.data) {
        this.gate.threshold = event.data.threshold;
      } else {
        this.gate.setMuted(event.data.muted);
      }
    };
  }

  // One mono input, the microphone, and one mono output, what is sent.
  process(inputs: Float32Array[][], outputs: Float32Array[][]): boolean {
    const output = outputs[0]?.[0];
    if (output !== undefined) {
      this.gate.process(inputs[0]?.[0], output);
    }
    return true;
  }
}

registerProcessor("voice-gate", VoiceGateProcessor);
