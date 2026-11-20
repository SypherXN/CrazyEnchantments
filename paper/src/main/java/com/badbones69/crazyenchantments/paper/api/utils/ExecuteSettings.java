package com.badbones69.crazyenchantments.paper.api.utils;

/**
 * Execute: always-on sword enchant, no chance system. A direct melee hit on a
 * target at or below {@code thresholdPp} percent of its max health deals
 * {@code bonusPerLevelPp * level} percentage points more damage.
 *
 * <p>DOC: Cosmic's Execute adds damage against low-HP targets (max VII). The
 * under-6-HP rule belongs to the Heroic version and is NOT used here. FORK: the
 * threshold is 30% of the target's max health and the bonus is 3 pp per level,
 * +21% at VII. The bonus is added through the shared outgoing-bonus budget.</p>
 */
public record ExecuteSettings(double thresholdPp, double bonusPerLevelPp) {

    public static final int MAX_LEVEL = 7;

    public static final ExecuteSettings DEFAULTS = new ExecuteSettings(30.0, 3.0);

    public ExecuteSettings {
        thresholdPp = TuningConfig.BowValues.checkedNumber(thresholdPp, 0.0, 100.0);
        bonusPerLevelPp = TuningConfig.BowValues.checkedNumber(bonusPerLevelPp, 0.0, 100.0 / MAX_LEVEL);
    }

    /** Target health at or below the threshold; a dead or max-less target never counts. */
    public boolean belowThreshold(final double health, final double maxHealth) {
        if (!(maxHealth > 0.0) || !(health > 0.0)) return false;

        return health <= maxHealth * this.thresholdPp / 100.0;
    }

    /** Bonus as a share of the hit, e.g. 0.21 at level VII; zero above the threshold. */
    public double bonusFraction(final int level, final double health, final double maxHealth) {
        if (level < 1 || !belowThreshold(health, maxHealth)) return 0.0;

        return this.bonusPerLevelPp * Math.min(level, MAX_LEVEL) / 100.0;
    }
}
