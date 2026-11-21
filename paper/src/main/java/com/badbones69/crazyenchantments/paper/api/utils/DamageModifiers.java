package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Shared damage-modifier rules:
 * <ul>
 *     <li>incoming reductions add as percentage points across worn pieces, are
 *     applied once per hit and are capped;</li>
 *     <li>the reduction class is exclusive: an arrow hit is arrow damage only,
 *     and the attacker's hand is only consulted for direct melee;</li>
 *     <li>outgoing multipliers from different enchants multiply;</li>
 *     <li>Deathbringer contributes one fixed multiplier no matter how many
 *     pieces proc;</li>
 *     <li>the bonus a hit may gain is uncapped unless the operator configures
 *     a cap, and the result is rounded to 0.01.</li>
 * </ul>
 */
public final class DamageModifiers {

    /** Upper bound accepted for a configured bonus cap. A cap of 0 means no cap. */
    public static final double MAX_CONFIGURED_BONUS_HP = 100.0;

    // A Bukkit damage event is dispatched on one thread. Weak keys cover events that
    // are cancelled or never reach a cleanup handler, without a cross-region lock.
    private static final ThreadLocal<Map<EntityDamageEvent, OutgoingBonus>> OUTGOING =
            ThreadLocal.withInitial(WeakHashMap::new);

    private record OutgoingBonus(double baseDamage, double limit, double accepted) {}

    private DamageModifiers() {}

    /**
     * Adds up to {@code requestedBonus} HP to the current event damage and returns
     * how much was added. Call on the event's owning thread, passing the damage
     * entering your handler as {@code baseDamage}: the first call fixes that
     * baseline for the event. Later reductions don't refund budget. Pass zero if
     * the first handler needs to establish the baseline before applying a
     * reduction; don't apply the returned amount a second time. A cap applies
     * only when {@code Bonus-Damage-Cap-HP} is above zero; it ships disabled.
     */
    public static double applyOutgoingBonus(final EntityDamageEvent event, final double baseDamage,
                                            final double requestedBonus) {
        final double current = event.getDamage();
        if (event.isCancelled() || !(baseDamage >= 0.0) || !Double.isFinite(baseDamage)
                || !(current >= 0.0) || !Double.isFinite(current)
                || !(requestedBonus >= 0.0) || !Double.isFinite(requestedBonus)) return 0.0;

        final double configuredLimit = TuningConfig.damageModifiers().bonusDamageCapHp();

        // A cap of 0 (the shipped default) leaves outgoing bonuses unlimited.
        if (!(configuredLimit > 0.0)) {
            final double updated = current + requestedBonus;

            if (!Double.isFinite(updated)) return 0.0;

            event.setDamage(updated);
            return requestedBonus;
        }

        final Map<EntityDamageEvent, OutgoingBonus> events = OUTGOING.get();
        final OutgoingBonus state = events.computeIfAbsent(event, ignored -> new OutgoingBonus(baseDamage,
                configuredLimit, 0.0));
        final double ceiling = state.baseDamage() + state.limit();
        final double available = Math.min(state.limit() - state.accepted(), ceiling - current);
        final double bonus = Math.min(requestedBonus, available);
        if (!(bonus > 0.0)) return 0.0;

        final double updated = Math.min(current + bonus, ceiling);
        final double accepted = updated - current;
        if (!(accepted > 0.0)) return 0.0;

        event.setDamage(updated);
        events.put(event, new OutgoingBonus(state.baseDamage(), state.limit(), state.accepted() + accepted));
        return accepted;
    }

    public enum Reduction {
        HEAVY,
        ARMORED,
        TANK,
        NONE
    }

    /**
     * Percentage points one worn piece contributes at {@code level}. A piece
     * with no level, or a non-positive per-level value, contributes nothing.
     */
    public static double piecePP(final double perLevelPP, final int level) {
        if (perLevelPP <= 0.0 || level <= 0) return 0.0;

        return perLevelPP * level;
    }

