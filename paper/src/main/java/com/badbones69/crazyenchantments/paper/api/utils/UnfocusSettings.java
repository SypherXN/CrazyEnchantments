package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

public record UnfocusSettings(List<Double> reductionPp, List<Double> durationSeconds) {

    public static final UnfocusSettings DEFAULTS = new UnfocusSettings(
            List.of(10.0, 20.0, 30.0, 40.0, 50.0), List.of(2.0, 4.0, 6.0, 8.0, 10.0));

    public UnfocusSettings {
        reductionPp = TuningConfig.BowValues.checkedTable(reductionPp, 5, 0.0, 100.0);
        durationSeconds = TuningConfig.BowValues.checkedTable(durationSeconds, 5, 0.001, 600.0);
    }

    public Effect refresh(final Effect previous, final int level, final long nowMillis) {
        final int index = Math.clamp(level, 1, 5) - 1;
        final double reduction = this.reductionPp.get(index);
        final long expiry = nowMillis + Math.round(this.durationSeconds.get(index) * 1_000.0);

        if (previous == null || !previous.active(nowMillis)) return new Effect(reduction, expiry);

        return new Effect(Math.max(previous.reductionPp(), reduction), Math.max(previous.expiryMillis(), expiry));
    }

    public record Effect(double reductionPp, long expiryMillis) {

        public boolean active(final long nowMillis) {
            return nowMillis < this.expiryMillis;
        }

        public double multiplier() {
            return 1.0 - this.reductionPp / 100.0;
        }
    }
}
