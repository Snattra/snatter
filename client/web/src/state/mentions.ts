import type { Account, Channel } from "../api/types";

/*
 * Mentions as the member writes them. The composer shows `@username` and
 * `#Channel name`, which read well and can be typed by hand; sending turns
 * them into the `<@accountId>` and `<#channelId>` tokens the server and
 * other clients understand. Usernames and channel names are unique
 * regardless of case, so each one names exactly one member or channel.
 */

/** What the member is typing at the caret after `@` or `#`, and where it is in the text. */
export interface MentionQuery {
  sigil: "@" | "#";
  /** What follows the sigil, up to the caret. */
  query: string;
  /** Where the sigil is. */
  start: number;
  /** The caret. */
  end: number;
}

/** The most suggestions shown at once. */
export const MAX_SUGGESTIONS = 8;
/** How far back from the caret a channel name may start; the contract's longest name. */
const MAX_CHANNEL_NAME = 100;

const USERNAME_CHAR = /[A-Za-z0-9_]/;
/** A sigil only counts at the start of a word, so e-mail addresses and `a#b` stay text. */
const WORD_CHAR = /[\p{L}\p{N}_]/u;

/**
 * The mention being typed at the caret, if any. A member's query is
 * username characters; a channel's may hold spaces, as names do, but stays
 * on one line.
 */
export function queryAt(text: string, caret: number): MentionQuery | null {
  let i = caret;
  while (i > 0 && USERNAME_CHAR.test(text.charAt(i - 1))) {
    i--;
  }
  if (text.charAt(i - 1) === "@" && startsWord(text, i - 1)) {
    return { sigil: "@", query: text.slice(i, caret), start: i - 1, end: caret };
  }
  for (let j = caret; j > 0 && caret - j <= MAX_CHANNEL_NAME; j--) {
    const c = text.charAt(j - 1);
    if (c === "\n") {
      break;
    }
    if (c === "#" && startsWord(text, j - 1)) {
      return { sigil: "#", query: text.slice(j, caret), start: j - 1, end: caret };
    }
  }
  return null;
}

function startsWord(text: string, i: number): boolean {
  const before = text.charAt(i - 1);
  return before === "" || (!WORD_CHAR.test(before) && before !== "<" && before !== "\\");
}

/**
 * Members whose username or any word of whose display name starts with the
 * query, best first: usernames before display names, then alphabetically.
 */
export function matchingMembers(members: Account[], query: string): Account[] {
  const q = query.toLowerCase();
  const rank = (member: Account): number => {
    const username = member.username.toLowerCase();
    if (username === q) {
      return 0;
    }
    if (username.startsWith(q)) {
      return 1;
    }
    const name = member.displayName.toLowerCase();
    if (name.startsWith(q)) {
      return 2;
    }
    return name.split(/\s+/).some((word) => word.startsWith(q)) ? 3 : -1;
  };
  return members
    .map((member) => ({ member, rank: rank(member) }))
    .filter(({ rank }) => rank >= 0)
    .sort((a, b) => a.rank - b.rank || a.member.displayName.localeCompare(b.member.displayName))
    .slice(0, MAX_SUGGESTIONS)
    .map(({ member }) => member);
}

/** Channels whose name starts with the query, then those with a word that does, in list order. */
export function matchingChannels(channels: Channel[], query: string): Channel[] {
  const q = query.toLowerCase();
  const starts = channels.filter((c) => c.name.toLowerCase().startsWith(q));
  const words = channels.filter(
    (c) => !starts.includes(c) && c.name.toLowerCase().split(/\s+/).some((word) => word.startsWith(q)),
  );
  return [...starts, ...words].slice(0, MAX_SUGGESTIONS);
}

/** The text with the query replaced by the chosen mention and a space, and where the caret goes. */
export function insertMention(text: string, at: MentionQuery, mention: string): { text: string; caret: number } {
  const after = text.slice(at.end);
  const spaced = after.startsWith(" ") ? mention : mention + " ";
  // After the space, whether it was added or already there.
  return { text: text.slice(0, at.start) + spaced + after, caret: at.start + mention.length + 1 };
}

/** How a member is written in the composer. */
export function memberMention(member: Account): string {
  return "@" + member.username;
}

/** How a channel is written in the composer. */
export function channelMention(channel: Channel): string {
  return "#" + channel.name;
}

// Code keeps what is typed in it: fenced blocks first, then spans of any number of backticks.
const CODE = /```[\s\S]*?```|(`+)[\s\S]*?\1/g;

/**
 * The text as sent: each `@username` of a member and `#name` of a channel
 * becomes its token, outside code. Where channel names overlap, as with
 * "General" and "General chat", the longest one that fits wins. Anything
 * that names no one stays as typed.
 */
export function withTokens(text: string, members: Account[], channels: Channel[]): string {
  const byUsername = new Map(members.map((m) => [m.username.toLowerCase(), m.id]));
  const longestFirst = [...channels].sort((a, b) => b.name.length - a.name.length);
  let out = "";
  let last = 0;
  for (const code of text.matchAll(CODE)) {
    out += tokensIn(text.slice(last, code.index), byUsername, longestFirst) + code[0];
    last = code.index + code[0].length;
  }
  return out + tokensIn(text.slice(last), byUsername, longestFirst);
}

function tokensIn(text: string, byUsername: Map<string, string>, channels: Channel[]): string {
  let out = "";
  let i = 0;
  while (i < text.length) {
    const c = text.charAt(i);
    if ((c === "@" || c === "#") && startsWord(text, i)) {
      const token = c === "@" ? memberToken(text, i, byUsername) : channelToken(text, i, channels);
      if (token !== null) {
        out += token.text;
        i = token.end;
        continue;
      }
    }
    out += c;
    i++;
  }
  return out;
}

function memberToken(text: string, at: number, byUsername: Map<string, string>): { text: string; end: number } | null {
  let end = at + 1;
  while (USERNAME_CHAR.test(text.charAt(end))) {
    end++;
  }
  const id = byUsername.get(text.slice(at + 1, end).toLowerCase());
  return id === undefined ? null : { text: `<@${id}>`, end };
}

function channelToken(text: string, at: number, channels: Channel[]): { text: string; end: number } | null {
  const rest = text.slice(at + 1, at + 1 + MAX_CHANNEL_NAME).toLowerCase();
  for (const channel of channels) {
    const name = channel.name.toLowerCase();
    const end = at + 1 + name.length;
    if (rest.startsWith(name) && !WORD_CHAR.test(text.charAt(end))) {
      return { text: `<#${channel.id}>`, end };
    }
  }
  return null;
}
