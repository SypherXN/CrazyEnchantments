package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Rocket Escape: always-on boots enchant, no chance system. When the wearer is
 * hit at or below {@code triggerPp} percent of max health they are launched
 * straight up, then the enchant rests for a per-level cooldown.
 *
 * <p>DOC: Cosmic's Rocket Escape blasts the wearer upward at low HP. FORK:
 * trigger at 30% of max health, vertical velocity 9.0 / 10.0 / 11.0
 * and a 30 / 25 / 20 s cooldown at I / II / III. The cooldown starts
 * when a launch is accepted, and is over once {@code elapsed >= cooldown}.</p>
 *
 * <p>The launch is one {@code setVelocity} call, so it needs no packet-range
 * workaround: Paper 26.1.2 sends entity motion as a full-range Vec3
 * (MC-299627 removed the old ±3.9 packing clamp in 25w32a).</p>
 */
public record RocketEscapeSettings(double triggerPp, List<Double> velocity, List<Double> cooldownSeconds) {

    public static final int MAX_LEVEL = 3;

    /** Upper bound shared by the record and by the config validator in {@link TuningConfig}. */
    public static final double MAX_VELOCITY = 11.0;

    /** How long after a launch the wearer's own landing must not deal fall damage. */
    public static final long FALL_IMMUNITY_NANOS = 10_000_000_000L;

    public static final RocketEscapeSettings DEFAULTS = new RocketEscapeSettings(
            30.0, List.of(9.0, 10.0, 11.0), List.of(30.0, 25.0, 20.0));

    public RocketEscapeSettings {
        triggerPp = TuningConfig.BowValues.checkedNumber(triggerPp, 0.0, 100.0);
        velocity = TuningConfig.BowValues.checkedTable(velocity, MAX_LEVEL, 0.0, MAX_VELOCITY);
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

    /**
     * True while a launch's fall-immunity deadline is still in the future. An expired deadline
     * is never live, so the immunity cannot outlast the window it was granted for; the signed
     * difference keeps the comparison correct across {@link System#nanoTime()} wrap-around.
     */
    public static boolean fallImmunityLive(final Long untilNanos, final long nowNanos) {
        return untilNanos != null && nowNanos - untilNanos < 0;
    }

    private static int index(final int level) {
        return Math.clamp(level, 1, MAX_LEVEL) - 1;
    }
}
