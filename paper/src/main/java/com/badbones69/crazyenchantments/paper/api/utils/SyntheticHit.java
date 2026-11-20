package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * One attributed, cancellable synthetic damage call with a scoped provenance
 * marker. The marker is thread-local, so it never leaks across threads or
 * ticks, and it replaces {@link EventUtils}' global mutable sets for new code.
 *
 * <p>CE combat handlers normally ignore a hit while {@link #isSynthetic()} is true. This
 * class never changes the victim's vanilla invulnerability ticks; the normal
 * damage path (armor, protection plugins, attribution) stays intact. Callers
 * must run on the victim's owning thread.</p>
 */
public final class SyntheticHit {

    private enum Provenance { COMBAT, BLAST }

    private static final ThreadLocal<Provenance> ACTIVE = new ThreadLocal<>();

    private SyntheticHit() {}

    /**
     * Legacy name: true for any scoped synthetic hit, including blasts.
     * New code must use {@link #isSynthetic()} for the recursion guard.
     */
    public static boolean isBleedTick() {
        return isSynthetic();
    }

    public static boolean isSynthetic() {
        return ACTIVE.get() != null;
    }

    public static boolean isBlast() {
        return ACTIVE.get() == Provenance.BLAST;
    }

    /**
     * Damages {@code victim} through the normal attributed path, so armor and
     * protection plugins apply and the source receives kill credit.
     *
     * @return an estimate that the hit landed. Paper's damage API returns void;
     * a new uncancelled event attributed to the source and a net loss of health
     * plus absorption are required. Absorption loss alone counts. A hit with no net loss is
     * reported as not landed, and a cancellation after the read cannot be seen.
     */
    public static boolean damage(final Player source, final LivingEntity victim, final double amount) {
        return damage(source, victim, amount, Provenance.COMBAT);
    }

    /** Like {@link #damage}, but the scoped hit is identified as blast damage. */
    public static boolean damageBlast(final Player source, final LivingEntity victim, final double amount) {
        return damage(source, victim, amount, Provenance.BLAST);
    }

    private static boolean damage(final Player source, final LivingEntity victim, final double amount,
                                  final Provenance provenance) {
        if (source == null || victim == null || !(amount > 0.0) || !Double.isFinite(amount) || isSynthetic()) return false;
        if (!Bukkit.isOwnedByCurrentRegion(victim)) return false;
        if (victim.isDead() || !victim.isValid()) return false;

        final EntityDamageEvent previous = victim.getLastDamageCause();
        final double before = victim.getHealth() + victim.getAbsorptionAmount();
        ACTIVE.set(provenance);

        try {
            victim.damage(amount, source);
        } finally {
            ACTIVE.remove();
        }

        // A protection or death listener can move the victim during the call.
        if (!Bukkit.isOwnedByCurrentRegion(victim)) return false;

        final EntityDamageEvent observed = victim.getLastDamageCause();
        return observed instanceof EntityDamageByEntityEvent attributed && attributed.getDamager() == source
                && landed(before, victim.getHealth() + victim.getAbsorptionAmount(), previous, observed);
    }

    static boolean landed(final double before, final double after, final EntityDamageEvent previous,
                          final EntityDamageEvent observed) {
        return observed != null && observed != previous && !observed.isCancelled() && after < before;
    }
}
