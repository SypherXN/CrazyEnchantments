package com.badbones69.crazyenchantments.paper.api.utils;

public record ExplosiveSettings(double radius, double baseDamageHp, double damagePerLevelHp) {

    public static final ExplosiveSettings DEFAULTS = new ExplosiveSettings(3.0, 3.0, 0.5);

    public ExplosiveSettings {
        TuningConfig.BowValues.checkedNumber(radius, 0.5, 16.0);
        TuningConfig.BowValues.checkedNumber(baseDamageHp, 0.0, 100.0);
        TuningConfig.BowValues.checkedNumber(damagePerLevelHp, 0.0, 100.0);
    }

    public double damageHp(final int level) {
        return this.baseDamageHp + this.damagePerLevelHp * (Math.clamp(level, 1, 5) - 1);
    }
}
