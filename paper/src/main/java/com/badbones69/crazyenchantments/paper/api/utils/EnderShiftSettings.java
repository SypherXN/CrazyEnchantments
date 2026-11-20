package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Ender Shift: always-on helmet/boots enchant, no chance system. While the
 * wearer is at or below {@code triggerPp} percent of max health they keep Speed
 * and Health Boost. The effects end once health is above {@code clearPp}, the
 * gap between the two cuts being the hysteresis that stops a wearer hovering on
 * the line from flickering.
 *
 * <p>DOC: Cosmic's Ender Shift gives speed and a health boost at low HP.
 * FORK: trigger at 35% of max health, clear above 40%; Speed I at I-II and Speed
 * II at III; Health Boost I (+4 max HP); effects last 5 s and are refreshed
 * while the wearer stays low.</p>
 */
public record EnderShiftSettings(
        double triggerPp,
        double clearPp,
        List<Double> speedAmplifier,
        int healthBoostAmplifier,
        double effectSeconds
) {

    public static final int MAX_LEVEL = 3;

    /** Ticks between health reads and effect refreshes (one second). */
    public static final int REFRESH_TICKS = 20;

    public static final EnderShiftSettings DEFAULTS = new EnderShiftSettings(
            35.0, 40.0, List.of(0.0, 0.0, 1.0), 0, 5.0);

    public EnderShiftSettings {
        triggerPp = TuningConfig.BowValues.checkedNumber(triggerPp, 0.0, 100.0);
        clearPp = TuningConfig.BowValues.checkedNumber(clearPp, 0.0, 100.0);
        speedAmplifier = TuningConfig.BowValues.checkedTable(speedAmplifier, MAX_LEVEL, 0.0, 10.0);
        healthBoostAmplifier = (int) TuningConfig.BowValues.checkedNumber(healthBoostAmplifier, 0.0, 10.0);
        effectSeconds = TuningConfig.BowValues.checkedNumber(effectSeconds, 1.0, 600.0);

        if (clearPp < triggerPp) {
            throw new IllegalArgumentException("Clear-PP " + clearPp + " must not be below Trigger-PP " + triggerPp);
        }
    }

    /** Health at or below the trigger cut; a dead or max-less wearer is never low. */
    public boolean low(final double health, final double maxHealth) {
        if (!(maxHealth > 0.0) || !(health > 0.0)) return false;

        return health <= maxHealth * this.triggerPp / 100.0;
    }

    /** Health strictly above the clear cut ends the effects. */
    public boolean recovered(final double health, final double maxHealth) {
        if (!(maxHealth > 0.0)) return true;

        return health > maxHealth * this.clearPp / 100.0;
    }

    /**
     * The next state of the shift: a wearer who is not shifted becomes shifted
     * when low; a shifted wearer stays shifted until recovered.
     */
    public boolean nextActive(final boolean active, final double health, final double maxHealth) {
        if (active) return !recovered(health, maxHealth);

        return low(health, maxHealth);
    }

    public int speedAmplifier(final int level) {
        return (int) Math.round(this.speedAmplifier.get(Math.clamp(level, 1, MAX_LEVEL) - 1));
    }

    public int effectTicks() {
        return (int) Math.max(1L, Math.round(this.effectSeconds * 20.0));
    }
}
