package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefensiveArmorLogicTest {

    @Test
    void clarityBlocksEqualAndWeakerBlindnessButNotStronger() {
        final ClaritySettings settings = ClaritySettings.DEFAULTS;

        for (int level = 1; level <= 3; level++) {
            assertTrue(settings.blocks(level, 0));
            assertTrue(settings.blocks(level, level - 1));
            assertFalse(settings.blocks(level, level));
        }

        assertFalse(settings.blocks(0, 0));
        assertFalse(settings.blocks(3, -1));
        assertFalse(new ClaritySettings(2).blocks(3, 2));
    }

    @Test
    void arrowDeflectIntervalsAreFourHundredMillisPerLevel() {
        final ArrowDeflectSettings settings = ArrowDeflectSettings.DEFAULTS;

        assertEquals(400L, settings.intervalMillis(1));
        assertEquals(800L, settings.intervalMillis(2));
        assertEquals(1_200L, settings.intervalMillis(3));
        assertEquals(1_600L, settings.intervalMillis(4));
    }

    @Test
    void arrowDeflectDoesNotExtendItsWindowAndAllowsHitsAtTheExactBoundary() {
        final ArrowDeflectSettings settings = ArrowDeflectSettings.DEFAULTS;
        assertFalse(settings.blocks(null, 0L));

        final long firstDeadline = settings.windowAfterHit(null, 0L, 2);
        assertEquals(800_000_000L, firstDeadline);
        assertTrue(settings.blocks(firstDeadline, 799_999_999L));
        assertEquals(firstDeadline, settings.windowAfterHit(firstDeadline, 799_999_999L, 4));
        assertFalse(settings.blocks(firstDeadline, 800_000_000L));
        assertEquals(2_400_000_000L, settings.windowAfterHit(firstDeadline, 800_000_000L, 4));
    }

    @Test
    void enderWalkerHealsOnlyAtFourAndFiveAndIsCappedByMissingHealth() {
        final EnderWalkerSettings settings = EnderWalkerSettings.DEFAULTS;

        for (int level = 0; level <= 3; level++) {
            assertEquals(0.0, settings.healAmount(level, null, 0L, 10.0, 20.0));
        }

        assertEquals(0.5, settings.healAmount(4, null, 0L, 10.0, 20.0));
        assertEquals(0.5, settings.healAmount(5, null, 0L, 10.0, 20.0));
        assertEquals(0.0, settings.healAmount(6, null, 0L, 10.0, 20.0));
        assertEquals(0.25, settings.healAmount(5, null, 0L, 19.75, 20.0));
        assertEquals(0.0, settings.healAmount(5, null, 0L, 20.0, 20.0));
        assertEquals(0.0, settings.healAmount(5, null, 0L, 0.0, 20.0));
    }

    @Test
    void enderWalkerHealIsThrottledUntilExactlyOneSecondAfterTheLastHeal() {
        final EnderWalkerSettings settings = EnderWalkerSettings.DEFAULTS;
        final long last = 50L;
        final long next = last + 1_000_000_000L;

        assertEquals(0.0, settings.healAmount(4, last, last, 10.0, 20.0));
        assertEquals(0.0, settings.healAmount(5, last, next - 1L, 10.0, 20.0));
        assertEquals(0.5, settings.healAmount(5, last, next, 10.0, 20.0));
    }

    @Test
    void invalidTuningFallsBackAndWarnsOncePerKey() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(ClaritySettings.DEFAULTS, ClaritySettings.validated(0, warnings::add));
        assertEquals(ArrowDeflectSettings.DEFAULTS, ArrowDeflectSettings.validated(-1, warnings::add));
        assertEquals(EnderWalkerSettings.DEFAULTS, EnderWalkerSettings.validated(Double.NaN, 0.0, warnings::add));
        assertEquals(4, warnings.size());
    }
}
