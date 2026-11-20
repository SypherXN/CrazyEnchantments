package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Creeper Armor: always-on armor enchant (no chance system). Pure data and
 * math, no Bukkit types.
 *
 * <p>Explosion damage is cut by {@code reductionPp[level - 1]} percentage points
 * of the <em>final</em> explosion damage. Worn pieces add up, the total is
 * capped at 100 and the result is applied once per explosion hit. At the top
 * level the wearer also takes no explosion knockback and heals
 * {@code healHp} at most once per {@code healCooldownSeconds}.</p>
 */
public record CreeperArmorSettings(List<Double> reductionPp, double healHp, double healCooldownSeconds) {

    public static final int MAX_LEVEL = 3;

    /** The level at which knockback is cancelled and the wearer heals. */
    public static final int TOP_LEVEL = 3;

    public static final CreeperArmorSettings DEFAULTS = new CreeperArmorSettings(List.of(33.0, 67.0, 100.0), 1.0, 3.0);

    public CreeperArmorSettings {
        reductionPp = TuningConfig.BowValues.checkedTable(reductionPp, MAX_LEVEL, 0.0, 100.0);
        healHp = TuningConfig.BowValues.checkedNumber(healHp, 0.0, 100.0);
        healCooldownSeconds = TuningConfig.BowValues.checkedNumber(healCooldownSeconds, 0.001, 600.0);
    }

    /**
     * Percentage points one piece of {@code level} removes; 0 for no level.
     */
    public double piecePp(final int level) {
        if (level < 1) return 0.0;

        return this.reductionPp.get(Math.min(level, MAX_LEVEL) - 1);
    }

    /**
     * Sum over the worn pieces, capped at 100 so the damage never goes
     * negative.
     */
    public double totalPp(final int... pieceLevels) {
        double total = 0.0;

        for (final int level : pieceLevels) total += piecePp(level);

        return Math.min(100.0, total);
    }

    /**
     * The final damage left after {@code pp} percentage points are removed.
     */
    public static double reducedFinal(final double finalDamage, final double pp) {
        if (!(finalDamage > 0.0)) return 0.0;

        return finalDamage * (1.0 - Math.clamp(pp, 0.0, 100.0) / 100.0);
    }

    /**
     * Knockback is cancelled once any worn piece reaches the top level.
     */
    public boolean cancelsKnockback(final int highestLevel) {
        return highestLevel >= TOP_LEVEL;
    }

    public long healCooldownNanos() {
        return Math.round(this.healCooldownSeconds * 1_000_000_000.0);
    }

    /**
     * HP to heal for an explosion hit. Only the top level heals, never above
     * max health, and never twice inside the cooldown. The cooldown boundary is
     * inclusive: exactly {@code healCooldownSeconds} after the last heal the
     * next heal is allowed.
     */
    public double healAmount(
            final int highestLevel, final Long lastHealNanos, final long nowNanos, final double health, final double maxHealth
    ) {
        if (highestLevel < TOP_LEVEL || health <= 0.0 || health >= maxHealth) return 0.0;
        if (lastHealNanos != null && nowNanos - lastHealNanos < healCooldownNanos()) return 0.0;

        return Math.min(this.healHp, maxHealth - health);
    }

    /**
     * Finds the base damage whose resulting <em>final</em> damage is
     * {@code targetFinal}. Bukkit recomputes armor and absorption from the base
     * damage, and that is neither linear nor proportional, so scaling the base
     * by the wanted factor would not remove the wanted share. {@code finalOf}
     * maps a candidate base damage to the final damage it produces; it must be
     * non-decreasing. The returned base never yields more than the target.
     */
    public static double baseForFinal(final double base, final double targetFinal, final DoubleUnaryOperator finalOf) {
        if (!(targetFinal > 0.0) || !(base > 0.0)) return 0.0;
        if (finalOf.applyAsDouble(base) <= targetFinal) return base;

        double low = 0.0;
        double high = base;

        for (int i = 0; i < 40; i++) {
            final double mid = (low + high) / 2.0;

            if (finalOf.applyAsDouble(mid) <= targetFinal) low = mid;
            else high = mid;
        }

        return low;
    }
}
