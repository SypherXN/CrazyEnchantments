package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.Map;

/**
 * Validated Sniper (headshot) tuning. The enchant is always-on, so nothing here
 * is a chance: a fully drawn arrow that lands in the top of the victim's
 * bounding box is multiplied. The extra damage shares the optional
 * {@link DamageModifierSettings#bonusDamageCapHp()} cap, which is disabled by
 * default.
 */
public record SniperSettings(List<Double> multipliers, double minDrawForce, double headFraction, double forceTtlSeconds) {

    public static final SniperSettings DEFAULTS = new SniperSettings(
            List.of(1.5, 2.0, 2.5, 3.0, 3.5),
            0.9,
            0.25,
            60.0
    );

    public SniperSettings {
        multipliers = TuningConfig.BowValues.checkedTable(multipliers, 5, 1.0, 100.0);
        minDrawForce = TuningConfig.BowValues.checkedNumber(minDrawForce, 0.0, 1.0);
        headFraction = TuningConfig.BowValues.checkedNumber(headFraction, 0.01, 1.0);
        forceTtlSeconds = TuningConfig.BowValues.checkedNumber(forceTtlSeconds, 0.001, 600.0);
    }

    /**
     * Level I-V multiplier; a level past the table uses the last entry, and an
     * unusable level is neutral so a stray 0 never x0.
     */
    public double multiplier(final int level) {
        if (level < 1) return 1.0;

        return this.multipliers.get(Math.clamp(level, 1, this.multipliers.size()) - 1);
    }

    /**
     * A bow draw under the threshold is a weak shot and never headshots. NaN
     * force is never enough.
     */
    public boolean fullyDrawn(final double force) {
        return force >= this.minDrawForce;
    }

    /**
     * Top {@code headFraction} of the victim's box, and inside the box at all.
     * {@code impactY} is the arrow's Y where the projectile hit was reported,
     * in the same world coordinates as the box.
     */
    public boolean headshot(final double impactY, final double boxMinY, final double boxHeight) {
        if (boxHeight <= 0.0 || Double.isNaN(impactY)) return false;
        if (impactY < boxMinY || impactY > boxMinY + boxHeight) return false;

        return impactY >= boxMinY + (1.0 - this.headFraction) * boxHeight;
    }

    public long forceTtlMillis() {
        return Math.round(this.forceTtlSeconds * 1_000.0);
    }

    /**
     * Applies one projectile impact to the captured draw. A living victim's
     * damage event is still to come, so the entry stays and gains the impact
     * point the headshot test needs. A block or a non-living entity can never
     * produce that damage event, so the entry is dropped there instead of
     * waiting out the TTL.
     */
    public static <K> void recordImpact(final Map<K, Shot> shots, final K key, final boolean livingVictim,
                                        final double impactY) {
        if (key == null) return;

        if (!livingVictim) {
            shots.remove(key);

            return;
        }

        shots.computeIfPresent(key, (id, shot) -> shot.withImpact(impactY));
    }

    /**
     * The draw force captured at shoot time plus the impact point captured when
     * the projectile reports an entity hit. The entry is consumed on the damage
     * event, removed for a block hit, and expires by age otherwise, so a quit or
     * a despawn cannot leak one.
     */
    public record Shot(float force, long firedAtMillis, double impactY) {

        public static Shot inFlight(final float force, final long firedAtMillis) {
            return new Shot(force, firedAtMillis, Double.NaN);
        }

        public Shot withImpact(final double impactY) {
            return new Shot(this.force, this.firedAtMillis, impactY);
        }

        public boolean impactKnown() {
            return !Double.isNaN(this.impactY);
        }

        public boolean expired(final long nowMillis, final long ttlMillis) {
            return nowMillis - this.firedAtMillis >= ttlMillis;
        }
    }
}
