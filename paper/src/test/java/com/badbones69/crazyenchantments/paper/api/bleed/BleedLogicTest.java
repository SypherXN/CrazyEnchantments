package com.badbones69.crazyenchantments.paper.api.bleed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BleedLogicTest {

    private static final BleedSettings SETTINGS = BleedSettings.DEFAULTS;

    @Test
    void stacksCapAndEveryProcRefreshes() {
        final BleedState state = new BleedState();

        state.addStacks(1, 5, 100, 0);
        assertEquals(1, state.getStacks());
        assertFalse(state.isExpired(99));
        assertTrue(state.isExpired(100));

        for (int i = 0; i < 10; i++) state.addStacks(1, 5, 100, 19);

        assertEquals(5, state.getStacks());
        assertFalse(state.isExpired(118), "refresh just before the first cadence still lasts 100 owner ticks");
        assertTrue(state.isExpired(119));
    }

    @Test
    void defaultBleedLastsFiveTicks() {
        final BleedState state = new BleedState();
        state.addStacks(1, 5, SETTINGS.durationTicks(), 0);

        int ticks = 0;

        for (long now = 1; !state.isExpired(now - 1); now++) {
            if (now % SETTINGS.cadenceTicks() == 0) ticks++;
        }

        assertEquals(5, ticks, "5 s at a 1 s cadence must deal five ticks");
    }

    @Test
    void expiryBoundaryIsExplicitAndRefreshWins() {
        final BleedState state = new BleedState();

        state.addStacks(1, 5, 100, 0);
        assertTrue(state.isExpired(100));

        state.addStacks(1, 5, 100, 100);
        assertEquals(1, state.getStacks(), "a new application after expiry does not inherit expired stacks");
        assertFalse(state.isExpired(199));
        assertTrue(state.isExpired(200));
    }

    @Test
    void damageAndSlowScaleWithStacks() {
        final BleedState state = new BleedState();
        state.addStacks(5, 5, 100, 0);

        assertEquals(2.5, state.tickDamage(SETTINGS), 1.0e-9);
        assertEquals(-0.25, state.slowFraction(SETTINGS), 1.0e-9);
    }

    @Test
    void slowNeverExceedsCap() {
        final BleedState state = new BleedState();
        state.addStacks(20, 20, 100, 0);

        assertEquals(-0.60, state.slowFraction(SETTINGS), 1.0e-9);
    }

    @Test
    void invalidTuningFallsBackAndWarnsOncePerKey() {
        final List<String> warnings = new ArrayList<>();

        final BleedSettings validated = BleedSettings.validated(
                -1.0, Double.NaN, 0, 0.0, 200.0, 101.0, warnings::add
        );

        assertEquals(BleedSettings.DEFAULTS, validated);
        assertEquals(6, warnings.size());
    }

    @Test
    void cadenceLongerThanDurationIsRejected() {
        final List<String> warnings = new ArrayList<>();

        final BleedSettings validated = BleedSettings.validated(
                10.0, 0.5, 5, 1.0, 5.0, 60.0, warnings::add
        );

        assertTrue(validated.cadenceTicks() <= validated.durationTicks());
        assertEquals(200, validated.durationTicks());
        assertEquals(1, warnings.size());
    }

    @Test
    void defaultsMatchApprovedBalanceSheet() {
        assertEquals(20, SETTINGS.cadenceTicks());
        assertEquals(100, SETTINGS.durationTicks());
        assertEquals(0.5, SETTINGS.damagePerStackPerTick(), 0.0);
        assertEquals(5, SETTINGS.stackCap());
        assertEquals(5.0, SETTINGS.slowPerStackPp(), 0.0);
        assertEquals(60.0, SETTINGS.slowCapPp(), 0.0);
    }
}
