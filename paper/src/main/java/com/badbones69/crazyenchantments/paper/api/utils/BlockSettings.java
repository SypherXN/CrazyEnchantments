package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Block tuning. An accepted proc negates the whole incoming melee hit
 * and the attacker takes {@code reflectPercent(level)} of that hit's
 * pre-mitigation damage. Pure math, no Bukkit types.
 */
public record BlockSettings(double levelOne, double levelTwo, double levelThree) {

    public static final BlockSettings DEFAULTS = new BlockSettings(25.0, 35.0, 45.0);

    public double reflectPercent(final int level) {
        if (level < 1) return 0.0;

        return switch (Math.min(3, level)) {
            case 1 -> this.levelOne;
            case 2 -> this.levelTwo;
            default -> this.levelThree;
        };
    }

    /**
     * Damage reflected for one blocked hit. Non-positive or non-finite hits
     * reflect nothing.
     */
    public double reflectAmount(final int level, final double preMitigationDamage) {
        if (!Double.isFinite(preMitigationDamage) || preMitigationDamage <= 0.0) return 0.0;

        return preMitigationDamage * reflectPercent(level) / 100.0;
    }

    /**
     * The no-loop rule. A hit may roll Block only when it is not itself a
     * synthetic hit (a reflection or a Bleed tick) and no other plugin has
     * already cancelled it, so a reflection never re-enters the reflect path.
     */
    public static boolean mayProc(final boolean syntheticHitInProgress, final boolean alreadyCancelled) {
        return !syntheticHitInProgress && !alreadyCancelled;
    }

    public static BlockSettings validated(
            final double levelOne, final double levelTwo, final double levelThree, final Consumer<String> warn
    ) {
        final BlockSettings fallback = DEFAULTS;

        return new BlockSettings(
                number("Level-I", levelOne, fallback.levelOne(), warn),
                number("Level-II", levelTwo, fallback.levelTwo(), warn),
                number("Level-III", levelThree, fallback.levelThree(), warn)
        );
    }

    private static double number(final String key, final double value, final double fallback, final Consumer<String> warn) {
        if (value >= 0.0 && value <= 100.0) return value;

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Block." + key + " value '" + value + "'; using " + fallback + ".");
        }

        return fallback;
    }
}
