package com.badbones69.crazyenchantments.paper.api.bleed;

import java.util.function.Consumer;

/**
 * Validated Bleed tuning. Values are FORK defaults from the CosmicPvP balance
 * sheet (S04); none of them are historical CosmicPvP numbers.
 */
public record BleedSettings(
        double cadenceSeconds,
        double damagePerStackPerTick,
        int stackCap,
        double durationSeconds,
        double slowPerStackPp,
        double slowCapPp
) {

    public static final BleedSettings DEFAULTS = new BleedSettings(1.0, 0.5, 5, 5.0, 5.0, 60.0);

    public int cadenceTicks() {
        return Math.max(1, (int) Math.round(this.cadenceSeconds * 20.0));
    }

    public int durationTicks() {
        return Math.max(1, (int) Math.round(this.durationSeconds * 20.0));
    }

    public double tickDamage(int stacks) {
        return Math.max(0, stacks) * this.damagePerStackPerTick;
    }

    public static BleedSettings validated(
            final double cadenceSeconds,
            final double damagePerStackPerTick,
            final int stackCap,
            final double durationSeconds,
            final double slowPerStackPp,
            final double slowCapPp,
            final Consumer<String> warn
    ) {
        final BleedSettings fallback = DEFAULTS;

        final double cadence = number("cadence-seconds", cadenceSeconds, fallback.cadenceSeconds(), 0.05, 10.0, warn);
        final double damage = number("damage-per-stack-per-tick", damagePerStackPerTick, fallback.damagePerStackPerTick(), 0.0, 20.0, warn);
        double duration = number("duration-seconds", durationSeconds, fallback.durationSeconds(), 0.1, 120.0, warn);
        final double slow = number("slow-per-stack-pp", slowPerStackPp, fallback.slowPerStackPp(), 0.0, 100.0, warn);
        final double slowCap = number("slow-cap-pp", slowCapPp, fallback.slowCapPp(), 0.0, 100.0, warn);

        int cap = stackCap;

        if (cap < 1 || cap > 20) {
            warnFallback("stack-cap", String.valueOf(stackCap), String.valueOf(fallback.stackCap()), warn);
            cap = fallback.stackCap();
        }

        // A cadence longer than the duration would expire before the first tick.
        if (ticks(cadence) > ticks(duration)) {
            warnFallback("duration-seconds", String.valueOf(duration), ">= cadence-seconds", warn);
            duration = cadence;
        }

        return new BleedSettings(cadence, damage, cap, duration, slow, slowCap);
    }

    private static int ticks(final double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    private static double number(
            final String key,
            final double value,
            final double fallback,
            final double min,
            final double max,
            final Consumer<String> warn
    ) {
        if (Double.isNaN(value) || value < min || value > max) {
            warnFallback(key, String.valueOf(value), String.valueOf(fallback), warn);
            return fallback;
        }

        return value;
    }

    private static void warnFallback(final String key, final String value, final String fallback, final Consumer<String> warn) {
        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Bleed." + key + " value '" + value + "'; using " + fallback + ".");
        }
    }
}
