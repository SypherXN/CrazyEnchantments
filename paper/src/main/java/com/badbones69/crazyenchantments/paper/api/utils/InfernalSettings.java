package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

public record InfernalSettings(double radius, double damageHp, List<Double> igniteSeconds) {

    public static final InfernalSettings DEFAULTS = new InfernalSettings(2.0, 1.0, List.of(3.0, 4.0, 5.0));

    public InfernalSettings {
        TuningConfig.BowValues.checkedNumber(radius, 0.5, 16.0);
        TuningConfig.BowValues.checkedNumber(damageHp, 0.0, 100.0);
        igniteSeconds = TuningConfig.BowValues.checkedTable(igniteSeconds, 3, 0.0, 600.0);
    }

    public int fireTicks(final int level) {
        return (int) Math.round(this.igniteSeconds.get(Math.clamp(level, 1, 3) - 1) * 20.0);
    }
}
