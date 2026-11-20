package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Hand-off for the part of a landed bow hit that was folded explosion splash
 * rather than arrow damage.
 *
 * <h2>Contract</h2>
 * <ul>
 *     <li>{@link #fold(EntityDamageEvent, double, Runnable)} writes the folded
 *     amount for one event and runs the call that applies it to that existing
 *     projectile damage event.</li>
 *     <li>{@link #blastComponent(EntityDamageEvent)} returns the folded amount
 *     in HP for that exact event while, and after, the fold. A later listener
 *     in the same dispatch (for example an armor handler at
 *     {@code MONITOR}) can still read it. Any other event, and any event that
 *     was never folded, reads {@code 0.0}, so the call is safe for every
 *     {@code EntityDamageEvent}.</li>
 *     <li>The last folded value is retained on the calling thread instead of
 *     being cleared when the fold returns. The next fold on that thread
 *     overwrites it; the identity check stops the previous event from ever
 *     reading the new amount. A damage event is dispatched on one thread and
 *     is not reused, so one retained slot is enough and a stale read is
 *     impossible.</li>
 *     <li>The slot is thread-local: another thread sees nothing. It holds one
 *     event reference per thread until that thread folds again.</li>
 *     <li>Standalone splash is not announced here: a blast that is its own
 *     damage event is already marked by {@link SyntheticHit#damageBlast}.</li>
 * </ul>
 *
 * <p>Armor code that must treat the folded splash like an explosion calls
 * {@code BowBlastContext.blastComponent(event)} on the event object; it may do
 * so after the bow handler returns, anywhere in the same dispatch.</p>
 */
public final class BowBlastContext {

    private record Scope(EntityDamageEvent event, double blastHp) {}

    // Retained on purpose: a later listener in the same dispatch must still see
    // the amount after the fold returns. Overwritten by the next fold.
    private static final ThreadLocal<Scope> LAST_FOLD = new ThreadLocal<>();

    private BowBlastContext() {}

    /**
     * Records the folded splash for {@code event}, then runs {@code apply} once.
     * Nothing is cleared afterwards: the value stays readable for that event
     * identity until the next fold on this thread replaces it.
     */
    public static void fold(final EntityDamageEvent event, final double blastHp, final Runnable apply) {
        if (apply == null) return;

        if (event != null && blastHp > 0.0 && Double.isFinite(blastHp)) {
            LAST_FOLD.set(new Scope(event, blastHp));
        }

        apply.run();
    }

    /**
     * The folded splash in HP for {@code event}, or {@code 0.0} when this thread
     * last folded a different event, or never folded one.
     */
    public static double blastComponent(final EntityDamageEvent event) {
        final Scope scope = LAST_FOLD.get();

        return scope != null && scope.event() == event ? scope.blastHp() : 0.0;
    }
}
