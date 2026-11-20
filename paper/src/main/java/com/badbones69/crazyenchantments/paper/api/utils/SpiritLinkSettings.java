package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public record SpiritLinkSettings(double radius, double baseHeal, double healPerLevel, double cooldownSeconds) {

    public static final SpiritLinkSettings DEFAULTS = new SpiritLinkSettings(6.0, 1.0, 0.25, 2.0);

    public SpiritLinkSettings {
        radius = TuningConfig.BowValues.checkedNumber(radius, 0.5, 16.0);
        baseHeal = TuningConfig.BowValues.checkedNumber(baseHeal, 0.0, 100.0);
        healPerLevel = TuningConfig.BowValues.checkedNumber(healPerLevel, 0.0, 100.0);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.05, 600.0);
    }

    public double heal(final int level) {
        return level < 1 ? 0.0 : this.baseHeal + this.healPerLevel * (Math.min(level, 5) - 1);
    }

    public long cooldownNanos() {
        return Math.round(this.cooldownSeconds * 1_000_000_000.0);
    }

    /**
     * Whether the cooldown has already elapsed. Claims nothing: an enchant on cooldown
     * must neither roll nor fire its use event, and the claim happens only after the
     * roll accepted the proc.
     */
    public boolean ready(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        final Long last = cooldowns.get(wearer);

        return last == null || now - last >= cooldownNanos();
    }

    public boolean claim(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        return claim(cooldowns, wearer, now, this.cooldownSeconds);
    }

    static boolean claim(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer,
                         final long now, final double seconds) {
        final long window = Math.round(seconds * 1_000_000_000.0);
        final boolean[] claimed = {false};
        cooldowns.compute(wearer, (id, last) -> {
            if (last != null && now - last < window) return last;
            claimed[0] = true;
            return now;
        });
        // ponytail: prune once per proc to keep cooldowns across reconnects without expiry tasks.
        if (claimed[0]) cooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= window);
        return claimed[0];
    }
}
