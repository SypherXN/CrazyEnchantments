package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public record ArrowDeflectSettings(int intervalPerLevelMillis) {

    public static final ArrowDeflectSettings DEFAULTS = new ArrowDeflectSettings(750);

    public long intervalMillis(final int level) {
        return (long) this.intervalPerLevelMillis * Math.clamp(level, 0, 4);
    }

    public boolean blocks(final Long nextAllowedNanos, final long nowNanos) {
        return nextAllowedNanos != null && nowNanos - nextAllowedNanos < 0;
    }

    public long windowAfterHit(final Long nextAllowedNanos, final long nowNanos, final int level) {
        if (blocks(nextAllowedNanos, nowNanos)) return nextAllowedNanos;

        return nowNanos + TimeUnit.MILLISECONDS.toNanos(intervalMillis(level));
    }

    public static ArrowDeflectSettings validated(final int intervalPerLevelMillis, final Consumer<String> warn) {
        if (intervalPerLevelMillis >= 1 && intervalPerLevelMillis <= 60_000) {
            return new ArrowDeflectSettings(intervalPerLevelMillis);
        }

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.ArrowDeflect.Interval-Per-Level-Millis value '" + intervalPerLevelMillis
                    + "'; using " + DEFAULTS.intervalPerLevelMillis() + ".");
        }

        return DEFAULTS;
    }
}
