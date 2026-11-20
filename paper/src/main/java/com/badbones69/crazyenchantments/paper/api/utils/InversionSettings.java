package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated Inversion tuning. An accepted proc cancels the incoming direct
 * melee hit and heals the wearer by {@code healByLevel[level - 1]}, at most
 * once per {@code cooldownSeconds} per wearer. Pure data and math, no Bukkit
 * types.
 */
public record InversionSettings(List<Double> healByLevel, double cooldownSeconds) {

    public static final int MAX_LEVEL = 4;

    public static final InversionSettings DEFAULTS = new InversionSettings(List.of(1.0, 2.0, 2.0, 3.0), 2.0);

    public InversionSettings {
        healByLevel = checkHeals(healByLevel);
        cooldownSeconds = checkCooldown(cooldownSeconds);
    }

    /**
     * HP healed at {@code level}. A level past the list uses the last entry; a
     * level below I heals nothing.
     */
    public double heal(final int level) {
        if (level < 1) return 0.0;

        return this.healByLevel.get(Math.min(level, this.healByLevel.size()) - 1);
    }

    /**
     * The health after a heal, never above max health. A dead wearer or one
     * already at full health is left alone.
     */
    public double healedHealth(final int level, final double health, final double maxHealth) {
        if (!(maxHealth > 0.0) || health <= 0.0 || health >= maxHealth) return health;

        return Math.min(maxHealth, health + heal(level));
    }

    public long cooldownMillis() {
        return Math.max(0L, Math.round(this.cooldownSeconds * 1000.0));
    }

    /**
     * Whether the wearer's last proc is still cooling down.
     */
    public boolean onCooldown(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID wearer, final long nowMillis) {
        final Long last = cooldowns.get(wearer);

        return last != null && nowMillis < last + cooldownMillis();
    }

    /**
     * Claims the wearer's cooldown window. Returns true only when the cooldown
     * had elapsed; concurrent claims are serialized by the map.
     */
    public boolean claim(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID wearer, final long nowMillis) {
        final long window = cooldownMillis();
        final boolean[] claimed = {false};

        cooldowns.compute(wearer, (id, last) -> {
            if (last != null && nowMillis < last + window) return last;

            claimed[0] = true;

            return nowMillis;
        });

        return claimed[0];
    }

    public static InversionSettings validated(
            final List<Double> healByLevel,
            final double cooldownSeconds,
            final Consumer<String> warn
    ) {
        final InversionSettings fallback = DEFAULTS;

        return new InversionSettings(
                orDefault(() -> checkHeals(healByLevel), fallback.healByLevel(), warn),
                orDefault(() -> checkCooldown(cooldownSeconds), fallback.cooldownSeconds(), warn)
        );
    }

    private static List<Double> checkHeals(final List<Double> values) {
        if (values == null || values.isEmpty() || values.size() > 16) throw bad("Heal", values);

        for (final Double value : values) {
            if (value == null || !(value >= 0.0 && value <= 20.0)) throw bad("Heal", values);
        }

        return List.copyOf(values);
    }

    private static double checkCooldown(final double value) {
        if (!(value >= 0.0 && value <= 600.0)) throw bad("Cooldown-Seconds", value);

        return value;
    }

    private static IllegalArgumentException bad(final String key, final Object value) {
        return new IllegalArgumentException("Invalid CosmicEnchants.Inversion." + key + " value '" + value + "'");
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
