package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.Set;

/**
 * Metaphysical: always-on BOOTS enchant (no chance system). Pure data and
 * math, no Bukkit types.
 *
 * <p>When Slowness would be added to the wearer by another source it is ignored
 * with {@code resistPp[level - 1]} percent chance. Every other effect is left
 * alone, and a slow that is not resisted keeps its duration and amplifier
 * (this class never rewrites an effect, it only decides whether to cancel).
 * Self-inflicted effects are excluded: the wearer drinking a potion, milk and
 * commands, effect expiry, and the permanent effects CE's own armor enchants
 * put on the wearer, which are wrapped in {@link #runSelfInflicted}.</p>
 *
 * <p>Only the boots slot counts; other worn pieces never contribute.</p>
 */
public record MetaphysicalSettings(List<Double> resistPp) {

    public static final int MAX_LEVEL = 4;

    public static final MetaphysicalSettings DEFAULTS = new MetaphysicalSettings(List.of(30.0, 50.0, 70.0, 90.0));

    /**
     * {@code EntityPotionEffectEvent.Action} names that add or strengthen an
     * effect. CLEARED and REMOVED are never resisted.
     */
    private static final Set<String> APPLYING_ACTIONS = Set.of("ADDED", "CHANGED");

    /**
     * {@code EntityPotionEffectEvent.Cause} names that are the wearer's own
     * action, or that remove or expire an effect, and are therefore not
     * "another source". Everything else (plugins such as CE's Snare
     * and Pummel, tipped arrows, splash potions, clouds, ...) is resistible.
     */
    private static final Set<String> EXCLUDED_CAUSES = Set.of(
            "POTION_DRINK", "MILK", "COMMAND", "EXPIRATION", "DEATH", "TOTEM", "FOOD", "TURTLE_HELMET");

    private static final ThreadLocal<Integer> SELF_INFLICTED = ThreadLocal.withInitial(() -> 0);

    public MetaphysicalSettings {
        resistPp = TuningConfig.BowValues.checkedTable(resistPp, MAX_LEVEL, 0.0, 100.0);
    }

    /**
     * Percent chance that a slow is ignored; 0 for no level.
     */
    public double resistChancePp(final int level) {
        if (level < 1) return 0.0;

        return this.resistPp.get(Math.min(level, MAX_LEVEL) - 1);
    }

    public static boolean isApplyingAction(final String actionName) {
        return APPLYING_ACTIONS.contains(actionName);
    }

    public static boolean isExcludedCause(final String causeName) {
        return EXCLUDED_CAUSES.contains(causeName);
    }

    /**
     * Runs {@code action} with effects applied on this thread marked as the
     * wearer's own, so Metaphysical never resists them. Nests safely and always
     * unmarks, even when {@code action} throws.
     */
    public static void runSelfInflicted(final Runnable action) {
        SELF_INFLICTED.set(SELF_INFLICTED.get() + 1);

        try {
            action.run();
        } finally {
            final int depth = SELF_INFLICTED.get() - 1;

            if (depth <= 0) SELF_INFLICTED.remove();
            else SELF_INFLICTED.set(depth);
        }
    }

    public static boolean isSelfInflicted() {
        return SELF_INFLICTED.get() > 0;
    }

    /**
     * Whether Metaphysical cancels this potion-effect event. {@code roll} is
     * uniform in {@code [0, 1)} and is only consulted for an eligible slow.
     *
     * @param slowness whether the new effect is Slowness (Mining Fatigue and
     *                 every other effect are never resisted)
     * @param action   the event action name
     * @param cause    the event cause name
     * @param level    the boots level, 0 when the wearer has none
     */
    public boolean resists(
            final boolean slowness, final String actionName, final String causeName, final boolean selfInflicted,
            final int level, final double roll
    ) {
        if (!slowness || level < 1 || selfInflicted) return false;
        if (!isApplyingAction(actionName) || isExcludedCause(causeName)) return false;

        return roll < resistChancePp(level) / 100.0;
    }
}
