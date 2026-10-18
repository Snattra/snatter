/** What the gate says about the microphone, about 20 times a second and whenever it opens or shuts. */
export interface MicInput {
  /** The loudest the microphone was since the last report, in dBFS: 0 is full scale. */
  level: number;
  /** Whether its sound is being sent. */
  open: boolean;
}

/** What a level is measured over. */
const WINDOW_MS = 10;
/**
 * How many windows in a row must be louder than the threshold to open the
 * gate: a click shorter than a window reaches two at most, a syllable many.
 */
const OPEN_WINDOWS = 3;
/** How long the gate stays open after the sound drops, so pauses between words stay in. */
const HOLD_MS = 400;
/** How long opening and shutting fade, so neither clicks. */
const FADE_MS = 10;
/**
 * How far the sound sent runs behind the microphone: long enough to have
 * opened fully before the first of it goes out, so words keep their start.
 * A word starting late in a window may not make that window loud, so it
 * takes the windows to open after that one, and then the fade.
 */
const LOOKAHEAD_MS = (OPEN_WINDOWS + 1) * WINDOW_MS + FADE_MS;
/** How often the gate reports, in windows. */
const REPORT_WINDOWS = 5;
/** The level of silence, so a log of nothing is still a number. */
const SILENCE = -100;

/**
 * Lets the microphone's sound through only while it is louder than the
 * threshold, as speech is and the room is not. Sound must stay loud for a
 * moment to open it, so a click doesn't, and the sound runs a little behind,
 * so what opened it is still sent. Runs in the audio thread, sample by
 * sample, in the voice-gate worklet.
 */
export class VoiceGate {
  /** In dBFS, changed as the member moves the slider. */
  threshold: number;
  private readonly window: number;
  private readonly delay: Float32Array;
  private readonly fadeStep: number;
  private readonly holdWindows = HOLD_MS / WINDOW_MS;
  private delayAt = 0;
  private sum = 0;
  private count = 0;
  /** Loud windows in a row while shut, and quiet ones in a row while open. */
  private loud = 0;
  private quiet = 0;
  private open = false;
  private gain = 0;
  private peak = SILENCE;
  private windows = 0;

  constructor(
    sampleRate: number,
    threshold: number,
    private readonly report: (input: MicInput) => void,
  ) {
    this.threshold = threshold;
    this.window = Math.round((sampleRate * WINDOW_MS) / 1000);
    this.delay = new Float32Array(Math.round((sampleRate * LOOKAHEAD_MS) / 1000));
    this.fadeStep = 1000 / (sampleRate * FADE_MS);
  }

  /** Writes the gated sound to `output`, from `input`, or silence when the microphone gave none. */
  process(input: Float32Array | undefined, output: Float32Array): void {
    for (let i = 0; i < output.length; i++) {
      const sample = input?.[i] ?? 0;
      this.sum += sample * sample;
      if (++this.count === this.window) {
        this.measure();
      }
      const delayed = this.delay[this.delayAt] ?? 0;
      this.delay[this.delayAt] = sample;
      this.delayAt = (this.delayAt + 1) % this.delay.length;
      this.gain = this.open ? Math.min(1, this.gain + this.fadeStep) : Math.max(0, this.gain - this.fadeStep);
      output[i] = delayed * this.gain;
    }
  }

  private measure(): void {
    const level = this.sum > 0 ? Math.max(SILENCE, 10 * Math.log10(this.sum / this.count)) : SILENCE;
    this.sum = 0;
    this.count = 0;
    const loud = level >= this.threshold;
    const wasOpen = this.open;
    if (this.open) {
      this.quiet = loud ? 0 : this.quiet + 1;
      this.open = this.quiet < this.holdWindows;
    } else {
      this.loud = loud ? this.loud + 1 : 0;
      this.open = this.loud >= OPEN_WINDOWS;
    }
    if (this.open !== wasOpen) {
      this.loud = 0;
      this.quiet = 0;
    }
    this.peak = Math.max(this.peak, level);
    if (++this.windows === REPORT_WINDOWS || this.open !== wasOpen) {
      this.report({ level: this.peak, open: this.open });
      this.peak = SILENCE;
      this.windows = 0;
    }
  }
}
