package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated Anti Gank tuning and its tracking rule. Always-on (no chance
 * system). Pure data and math, no Bukkit types.
 *
 * <p>Each wielder owns a map of distinct attacker UUID to the nanoTime of that
 * attacker's latest hit on the wielder. An entry older than {@code windowSeconds}
 * is expired (a hit exactly {@code windowSeconds} old is already gone) and is
 * dropped whenever the map is read or written. All recent attackers count,
 * including the current target if they have hit the wielder. The bonus applies when {@code count > 6 - level} and is
 * {@code 1 + bonusPerExtraAttacker * (count - (6 - level))}, capped at {@code cap}.</p>
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning per bad key plus the default.</p>
 */
public record AntiGankSettings(double windowSeconds, double bonusPerExtraAttacker, double cap) {

    public static final int MAX_LEVEL = 4;

    /** The threshold at level L is {@code THRESHOLD_BASE - L} attackers; the bonus needs more than that. */
    public static final int THRESHOLD_BASE = 6;

    private static final double MAX_MULTIPLIER = 1.5;

    public static final AntiGankSettings DEFAULTS = new AntiGankSettings(5.0, 0.10, MAX_MULTIPLIER);

    public AntiGankSettings {
        windowSeconds = checkWindow(windowSeconds);
        bonusPerExtraAttacker = checkBonus(bonusPerExtraAttacker);
        cap = checkCap(cap);
    }

    public long windowNanos() {
        return Math.round(this.windowSeconds * 1_000_000_000.0);
    }

    /**
     * Distinct attackers a hit needs to exceed at {@code level} (2 at IV).
     */
    public int threshold(final int level) {
        return THRESHOLD_BASE - Math.clamp(level, 1, MAX_LEVEL);
    }

    /**
     * Outgoing melee multiplier for {@code count} distinct recent attackers.
     * No level, or a count at or below the threshold, leaves the hit unchanged.
     */
    public double multiplier(final int level, final int count) {
        if (level < 1) return 1.0;

        final int extra = count - threshold(level);

        if (extra <= 0) return 1.0;

        return Math.min(this.cap, 1.0 + this.bonusPerExtraAttacker * extra);
    }

    /**
     * Records {@code attacker} as having hit the wielder at {@code nowNanos}
     * (refreshing an existing entry), pruning and finding the oldest live
     * entry for eviction in one pass.
     */
    public void record(final Map<UUID, Long> hits, final UUID attacker, final long nowNanos) {
        final long window = windowNanos();
        UUID oldest = null;
        long oldestAge = Long.MIN_VALUE;

        for (var entries = hits.entrySet().iterator(); entries.hasNext();) {
            final var entry = entries.next();
            final long age = nowNanos - entry.getValue();
            if (age >= window) {
                entries.remove();
            } else if (oldest == null || age > oldestAge || (age == oldestAge && entry.getKey().compareTo(oldest) < 0)) {
                oldest = entry.getKey();
                oldestAge = age;
            }
        }

        if (!hits.containsKey(attacker) && hits.size() >= TuningConfig.MAX_ANTI_GANK_HISTORY) hits.remove(oldest);

        hits.put(attacker, nowNanos);
    }

    /**
     * Counts distinct unexpired attackers in the same pass that prunes expired entries.
     */
    public int count(final Map<UUID, Long> hits, final long nowNanos) {
        final long window = windowNanos();
        int count = 0;

        for (var entries = hits.values().iterator(); entries.hasNext();) {
            if (nowNanos - entries.next() >= window) entries.remove();
            else count++;
        }

        return count;
    }

    public static AntiGankSettings validated(
            final double windowSeconds,
            final double bonusPerExtraAttacker,
            final double cap,
            final Consumer<String> warn
    ) {
        final AntiGankSettings fallback = DEFAULTS;

        return new AntiGankSettings(
                orDefault(() -> checkWindow(windowSeconds), fallback.windowSeconds(), warn),
                orDefault(() -> checkBonus(bonusPerExtraAttacker), fallback.bonusPerExtraAttacker(), warn),
                orDefault(() -> checkCap(cap), fallback.cap(), warn)
        );
    }

    private static double checkWindow(final double value) {
        if (!(value > 0.0 && value <= 600.0)) throw bad("Window-Seconds", value);

        return value;
    }

    private static double checkBonus(final double value) {
        if (!(value >= 0.0 && value <= 5.0)) throw bad("Bonus-Per-Extra-Attacker", value);

        return value;
    }

    private static double checkCap(final double value) {
        if (!(value >= 1.0 && value <= MAX_MULTIPLIER)) throw bad("Cap", value);

        return value;
    }

    private static IllegalArgumentException bad(final String key, final Object value) {
        return new IllegalArgumentException("Invalid CosmicEnchants.AntiGank." + key + " value '" + value + "'");
    }

    private static <T> T orDefault(final Supplier<T> check, final T fallback, final Consumer<String> warn) {
        try {
            return check.get();
        } catch (final IllegalArgumentException exception) {
            if (warn != null) warn.accept(exception.getMessage() + "; using " + fallback + ".");

            return fallback;
        }
    }
}
