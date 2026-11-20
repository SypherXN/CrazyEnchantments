package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

public record EnderWalkerSettings(double heal, double cooldownSeconds) {

    public static final EnderWalkerSettings DEFAULTS = new EnderWalkerSettings(0.5, 1.0);

    public double healAmount(
            final int level, final Long lastHealNanos, final long nowNanos, final double health, final double maxHealth
    ) {
        if (level < 4 || level > 5 || health <= 0.0 || health >= maxHealth) return 0.0;
        if (lastHealNanos != null && nowNanos - lastHealNanos < Math.round(this.cooldownSeconds * 1_000_000_000.0)) {
            return 0.0;
        }

        return Math.min(this.heal, maxHealth - health);
    }

    public static EnderWalkerSettings validated(final double heal, final double cooldownSeconds, final Consumer<String> warn) {
        return new EnderWalkerSettings(
                number("Heal", heal, DEFAULTS.heal(), 0.0, 20.0, warn),
                number("Cooldown-Seconds", cooldownSeconds, DEFAULTS.cooldownSeconds(), 0.001, 60.0, warn)
        );
    }

    private static double number(
            final String key, final double value, final double fallback, final double min, final double max,
            final Consumer<String> warn
    ) {
        if (value >= min && value <= max) return value;

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.EnderWalker." + key + " value '" + value + "'; using " + fallback + ".");
        }

        return fallback;
    }
}
