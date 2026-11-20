package com.badbones69.crazyenchantments.paper.api.utils;

public record ArrowLifestealSettings(double damageShare, double capHp) {

    public static final ArrowLifestealSettings DEFAULTS = new ArrowLifestealSettings(0.20, 3.0);

    public ArrowLifestealSettings {
        TuningConfig.BowValues.checkedNumber(damageShare, 0.0, 1.0);
        TuningConfig.BowValues.checkedNumber(capHp, 0.0, 100.0);
    }

    public double healedHealth(final double health, final double maxHealth, final double damageDealt) {
        if (!(damageDealt > 0.0) || !Double.isFinite(damageDealt)) return health;

        return Math.min(maxHealth, health + Math.min(this.capHp, this.damageShare * damageDealt));
    }
}
