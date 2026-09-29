import { describe, expect, it } from "vitest";
import { type Block, type Inline, parseInline, parseMarkdown } from "./markdown";

function text(text: string): Inline {
  return { type: "text", text };
}

function paragraph(...content: Inline[]): Block {
  return { type: "paragraph", content };
}

describe("parseInline", () => {
  it("leaves plain text alone, line breaks and spacing included", () => {
    expect(parseInline("hello  there\n\nfriend")).toEqual([text("hello  there\n\nfriend")]);
  });

  it("reads bold, italic, underline, strikethrough and spoilers", () => {
    expect(parseInline("**b** *i* _i_ __u__ ~~s~~ ||x||")).toEqual([
      { type: "strong", content: [text("b")] },
      text(" "),
      { type: "emphasis", content: [text("i")] },
      text(" "),
      { type: "emphasis", content: [text("i")] },
      text(" "),
      { type: "underline", content: [text("u")] },
      text(" "),
      { type: "strike", content: [text("s")] },
      text(" "),
      { type: "spoiler", content: [text("x")] },
    ]);
  });

  it("nests emphasis", () => {
    expect(parseInline("***both***")).toEqual([
      { type: "emphasis", content: [{ type: "strong", content: [text("both")] }] },
    ]);
    expect(parseInline("||**bold** secret||")).toEqual([
      { type: "spoiler", content: [{ type: "strong", content: [text("bold")] }, text(" secret")] },
    ]);
  });

  it("lets emphasis run across lines", () => {
    expect(parseInline("*one\ntwo*")).toEqual([{ type: "emphasis", content: [text("one\ntwo")] }]);
  });

  it("leaves markers that do not hug their text", () => {
    expect(parseInline("2 * 3 * 4")).toEqual([text("2 * 3 * 4")]);
    expect(parseInline("** not bold **")).toEqual([text("** not bold **")]);
  });

  it("leaves underscores inside words", () => {
    expect(parseInline("snake_case_name and __dunder__init")).toEqual([text("snake_case_name and __dunder__init")]);
  });

  it("leaves unclosed and empty markers as text", () => {
    expect(parseInline("**almost")).toEqual([text("**almost")]);
    expect(parseInline("**** and ||||")).toEqual([text("**** and ||||")]);
    expect(parseInline("*a **b*")).toEqual([text("*a *"), { type: "emphasis", content: [text("b")] }]);
  });

  it("reads code literally", () => {
    expect(parseInline("run `npm *install*` now")).toEqual([
      text("run "),
      { type: "code", text: "npm *install*" },
      text(" now"),
    ]);
    expect(parseInline("``has ` inside``")).toEqual([{ type: "code", text: "has ` inside" }]);
    expect(parseInline("`` `ticked` ``")).toEqual([{ type: "code", text: "`ticked`" }]);
    expect(parseInline("a ` alone")).toEqual([text("a ` alone")]);
    expect(parseInline("``` ` ```x")).toEqual([{ type: "code", text: "`" }, text("x")]);
  });

  it("drops the backslash before an escaped marker", () => {
    expect(parseInline("\\*not italic\\* and \\\\")).toEqual([text("*not italic* and \\")]);
    expect(parseInline("a\\b")).toEqual([text("a\\b")]);
  });

  it("finds bare web addresses", () => {
    expect(parseInline("see https://snatter.app/docs?x=1#top now")).toEqual([
      text("see "),
      { type: "link", url: "https://snatter.app/docs?x=1#top" },
      text(" now"),
    ]);
  });

  it("leaves sentence punctuation after an address", () => {
    expect(parseInline("go to https://snatter.app.")).toEqual([
      text("go to "),
      { type: "link", url: "https://snatter.app" },
      text("."),
    ]);
    expect(parseInline("(https://snatter.app)")).toEqual([
      text("("),
      { type: "link", url: "https://snatter.app" },
      text(")"),
    ]);
    expect(parseInline("https://en.wikipedia.org/wiki/Snatter_(chat)")).toEqual([
      { type: "link", url: "https://en.wikipedia.org/wiki/Snatter_(chat)" },
    ]);
  });

  it("keeps underscores in an address", () => {
    expect(parseInline("_see https://a.example/x_y_z_")).toEqual([
      { type: "emphasis", content: [text("see "), { type: "link", url: "https://a.example/x_y_z" }] },
    ]);
  });

  it("reads an address in angle brackets whole", () => {
    expect(parseInline("<https://snatter.app/a.>")).toEqual([{ type: "link", url: "https://snatter.app/a." }]);
  });

  it("links only web addresses with a host", () => {
    expect(parseInline("javascript:alert(1) ftp://x.example https:// <javascript:x>")).toEqual([
      text("javascript:alert(1) ftp://x.example https:// <javascript:x>"),
    ]);
    expect(parseInline("xhttps://snatter.app")).toEqual([text("xhttps://snatter.app")]);
  });

  it("nests emphasis at most eight deep", () => {
    let node = parseInline("*a _a ".repeat(10) + "a_ a* ".repeat(10).trimEnd())[0];
    let depth = 0;
    while (node !== undefined && node.type === "emphasis") {
      depth++;
      node = node.content.find((inline) => inline.type !== "text");
    }
    expect(depth).toBe(8);
  });

  it("stays quick on text written to be slow", () => {
    const hostile = [
      "*_~|`".repeat(800),
      "**a ".repeat(999) + "a**",
      "*a ".repeat(1300) + "a*",
      "*a".repeat(2000),
      "*a".repeat(1000) + "a*".repeat(1000),
      "<https://a.b ".repeat(300),
      "` a ".repeat(1000),
      "https://a.b/" + ")".repeat(3980),
    ];
    for (const text of hostile) {
      const started = performance.now();
      parseMarkdown(text.slice(0, 4000));
      expect(performance.now() - started).toBeLessThan(100);
    }
  });
});

