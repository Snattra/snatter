import { describe, expect, it } from "vitest";
import { type MicInput, VoiceGate } from "./gate";

const RATE = 48000;
const ms = (n: number) => (RATE * n) / 1000;

/** A 440 Hz tone; amplitude 0.1 is about -23 dBFS, 0.001 about -63. */
function tone(durationMs: number, amplitude: number): Float32Array {
  return Float32Array.from({ length: ms(durationMs) }, (_, i) => amplitude * Math.sin((2 * Math.PI * 440 * i) / RATE));
}

const silence = (durationMs: number) => new Float32Array(ms(durationMs));

function join(...parts: Float32Array[]): Float32Array {
  const all = new Float32Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const part of parts) {
    all.set(part, at);
    at += part.length;
  }
  return all;
}

/** Runs the gate over the input in the worklet's 128-sample blocks. */
function run(input: Float32Array, threshold = -50, gate?: VoiceGate) {
  const reports: (MicInput & { at: number })[] = [];
  let at = 0;
  gate ??= new VoiceGate(RATE, threshold, (report) => reports.push({ ...report, at }));
  const output = new Float32Array(input.length);
  for (at = 0; at < input.length; at += 128) {
    gate.process(input.subarray(at, at + 128), output.subarray(at, at + 128));
  }
  return { output, reports, gate };
}

const DELAY = ms(40);

describe("VoiceGate", () => {
  it("keeps quiet sound out", () => {
    const { output, reports } = run(tone(500, 0.001));
    expect(output.every((s) => s === 0)).toBe(true);
    expect(reports.some((r) => r.open)).toBe(false);
  });

  it("lets speech through from its very start, a little later", () => {
    const start = ms(100);
    const input = join(silence(100), tone(300, 0.1));
    const { output } = run(input);
    expect(output.subarray(0, start + DELAY).every((s) => s === 0)).toBe(true);
    for (let i = start; i < input.length - DELAY; i++) {
      expect(output[i + DELAY]).toBeCloseTo(input[i] ?? NaN, 6);
    }
  });

  it("keeps a click out, also one across two windows", () => {
    // 3 ms of loud sound from 8 ms into a 10 ms window to 1 ms into the next.
    const { output, reports } = run(join(silence(98), tone(3, 0.5), silence(300)));
    expect(output.every((s) => s === 0)).toBe(true);
    expect(reports.some((r) => r.open)).toBe(false);
  });

  it("keeps the pauses between words, then shuts after them", () => {
    const pause = tone(300, 0.001);
    const input = join(tone(200, 0.1), pause, tone(200, 0.1), tone(1000, 0.001));
    const { output, reports } = run(input);
    const inPause = output.subarray(ms(200) + DELAY, ms(500) + DELAY);
    expect(inPause).toEqual(pause);
    const shut = reports.filter((r) => !r.open && reports[reports.indexOf(r) - 1]?.open);
    expect(shut).toHaveLength(1);
    // Shut a hold of 400 ms after the last loud window, give or take a window and a block.
    expect((shut[0]?.at ?? 0) / RATE).toBeCloseTo(0.7 + 0.4, 1);
    expect(output.subarray(output.length - ms(300)).every((s) => s === 0)).toBe(true);
  });

  it("reports the loudest level about 20 times a second", () => {
    const { reports } = run(tone(1000, 0.1));
    const steady = reports.slice(1, -1);
    expect(reports.length).toBeGreaterThanOrEqual(20);
    expect(reports.length).toBeLessThanOrEqual(21);
    for (const report of steady) {
      expect(report.level).toBeCloseTo(20 * Math.log10(0.1 / Math.SQRT2), 0);
    }
  });

  it("takes a new threshold at once", () => {
    const quiet = tone(200, 0.001);
    const { gate, reports } = run(quiet);
    expect(reports.some((r) => r.open)).toBe(false);
    gate.threshold = -70;
    const { output } = run(quiet, undefined, gate);
    expect(output.some((s) => s !== 0)).toBe(true);
  });
});
