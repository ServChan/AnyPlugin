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
}
