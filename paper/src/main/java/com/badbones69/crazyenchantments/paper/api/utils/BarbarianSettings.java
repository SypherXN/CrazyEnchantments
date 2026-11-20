package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Barbarian tuning. Always-on (no chance system). Against a target
 * whose main hand is an axe the outgoing melee hit gains
 * {@code bonusPerLevelPp * level} percent (relative): 5/10/15/20% at I-IV.
 * Any other target gets no bonus. Pure data and math, no Bukkit types.
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning and the default.</p>
 */
public record BarbarianSettings(double bonusPerLevelPp) {

    public static final int MAX_LEVEL = 4;

    public static final BarbarianSettings DEFAULTS = new BarbarianSettings(5.0);

    public BarbarianSettings {
        bonusPerLevelPp = check(bonusPerLevelPp);
    }

    /**
     * Whether a material name is an axe. {@code _PICKAXE} does not end in
     * {@code _AXE}, so pickaxes are not axes.
     */
    public static boolean isAxe(final String materialName) {
        return materialName != null && materialName.endsWith("_AXE");
    }

    public double bonusPp(final int level) {
        if (level < 1) return 0.0;

        return this.bonusPerLevelPp * Math.min(level, MAX_LEVEL);
    }

    public double multiplier(final int level, final boolean targetHoldsAxe) {
        if (!targetHoldsAxe) return 1.0;

        return DamageModifiers.outgoingMultiplier(bonusPp(level));
    }

    public static BarbarianSettings validated(final double bonusPerLevelPp, final Consumer<String> warn) {
        try {
            return new BarbarianSettings(bonusPerLevelPp);
        } catch (final IllegalArgumentException exception) {
            if (warn != null) warn.accept(exception.getMessage() + "; using " + DEFAULTS.bonusPerLevelPp() + ".");

            return DEFAULTS;
        }
    }

    private static double check(final double value) {
        if (!(value >= 0.0 && value <= 100.0)) {
            throw new IllegalArgumentException("Invalid CosmicEnchants.Barbarian.Bonus-Per-Level-PP value '" + value + "'");
        }

        return value;
    }
}
