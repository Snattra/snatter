/**
 * The Markdown of message content, a chat dialect: what the author typed
 * stays as typed (line breaks and blank lines included), with bold, italic,
 * underline, strikethrough, spoilers, code, quotes, lists and bare links on
 * top. There is no HTML, no image, no heading and no link with its own text:
 * a link always shows where it goes. Parsing never fails; anything that is
 * not formatting is text.
 *
 * This parses into a tree of plain data, which `MessageText` renders as
 * React elements, so no text ever becomes HTML.
 */

export type Block =
  /** Lines of text; line breaks stay in the text. */
  | { type: "paragraph"; content: Inline[] }
  | { type: "code-block"; language: string | null; text: string }
  | { type: "quote"; content: Block[] }
  | { type: "list"; ordered: boolean; start: number; items: ListItem[] };

export interface ListItem {
  content: Inline[];
  /** Lists indented under this item. */
  lists: Block[];
}

export type Inline =
  | { type: "text"; text: string }
  | { type: "code"; text: string }
  | { type: "link"; url: string }
  | { type: Emphasis; content: Inline[] };

export type Emphasis = "strong" | "emphasis" | "underline" | "strike" | "spoiler";

/** How deep quotes, lists and emphasis may nest; deeper markers are text. */
const MAX_DEPTH = 8;

export function parseMarkdown(source: string): Block[] {
  return parseBlocks(source.split("\n"), 0);
}

// ---------- Blocks ----------

