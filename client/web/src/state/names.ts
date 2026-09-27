/**
 * The server's rules for names, checked here to say what is wrong before a
 * form is sent. The server has the last word: its Unicode tables may be newer.
 */

/** Usernames: 3 to 32 letters A to Z, digits and underscores, unique regardless of case. */
export const USERNAME_PATTERN = "[A-Za-z0-9_]+";

const EDGES = /^[ \t\n\r\f\v]+|[ \t\n\r\f\v]+$/g;
const SPACES = / {2,}/g;
const EMOJI = /[\p{Emoji_Presentation}\p{Emoji_Modifier}\u{1F1E6}-\u{1F1FF}]/u;
const ALLOWED = /^[\p{L}\p{N}\p{P}\p{S} ]$/u;
/** Hangul fillers and the blank braille pattern: a letter or symbol that draws nothing. */
const BLANKS = new Set(["ᅟ", "ᅠ", "ㅤ", "ﾠ", "⠀"]);

/** The display name as the server keeps it: whitespace off the ends and runs of spaces as one; "" when blank. */
export function tidyDisplayName(input: string): string {
  return input.replace(EDGES, "").replace(SPACES, " ");
}

/**
 * What is wrong with a display name, in the server's words, or null when
 * nothing is. Letters of any script, digits, punctuation, symbols and plain
 * spaces are fine; emoji, invisible characters and combining marks are not.
 */
export function displayNameProblem(input: string): string | null {
  for (const char of tidyDisplayName(input)) {
    if (EMOJI.test(char)) {
      return "Leave out emoji: display names use letters, digits, punctuation and spaces.";
    }
    if (!ALLOWED.test(char) || BLANKS.has(char)) {
      return "Leave out invisible and special characters: display names use letters, digits, punctuation and spaces.";
    }
  }
  return null;
}
