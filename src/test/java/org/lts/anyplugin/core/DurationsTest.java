package org.lts.anyplugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parsesUnits() {
        assertEquals(30 * 60_000L, Durations.parse("30m"));
        assertEquals(86_400_000L + 12 * 3_600_000L, Durations.parse("1d12h"));
        assertEquals(7 * 86_400_000L, Durations.parse("7д"));
        assertEquals(45_000L, Durations.parse("45"));
    }

    @Test
    void rejectsGarbage() {
        assertEquals(-1, Durations.parse("abc"));
        assertEquals(-1, Durations.parse(""));
        assertEquals(-1, Durations.parse("5x"));
        assertEquals(-1, Durations.parse("0m"));
    }

    @Test
    void durationNeedsUnitToLookLikeOne() {
        assertTrue(Durations.looksLikeDuration("2h"));
        assertFalse(Durations.looksLikeDuration("123"));
        assertFalse(Durations.looksLikeDuration("гриф"));
    }

    @Test
    void parsesEveryUnitInBothAlphabets() {
        assertEquals(1000L, Durations.parse("1s"));
        assertEquals(1000L, Durations.parse("1с"));
        assertEquals(60_000L, Durations.parse("1м"));
        assertEquals(3_600_000L, Durations.parse("1H"));
        assertEquals(3_600_000L, Durations.parse("1ч"));
        assertEquals(86_400_000L, Durations.parse("1D"));
        assertEquals(604_800_000L, Durations.parse("1w"));
        assertEquals(604_800_000L, Durations.parse("1н"));
        assertEquals(31_536_000_000L, Durations.parse("1y"));
        assertEquals(31_536_000_000L, Durations.parse("1г"));
        assertEquals(90_000L, Durations.parse("1m30"));
    }

    @Test
    void rejectsInjectionAndOverflow() {
        assertEquals(-1, Durations.parse(null));
        assertEquals(-1, Durations.parse("1d 2h"));
        assertEquals(-1, Durations.parse("1d\nop"));
        assertEquals(-1, Durations.parse("-1d"));
        assertEquals(-1, Durations.parse("d"));
        assertEquals(-1, Durations.parse("1dd"));
        assertEquals(-1, Durations.parse("1000001s"));
        assertEquals(1_000_000L * 31_536_000_000L, Durations.parse("1000000y"));
    }
}
