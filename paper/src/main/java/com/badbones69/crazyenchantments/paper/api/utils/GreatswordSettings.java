package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Greatsword tuning. Always-on: a melee hit against a target whose
 * main hand is a bow or crossbow gains {@code bonusPerLevelPp * level}
 * percentage points. Pure data and math, no Bukkit types.
 */
public record GreatswordSettings(double bonusPerLevelPp) {

    public static final int MAX_LEVEL = 5;

    public static final GreatswordSettings DEFAULTS = new GreatswordSettings(4.0);

    public GreatswordSettings {
        if (!(bonusPerLevelPp >= 0.0 && bonusPerLevelPp <= 100.0)) {
            throw new IllegalArgumentException("Invalid CosmicEnchants.Greatsword.Bonus-Per-Level-PP value '" + bonusPerLevelPp + "'");
        }
    }

    /**
     * Outgoing multiplier at {@code level}. A non-bow target or a level below I
     * leaves the hit unchanged.
     */
    public double multiplier(final int level, final boolean targetHoldsBow) {
        if (level < 1 || !targetHoldsBow) return 1.0;

        return 1.0 + this.bonusPerLevelPp * Math.min(MAX_LEVEL, level) / 100.0;
    }

    /**
     * Exact main-hand check: only a bow or a crossbow earns the bonus.
     */
    public static boolean holdsBow(final String material) {
        return "BOW".equals(material) || "CROSSBOW".equals(material);
    }

    public static GreatswordSettings validated(final double bonusPerLevelPp, final Consumer<String> warn) {
        if (bonusPerLevelPp >= 0.0 && bonusPerLevelPp <= 100.0) return new GreatswordSettings(bonusPerLevelPp);

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Greatsword.Bonus-Per-Level-PP value '" + bonusPerLevelPp
                    + "'; using " + DEFAULTS.bonusPerLevelPp() + ".");
        }

        return DEFAULTS;
    }
}
