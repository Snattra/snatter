import { describe, expect, it } from "vitest";
import { emptyList, parseServerList, renamed, selecting, withServer, withoutServer } from "./serverList";

const demo = { origin: "https://demo.snatter.app", name: "Snatter Demo Server" };
const home = { origin: "http://192.168.1.10:8080", name: "Pondside" };
const club = { origin: "https://chat.example.com", name: "Duck club" };

describe("parseServerList", () => {
  it("reads what was kept", () => {
    const list = { servers: [demo, home], selected: home.origin };
    expect(parseServerList(JSON.stringify(list))).toEqual(list);
  });

  it("starts empty when nothing usable was kept", () => {
    for (const text of [null, "", "not json", "42", "{}", '{"servers": "no"}']) {
      expect(parseServerList(text), String(text)).toEqual(emptyList);
    }
  });

  it("drops entries that are not servers, and servers listed twice", () => {
    const text = JSON.stringify({
      servers: [demo, { origin: 1 }, null, demo, { name: "No origin" }],
      selected: demo.origin,
    });
    expect(parseServerList(text)).toEqual({ servers: [demo], selected: demo.origin });
  });

  it("opens the first server when the one kept as open is not on the list", () => {
    expect(
      parseServerList(JSON.stringify({ servers: [demo, home], selected: "https://gone.example.com" })).selected,
    ).toBe(demo.origin);
  });
});

describe("withServer", () => {
  it("adds a server at the end and opens it", () => {
    expect(withServer({ servers: [demo], selected: demo.origin }, home)).toEqual({
      servers: [demo, home],
      selected: home.origin,
    });
  });

  it("opens a server added before, under its new name, in its old place", () => {
    const list = { servers: [demo, home], selected: home.origin };
    expect(withServer(list, { ...demo, name: "Demo" })).toEqual({
      servers: [{ ...demo, name: "Demo" }, home],
      selected: demo.origin,
    });
  });
});

describe("withoutServer", () => {
  const list = { servers: [demo, home, club], selected: home.origin };

  it("opens the next server when the open one goes, or else the one before", () => {
    expect(withoutServer(list, home.origin)).toEqual({ servers: [demo, club], selected: club.origin });
    expect(withoutServer({ ...list, selected: club.origin }, club.origin).selected).toBe(home.origin);
    expect(withoutServer({ servers: [demo], selected: demo.origin }, demo.origin)).toEqual(emptyList);
  });

  it("keeps the open server open when another goes", () => {
    expect(withoutServer(list, demo.origin)).toEqual({ servers: [home, club], selected: home.origin });
    expect(withoutServer(list, "https://unknown.example.com")).toBe(list);
  });
});

describe("selecting and renamed", () => {
  const list = { servers: [demo, home], selected: demo.origin };

  it("opens only servers on the list", () => {
    expect(selecting(list, home.origin).selected).toBe(home.origin);
    expect(selecting(list, club.origin)).toBe(list);
  });

  it("renames a server, and changes nothing when the name is the same", () => {
    expect(renamed(list, home.origin, "Pond").servers[1]).toEqual({ ...home, name: "Pond" });
    expect(renamed(list, home.origin, home.name)).toBe(list);
  });
});
