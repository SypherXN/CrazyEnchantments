package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;

public record SpiritsSettings(double radius, double heal, double cadenceSeconds, double lifetimeSeconds,
                              double cooldownSeconds) {

    public static final SpiritsSettings DEFAULTS = new SpiritsSettings(4.0, 1.0, 2.0, 10.0, 30.0);

    public SpiritsSettings {
        radius = TuningConfig.BowValues.checkedNumber(radius, 0.5, 16.0);
        heal = TuningConfig.BowValues.checkedNumber(heal, 0.0, 100.0);
        cadenceSeconds = TuningConfig.BowValues.checkedNumber(cadenceSeconds, 0.05, 600.0);
        lifetimeSeconds = TuningConfig.BowValues.checkedNumber(lifetimeSeconds, 0.05, 600.0);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.05, 600.0);
    }

    public int blazeCount(final int level) {
        return level < 1 ? 0 : 1 + (Math.min(level, 10) - 1) / 3;
    }

    public long cadenceTicks() {
        return Math.round(this.cadenceSeconds * 20.0);
    }

    public long lifetimeTicks() {
        return Math.round(this.lifetimeSeconds * 20.0);
    }

    /**
     * Whether a damage source may summon or renew the support runs: not the
     * wearer, owned by the current region, and not an ally. The caller also
     * requires a living attacker, so one predicate covers Angelic, Spirit Link
     * and Spirits and none of them can answer TNT, end crystals or another
     * nonliving source.
     *
     * <p>The ally test is a lazy argument: a self-inflicted hit or an attacker
     * owned by another region short-circuits before it is called, so no remote
     * ally or location is ever read.</p>
     */
    public static boolean hostileSource(final boolean selfInflicted, final boolean sameRegion,
                                        final BooleanSupplier ally) {
        return !selfInflicted && sameRegion && !ally.getAsBoolean();
    }

    public boolean claim(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        return SpiritLinkSettings.claim(cooldowns, wearer, now, this.cooldownSeconds);
    }

    /**
     * Whether the cooldown has already elapsed. Claims nothing: an enchant on cooldown must
     * neither roll nor fire its use event, and the claim happens only after the roll accepted
     * the proc. Uses the same boundary as the claim.
     */
    public boolean ready(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        final Long last = cooldowns.get(wearer);

        return last == null || now - last >= Math.round(this.cooldownSeconds * 1_000_000_000.0);
    }
}
