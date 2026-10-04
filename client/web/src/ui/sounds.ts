import type { VoiceSound } from "../state/voice";

// Two short tones made on the spot, rising for someone joining and falling
// for someone leaving, so there are no sound files to ship.

const NOTES: Record<VoiceSound, number[]> = {
  join: [587.33, 880],
  leave: [880, 587.33],
};
const NOTE_GAP_S = 0.09;
const NOTE_LENGTH_S = 0.18;
const VOLUME = 0.12;
/** Sounds closer together than this are one, so a crowd arriving after a restart rings once. */
const MIN_INTERVAL_MS = 300;

let context: AudioContext | null = null;
let lastPlayed = 0;

/**
 * Lets sounds play from now on. Browsers allow sound only after the person
 * has clicked something, so call it from a click, such as joining voice.
 */
export function unlockSounds(): void {
  context ??= new AudioContext();
  void context.resume();
}

export function playVoiceSound(sound: VoiceSound): void {
  const now = Date.now();
  if (context === null || context.state !== "running" || now - lastPlayed < MIN_INTERVAL_MS) {
    return;
  }
  lastPlayed = now;
  const audio = context;
  NOTES[sound].forEach((frequency, i) => {
    const start = audio.currentTime + i * NOTE_GAP_S;
    const tone = audio.createOscillator();
    const gain = audio.createGain();
    tone.type = "sine";
    tone.frequency.value = frequency;
    gain.gain.setValueAtTime(0, start);
    gain.gain.linearRampToValueAtTime(VOLUME, start + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + NOTE_LENGTH_S);
    tone.connect(gain).connect(audio.destination);
    tone.start(start);
    tone.stop(start + NOTE_LENGTH_S);
  });
}
