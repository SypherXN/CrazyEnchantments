package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Rocket Escape: always-on boots enchant, no chance system. When the wearer is
 * hit at or below {@code triggerPp} percent of max health they are launched
 * straight up, then the enchant rests for a per-level cooldown.
 *
 * <p>DOC: Cosmic's Rocket Escape blasts the wearer upward at low HP. FORK:
 * trigger at 30% of max health, vertical velocity 0.9 / 1.0 / 1.1 (about 4 to 6
 * blocks) and a 20 / 15 / 10 s cooldown at I / II / III. The cooldown starts
 * when a launch is accepted, and is over once {@code elapsed >= cooldown}.</p>
 */
public record RocketEscapeSettings(double triggerPp, List<Double> velocity, List<Double> cooldownSeconds) {

    public static final int MAX_LEVEL = 3;

    public static final RocketEscapeSettings DEFAULTS = new RocketEscapeSettings(
            30.0, List.of(0.9, 1.0, 1.1), List.of(20.0, 15.0, 10.0));

    public RocketEscapeSettings {
        triggerPp = TuningConfig.BowValues.checkedNumber(triggerPp, 0.0, 100.0);
        velocity = TuningConfig.BowValues.checkedTable(velocity, MAX_LEVEL, 0.0, 5.0);
        cooldownSeconds = TuningConfig.BowValues.checkedTable(cooldownSeconds, MAX_LEVEL, 0.0, 3_600.0);
    }

    /** Health at or below the trigger cut; a dead or max-less wearer never triggers. */
    public boolean triggers(final double health, final double maxHealth) {
        if (!(maxHealth > 0.0) || !(health > 0.0)) return false;

        return health <= maxHealth * this.triggerPp / 100.0;
    }

    public double velocity(final int level) {
        return this.velocity.get(index(level));
    }

    public long cooldownNanos(final int level) {
        return Math.round(this.cooldownSeconds.get(index(level)) * 1_000_000_000.0);
    }

    /** True with no previous launch, or once the full cooldown has elapsed. */
    public boolean ready(final Long lastLaunchNanos, final long nowNanos, final int level) {
        return lastLaunchNanos == null || nowNanos - lastLaunchNanos >= cooldownNanos(level);
    }

    private static int index(final int level) {
        return Math.clamp(level, 1, MAX_LEVEL) - 1;
    }
}
