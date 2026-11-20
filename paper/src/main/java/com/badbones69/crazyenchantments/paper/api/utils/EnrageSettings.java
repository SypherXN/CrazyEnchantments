package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Enrage tuning. Always-on (no chance system): the bonus ramps with
 * missing health, {@code ppPerLevel * level * missingFraction} percentage
 * points, where {@code missingFraction = 1 - health / maxHealth} clamped to
 * [0, 1] (current and max health only, absorption is ignored). Level III at
 * 100% missing is +30%, at 50% missing +15%. Pure math, no Bukkit types.
 */
public record EnrageSettings(double ppPerLevel) {

    public static final EnrageSettings DEFAULTS = new EnrageSettings(10.0);

    /**
     * Fraction of max health the wielder is missing, in [0, 1]. Unusable
     * inputs count as no missing health.
     */
    public static double missingFraction(final double health, final double maxHealth) {
        if (Double.isNaN(health) || Double.isNaN(maxHealth) || maxHealth <= 0.0) return 0.0;

        return Math.clamp(1.0 - health / maxHealth, 0.0, 1.0);
    }

    /**
     * Percentage points gained at {@code level} for the wielder's current health.
     */
    public double bonusPp(final int level, final double health, final double maxHealth) {
        if (level < 1) return 0.0;

        return this.ppPerLevel * level * missingFraction(health, maxHealth);
    }

    public double multiplier(final int level, final double health, final double maxHealth) {
        return DamageModifiers.outgoingMultiplier(bonusPp(level, health, maxHealth));
    }

    public static EnrageSettings validated(final double ppPerLevel, final Consumer<String> warn) {
        if (ppPerLevel >= 0.0 && ppPerLevel <= 100.0) return new EnrageSettings(ppPerLevel);

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Enrage.PP-Per-Level value '" + ppPerLevel + "'; using " + DEFAULTS.ppPerLevel() + ".");
        }

        return DEFAULTS;
    }
}
