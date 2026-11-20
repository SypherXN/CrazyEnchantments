package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Thundering Blow tuning. A proc adds {@code hpPerLevel * level} HP of
 * flat damage to the hit, with no condition on the target. Pure math, no Bukkit
 * types.
 */
public record ThunderingBlowSettings(double hpPerLevel) {

    public static final int MAX_LEVEL = 3;

    public static final ThunderingBlowSettings DEFAULTS = new ThunderingBlowSettings(1.0);

    public double bonusHp(final int level) {
        if (level < 1) return 0.0;

        return this.hpPerLevel * Math.min(MAX_LEVEL, level);
    }

    public static ThunderingBlowSettings validated(final double hpPerLevel, final Consumer<String> warn) {
        if (hpPerLevel >= 0.0 && hpPerLevel <= 20.0) return new ThunderingBlowSettings(hpPerLevel);

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.ThunderingBlow.HP-Per-Level value '" + hpPerLevel
                    + "'; using " + DEFAULTS.hpPerLevel() + ".");
        }

        return DEFAULTS;
    }
}
