package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated Disintegrate tuning. A proc wears every worn enemy armor piece by
 * {@code max(1, floor(finalDamage / damagePerPoint))} points, capped at
 * {@code capPerPiece}. Pure data and math, no Bukkit types.
 */
public record DisintegrateSettings(double damagePerPoint, int capPerPiece) {

    public static final DisintegrateSettings DEFAULTS = new DisintegrateSettings(4.0, 4);

    public DisintegrateSettings {
        if (!(damagePerPoint > 0.0 && damagePerPoint <= 100.0)) {
            throw new IllegalArgumentException("Invalid CosmicEnchants.Disintegrate.Damage-Per-Point value '" + damagePerPoint + "'");
        }

        if (capPerPiece < 1 || capPerPiece > 20) {
            throw new IllegalArgumentException("Invalid CosmicEnchants.Disintegrate.Cap-Per-Piece value '" + capPerPiece + "'");
        }
    }

    /**
     * Durability points one armor piece loses for a hit of {@code finalDamage}.
     * A non-positive or non-finite hit wears nothing.
     */
    public int points(final double finalDamage) {
        if (!(finalDamage > 0.0) || !Double.isFinite(finalDamage)) return 0;

        return Math.min(this.capPerPiece, Math.max(1, (int) Math.floor(finalDamage / this.damagePerPoint)));
    }

    public static DisintegrateSettings validated(
            final double damagePerPoint,
            final int capPerPiece,
            final Consumer<String> warn
    ) {
        final DisintegrateSettings fallback = DEFAULTS;

        double points = damagePerPoint;

        if (!(damagePerPoint > 0.0 && damagePerPoint <= 100.0)) {
            warnFallback("Damage-Per-Point", String.valueOf(damagePerPoint), String.valueOf(fallback.damagePerPoint()), warn);
            points = fallback.damagePerPoint();
        }

        int cap = capPerPiece;

        if (cap < 1 || cap > 20) {
            warnFallback("Cap-Per-Piece", String.valueOf(capPerPiece), String.valueOf(fallback.capPerPiece()), warn);
            cap = fallback.capPerPiece();
        }

        return new DisintegrateSettings(points, cap);
    }

    private static void warnFallback(final String key, final String value, final String fallback, final Consumer<String> warn) {
        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Disintegrate." + key + " value '" + value + "'; using " + fallback + ".");
        }
    }
}
