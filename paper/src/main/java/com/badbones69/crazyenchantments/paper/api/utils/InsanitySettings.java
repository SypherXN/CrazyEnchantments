package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Insanity tuning. Always-on (no chance system). Against a target
 * whose main hand is a sword the outgoing melee hit gains
 * {@code bonusPerLevelPp * level} percent (relative): 2.5% at I up to 20% at
 * VIII. Any other target gets no bonus. Pure data and math, no Bukkit types.
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning and the default.</p>
 */
public record InsanitySettings(double bonusPerLevelPp) {

    public static final int MAX_LEVEL = 8;

    public static final InsanitySettings DEFAULTS = new InsanitySettings(2.5);

    public InsanitySettings {
        bonusPerLevelPp = check(bonusPerLevelPp);
    }

    /**
     * Whether a material name is a sword.
     */
    public static boolean isSword(final String materialName) {
        return materialName != null && materialName.endsWith("_SWORD");
    }

    public double bonusPp(final int level) {
        if (level < 1) return 0.0;

        return this.bonusPerLevelPp * Math.min(level, MAX_LEVEL);
    }

    public double multiplier(final int level, final boolean targetHoldsSword) {
        if (!targetHoldsSword) return 1.0;

        return DamageModifiers.outgoingMultiplier(bonusPp(level));
    }

    public static InsanitySettings validated(final double bonusPerLevelPp, final Consumer<String> warn) {
        try {
            return new InsanitySettings(bonusPerLevelPp);
        } catch (final IllegalArgumentException exception) {
            if (warn != null) warn.accept(exception.getMessage() + "; using " + DEFAULTS.bonusPerLevelPp() + ".");

            return DEFAULTS;
        }
    }

    private static double check(final double value) {
        if (!(value >= 0.0 && value <= 100.0)) {
            throw new IllegalArgumentException("Invalid CosmicEnchants.Insanity.Bonus-Per-Level-PP value '" + value + "'");
        }

        return value;
    }
}
