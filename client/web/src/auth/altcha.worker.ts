/// <reference lib="webworker" />
// Finds the number whose SHA-256 of salt + number is the challenge; off the
// main thread, since it can take a few seconds.

interface Task {
  challenge: string;
  salt: string;
  maxnumber: number;
}

self.onmessage = async (event: MessageEvent<Task>) => {
  const { challenge, salt, maxnumber } = event.data;
  const encoder = new TextEncoder();
  for (let number = 0; number <= maxnumber; number++) {
    const digest = await crypto.subtle.digest("SHA-256", encoder.encode(salt + number));
    if (toHex(digest) === challenge) {
      self.postMessage(number);
      return;
    }
  }
  self.postMessage(null);
};

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer), (b) => b.toString(16).padStart(2, "0")).join("");
}