    /**
     * Sums per-piece percentage points and clamps the total to
     * {@code [0, capPP]}. Negative and NaN inputs are ignored.
     */
    public static double sumPP(final double capPP, final double... perPiecePP) {
        double total = 0.0;

        for (final double pp : perPiecePP) {
            if (pp > 0.0 && !Double.isNaN(pp)) total += pp;
        }

        return Math.min(total, Math.max(0.0, capPP));
    }

    /**
     * Applies a summed reduction to one hit, exactly once. Zero or negative
     * damage and non-positive reductions leave the hit unchanged.
     */
    public static double reduceOnce(final double damage, final double reductionPP) {
        if (damage <= 0.0 || reductionPP <= 0.0) return damage;

        return damage * (1.0 - Math.min(reductionPP, 100.0) / 100.0);
    }

    /**
     * The one reduction class a hit may use. An arrow hit is arrow damage only,
     * so only direct melee consults the attacker's hand.
     */
    public static Reduction reductionFor(final boolean arrow, final boolean directMelee, final Supplier<String> mainHandMaterial) {
        if (arrow) return Reduction.HEAVY;
        if (!directMelee) return Reduction.NONE;

        final String material = mainHandMaterial.get();
        if (material.endsWith("_SWORD")) return Reduction.ARMORED;
        if (material.endsWith("_AXE")) return Reduction.TANK;

        return Reduction.NONE;
    }

    /**
     * Exact-percent roll for Deathbringer: a chance of 3 means rolls 0-2 pass
     * and roll 3 fails. {@code roll} is expected in {@code [0, 99]}.
     */
    public static boolean exactChance(final int chance, final int roll) {
        return roll >= 0 && roll < chance;
    }

    /**
     * Deathbringer's multiplier: any accepted proc gives the fixed multiplier,
     * and extra procs on the same hit never compound.
     */
    public static double deathbringerMultiplier(final int procs) {
        return procs > 0 ? 1.5 : 1.0;
    }

    /**
     * A bonus expressed in percentage points as an outgoing multiplier, e.g.
     * 24 pp becomes 1.24x.
     */
    public static double outgoingMultiplier(final double bonusPP) {
        return 1.0 + Math.max(0.0, bonusPP) / 100.0;
    }

    /**
     * Multiplies the multipliers of different enchants. Non-positive and NaN
     * values are treated as neutral (1.0).
     */
    public static double multiply(final double... multipliers) {
        double result = 1.0;

        for (final double multiplier : multipliers) {
            if (multiplier > 0.0 && !Double.isNaN(multiplier)) result *= multiplier;
        }

        return result;
    }

    /**
     * Applies an outgoing multiplier and caps the bonus over the original hit.
     * The multiplier can only raise damage; it never lowers it.
     *
     * <p>Legacy pre-event helper: it caps one lane's bonus on its own, so two lanes
     * that both use it can exceed the CE cap. New code must route the bonus through
     * {@link #applyOutgoingBonus} instead, which holds one budget per event.</p>
     */
    public static double capBonus(final double damage, final double multiplier, final double bonusCapHp) {
        if (damage <= 0.0) return damage;

        final double boosted = damage * Math.max(1.0, multiplier);

        return Math.min(boosted, damage + Math.max(0.0, bonusCapHp));
    }

    /**
     * Vanilla Strength adds 3 HP per level to melee damage. Returns the hit with
     * the excess over {@code perLevel} removed, clamped at zero, so a per-level
     * value of 1 takes 6 off a level III hit. A {@code perLevel} outside
     * {@code [0, 3]} falls back to the shipped 1.
     */
    public static double strengthDamage(final double damage, final int level, final double perLevel) {
        if (!(damage > 0.0) || level <= 0) return damage;

        final double configured = perLevel >= 0.0 && perLevel <= 3.0 ? perLevel : 1.0;

        return Math.max(0.0, damage - (3.0 - configured) * level);
    }

    /**
     * Rounds a modified hit to two decimals, leaving untouched hits alone.
     */
    public static double roundDamage(final double original, final double damage) {
        if (original == damage || !Double.isFinite(damage) || Math.ulp(damage) >= 0.01) return damage;

        return Math.round(damage * 100.0) / 100.0;
    }
}
