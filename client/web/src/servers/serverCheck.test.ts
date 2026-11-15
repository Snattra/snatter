import { describe, expect, it } from "vitest";
import type { ServerInfo } from "../api/types";
import { PROTOCOL_VERSION } from "../state/protocol";
import { type CheckEnvironment, checkServer } from "./serverCheck";

const info: ServerInfo = {
  name: "Snatter",
  version: "0.1.0",
  protocol: { version: PROTOCOL_VERSION, minClient: "1.0" },
  community: { name: "Snatter Demo Server", description: null },
  registration: { mode: "invite_only", challengeRequired: true, setupRequired: false },
  voice: { defaultBitrate: 64_000 },
  ownerId: null,
};

function answering(answer: () => Promise<Response>, pageProtocol = "https:"): CheckEnvironment & { asked: string[] } {
  const asked: string[] = [];
  return {
    asked,
    pageProtocol,
    fetch: (input) => {
      asked.push(String(input));
      return answer();
    },
  };
}

const json =
  (body: unknown, status = 200) =>
  async () =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("checkServer", () => {
  it("finds a Snatter server by its server info", async () => {
    const environment = answering(json(info));
    expect(await checkServer("https://demo.snatter.app", environment)).toEqual({
      kind: "ok",
      origin: "https://demo.snatter.app",
      info,
      compatibility: "current",
    });
    expect(environment.asked).toEqual(["https://demo.snatter.app/api/v1/server-info"]);
  });

  it("says when the server and the app are too far apart in version", async () => {
    const old = { ...info, protocol: { version: "0.9", minClient: "0.9" } };
    expect(await checkServer("https://old.example.com", answering(json(old)))).toMatchObject({
      kind: "outdated",
      outdated: "server_outdated",
    });
    const ahead = { ...info, protocol: { version: "9.0", minClient: "9.0" } };
    expect(await checkServer("https://new.example.com", answering(json(ahead)))).toMatchObject({
      kind: "outdated",
      outdated: "client_outdated",
    });
  });

  it("takes no answer as unreachable, as the browser reports a server that does not let the app in", async () => {
    const refused = answering(() => Promise.reject(new TypeError("Failed to fetch")));
    expect(await checkServer("https://closed.example.com", refused)).toEqual({
      kind: "unreachable",
      origin: "https://closed.example.com",
    });
  });

  it("takes any other answer as not Snatter", async () => {
    const answers = [
      json(info, 404),
      json({ ...info, name: "Something else" }),
      json({ name: "Snatter" }),
      async () => new Response("<html></html>", { status: 200 }),
    ];
    for (const answer of answers) {
      expect((await checkServer("https://www.example.com", answering(answer))).kind).toBe("not_snatter");
    }
  });

  it("does not ask a plain HTTP server from an HTTPS page, which the browser would block", async () => {
    const environment = answering(json(info));
    expect(await checkServer("http://192.168.1.10:8080", environment)).toEqual({
      kind: "insecure",
      origin: "http://192.168.1.10:8080",
    });
    expect(environment.asked).toEqual([]);
    expect((await checkServer("http://localhost:8080", environment)).kind).toBe("ok");
  });
});
