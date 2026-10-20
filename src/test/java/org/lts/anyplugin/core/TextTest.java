package org.lts.anyplugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void convertsLegacyCodes() {
        assertEquals("<red>Hi <bold>there", Text.legacyToMini("&cHi &lthere"));
        assertEquals("<#ff00aa>pink", Text.legacyToMini("&#ff00aapink"));
    }

    @Test
    void userInputWithoutColorsStaysPlain() {
        assertEquals("<red>x &cy", Text.plain(Text.userInput("<red>x &cy", false)));
        assertEquals("x y", Text.plain(Text.userInput("<red>x &cy", true)));
    }

    @Test
    void userInputCannotClick() {
        assertEquals("<click:run_command:'/op me'>hi", Text.plain(Text.userInput("<click:run_command:'/op me'>hi", true)));
    }
}
