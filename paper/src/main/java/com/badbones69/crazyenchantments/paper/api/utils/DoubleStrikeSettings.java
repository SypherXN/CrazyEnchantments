package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Validated Double Strike tuning. An accepted proc deals a second damage
 * instance equal to the first hit's pre-multiplier damage, at most once per
 * {@code cooldownSeconds} per attacker. Pure data and math, no Bukkit types.
 */
public record DoubleStrikeSettings(double cooldownSeconds) {

    public static final DoubleStrikeSettings DEFAULTS = new DoubleStrikeSettings(1.0);

    public DoubleStrikeSettings {
        if (!(cooldownSeconds >= 0.0 && cooldownSeconds <= 600.0)) {
            throw new IllegalArgumentException("Invalid CosmicEnchants.DoubleStrike.Cooldown-Seconds value '" + cooldownSeconds + "'");
        }
    }

    public long cooldownMillis() {
        return Math.max(0L, Math.round(this.cooldownSeconds * 1000.0));
    }

    /**
     * Whether the attacker's last proc is still cooling down.
     */
    public boolean onCooldown(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID attacker, final long nowMillis) {
        final Long last = cooldowns.get(attacker);

        return last != null && nowMillis < last + cooldownMillis();
    }

    /**
     * Claims the attacker's cooldown window. Returns true only when the
     * cooldown had elapsed; concurrent claims are serialized by the map.
     */
    public boolean claim(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID attacker, final long nowMillis) {
        final long window = cooldownMillis();
        final boolean[] claimed = {false};

        cooldowns.compute(attacker, (id, last) -> {
            if (last != null && nowMillis < last + window) return last;

            claimed[0] = true;

            return nowMillis;
        });

        return claimed[0];
    }

    public static DoubleStrikeSettings validated(final double cooldownSeconds, final Consumer<String> warn) {
        try {
            return new DoubleStrikeSettings(cooldownSeconds);
        } catch (final IllegalArgumentException exception) {
            if (warn != null) warn.accept(exception.getMessage() + "; using " + DEFAULTS.cooldownSeconds() + ".");

            return DEFAULTS;
        }
    }
}
