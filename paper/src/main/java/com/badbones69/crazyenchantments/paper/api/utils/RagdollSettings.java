package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ragdoll: always-on armor enchant (no chance system). Pure data and math, no
 * Bukkit types.
 *
 * <p>A direct melee hit pushes the wearer horizontally away from the damager
 * with {@code horizontal[level - 1]} velocity plus {@code vertical} upward, at
 * most once per {@code cooldownSeconds} for each wearer. Projectiles, thorns,
 * explosions, allies and self hits never push (the listener filters those).
 * The highest worn level counts; pieces do not add.</p>
 */
public record RagdollSettings(List<Double> horizontal, double vertical, double cooldownSeconds) {

    public static final int MAX_LEVEL = 4;

    public static final RagdollSettings DEFAULTS = new RagdollSettings(List.of(0.35, 0.45, 0.55, 0.65), 0.1, 1.0);

    public RagdollSettings {
        horizontal = TuningConfig.BowValues.checkedTable(horizontal, MAX_LEVEL, 0.0, 10.0);
        vertical = TuningConfig.BowValues.checkedNumber(vertical, 0.0, 10.0);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.0, 600.0);
    }

    public long cooldownNanos() {
        return Math.round(this.cooldownSeconds * 1_000_000_000.0);
    }

    /**
     * Horizontal velocity magnitude for {@code level}; 0 for no level.
     */
    public double horizontalVelocity(final int level) {
        if (level < 1) return 0.0;

        return this.horizontal.get(Math.min(level, MAX_LEVEL) - 1);
    }

    /**
     * The push for a wearer standing at {@code (dx, dz)} from the damager:
     * the horizontal unit direction away from the damager scaled by the level
     * velocity, with the fixed vertical part. Overlapping entities get no push
     * instead of a NaN vector.
     */
    public Push push(final int level, final double dx, final double dz) {
        final double length = Math.hypot(dx, dz);

        if (level < 1 || !(length > 1.0e-6)) return Push.NONE;

        final double speed = horizontalVelocity(level);

        return new Push(dx / length * speed, this.vertical, dz / length * speed);
    }

    /**
     * Claims the wearer's cooldown window. True only when the cooldown has
     * elapsed (inclusive at the boundary); the claim is atomic per wearer.
     */
    public boolean claim(final Map<UUID, Long> cooldowns, final UUID wearer, final long nowNanos) {
        final long window = cooldownNanos();
        final boolean[] claimed = {false};

        cooldowns.compute(wearer, (id, last) -> {
            if (last != null && nowNanos - last < window) return last;

            claimed[0] = true;

            return nowNanos;
        });

        return claimed[0];
    }

    public record Push(double x, double y, double z) {

        public static final Push NONE = new Push(0.0, 0.0, 0.0);

        public boolean isNone() {
            return this.x == 0.0 && this.y == 0.0 && this.z == 0.0;
        }
    }
}
