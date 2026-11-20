package com.badbones69.crazyenchantments.paper.api.utils;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Validated Blood Lust tuning. The chance curve stays in the existing enchant
 * chance system (Enchantments.yml Base 5, Increase 3); this only holds the
 * FORK healing knobs. Values are FORK defaults from the CosmicPvP balance
 * sheet (S04); none of them are historical CosmicPvP numbers.
 */
public record BloodLustSettings(double heal, double cooldownSeconds, int radius, double chanceMultiplier) {

    public static final BloodLustSettings DEFAULTS = new BloodLustSettings(0.5, 1.0, 3, 1.0);

    public long cooldownMillis() {
        return Math.max(0L, Math.round(this.cooldownSeconds * 1000.0));
    }

    /**
     * A 7x7 block footprint by default: the block deltas must be within the
     * radius on every axis, so radius 3 includes the exact 3-block edge.
     */
    public boolean withinFootprint(final int dx, final int dy, final int dz) {
        return Math.abs(dx) <= this.radius && Math.abs(dy) <= this.radius && Math.abs(dz) <= this.radius;
    }

    /**
     * Claims one heal window for the wearer. Returns true only when the
     * cooldown has elapsed; concurrent claims for the same wearer are
     * serialized by the map.
     */
    public boolean claim(
            @NotNull final ConcurrentHashMap<UUID, Long> cooldowns,
            @NotNull final UUID wearer,
            final long nowMillis
    ) {
        final long window = cooldownMillis();
        final boolean[] claimed = {false};

        cooldowns.compute(wearer, (id, last) -> {
            if (last != null && nowMillis < last + window) return last;

            claimed[0] = true;

            return nowMillis;
        });

        return claimed[0];
    }

    /**
     * Whether the cooldown has already elapsed for this wearer. Claims nothing: a wearer
     * still inside the window must not roll the chance or fire the use event.
     */
    public boolean ready(
            @NotNull final ConcurrentHashMap<UUID, Long> cooldowns,
            @NotNull final UUID wearer,
            final long nowMillis
    ) {
        final Long last = cooldowns.get(wearer);

        return last == null || nowMillis - last >= cooldownMillis();
    }

    public static BloodLustSettings validated(
            final double heal,
            final double cooldownSeconds,
            final int radius,
            final double chanceMultiplier,
            final Consumer<String> warn
    ) {
        final BloodLustSettings fallback = DEFAULTS;

        final double healValue = number("Heal", heal, fallback.heal(), 0.0, 20.0, warn);
        final double cooldown = number("Cooldown-Seconds", cooldownSeconds, fallback.cooldownSeconds(), 0.0, 60.0, warn);
        final double multiplier = number("Chance-Multiplier", chanceMultiplier, fallback.chanceMultiplier(), 0.0, 10.0, warn);

        int blocks = radius;

        if (blocks < 0 || blocks > 16) {
            warnFallback("Radius", String.valueOf(radius), String.valueOf(fallback.radius()), warn);
            blocks = fallback.radius();
        }

        return new BloodLustSettings(healValue, cooldown, blocks, multiplier);
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
            warn.accept("Invalid CosmicEnchants.BloodLust." + key + " value '" + value + "'; using " + fallback + ".");
        }
    }
}
