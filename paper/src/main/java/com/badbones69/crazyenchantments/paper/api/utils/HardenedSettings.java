package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Hardened: always-on armor enchant (no chance system). Pure data and math, no
 * Bukkit types.
 *
 * <p>Every durability wear point on a worn armor piece is skipped on its own
 * with {@code skipPp[level - 1]} percent chance, using that piece's own level
 * (pieces never add). Held items are not armor and are never touched here.</p>
 */
public record HardenedSettings(List<Double> skipPp) {

    public static final int MAX_LEVEL = 3;

    public static final HardenedSettings DEFAULTS = new HardenedSettings(List.of(10.0, 20.0, 30.0));

    public HardenedSettings {
        skipPp = TuningConfig.BowValues.checkedTable(skipPp, MAX_LEVEL, 0.0, 100.0);
    }

    /**
     * Percent chance that one wear point is skipped; 0 for no level.
     */
    public double skipChancePp(final int level) {
        if (level < 1) return 0.0;

        return this.skipPp.get(Math.min(level, MAX_LEVEL) - 1);
    }

    /**
     * Wear points left after every point rolled independently. {@code rolls}
     * yields uniform values in {@code [0, 1)}; a point is skipped when its roll
     * is below the skip chance, so 0% never skips and 100% always skips.
     */
    public int survivingPoints(final int points, final int level, final DoubleSupplier rolls) {
        if (points <= 0) return 0;

        final double skip = skipChancePp(level) / 100.0;

        if (skip <= 0.0) return points;

        int surviving = 0;

        for (int i = 0; i < points; i++) {
            if (!(rolls.getAsDouble() < skip)) surviving++;
        }

        return surviving;
    }
}
