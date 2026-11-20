package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public record AngelicSettings(double healPerSecond, double durationSeconds) {

    public static final AngelicSettings DEFAULTS = new AngelicSettings(0.5, 5.0);
    public static final long PULSE_TICKS = 20L;

    public AngelicSettings {
        healPerSecond = TuningConfig.BowValues.checkedNumber(healPerSecond, 0.0, 100.0);
        durationSeconds = TuningConfig.BowValues.checkedNumber(durationSeconds, 1.0, 600.0);
    }

    public long durationTicks() {
        return Math.round(this.durationSeconds * 20.0);
    }

    /**
     * One piece's chance in percentage points at {@code level}: the base chance plus the
     * per-level increase, exactly as the shared chance roll builds it.
     */
    public static double procChancePp(final int base, final int increase, final int level) {
        return Math.max(0.0, Math.min(100.0, base + (double) increase * (level - 1)));
    }

    /**
     * The probability that at least one piece procs, from each piece's own percentage
     * chance. The pieces are alternatives, so the odds combine into a single attempt
     * instead of one roll per piece: the chance that none procs is the product of
     * each piece's chance of not proccing.
     */
    public static double combinedChance(final double... piecePp) {
        double none = 1.0;

        for (final double pp : piecePp) none *= 1.0 - Math.clamp(pp, 0.0, 100.0) / 100.0;

        return 1.0 - none;
    }

    public static <T> boolean claim(final ConcurrentMap<UUID, T> active, final UUID wearer, final T run) {
        return active.putIfAbsent(wearer, run) == null;
    }
}
