package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public record PacifySettings(List<Double> durationSeconds) {

    public static final PacifySettings DEFAULTS = new PacifySettings(List.of(1.0, 1.7, 2.3, 3.0));

    public PacifySettings {
        durationSeconds = TuningConfig.BowValues.checkedTable(durationSeconds, 4, 0.001, 600.0);
    }

    public long durationMillis(final int level) {
        return Math.round(this.durationSeconds.get(Math.clamp(level, 1, 4) - 1) * 1_000.0);
    }

    public enum RageHit { NONE, HOLD, GAIN }

    public static RageHit rageHit(final boolean pacified, final boolean hasRageStack, final boolean holdsRage, final BooleanSupplier activate) {
        if (pacified) return hasRageStack && holdsRage ? RageHit.HOLD : RageHit.NONE;
        return activate.getAsBoolean() ? RageHit.GAIN : RageHit.NONE;
    }

    public static final class PacifyState {

        private static final ConcurrentHashMap<UUID, Long> EXPIRIES = new ConcurrentHashMap<>();

        private PacifyState() {}

        public static void apply(final UUID victim, final PacifySettings settings, final int level, final long nowMillis) {
            EXPIRIES.merge(victim, nowMillis + settings.durationMillis(level), Math::max);
        }

        public static long remainingMillis(final UUID victim) {
            return remainingMillis(victim, System.nanoTime() / 1_000_000L);
        }

        static long remainingMillis(final UUID victim, final long nowMillis) {
            final Long expiry = EXPIRIES.computeIfPresent(victim, (id, time) -> time > nowMillis ? time : null);
            return expiry == null ? 0L : expiry - nowMillis;
        }

        public static void prune(final long nowMillis) {
            EXPIRIES.values().removeIf(expiry -> expiry <= nowMillis);
        }
    }
}