describe("parseMarkdown", () => {
  it("keeps lines of text in one paragraph", () => {
    expect(parseMarkdown("one\ntwo\n\nthree")).toEqual([paragraph(text("one\ntwo\n\nthree"))]);
  });

  it("reads a fenced code block with its language", () => {
    expect(parseMarkdown("look:\n```java\nrecord A() {}\n  // **x**\n```\nnice")).toEqual([
      paragraph(text("look:")),
      { type: "code-block", language: "java", text: "record A() {}\n  // **x**" },
      paragraph(text("nice")),
    ]);
  });

  it("reads a code block on one line, and text after its fence", () => {
    expect(parseMarkdown("```one line``` then")).toEqual([
      { type: "code-block", language: null, text: "one line" },
      paragraph(text(" then")),
    ]);
    expect(parseMarkdown("```\nplain\n``` done")).toEqual([
      { type: "code-block", language: null, text: "plain" },
      paragraph(text(" done")),
    ]);
  });

  it("leaves an unclosed fence as text", () => {
    expect(parseMarkdown("```\nnever closed")).toEqual([paragraph(text("```\nnever closed"))]);
  });

  it("reads quotes, but not faces", () => {
    expect(parseMarkdown("> quoted **bold**\n>\n> more\nafter\n>.<")).toEqual([
      {
        type: "quote",
        content: [paragraph(text("quoted "), { type: "strong", content: [text("bold")] }, text("\n\nmore"))],
      },
      paragraph(text("after\n>.<")),
    ]);
  });

  it("reads lists, nested by indentation", () => {
    expect(parseMarkdown("- one\n  1. first\n  2. second\n- two\n3) three")).toEqual([
      {
        type: "list",
        ordered: false,
        start: 1,
        items: [
          {
            content: [text("one")],
            lists: [
              {
                type: "list",
                ordered: true,
                start: 1,
                items: [
                  { content: [text("first")], lists: [] },
                  { content: [text("second")], lists: [] },
                ],
              },
            ],
          },
          { content: [text("two")], lists: [] },
        ],
      },
      { type: "list", ordered: true, start: 3, items: [{ content: [text("three")], lists: [] }] },
    ]);
  });

  it("tells a list marker from emphasis and a minus", () => {
    expect(parseMarkdown("*not a list*\n-5 degrees")).toEqual([
      paragraph({ type: "emphasis", content: [text("not a list")] }, text("\n-5 degrees")),
    ]);
  });

  it("leaves what has no meaning here as text", () => {
    expect(parseMarkdown("# heading\n[text](https://x.example)\n![img](https://x.example/a.png)\n<b>hi</b>")).toEqual([
      paragraph(
        text("# heading\n[text]("),
        { type: "link", url: "https://x.example" },
        text(")\n![img]("),
        { type: "link", url: "https://x.example/a.png" },
        text(")\n<b>hi</b>"),
      ),
    ]);
  });

  it("escapes a block marker", () => {
    expect(parseMarkdown("\\> not quoted\n\\- not listed")).toEqual([paragraph(text("> not quoted\n- not listed"))]);
  });

  it("drops blank lines between blocks", () => {
    expect(parseMarkdown("a\n\n\n> b\n\n  \nc")).toEqual([
      paragraph(text("a")),
      { type: "quote", content: [paragraph(text("b"))] },
      paragraph(text("c")),
    ]);
  });
});