const FENCE = "```";
const LANGUAGE = /^([A-Za-z0-9_+#.-]+)\n/;
const QUOTE = /^> ?/;
const LIST_ITEM = /^( *)(?:[-*]|(\d{1,9})[.)]) +(\S.*)$/;

function parseBlocks(source: string[], depth: number): Block[] {
  const lines = [...source];
  const blocks: Block[] = [];
  let paragraph: string[] = [];

  // Blank lines around a block are the space between blocks, not part of it.
  const endParagraph = () => {
    const first = paragraph.findIndex((line) => line.trim() !== "");
    if (first !== -1) {
      const last = paragraph.findLastIndex((line) => line.trim() !== "");
      blocks.push({ type: "paragraph", content: parseInline(paragraph.slice(first, last + 1).join("\n")) });
    }
    paragraph = [];
  };

  let i = 0;
  while (i < lines.length) {
    const line = lines[i] ?? "";

    if (line.startsWith(FENCE)) {
      const fenced = codeBlock(lines, i);
      if (fenced !== null) {
        endParagraph();
        blocks.push(fenced.block);
        if (fenced.rest.trim() === "") {
          i = fenced.end + 1;
        } else {
          // Text after the closing fence goes on as a line of its own.
          lines[fenced.end] = fenced.rest;
          i = fenced.end;
        }
        continue;
      }
    }

    if (depth < MAX_DEPTH && isQuoteLine(line)) {
      endParagraph();
      const quoted: string[] = [];
      for (let next = lines[i]; next !== undefined && isQuoteLine(next); next = lines[++i]) {
        quoted.push(next.replace(QUOTE, ""));
      }
      blocks.push({ type: "quote", content: parseBlocks(quoted, depth + 1) });
      continue;
    }

    const item = listItem(line);
    if (item !== null) {
      endParagraph();
      const list = parseList(lines, i, item, depth);
      blocks.push(list.block);
      i = list.end;
      continue;
    }

    paragraph.push(line);
    i++;
  }
  endParagraph();
  return blocks;
}

/** `> ` or a lone `>`, so `>.<` stays text. */
function isQuoteLine(line: string): boolean {
  return line === ">" || line.startsWith("> ");
}

/**
 * A code block from a line starting with the fence to the next fence, which
 * may be on the same line. A word right after the opening fence, alone on its
 * line, is the language. Without a closing fence it is not a code block.
 */
function codeBlock(lines: string[], start: number): { block: Block; end: number; rest: string } | null {
  let body = "";
  for (let end = start; end < lines.length; end++) {
    const line = end === start ? (lines[end] ?? "").slice(FENCE.length) : (lines[end] ?? "");
    const close = line.indexOf(FENCE);
    if (close === -1) {
      body += line + "\n";
      continue;
    }
    body += line.slice(0, close);
    let text = body;
    let language: string | null = null;
    const word = LANGUAGE.exec(text);
    if (word !== null) {
      language = word[1] ?? null;
      text = text.slice(word[0].length);
    }
    text = text.replace(/^\n/, "").replace(/\n$/, "");
    if (text.trim() === "") {
      return null;
    }
    return { block: { type: "code-block", language, text }, end, rest: line.slice(close + FENCE.length) };
  }
  return null;
}

interface Item {
  indent: number;
  /** The number of an ordered item; null for a bullet. */
  number: number | null;
  text: string;
}

function listItem(line: string): Item | null {
  const match = LIST_ITEM.exec(line);
  if (match === null) {
    return null;
  }
  const [, indent = "", number, text = ""] = match;
  return { indent: indent.length, number: number === undefined ? null : Number(number), text };
}

/**
 * Consecutive items with the same indentation and kind of marker. More
 * deeply indented items nest under the item before them; anything else ends
 * the list.
 */
function parseList(lines: string[], start: number, first: Item, depth: number): { block: Block; end: number } {
  const ordered = first.number !== null;
  const items: ListItem[] = [];
  let i = start;
  while (i < lines.length) {
    const item = listItem(lines[i] ?? "");
    if (item === null || item.indent < first.indent) {
      break;
    }
    const parent = items.at(-1);
    if (item.indent > first.indent && parent !== undefined && depth + 1 < MAX_DEPTH) {
      const nested = parseList(lines, i, item, depth + 1);
      parent.lists.push(nested.block);
      i = nested.end;
      continue;
    }
    if ((item.number !== null) !== ordered) {
      break;
    }
    items.push({ content: parseInline(item.text), lists: [] });
    i++;
  }
  return { block: { type: "list", ordered, start: first.number ?? 1, items }, end: i };
}

// ---------- Inline ----------

/*
 * Inline text is read in two passes, the way CommonMark reads emphasis, so it
 * takes time in proportion to the text however it is written. The first
 * splits it into text, code, links and runs of marker characters, and notes
 * whether each run could open or close emphasis. The second pairs each
 * closing run with the nearest open run of the same character; open runs
 * between the two are left as text, so what pairs up always nests.
 */

type Marker = "*" | "_" | "~" | "|";

/** A run of one marker character, such as `***`. */
interface Run {
  type: "run";
  char: Marker;
  length: number;
  canOpen: boolean;
  canClose: boolean;
  /** What it closes, innermost first. */
  closes: Pair[];
  /** What it opens, outermost first. */
  opens: Pair[];
}

/** The marker characters one side of a pair uses: `**` is strong, `*` emphasis. */
interface Pair {
  type: Emphasis;
  marker: string;
}

type Piece = Inline | Run;

const ESCAPABLE = /[!-/:-@[-`{-~]/;
const URL_START = /^https?:\/\//i;
const WORD = /[\p{L}\p{N}]/u;
const SPACE = /\s/;

export function parseInline(text: string): Inline[] {
  const pieces = split(text);
  pair(pieces);
  return build(pieces);
}

function isMarker(c: string): c is Marker {
  return c === "*" || c === "_" || c === "~" || c === "|";
}

function split(text: string): Piece[] {
  const pieces: Piece[] = [];
  let plain = "";
  const push = (piece: Piece) => {
    if (plain !== "") {
      pieces.push({ type: "text", text: plain });
      plain = "";
    }
    pieces.push(piece);
  };

  let i = 0;
  while (i < text.length) {
    const c = text.charAt(i);
    if (c === "\\" && ESCAPABLE.test(text.charAt(i + 1))) {
      plain += text.charAt(i + 1);
      i += 2;
      continue;
    }

    if (c === "`") {
      const length = runLength(text, i);
      const code = codeSpan(text, i, length);
      if (code !== null) {
        push({ type: "code", text: code.text });
        i = code.end;
      } else {
        // The whole run is text, so a shorter run inside it cannot open.
        plain += text.slice(i, i + length);
        i += length;
      }
      continue;
    }

    if (c === "<" || c === "h" || c === "H") {
      const link = linkAt(text, i);
      if (link !== null) {
        push({ type: "link", url: link.url });
        i = link.end;
        continue;
      }
    }

    if (isMarker(c)) {
      const length = runLength(text, i);
      push(markerRun(text, i, c, length));
      i += length;
      continue;
    }

    plain += c;
    i++;
  }
  if (plain !== "") {
    pieces.push({ type: "text", text: plain });
  }
  return pieces;
}

