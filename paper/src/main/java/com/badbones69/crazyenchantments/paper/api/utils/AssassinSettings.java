package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Assassin tuning and its distance rule. Always-on (no chance
 * system). Pure math, no Bukkit types.
 *
 * <p>At {@code closeRangeBlocks} or closer (inclusive: exactly on the boundary
 * still counts as close) the outgoing melee multiplier is
 * {@code 1 + bonusPerLevelPp * level / 100}, so 1.05x at I up to 1.25x at V by
 * default. Strictly beyond it the hit gets the flat {@code farMultiplier}
 * (0.90x) and the bonus is lost entirely.</p>
 */
public record AssassinSettings(double bonusPerLevelPp, double closeRangeBlocks, double farMultiplier) {

    public static final int MAX_LEVEL = 5;

    public static final AssassinSettings DEFAULTS = new AssassinSettings(5.0, 2.0, 0.9);

    /**
     * Outgoing melee multiplier for a hit at {@code distance} blocks. A level
     * below I or an unusable distance leaves the hit unchanged.
     */
    public double multiplier(final int level, final double distance) {
        if (level < 1 || Double.isNaN(distance)) return 1.0;

        // Strict '>' so exactly closeRangeBlocks still earns the bonus.
        if (distance > this.closeRangeBlocks) return this.farMultiplier;

        return closeMultiplier(level);
    }

    public double closeMultiplier(final int level) {
        return 1.0 + this.bonusPerLevelPp * Math.clamp(level, 1, MAX_LEVEL) / 100.0;
    }

    public static AssassinSettings validated(
            final double bonusPerLevelPp,
            final double closeRangeBlocks,
            final double farMultiplier,
            final Consumer<String> warn
    ) {
        final AssassinSettings fallback = DEFAULTS;

        return new AssassinSettings(
                number("Bonus-Per-Level-PP", bonusPerLevelPp, fallback.bonusPerLevelPp(), 0.0, 100.0, warn),
                number("Close-Range-Blocks", closeRangeBlocks, fallback.closeRangeBlocks(), 0.0, 16.0, warn),
                number("Far-Multiplier", farMultiplier, fallback.farMultiplier(), 0.01, 1.0, warn)
        );
    }

    private static double number(
            final String key, final double value, final double fallback, final double min, final double max,
            final Consumer<String> warn
    ) {
        if (value >= min && value <= max) return value;

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Assassin." + key + " value '" + value + "'; using " + fallback + ".");
        }

        return fallback;
    }
}
