import { describe, expect, it } from "vitest";
import type { Account, Channel } from "../api/types";
import { insertMention, matchingChannels, matchingMembers, queryAt, withTokens } from "./mentions";

function account(id: string, username: string, displayName: string): Account {
  return { id, username, displayName, roleIds: [], createdAt: "2026-01-01T00:00:00Z" };
}

function channel(id: string, name: string, position: number): Channel {
  return { id, type: "text", name, position, requiredRoleIds: [], createdAt: "2026-01-01T00:00:00Z" };
}

const wigeon = account("a1", "wigeon", "Wigeon");
const mallard = account("a2", "mallard_77", "Green Mallard");
const teal = account("a3", "teal", "Teal");
const members = [wigeon, mallard, teal];

const general = channel("c1", "General", 0);
const generalChat = channel("c2", "General chat", 1);
const patchNotes = channel("c3", "patch-notes", 2);
const channels = [general, generalChat, patchNotes];

/** The query at the end of the text. */
function queryAtEnd(text: string) {
  return queryAt(text, text.length);
}

describe("queryAt", () => {
  it("finds a member query at the start of a word", () => {
    expect(queryAtEnd("hi @wig")).toEqual({ sigil: "@", query: "wig", start: 3, end: 7 });
    expect(queryAtEnd("@")).toEqual({ sigil: "@", query: "", start: 0, end: 1 });
    expect(queryAtEnd("(@te")).toEqual({ sigil: "@", query: "te", start: 1, end: 4 });
  });

  it("ignores an @ inside a word, a token or after a backslash", () => {
    expect(queryAtEnd("me@example")).toBeNull();
    expect(queryAtEnd("<@abc")).toBeNull();
    expect(queryAtEnd("\\@wig")).toBeNull();
    expect(queryAtEnd("@wig on")).toBeNull();
  });

  it("finds a channel query with spaces, on its line", () => {
    expect(queryAtEnd("see #general ch")).toEqual({ sigil: "#", query: "general ch", start: 4, end: 15 });
    expect(queryAtEnd("#gen\nmore")).toBeNull();
    expect(queryAtEnd("issue#12")).toBeNull();
  });

  it("reads up to the caret, not the end", () => {
    expect(queryAt("@wigeon rest", 4)).toEqual({ sigil: "@", query: "wig", start: 0, end: 4 });
  });
});

describe("matchingMembers", () => {
  it("puts usernames before display names", () => {
    expect(matchingMembers(members, "ma")).toEqual([mallard]);
    expect(matchingMembers(members, "gre")).toEqual([mallard]);
    expect(matchingMembers(members, "T")).toEqual([teal]);
    expect(matchingMembers([account("a4", "greenie", "Zed"), mallard], "green")).toEqual([
      account("a4", "greenie", "Zed"),
      mallard,
    ]);
  });

  it("offers everyone for an empty query, alphabetically", () => {
    expect(matchingMembers(members, "")).toEqual([mallard, teal, wigeon]);
  });
});

describe("matchingChannels", () => {
  it("puts names that start with the query first", () => {
    expect(matchingChannels(channels, "gen")).toEqual([general, generalChat]);
    expect(matchingChannels(channels, "chat")).toEqual([generalChat]);
    expect(matchingChannels(channels, "x")).toEqual([]);
  });
});

describe("insertMention", () => {
  it("replaces the query and adds a space", () => {
    const text = "hi @wi and more";
    const at = queryAt(text, 6)!;
    expect(insertMention(text, at, "@wigeon")).toEqual({ text: "hi @wigeon and more", caret: 11 });
    expect(insertMention("@wi", queryAtEnd("@wi")!, "@wigeon")).toEqual({ text: "@wigeon ", caret: 8 });
  });

  it("does not double a space that is already there", () => {
    const text = "@wi more";
    expect(insertMention(text, queryAt(text, 3)!, "@wigeon")).toEqual({ text: "@wigeon more", caret: 8 });
  });
});

describe("withTokens", () => {
  it("turns usernames and channel names into tokens, in any case", () => {
    expect(withTokens("hey @Wigeon and @mallard_77, see #general.", members, channels)).toBe(
      "hey <@a1> and <@a2>, see <#c1>.",
    );
  });

  it("takes the longest channel name that fits", () => {
    expect(withTokens("#General chat is busy, #general is not", members, channels)).toBe(
      "<#c2> is busy, <#c1> is not",
    );
    expect(withTokens("#patch-notes!", members, channels)).toBe("<#c3>!");
  });

  it("leaves what names no one, and what is not a mention", () => {
    expect(withTokens("@nobody #nowhere me@wigeon #generals \\@wigeon", members, channels)).toBe(
      "@nobody #nowhere me@wigeon #generals \\@wigeon",
    );
  });

  it("leaves code alone", () => {
    expect(withTokens("`@wigeon` @wigeon\n```\n#general\n``` #general", members, channels)).toBe(
      "`@wigeon` <@a1>\n```\n#general\n``` <#c1>",
    );
  });
});
