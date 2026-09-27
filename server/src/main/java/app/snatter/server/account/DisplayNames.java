package app.snatter.server.account;

import app.snatter.server.api.ApiException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules for display names: letters of any script, digits, punctuation,
 * symbols and plain spaces, and nothing else. That leaves out emoji, control
 * and format characters (line breaks, bidirectional overrides, zero-width
 * spaces), other kinds of space, combining marks, and letters that draw
 * nothing. Names are not normalized: the characters given are the characters
 * kept. Clients render names as text, so none of this is about markup; it
 * keeps names readable and hard to fake.
 */
public final class DisplayNames {

    /** ASCII whitespace at either end, as from a paste; any other whitespace is refused. */
    private static final Pattern EDGES = Pattern.compile("^[ \\t\\n\\r\\f\\x0B]+|[ \\t\\n\\r\\f\\x0B]+$");

    private static final Pattern SPACES = Pattern.compile(" {2,}");

    /** Hangul fillers and the blank braille pattern: a letter or symbol that draws nothing. */
    private static final Set<Integer> BLANKS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    private DisplayNames() {
    }

    /**
     * The name as it is stored: trimmed, with runs of spaces as one. Null
     * when the input is null or blank, so the username stands in.
     *
     * @throws ApiException {@code invalid_display_name} for any other character
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String name = SPACES.matcher(EDGES.matcher(input).replaceAll("")).replaceAll(" ");
        if (name.isEmpty()) {
            return null;
        }
        name.codePoints().forEach(cp -> {
            if (isEmoji(cp)) {
                throw invalid("Leave out emoji: display names use letters, digits, punctuation and spaces.");
            }
            if (!allowed(cp)) {
                throw invalid("Leave out invisible and special characters: display names use letters, digits, punctuation and spaces.");
            }
        });
        return name;
    }

    /** Drawn as emoji by default, flags and skin tones included. Symbols drawn as text, such as ♥ and ©, are not. */
    static boolean isEmoji(int cp) {
        return Character.isEmojiPresentation(cp) || Character.isEmojiModifier(cp) || (cp >= 0x1F1E6 && cp <= 0x1F1FF);
    }

    private static boolean allowed(int cp) {
        if (cp == ' ') {
            return true;
        }
        if (BLANKS.contains(cp)) {
            return false;
        }
        return switch (Character.getType(cp)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                 Character.MODIFIER_LETTER, Character.OTHER_LETTER,
                 Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
                 Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION,
                 Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("invalid_display_name", message);
    }
}
