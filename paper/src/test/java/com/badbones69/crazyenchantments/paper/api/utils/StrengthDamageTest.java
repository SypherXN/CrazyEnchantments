package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Configurable Strength: vanilla adds 3 HP per level, the shipped fork value
 * adds 1, and the correction is the difference, clamped at zero.
 */
class StrengthDamageTest {

    @Test
    void shippedValueTakesTheVanillaExcessOff() {
        // Level III: vanilla 9, configured 3, so the hit loses 6.
        assertEquals(14.0, DamageModifiers.strengthDamage(20.0, 3, 1.0), 1e-9);
        assertEquals(9.0, DamageModifiers.strengthDamage(12.0, 3, 2.0), 1e-9);
    }

    @Test
    void vanillaValueLeavesTheHitAlone() {
        assertEquals(20.0, DamageModifiers.strengthDamage(20.0, 1, 3.0), 1e-9);
        assertEquals(20.0, DamageModifiers.strengthDamage(20.0, 3, 3.0), 1e-9);
    }

    @Test
    void excessLargerThanTheHitClampsAtZero() {
        assertEquals(0.0, DamageModifiers.strengthDamage(4.0, 3, 1.0), 1e-9);
        assertEquals(0.0, DamageModifiers.strengthDamage(0.5, 2, 0.0), 1e-9);
    }

    @Test
    void invalidConfigFallsBackToTheShippedDefault() {
        assertEquals(14.0, DamageModifiers.strengthDamage(20.0, 3, -1.0), 1e-9);
        assertEquals(14.0, DamageModifiers.strengthDamage(20.0, 3, Double.NaN), 1e-9);
        assertEquals(14.0, DamageModifiers.strengthDamage(20.0, 3, 4.0), 1e-9);

        assertEquals(1.0, DamageModifierSettings.DEFAULTS.strengthDamagePerLevel(), 1e-9);
        assertEquals(1.0, DamageModifierSettings.validated(80.0, 0.0, 1.85, 2.0, 1.85, 1.5, 25.0, -1.0, null)
                .strengthDamagePerLevel(), 1e-9);
    }
}