function runLength(text: string, i: number): number {
  let n = 1;
  while (text.charAt(i + n) === text.charAt(i)) {
    n++;
  }
  return n;
}

/**
 * `*` and `_` must hug their text, so `2 * 3 * 4` stays text, and `_` does
 * not open or close inside a word, so snake_case stays text. `~~` and `||`
 * only need something on the side they face.
 */
function markerRun(text: string, i: number, char: Marker, length: number): Run {
  const before = text.charAt(i - 1);
  const after = text.charAt(i + length);
  const hugs = char === "*" || char === "_";
  let canOpen = after !== "" && !(hugs && SPACE.test(after));
  let canClose = before !== "" && !(hugs && SPACE.test(before));
  if (char === "_") {
    canOpen &&= !WORD.test(before);
    canClose &&= !WORD.test(after);
  }
  return { type: "run", char, length, canOpen, canClose, closes: [], opens: [] };
}

/** A run of backticks up to the next run of the same length. One space inside each end is padding. */
function codeSpan(text: string, i: number, open: number): { text: string; end: number } | null {
  let j = i + open;
  while (j < text.length) {
    const close = text.indexOf("`", j);
    if (close === -1) {
      return null;
    }
    const run = runLength(text, close);
    if (run === open) {
      let code = text.slice(i + open, close);
      if (code.trim() === "") {
        return null;
      }
      if (code.length > 2 && code.startsWith(" ") && code.endsWith(" ")) {
        code = code.slice(1, -1);
      }
      return { text: code, end: close + run };
    }
    j = close + run;
  }
  return null;
}

/**
 * An http or https address, bare or in angle brackets. Punctuation at the
 * end of a bare one belongs to the sentence, except a closing bracket that
 * pairs with one in the address.
 */
function linkAt(text: string, i: number): { url: string; end: number } | null {
  if (text.charAt(i) === "<") {
    const close = text.indexOf(">", i);
    const inside = close === -1 ? "" : text.slice(i + 1, close);
    if (URL_START.test(inside) && !SPACE.test(inside) && isUrl(inside)) {
      return { url: inside, end: close + 1 };
    }
    return null;
  }
  if (!URL_START.test(text.slice(i, i + 8)) || WORD.test(text.charAt(i - 1))) {
    return null;
  }
  let end = i;
  let opened = 0;
  let closed = 0;
  for (let c = text.charAt(end); c !== "" && !SPACE.test(c) && c !== "<"; c = text.charAt(++end)) {
    if (c === "(") {
      opened++;
    } else if (c === ")") {
      closed++;
    }
  }
  while (end > i) {
    const last = text.charAt(end - 1);
    if (".,:;!?'\"*_~|".includes(last)) {
      end--;
    } else if (last === ")" && closed > opened) {
      closed--;
      end--;
    } else {
      break;
    }
  }
  const url = text.slice(i, end);
  return isUrl(url) ? { url, end } : null;
}

