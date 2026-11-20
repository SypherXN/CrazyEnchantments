package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated Pummel tuning. Chance-based (Enchantments.yml, Base 8, +4 per
 * level). A proc applies Slowness to up to {@link #MAX_TARGETS} enemies within {@code radius} blocks
 * of the victim, the victim included. Pure data and math, no Bukkit types.
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning per bad key plus the default.</p>
 */
public record PummelSettings(double radius, int amplifier, List<Double> durationSeconds) {

    public static final int MAX_LEVEL = 3;
    public static final int MAX_TARGETS = 32;

    public static final PummelSettings DEFAULTS = new PummelSettings(3.0, 1, List.of(3.0, 4.0, 5.0));

    public PummelSettings {
        radius = checkRadius(radius);
        amplifier = checkAmplifier(amplifier);
        durationSeconds = checkDurations(durationSeconds);
    }

    /**
     * Slowness duration at {@code level}. A level past the list uses the last
     * entry; a level below I gives nothing.
     */
    public int durationTicks(final int level) {
        if (level < 1) return 0;

        final double seconds = this.durationSeconds.get(Math.min(level, this.durationSeconds.size()) - 1);

        return (int) Math.round(seconds * 20.0);
    }

    public static PummelSettings validated(
            final double radius,
            final int amplifier,
            final List<Double> durationSeconds,
            final Consumer<String> warn
    ) {
        final PummelSettings fallback = DEFAULTS;

        return new PummelSettings(
                orDefault(() -> checkRadius(radius), fallback.radius(), warn),
                orDefault(() -> checkAmplifier(amplifier), fallback.amplifier(), warn),
                orDefault(() -> checkDurations(durationSeconds), fallback.durationSeconds(), warn)
        );
    }

    private static double checkRadius(final double value) {
        if (!(value >= 0.5 && value <= 10.0)) throw bad("Radius", value);

        return value;
    }

    private static int checkAmplifier(final int value) {
        if (value < 0 || value > 9) throw bad("Slowness-Amplifier", value);

        return value;
    }

    private static List<Double> checkDurations(final List<Double> values) {
        if (values == null || values.isEmpty() || values.size() > 16) throw bad("Duration-Seconds", values);

        for (final Double value : values) {
            if (value == null || !(value > 0.0 && value <= 600.0)) throw bad("Duration-Seconds", values);
        }

        return List.copyOf(values);
    }

    private static IllegalArgumentException bad(final String key, final Object value) {
        return new IllegalArgumentException("Invalid CosmicEnchants.Pummel." + key + " value '" + value + "'");
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
