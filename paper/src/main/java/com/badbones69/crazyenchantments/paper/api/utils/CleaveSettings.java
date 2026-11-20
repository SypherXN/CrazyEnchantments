package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated Cleave tuning. Chance-based (Enchantments.yml, Base 12, +2 per
 * level). A proc splashes {@code splashShare} of the primary hit's final
 * damage to up to {@code maxTargets} other enemies within
 * {@code baseRadius + radiusPerLevel * level} blocks of the victim. Pure data
 * and math, no Bukkit types.
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning per bad key plus the default.</p>
 */
public record CleaveSettings(double splashShare, int maxTargets, double baseRadius, double radiusPerLevel) {

    public static final int MAX_LEVEL = 7;

    public static final CleaveSettings DEFAULTS = new CleaveSettings(0.35, 4, 1.0, 0.5);

    public CleaveSettings {
        splashShare = checkShare(splashShare);
        maxTargets = checkTargets(maxTargets);
        baseRadius = checkBase(baseRadius);
        radiusPerLevel = checkPerLevel(radiusPerLevel);
    }

    /**
     * Splash radius in blocks at {@code level}: 1.5 at I up to 4.5 at VII by default.
     * A level below I is treated as I, a level past VII as VII.
     */
    public double radius(final int level) {
        return this.baseRadius + this.radiusPerLevel * Math.clamp(level, 1, MAX_LEVEL);
    }

    /**
     * Damage dealt to each splash target for a primary hit of {@code finalDamage}.
     * An unusable hit (zero, negative, NaN, infinite) splashes nothing.
     */
    public double splashDamage(final double finalDamage) {
        if (!(finalDamage > 0.0) || Double.isInfinite(finalDamage)) return 0.0;

        return finalDamage * this.splashShare;
    }

    public static CleaveSettings validated(
            final double splashShare,
            final int maxTargets,
            final double baseRadius,
            final double radiusPerLevel,
            final Consumer<String> warn
    ) {
        final CleaveSettings fallback = DEFAULTS;

        return new CleaveSettings(
                orDefault(() -> checkShare(splashShare), fallback.splashShare(), warn),
                orDefault(() -> checkTargets(maxTargets), fallback.maxTargets(), warn),
                orDefault(() -> checkBase(baseRadius), fallback.baseRadius(), warn),
                orDefault(() -> checkPerLevel(radiusPerLevel), fallback.radiusPerLevel(), warn)
        );
    }

    private static double checkShare(final double value) {
        if (!(value > 0.0 && value <= 1.0)) throw bad("Splash-Share", value);

        return value;
    }

    private static int checkTargets(final int value) {
        if (value < 1 || value > PummelSettings.MAX_TARGETS) throw bad("Max-Targets", value);

        return value;
    }

    private static double checkBase(final double value) {
        if (!(value >= 0.0 && value <= 16.0)) throw bad("Base-Radius", value);

        return value;
    }

    private static double checkPerLevel(final double value) {
        if (!(value >= 0.0 && value <= 4.0)) throw bad("Radius-Per-Level", value);

        return value;
    }

    private static IllegalArgumentException bad(final String key, final Object value) {
        return new IllegalArgumentException("Invalid CosmicEnchants.Cleave." + key + " value '" + value + "'");
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
