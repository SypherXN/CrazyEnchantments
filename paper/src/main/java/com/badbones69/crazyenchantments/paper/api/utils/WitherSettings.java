package com.badbones69.crazyenchantments.paper.api.utils;

/**
 * Wither: armor enchant with the existing chance system (10% + 3% per level).
 * When a worn piece procs on being hit, the attacker gets the Wither effect at
 * {@code amplifier} for {@code durationSeconds}. The effect itself is the one the
 * enchant had on swords (Wither III for 2 s); only the item type moved.
 */
public record WitherSettings(int amplifier, double durationSeconds) {

    public static final int MAX_LEVEL = 5;

    public static final WitherSettings DEFAULTS = new WitherSettings(2, 2.0);

    public WitherSettings {
        amplifier = (int) TuningConfig.BowValues.checkedNumber(amplifier, 0.0, 10.0);
        durationSeconds = TuningConfig.BowValues.checkedNumber(durationSeconds, 0.05, 600.0);
    }

    public int durationTicks() {
        return (int) Math.max(1L, Math.round(this.durationSeconds * 20.0));
    }
}
