package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated Rogue tuning and its rear-attack rule. Always-on (no chance
 * system, no souls, no currency). A melee hit from behind is multiplied by
 * {@code rearMultipliers[level - 1]} (1.10/1.15/1.20 at I-III), at most once
 * per {@code cooldownSeconds} per attacker. Pure data and math, no Bukkit types.
 *
 * <p>"Behind" is measured in the horizontal plane: the angle between the
 * victim's facing and the vector from the victim to the attacker must be at
 * least {@code rearAngleDegrees} (135 by default, a 90-degree rear cone). A
 * zero-length vector (looking straight up or down, or standing on the victim)
 * is never behind.</p>
 *
 * <p>The constructor throws {@link IllegalArgumentException} on a bad value;
 * {@link #validated} turns that into one warning per bad key plus the default.</p>
 */
public record RogueSettings(List<Double> rearMultipliers, double rearAngleDegrees, double cooldownSeconds) {

    public static final int MAX_LEVEL = 3;

    private static final double ZERO_LENGTH = 1.0e-9;

    private static final double ANGLE_EPSILON = 1.0e-9;

    public static final RogueSettings DEFAULTS = new RogueSettings(List.of(1.10, 1.15, 1.20), 135.0, 2.0);

    public RogueSettings {
        rearMultipliers = checkMultipliers(rearMultipliers);
        rearAngleDegrees = checkAngle(rearAngleDegrees);
        cooldownSeconds = checkCooldown(cooldownSeconds);
    }

    /**
     * Rear multiplier at {@code level}. A level past the list uses the last
     * entry; a level below I leaves the hit unchanged.
     */
    public double multiplier(final int level) {
        if (level < 1) return 1.0;

        return this.rearMultipliers.get(Math.min(level, this.rearMultipliers.size()) - 1);
    }

    /**
     * Whether the attacker is in the victim's rear cone. All four inputs are
     * horizontal components (the Y component is already dropped by the caller):
     * the victim's facing, then the victim-to-attacker vector.
     */
    public boolean isBehind(final double facingX, final double facingZ, final double toAttackerX, final double toAttackerZ) {
        final double facing = Math.hypot(facingX, facingZ);
        final double toAttacker = Math.hypot(toAttackerX, toAttackerZ);

        if (!(facing > ZERO_LENGTH) || !(toAttacker > ZERO_LENGTH) || Double.isInfinite(facing) || Double.isInfinite(toAttacker)) {
            return false;
        }

        final double cos = Math.clamp((facingX * toAttackerX + facingZ * toAttackerZ) / (facing * toAttacker), -1.0, 1.0);

        return Math.toDegrees(Math.acos(cos)) >= this.rearAngleDegrees - ANGLE_EPSILON;
    }

    public long cooldownMillis() {
        return Math.max(0L, Math.round(this.cooldownSeconds * 1000.0));
    }

    /**
     * Whether the attacker's last rear bonus is still cooling down.
     */
    public boolean onCooldown(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID attacker, final long nowMillis) {
        final Long last = cooldowns.get(attacker);

        return last != null && nowMillis < last + cooldownMillis();
    }

    /**
     * Claims the attacker's cooldown window. Returns true only when the
     * cooldown had elapsed; concurrent claims are serialized by the map.
     */
    public boolean claim(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID attacker, final long nowMillis) {
        final long window = cooldownMillis();
        final boolean[] claimed = {false};

        cooldowns.compute(attacker, (id, last) -> {
            if (last != null && nowMillis < last + window) return last;

            claimed[0] = true;

            return nowMillis;
        });

        return claimed[0];
    }

    public static RogueSettings validated(
            final List<Double> rearMultipliers,
            final double rearAngleDegrees,
            final double cooldownSeconds,
            final Consumer<String> warn
    ) {
        final RogueSettings fallback = DEFAULTS;

        return new RogueSettings(
                orDefault(() -> checkMultipliers(rearMultipliers), fallback.rearMultipliers(), warn),
                orDefault(() -> checkAngle(rearAngleDegrees), fallback.rearAngleDegrees(), warn),
                orDefault(() -> checkCooldown(cooldownSeconds), fallback.cooldownSeconds(), warn)
        );
    }

    private static List<Double> checkMultipliers(final List<Double> values) {
        if (values == null || values.isEmpty() || values.size() > 16) throw bad("Rear-Multipliers", values);

        for (final Double value : values) {
            if (value == null || !(value >= 1.0 && value <= 5.0)) throw bad("Rear-Multipliers", values);
        }

        return List.copyOf(values);
    }

    private static double checkAngle(final double value) {
        if (!(value > 0.0 && value <= 180.0)) throw bad("Rear-Angle-Degrees", value);

        return value;
    }

    private static double checkCooldown(final double value) {
        if (!(value >= 0.0 && value <= 600.0)) throw bad("Cooldown-Seconds", value);

        return value;
    }

    private static IllegalArgumentException bad(final String key, final Object value) {
        return new IllegalArgumentException("Invalid CosmicEnchants.Rogue." + key + " value '" + value + "'");
    }

    private static <T> T orDefault(final Supplier<T> check, final T fallback, final Consumer<String> warn) {
        try {
            return check.get();
        } catch (final IllegalArgumentException exception) {
            if (warn != null) warn.accept(exception.getMessage() + "; using " + fallback + ".");

            return fallback;
        }
    }
}
