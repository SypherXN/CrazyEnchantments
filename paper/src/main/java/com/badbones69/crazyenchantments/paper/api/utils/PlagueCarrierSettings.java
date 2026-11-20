package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * Plague Carrier: always-on LEGGINGS enchant (no chance system). Pure data and
 * math, no Bukkit types.
 *
 * <p>When the wearer's health is at {@code triggerPp} percent of max health or
 * below the enchant fires once: {@code creepers(level)} creepers are summoned and
 * each fakes an explosion after {@code fuseSeconds}. The wearer must climb back
 * strictly above {@code rearmPp} percent before it can fire again, and one
 * {@code cooldownSeconds} window is spent per wearer. Health is read on the
 * wearer's own tick, so equipping at low health or a max-health change triggers
 * it just like a hit does.</p>
 *
 * <p>Two protections survive a reconnect but are bounded. The poison fills a gap
 * only: when the target has any Poison of its own, nothing is added, overwritten
 * or extended. The "already triggered, waiting for recovery" latch keeps its
 * timestamp and expires after {@link #RECOVERY_TIMEOUT_NANOS ten minutes} without
 * a recovery, so a player can never pin it forever by disconnecting; a recovery
 * still requires the leggings to be worn.</p>
 */
public record PlagueCarrierSettings(double triggerPp, double rearmPp, double cooldownSeconds, double fuseSeconds,
                                    double explosionRadius, double explosionDamageHp, double poisonRadius,
                                    double poisonSeconds) {

    public static final int MAX_LEVEL = 8;

    /** Ticks between reads of the worn leggings; health is checked every tick. */
    public static final int REFRESH_TICKS = 10;

    /**
     * How long a trigger latch survives without a recovery. Ten minutes: long
     * enough that a reconnect cannot dodge the required heal, short enough that a
     * player who never recovers is not locked out forever.
     */
    public static final long RECOVERY_TIMEOUT_NANOS = 10L * 60L * 1_000_000_000L;

    public static final PlagueCarrierSettings DEFAULTS =
            new PlagueCarrierSettings(30.0, 50.0, 60.0, 4.0, 3.0, 4.0, 4.0, 4.0);

    public PlagueCarrierSettings {
        triggerPp = TuningConfig.BowValues.checkedNumber(triggerPp, 0.0, 100.0);
        rearmPp = TuningConfig.BowValues.checkedNumber(rearmPp, 0.0, 100.0);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.0, 600.0);
        fuseSeconds = TuningConfig.BowValues.checkedNumber(fuseSeconds, 0.05, 600.0);
        explosionRadius = TuningConfig.BowValues.checkedNumber(explosionRadius, 0.5, 16.0);
        explosionDamageHp = TuningConfig.BowValues.checkedNumber(explosionDamageHp, 0.0, 100.0);
        poisonRadius = TuningConfig.BowValues.checkedNumber(poisonRadius, 0.5, 16.0);
        poisonSeconds = TuningConfig.BowValues.checkedNumber(poisonSeconds, 0.05, 600.0);
    }

    /**
     * Creepers summoned at levels I-VIII: {@code [1,1,1,2,2,2,3,3]}.
     */
    public int creepers(final int level) {
        if (level < 1) return 0;

        return 1 + (Math.min(level, MAX_LEVEL) - 1) / 3;
    }

    /**
     * Whether the wearer's current health fires the enchant.
     */
    public boolean triggers(final double healthAfterHit, final double maxHealth) {
        if (!(maxHealth > 0.0)) return false;

        return healthAfterHit <= maxHealth * this.triggerPp / 100.0;
    }

    /**
     * Whether the wearer has healed strictly above the re-arm cut, so the
     * enchant may fire again. Exactly the cut is not enough: a wearer sitting on
     * 50% must have actually recovered past it.
     */
    public boolean rearmed(final double health, final double maxHealth) {
        if (!(maxHealth > 0.0)) return false;

        return health > maxHealth * this.rearmPp / 100.0;
    }

    /**
     * Whether a scheduled fuse may still detonate: the plugin is running, the
     * settings record is the current one (a reload builds a new record), the run
     * is still the wearer's registered run and was not retired, the creeper is
     * still alive, and the wearer is still on this region (a teleport out of the
     * region or a world change invalidates the burst). Anything else retires the
     * creeper instead of firing.
     */
    public static boolean mayDetonate(final boolean stopped, final boolean currentSettings, final boolean currentRun,
                                      final boolean retired, final boolean creeperValid, final boolean wearerPresent) {
        return !stopped && currentSettings && currentRun && !retired && creeperValid && wearerPresent;
    }

    /**
     * Whether an entity is inside the poison radius.
     */
    public boolean withinPoison(final double distanceSquared) {
        return distanceSquared >= 0.0 && distanceSquared <= this.poisonRadius * this.poisonRadius;
    }

    /**
     * Whether the burst may poison a target: only when the target has no Poison of
     * its own. Ownership of a potion effect cannot be proven, so an existing Poison
     * - another source's or a previous burst's - is never overwritten or extended.
     */
    public static boolean mayPoison(final boolean poisonPresent) {
        return !poisonPresent;
    }

    /**
     * Whether a trigger latch has outlived {@link #RECOVERY_TIMEOUT_NANOS} without a
     * recovery, and may therefore be forgotten.
     */
    public static boolean expired(final long spentAt, final long now) {
        return now - spentAt >= RECOVERY_TIMEOUT_NANOS;
    }

    public long fuseTicks() {
        return Math.max(1L, Math.round(this.fuseSeconds * 20.0));
    }

    public int poisonTicks() {
        return (int) Math.max(1L, Math.round(this.poisonSeconds * 20.0));
    }

    public boolean claim(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        return SpiritLinkSettings.claim(cooldowns, wearer, now, this.cooldownSeconds);
    }

    /**
     * Whether the cooldown has already elapsed. Claims nothing, so a wearer still inside the
     * window neither rolls nor fires the use event; the claim follows only on acceptance.
     */
    public boolean ready(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        final Long last = cooldowns.get(wearer);

        return last == null || now - last >= Math.round(this.cooldownSeconds * 1_000_000_000.0);
    }
}
