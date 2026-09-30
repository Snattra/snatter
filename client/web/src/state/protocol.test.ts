import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { OLDEST_SERVER_PROTOCOL, PROTOCOL_VERSION, compatibility, isOutdated } from "./protocol";

const [major, minor] = PROTOCOL_VERSION.split(".").map(Number) as [number, number];

function server(version: string, minClient = `${version.split(".")[0]}.0`) {
  return { version, minClient };
}

describe("protocol", () => {
  it("is the version of the contract the client is built from", () => {
    const contract = readFileSync(new URL("../../../../protocol/openapi/openapi.yaml", import.meta.url), "utf8");
    const version = /^info:\n(?: .*\n)*? {2}version: "([^"]+)"$/m.exec(contract)?.[1];
    expect(version).toBe(PROTOCOL_VERSION);
  });

  it("supports servers of the previous major version and on", () => {
    const [oldestMajor, oldestMinor] = OLDEST_SERVER_PROTOCOL.split(".").map(Number);
    expect(oldestMinor).toBe(0);
    expect(oldestMajor).toBe(Math.max(1, major - 1));
  });
});

describe("compatibility", () => {
  it("is current when both speak the same version", () => {
    expect(compatibility(server(PROTOCOL_VERSION))).toBe("current");
  });

  it("offers an update when the server is newer but still lets this client in", () => {
    expect(compatibility(server(`${major}.${minor + 1}`))).toBe("update_available");
  });

  it("works with an older server it still supports", () => {
    const older = server(OLDEST_SERVER_PROTOCOL);
    expect(compatibility(older)).toBe(PROTOCOL_VERSION === OLDEST_SERVER_PROTOCOL ? "current" : "server_older");
  });

  it("is outdated below the server's minimum", () => {
    expect(compatibility(server(`${major + 1}.0`))).toBe("client_outdated");
    expect(compatibility(server(`${major}.${minor + 2}`, `${major}.${minor + 1}`))).toBe("client_outdated");
  });

  it("finds a server outdated that is older than supported or states no version", () => {
    expect(compatibility(server("0.9"))).toBe("server_outdated");
    expect(compatibility(undefined)).toBe("server_outdated");
    expect(compatibility({ version: "one", minClient: "1.0" })).toBe("server_outdated");
  });

  it("tells the outdated apart from the rest", () => {
    expect(isOutdated("client_outdated")).toBe(true);
    expect(isOutdated("server_outdated")).toBe(true);
    expect(isOutdated("update_available")).toBe(false);
  });
});
