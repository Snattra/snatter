package app.snatter.server.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.api.ApiException;
import org.junit.jupiter.api.Test;

class DisplayNamesTest {

    private static String refused(String name) {
        ApiException e = assertThrows(ApiException.class, () -> DisplayNames.normalize(name), name);
        assertEquals("invalid_display_name", e.code());
        return e.getMessage();
    }

    @Test
    void blankLeavesItToTheUsername() {
        assertNull(DisplayNames.normalize(null));
        assertNull(DisplayNames.normalize(""));
        assertNull(DisplayNames.normalize(" \t\n  "));
    }

    @Test
    void trimsAndCollapsesSpacesAndChangesNothingElse() {
        assertEquals("Quacky McQuack", DisplayNames.normalize("  Quacky    McQuack\n"));
        // Not normalized: a precomposed é stays as it is.
        assertEquals("André", DisplayNames.normalize("André"));
    }

    @Test
    void keepsLettersOfAnyScriptDigitsPunctuationAndSymbols() {
        for (String name : new String[] {
            "Robin Jönsson", "Åsa Öberg", "日本の鴨", "Маллард",
            "مرغابی", "Tiếng Việt", "O'Brien-Smith (away)", "© Pond & Co. ♥ €5",
            "_xX_Gadwall_Xx_", "Player #1",
        }) {
            assertEquals(name, DisplayNames.normalize(name), name);
        }
    }

    @Test
    void refusesEmojiFlagsAndSkinTones() {
        assertTrue(refused("Mallard 🦆").contains("emoji"));
        refused("🇸🇪");        // a flag: two regional indicators
        refused("Wave 👋🏽");   // a hand with a skin tone
        refused("😀");
    }

    @Test
    void refusesWhatIsInvisibleOrChangesHowTextIsDrawn() {
        refused("Rob\nin");            // a line break inside
        refused("Rob\tin");
        refused("bell\u0007");         // control character
        refused("‮evil");         // right-to-left override
        refused("Rob​in");        // zero-width space
        refused("Rob‍in");        // zero-width joiner
        refused("Robin﻿");        // byte order mark
        refused("Rob in");        // no-break space: only plain spaces
        refused("Rob　in");        // ideographic space
        refused("private");      // private use
        refused("ㅤ");             // Hangul filler, a letter that draws nothing
        refused("⠀⠀");       // braille blank
    }

    @Test
    void refusesCombiningMarks() {
        refused("André");        // an accent added to the letter before it
        refused("x́̂̃̄");
        refused("1️⃣");      // a keycap
    }
}
