import type { Challenge } from "../api/types";
import { trustedScriptUrl } from "../trustedTypes";
import workerUrl from "./altcha.worker.ts?worker&url";

/**
 * Solves a registration challenge and returns what the server expects in
 * `RegisterRequest.altcha`: the solution as base64-encoded JSON.
 */
export async function solveChallenge(challenge: Challenge): Promise<string> {
  const worker = new Worker(trustedScriptUrl(workerUrl), { type: "module" });
  try {
    const number = await new Promise<number | null>((resolve, reject) => {
      worker.onmessage = (event: MessageEvent<number | null>) => resolve(event.data);
      worker.onerror = (event) => reject(new Error(event.message));
      worker.postMessage({ challenge: challenge.challenge, salt: challenge.salt, maxnumber: challenge.maxnumber });
    });
    if (number === null) {
      throw new Error("The server's challenge has no solution");
    }
    const { algorithm, salt, signature } = challenge;
    return btoa(JSON.stringify({ algorithm, challenge: challenge.challenge, number, salt, signature }));
  } finally {
    worker.terminate();
  }
}
