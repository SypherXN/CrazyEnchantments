package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated Blacksmith tuning. Chance-based (Enchantments.yml, Base 6, +3 per
 * level), and only against a player victim. A proc scales the hit by
 * {@code damageMultiplier} and repairs the attacker's most damaged worn armor
 * piece by {@code repairPoints[level - 1]} durability points (points, not
 * pieces). Pure data and math, no Bukkit types.
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning per bad key plus the default.</p>
 */
public record BlacksmithSettings(double damageMultiplier, List<Integer> repairPoints) {

    public static final int MAX_LEVEL = 5;

    public static final BlacksmithSettings DEFAULTS = new BlacksmithSettings(0.5, List.of(1, 1, 1, 2, 2));

    public BlacksmithSettings {
        damageMultiplier = checkMultiplier(damageMultiplier);
        repairPoints = checkPoints(repairPoints);
    }

    /**
     * Durability points repaired at {@code level}. A level past the list uses
     * the last entry; a level below I repairs nothing.
     */
    public int repairPoints(final int level) {
        if (level < 1) return 0;

        return this.repairPoints.get(Math.min(level, this.repairPoints.size()) - 1);
    }

    /**
     * The hit after the Blacksmith reduction. An unusable hit is returned untouched.
     */
    public double reduce(final double damage) {
        if (!(damage > 0.0) || Double.isInfinite(damage)) return damage;

        return damage * this.damageMultiplier;
    }

    /**
     * Index of the most damaged piece: the largest {@code max - durability}
     * wins, and the earliest index wins a tie. A piece with no max durability
     * (air, a non-damageable item) or no damage is never chosen.
     *
     * @param maxDurability  per-slot maximum durability, 0 for an empty slot
     * @param damageTaken    per-slot damage already taken (durability lost)
     * @return the chosen index, or -1 when no piece is damaged
     */
    public static int mostDamaged(final int[] maxDurability, final int[] damageTaken) {
        int best = -1;
        int bestMissing = 0;

        final int length = Math.min(maxDurability.length, damageTaken.length);

        for (int i = 0; i < length; i++) {
            if (maxDurability[i] <= 0) continue;

            final int missing = Math.min(damageTaken[i], maxDurability[i]);

            if (missing > bestMissing) {
                best = i;
                bestMissing = missing;
            }
        }

        return best;
    }

    /**
     * The damage a piece has left after repairing {@code points}; never below zero.
     */
    public static int repairedDamage(final int damageTaken, final int points) {
        if (points <= 0) return Math.max(0, damageTaken);

        return Math.max(0, damageTaken - points);
    }

    public static BlacksmithSettings validated(
            final double damageMultiplier,
            final List<Integer> repairPoints,
            final Consumer<String> warn
    ) {
        final BlacksmithSettings fallback = DEFAULTS;

        return new BlacksmithSettings(
                orDefault(() -> checkMultiplier(damageMultiplier), fallback.damageMultiplier(), warn),
                orDefault(() -> checkPoints(repairPoints), fallback.repairPoints(), warn)
        );
    }

    private static double checkMultiplier(final double value) {
        if (!(value > 0.0 && value <= 1.0)) throw bad("Damage-Multiplier", value);

        return value;
    }

    private static List<Integer> checkPoints(final List<Integer> values) {
        if (values == null || values.isEmpty() || values.size() > 16) throw bad("Repair-Points", values);

        for (final Integer value : values) {
            if (value == null || value < 0 || value > 1000) throw bad("Repair-Points", values);
        }

        return List.copyOf(values);
    }

    private static IllegalArgumentException bad(final String key, final Object value) {
        return new IllegalArgumentException("Invalid CosmicEnchants.Blacksmith." + key + " value '" + value + "'");
    }

    private static <T> T orDefault(final Supplier<T> check, final T fallback, final Consumer<String> warn) {
        try {
            return check.get();
        } catch (final IllegalArgumentException exception) {
            if (warn != null) warn.accept(exception.getMessage() + "; using " + fallback + ".");

            return fallback;
        }
    }
}
