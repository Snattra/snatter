/// <reference lib="webworker" />
// Finds the number whose SHA-256 of salt + number is the challenge; off the
// main thread, since it can take a few seconds. Pages over plain HTTP have no
// crypto.subtle, so there the hash is worked out by sha256Hex.

import { sha256Hex } from "./sha256";

interface Task {
  challenge: string;
  salt: string;
  maxnumber: number;
}

self.onmessage = async (event: MessageEvent<Task>) => {
  const { challenge, salt, maxnumber } = event.data;
  const encoder = new TextEncoder();
  const subtle = self.crypto?.subtle;
  for (let number = 0; number <= maxnumber; number++) {
    const input = encoder.encode(salt + number);
    const hash = subtle ? toHex(await subtle.digest("SHA-256", input)) : sha256Hex(input);
    if (hash === challenge) {
      self.postMessage(number);
      return;
    }
  }
  self.postMessage(null);
};

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer), (b) => b.toString(16).padStart(2, "0")).join("");
}
