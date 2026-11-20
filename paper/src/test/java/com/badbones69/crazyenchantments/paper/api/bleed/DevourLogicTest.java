package com.badbones69.crazyenchantments.paper.api.bleed;

import com.badbones69.crazyenchantments.paper.api.utils.DevourSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevourLogicTest {

    private static final DevourSettings SETTINGS = DevourSettings.DEFAULTS;

    @Test
    void bonusGrowsWithEachLevel() {
        assertEquals(0.05, SETTINGS.bonusFraction(1), 1.0e-9);
        assertEquals(0.10, SETTINGS.bonusFraction(2), 1.0e-9);
        assertEquals(0.15, SETTINGS.bonusFraction(3), 1.0e-9);
        assertEquals(0.20, SETTINGS.bonusFraction(4), 1.0e-9);

        assertEquals(12.0, SETTINGS.bonusDamage(10.0, true, 4), 1.0e-9);
    }

    @Test
    void noBonusWithoutBleedStacks() {
        final BleedState state = new BleedState();

        assertFalse(bleeding(state, 0));
        assertEquals(10.0, SETTINGS.bonusDamage(10.0, bleeding(state, 0), 4), 1.0e-9);
    }

    @Test
    void noBonusAfterTheBleedExpires() {
        final BleedState state = new BleedState();

        state.addStacks(1, 5, 20, 0);
        assertTrue(bleeding(state, 19));
        assertEquals(12.0, SETTINGS.bonusDamage(10.0, bleeding(state, 19), 4), 1.0e-9);

        assertTrue(state.isExpired(20));
        assertEquals(10.0, SETTINGS.bonusDamage(10.0, bleeding(state, 20), 4), 1.0e-9);
    }

    @Test
    void invalidTuningFallsBackAndWarnsOncePerKey() {
        final List<String> warnings = new ArrayList<>();

        final DevourSettings validated = DevourSettings.validated(
                -1.0, Double.NaN, 501.0, -5.0, warnings::add
        );

        assertEquals(DevourSettings.DEFAULTS, validated);
        assertEquals(4, warnings.size());
    }

    /**
     * Mirrors what {@link BleedSystem#isBleeding} reports: an expired lease and
     * a victim without stacks both count as not bleeding.
     */
    private static boolean bleeding(final BleedState state, final long ownerTick) {
        return !state.isExpired(ownerTick) && state.getStacks() > 0;
    }
}
