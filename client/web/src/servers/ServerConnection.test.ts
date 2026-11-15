import { afterEach, describe, expect, it, vi } from "vitest";
import { useServers } from "../state/store";
import { connectionTo, forgetServer } from "./ServerConnection";

/** localStorage as a map, or one that refuses every access, as some private windows do. */
function storage(blocked = false) {
  const kept = new Map<string, string>();
  const refuse = () => {
    throw new DOMException("The operation is insecure.", "SecurityError");
  };
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => (blocked ? refuse() : (kept.get(key) ?? null)),
    setItem: (key: string, value: string) => (blocked ? refuse() : void kept.set(key, value)),
    removeItem: (key: string) => (blocked ? refuse() : void kept.delete(key)),
  });
  return kept;
}

/** The server as fetch sees it: requests noted as "METHOD /path token", and signing in held until let go. */
function server() {
  const requests: string[] = [];
  let answerLogin: (response: Response) => void = () => {};
  vi.stubGlobal("fetch", async (request: Request) => {
    const path = new URL(request.url).pathname;
    requests.push(`${request.method} ${path} ${request.headers.get("Authorization") ?? "-"}`);
    if (path === "/api/v1/auth/login") {
      return new Promise<Response>((resolve) => (answerLogin = resolve));
    }
    return new Response(null, { status: 204 });
  });
  return {
    requests,
    letLoginThrough(token: string) {
      answerLogin(Response.json({ token, accountId: "01a15010-cb90-7290-979d-ce3eedb03f75" }));
    },
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("forgetServer", () => {
  it("keeps a sign-in that finishes after the server was removed from bringing it back", async () => {
    const kept = storage();
    const fake = server();
    const origin = "https://late.example.com";

    const signingIn = connectionTo(origin).logIn("robin", "a long enough password");
    await forgetServer(origin);
    fake.letLoginThrough("snt_late");
    await signingIn;

    expect(useServers.getState().servers[origin]).toBeUndefined();
    expect([...kept.keys()]).toEqual([]);
    // The session it was given is ended, not kept.
    expect(fake.requests).toContain("POST /api/v1/auth/logout Bearer snt_late");
  });

  it("ends the session kept for the server, on the server too", async () => {
    const kept = storage();
    const fake = server();
    const origin = "https://kept.example.com";
    kept.set(`snatter:session:${origin}`, "snt_kept");

    await forgetServer(origin);

    expect([...kept.keys()]).toEqual([]);
    expect(fake.requests).toEqual(["POST /api/v1/auth/logout Bearer snt_kept"]);
    expect(useServers.getState().servers[origin]).toBeUndefined();
  });

  it("removes the server when storage is off", async () => {
    storage(true);
    const fake = server();
    const origin = "https://private.example.com";
    await connectionTo(origin).resume();

    await forgetServer(origin);

    expect(useServers.getState().servers[origin]).toBeUndefined();
    expect(fake.requests).toEqual([]);
  });
});

describe("joinVoice", () => {
  it("leaves voice on every other server, as the app is in voice on one at a time", async () => {
    storage();
    server();
    // No microphone here, so the new call ends with a notice; what matters is the other server.
    vi.stubGlobal("window", { isSecureContext: true });
    const first = "https://first.example.com";
    const second = "https://second.example.com";
    connectionTo(first);
    useServers.getState().update(first, (entry) => ({ ...entry, voice: { ...entry.voice, channelId: "lounge" } }));

    connectionTo(second).joinVoice("hangout");

    expect(useServers.getState().servers[first]?.voice.channelId).toBeNull();
    expect(useServers.getState().servers[second]?.voice.channelId).toBe("hangout");
  });
});
