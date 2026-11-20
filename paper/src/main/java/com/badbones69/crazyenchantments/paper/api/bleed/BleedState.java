package com.badbones69.crazyenchantments.paper.api.bleed;

import java.util.UUID;

/**
 * Bleed state for one victim. Pure Java so the stacking/lifetime math is unit
 * testable; the scheduler handle and identity live in {@link BleedSystem}.
 *
 * <p>Lifetime is measured in owner ticks, not wall clock, so it is
 * deterministic regardless of server load and independent of the cadence phase.</p>
 */
final class BleedState {

    private volatile UUID sourceId;
    private volatile int stacks;
    private volatile long expiresAt;

    UUID getSourceId() {
        return this.sourceId;
    }

    void setSource(final UUID sourceId) {
        this.sourceId = sourceId;
    }

    int getStacks() {
        return this.stacks;
    }

    /**
     * Adds stacks up to the cap and refreshes the lifetime on every proc.
     */
    void addStacks(final int amount, final int cap, final int durationTicks, final long ownerTick) {
        final int current = isExpired(ownerTick) ? 0 : this.stacks;
        this.stacks = Math.min(cap, current + Math.max(0, amount));
        this.expiresAt = ownerTick + Math.max(1, durationTicks);
    }

    boolean isExpired(final long ownerTick) {
        return ownerTick >= this.expiresAt;
    }

    double tickDamage(final BleedSettings settings) {
        return settings.tickDamage(this.stacks);
    }

    double slowFraction(final BleedSettings settings) {
        return -Math.min(this.stacks * settings.slowPerStackPp(), settings.slowCapPp()) / 100.0;
    }
}
