package com.badbones69.crazyenchantments.paper.api.utils;

public record LongbowSettings(double bonusPerLevelPp) {

    public static final LongbowSettings DEFAULTS = new LongbowSettings(5.0);

    public LongbowSettings {
        TuningConfig.BowValues.checkedNumber(bonusPerLevelPp, 0.0, 100.0);
    }

    public double multiplier(final int level) {
        return 1.0 + this.bonusPerLevelPp * Math.clamp(level, 0, 4) / 100.0;
    }
}