/** A web address with a host, so `https://` alone stays text. */
function isUrl(text: string): boolean {
  try {
    const url = new URL(text);
    return (url.protocol === "http:" || url.protocol === "https:") && url.host !== "";
  } catch {
    return false;
  }
}

/** What a pair of runs means, by the character and how many of it each side uses. */
function pairOf(char: Marker, size: 1 | 2): Pair | null {
  switch (char) {
    case "*":
      return size === 2 ? { type: "strong", marker: "**" } : { type: "emphasis", marker: "*" };
    case "_":
      return size === 2 ? { type: "underline", marker: "__" } : { type: "emphasis", marker: "_" };
    case "~":
      return size === 2 ? { type: "strike", marker: "~~" } : null;
    case "|":
      return size === 2 ? { type: "spoiler", marker: "||" } : null;
  }
}

/**
 * Pairs closing runs with open ones. Both use characters from the side
 * facing the text between them, two at a time where both runs have two
 * (`**` over `*`), so `***both***` is strong and emphasis at once. `~` and
 * `|` only ever pair two at a time.
 */
function pair(pieces: Piece[]) {
  const open: { run: Run; left: number }[] = [];
  // Per character, where its open runs are in `open`, so the nearest is found at once.
  const byChar: Record<Marker, number[]> = { "*": [], _: [], "~": [], "|": [] };

  for (const run of pieces) {
    if (run.type !== "run") {
      continue;
    }
    let left = run.length;
    const mine = byChar[run.char];
    while (run.canClose && left > 0) {
      const at = mine.at(-1);
      const opener = at === undefined ? undefined : open[at];
      if (opener === undefined) {
        break;
      }
      const size = opener.left >= 2 && left >= 2 ? 2 : 1;
      const pairing = pairOf(run.char, size);
      if (pairing === null) {
        break;
      }
      // What opened in between never closed.
      for (let above = open.at(-1); above !== undefined && above !== opener; above = open.at(-1)) {
        open.pop();
        byChar[above.run.char].pop();
      }
      opener.run.opens.unshift(pairing);
      opener.left -= size;
      run.closes.push(pairing);
      left -= size;
      if (opener.left === 0) {
        open.pop();
        mine.pop();
      }
    }
    if (run.canOpen && left > 0) {
      open.push({ run, left });
      mine.push(open.length - 1);
    }
  }
}

/**
 * The tree from the paired pieces: a run is what it closes, then its unused
 * characters as text, then what it opens. Emphasis nested deeper than
 * MAX_DEPTH stays text.
 */
function build(pieces: Piece[]): Inline[] {
  const root: Inline[] = [];
  const stack: { pair: Pair; content: Inline[] }[] = [];
  const into = () => stack.at(-1)?.content ?? root;

  for (const piece of pieces) {
    if (piece.type !== "run") {
      append(into(), piece);
      continue;
    }
    for (const closing of piece.closes) {
      const node = stack.pop();
      if (node === undefined) {
        break;
      }
      if (stack.length < MAX_DEPTH) {
        append(into(), { type: node.pair.type, content: node.content });
      } else {
        append(into(), { type: "text", text: node.pair.marker });
        node.content.forEach((inline) => append(into(), inline));
        append(into(), { type: "text", text: closing.marker });
      }
    }
    const used = [...piece.closes, ...piece.opens].reduce((n, p) => n + p.marker.length, 0);
    if (used < piece.length) {
      append(into(), { type: "text", text: piece.char.repeat(piece.length - used) });
    }
    for (const opening of piece.opens) {
      stack.push({ pair: opening, content: [] });
    }
  }
  return root;
}

/** Adds to a list of inlines, joining text to text. */
function append(list: Inline[], inline: Inline) {
  const last = list.at(-1);
  if (inline.type === "text" && last?.type === "text") {
    list[list.length - 1] = { type: "text", text: last.text + inline.text };
  } else {
    list.push(inline);
  }
}
