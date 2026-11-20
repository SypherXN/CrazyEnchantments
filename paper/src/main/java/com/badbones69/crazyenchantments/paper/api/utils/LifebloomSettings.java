package com.badbones69.crazyenchantments.paper.api.utils;

public record LifebloomSettings(double radius) {

    public static final LifebloomSettings DEFAULTS = new LifebloomSettings(8.0);

    public LifebloomSettings {
        radius = TuningConfig.BowValues.checkedNumber(radius, 0.5, 16.0);
    }

    public static boolean eligible(final boolean dead, final boolean attacker, final boolean wearer,
                                   final boolean includeWearer, final boolean friendly) {
        return !dead && !attacker && (wearer ? includeWearer : friendly);
    }

    public static double healedHealth(final boolean dead, final double health, final double maxHealth, final double heal) {
        return dead || health <= 0.0 || health >= maxHealth ? health : Math.min(maxHealth, health + heal);
    }
}
