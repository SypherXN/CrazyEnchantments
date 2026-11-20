package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Implants: always-on helmet enchant, no chance system. On a fixed interval the
 * wearer regains {@code healHp} HP and {@code hunger} food points, never past
 * max health or a full food bar.
 *
 * <p>DOC: Cosmic's Implants passively restores 1 HP and 1 hunger every few
 * seconds. FORK: the interval is 6 / 5 / 4 s at I / II / III, 1.0 HP and one
 * hunger point (half a drumstick) per proc.</p>
 */
public record ImplantsSettings(List<Double> intervalSeconds, double healHp, int hunger) {

    public static final int MAX_LEVEL = 3;

    /** The run polls once a second; an interval is measured in whole polls of this size. */
    public static final int POLL_TICKS = 20;

    public static final int MAX_FOOD = 20;

    public static final ImplantsSettings DEFAULTS = new ImplantsSettings(List.of(6.0, 5.0, 4.0), 1.0, 1);

    public ImplantsSettings {
        intervalSeconds = TuningConfig.BowValues.checkedTable(intervalSeconds, MAX_LEVEL, 1.0, 600.0);
        healHp = TuningConfig.BowValues.checkedNumber(healHp, 0.0, 20.0);
        hunger = (int) TuningConfig.BowValues.checkedNumber(hunger, 0.0, MAX_FOOD);
    }

    /** Seconds between procs at {@code level}; a level past the table uses the last entry. */
    public double intervalSeconds(final int level) {
        if (level < 1) return this.intervalSeconds.get(0);

        return this.intervalSeconds.get(Math.min(level, MAX_LEVEL) - 1);
    }

    public long intervalTicks(final int level) {
        return Math.max(1L, Math.round(intervalSeconds(level) * 20.0));
    }

    /** True while at least one of the two restores would change something. */
    public boolean needs(final double health, final double maxHealth, final int food) {
        return (this.healHp > 0.0 && health > 0.0 && health < maxHealth) || (this.hunger > 0 && food < MAX_FOOD);
    }

    /** Health after one proc, capped at max health; a dead wearer is never healed. */
    public double healed(final double health, final double maxHealth) {
        if (health <= 0.0 || health >= maxHealth) return health;

        return Math.min(maxHealth, health + this.healHp);
    }

    /** Food level after one proc, capped at a full bar. */
    public int fed(final int food) {
        return Math.min(MAX_FOOD, Math.max(0, food) + this.hunger);
    }
}
