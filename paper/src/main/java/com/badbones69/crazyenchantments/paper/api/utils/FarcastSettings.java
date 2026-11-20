package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validated Farcast tuning. The chance curve stays in Enchantments.yml
 * (Base 8, Increase 4) and is rolled by the holder when a melee attacker hits
 * them; a holder at or below the low-health cut rolls at an increased chance.
 */
public record FarcastSettings(double pushStrength, double cooldownSeconds,
                              double lowHealthPp, double lowHealthChanceMultiplier) {

    public static final FarcastSettings DEFAULTS = new FarcastSettings(2.0, 3.0, 40.0, 1.5);

    public FarcastSettings {
        pushStrength = TuningConfig.BowValues.checkedNumber(pushStrength, 0.0, 100.0);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.0, 600.0);
        lowHealthPp = TuningConfig.BowValues.checkedNumber(lowHealthPp, 0.0, 100.0);
        lowHealthChanceMultiplier = TuningConfig.BowValues.checkedNumber(lowHealthChanceMultiplier, 0.0, 10.0);
    }

    public long cooldownMillis() {
        return Math.round(this.cooldownSeconds * 1_000.0);
    }

    /**
     * Inclusive at the cut, so exactly 40% counts as low health.
     */
    public boolean lowHealth(final double health, final double maxHealth) {
        if (maxHealth <= 0.0 || Double.isNaN(health)) return false;

        return health / maxHealth * 100.0 <= this.lowHealthPp;
    }

    public double chanceMultiplier(final double health, final double maxHealth) {
        return lowHealth(health, maxHealth) ? this.lowHealthChanceMultiplier : 1.0;
    }

    public boolean available(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID holder, final long nowMillis) {
        final Long last = cooldowns.get(holder);

        return last == null || nowMillis - last >= cooldownMillis();
    }

    public void claim(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID holder, final long nowMillis) {
        cooldowns.put(holder, nowMillis);
    }

    /**
     * Horizontal unit direction from the shooter to the attacker, scaled by the
     * push strength. Overlapping entities get no push instead of a NaN vector.
     */
    public Push push(final double dx, final double dz) {
        final double length = Math.hypot(dx, dz);

        if (!(length > 1.0e-6)) return new Push(0.0, 0.0);

        return new Push(dx / length * this.pushStrength, dz / length * this.pushStrength);
    }

    public record Push(double x, double z) {}
}
