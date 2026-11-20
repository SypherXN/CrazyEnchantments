package com.badbones69.crazyenchantments.paper.api.utils;

/**
 * Destruction: HELMET enchant with the chance in Enchantments.yml. Pure data
 * and math, no Bukkit types.
 *
 * <p>Every {@code cadenceSeconds} the worn helmet rolls its chance; on a proc
 * one random enemy within {@code radiusBlocks} takes {@code damageHp} HP. When
 * {@code applySlowness} is on the same target also gets Slowness I for
 * {@code slownessSeconds}. The wearer, allies, dead entities, entities owned by
 * another region and ignored entities are never targets.</p>
 */
public record DestructionSettings(double cadenceSeconds, double radiusBlocks, double damageHp,
                                  boolean applySlowness, double slownessSeconds) {

    public static final DestructionSettings DEFAULTS = new DestructionSettings(3.0, 4.0, 1.5, false, 2.0);

    public DestructionSettings {
        cadenceSeconds = TuningConfig.BowValues.checkedNumber(cadenceSeconds, 0.05, 600.0);
        radiusBlocks = TuningConfig.BowValues.checkedNumber(radiusBlocks, 0.5, 16.0);
        damageHp = TuningConfig.BowValues.checkedNumber(damageHp, 0.0, 100.0);
        slownessSeconds = TuningConfig.BowValues.checkedNumber(slownessSeconds, 0.05, 600.0);
    }

    public long cadenceTicks() {
        return Math.max(1L, Math.round(this.cadenceSeconds * 20.0));
    }

    public int slownessTicks() {
        return (int) Math.max(1L, Math.round(this.slownessSeconds * 20.0));
    }

    /**
     * Whether one candidate is a legal target. {@code distanceSquared} is a real
     * distance only for an entity this region owns; a foreign entity must pass
     * {@code owned == false} and is rejected before its location is read.
     */
    public boolean mayHit(final boolean self, final boolean ally, final boolean dead, final boolean owned,
                          final boolean ignored, final double distanceSquared) {
        if (self || ally || dead || ignored || !owned) return false;

        return distanceSquared >= 0.0 && distanceSquared <= this.radiusBlocks * this.radiusBlocks;
    }
}
