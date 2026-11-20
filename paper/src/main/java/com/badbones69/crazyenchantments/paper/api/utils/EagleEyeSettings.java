package com.badbones69.crazyenchantments.paper.api.utils;

public record EagleEyeSettings(double bandWidthBlocks) {

    public static final EagleEyeSettings DEFAULTS = new EagleEyeSettings(15.0);

    public EagleEyeSettings {
        TuningConfig.BowValues.checkedNumber(bandWidthBlocks, 0.01, 1_000.0);
    }

    public int wearPoints(final double distance) {
        if (distance < this.bandWidthBlocks) return 1;
        if (distance < 2 * this.bandWidthBlocks) return 2;
        if (distance < 3 * this.bandWidthBlocks) return 3;

        return 4;
    }

    public static int wornDamage(final int current, final int maxDurability, final int points) {
        if (maxDurability <= 1 || current >= maxDurability - 1 || points <= 0) return current;

        return current + Math.min(points, maxDurability - 1 - current);
    }
}
