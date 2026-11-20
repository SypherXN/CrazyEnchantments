package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Vampire: sword enchant with the existing chance system (5% + 5% per level).
 * An accepted proc schedules one heal for the wielder {@code delaySeconds} after
 * the strike. Only one heal may be pending per wielder, and a heal pending when
 * the wielder dies is dropped.
 *
 * <p>DOC: Cosmic's Vampire heals up to 3 HP a few seconds after a strike. FORK:
 * 1 / 2 / 3 HP at I / II / III (III is the DOC maximum), 3 s delay.</p>
 */
public record VampireSettings(List<Double> healHp, double delaySeconds) {

    public static final int MAX_LEVEL = 3;

    /** The DOC ceiling: no level and no tuning may heal more than this per proc. */
    public static final double MAX_HEAL_HP = 3.0;

    public static final VampireSettings DEFAULTS = new VampireSettings(List.of(1.0, 2.0, 3.0), 3.0);

    public VampireSettings {
        healHp = TuningConfig.BowValues.checkedTable(healHp, MAX_LEVEL, 0.0, MAX_HEAL_HP);
        delaySeconds = TuningConfig.BowValues.checkedNumber(delaySeconds, 0.05, 60.0);
    }

    public double heal(final int level) {
        return this.healHp.get(Math.clamp(level, 1, MAX_LEVEL) - 1);
    }

    public long delayTicks() {
        return Math.max(1L, Math.round(this.delaySeconds * 20.0));
    }

    /** Health after the delayed heal, capped at max health; a dead wielder gets nothing. */
    public static double healed(final double health, final double maxHealth, final double heal) {
        if (!(health > 0.0) || health >= maxHealth) return health;

        return Math.min(maxHealth, health + Math.max(0.0, heal));
    }
}
