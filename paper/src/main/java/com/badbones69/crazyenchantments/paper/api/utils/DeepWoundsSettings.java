package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Validated Deep Wounds tuning. Deep Wounds is always-on (no chance of its own):
 * while an axe with it sits in the wielder's main hand, that player's
 * single normal Bleed roll is boosted.
 *
 * <p>Pure data and math, no Bukkit types, so the table stays unit-testable.</p>
 */
public record DeepWoundsSettings(List<Double> factors) {

    public static final int MAX_LEVEL = 3;

    /**
     * Bleed-chance multipliers at levels I-III (1.25 = +25%).
     */
    public static final DeepWoundsSettings DEFAULTS = new DeepWoundsSettings(List.of(1.25, 1.50, 1.75));

    public DeepWoundsSettings {
        factors = TuningConfig.BowValues.checkedTable(factors, MAX_LEVEL, 1.0, 100.0);
    }

    /**
     * Bleed-chance multiplier at {@code level}. No Deep Wounds (level below I)
     * is neutral, and a level past III reuses the last entry.
     */
    public double factor(final int level) {
        if (level < 1) return 1.0;

        return this.factors.get(Math.clamp(level, 1, this.factors.size()) - 1);
    }

    /**
     * Bleed chance in percent at {@code level}, rounded and capped at 100.
     * A chance of 0 (an always-on chance, meaning no roll) is returned
     * untouched, never turned into a boost.
     */
    public int boostedChance(final int baseChance, final int level) {
        if (baseChance <= 0) return baseChance;
        if (baseChance >= 100) return 100;

        return (int) Math.min(100L, Math.round(baseChance * factor(level)));
    }

    /**
     * Multiplier for the normal enchant roll, which truncates to an int.
     * Round the ratio up by one ULP so roundoff cannot turn an intended 100
     * into 99 (or drop any other whole percentage point).
     */
    public double chanceMultiplier(final int baseChance, final int level) {
        if (baseChance <= 0 || baseChance >= 100) return 1.0;
        final int boosted = boostedChance(baseChance, level);
        return boosted == baseChance ? 1.0 : Math.nextUp((double) boosted / baseChance);
    }
}
