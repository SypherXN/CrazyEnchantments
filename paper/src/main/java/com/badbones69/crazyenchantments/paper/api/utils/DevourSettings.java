package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Devour tuning. The bonuses are FORK defaults from the CosmicPvP
 * balance sheet (S04); none of them are historical CosmicPvP numbers.
 */
public record DevourSettings(double levelOne, double levelTwo, double levelThree, double levelFour) {

    public static final DevourSettings DEFAULTS = new DevourSettings(5.0, 10.0, 15.0, 20.0);

    /**
     * Relative bonus as a fraction of the hit, e.g. level I -> 0.05. Levels
     * outside I-IV use the nearest configured level.
     */
    public double bonusFraction(final int level) {
        final double percent = switch (Math.max(1, Math.min(4, level))) {
            case 1 -> this.levelOne;
            case 2 -> this.levelTwo;
            case 3 -> this.levelThree;
            default -> this.levelFour;
        };

        return percent / 100.0;
    }

    /**
     * The hit's damage after the bonus. A target that is not bleeding takes the
     * hit unchanged.
     */
    public double bonusDamage(final double damage, final boolean bleeding, final int level) {
        return bleeding ? damage * (1.0 + bonusFraction(level)) : damage;
    }

    public static DevourSettings validated(
            final double levelOne,
            final double levelTwo,
            final double levelThree,
            final double levelFour,
            final Consumer<String> warn
    ) {
        final DevourSettings fallback = DEFAULTS;

        return new DevourSettings(
                number("Level-I", levelOne, fallback.levelOne(), warn),
                number("Level-II", levelTwo, fallback.levelTwo(), warn),
                number("Level-III", levelThree, fallback.levelThree(), warn),
                number("Level-IV", levelFour, fallback.levelFour(), warn)
        );
    }

    private static double number(final String key, final double value, final double fallback, final Consumer<String> warn) {
        if (Double.isNaN(value) || value < 0.0 || value > 500.0) {
            if (warn != null) {
                warn.accept("Invalid CosmicEnchants.Devour." + key + " value '" + value + "'; using " + fallback + ".");
            }

            return fallback;
        }

        return value;
    }
}
