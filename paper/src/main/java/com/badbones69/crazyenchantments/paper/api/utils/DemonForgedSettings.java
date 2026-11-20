package com.badbones69.crazyenchantments.paper.api.utils;

/**
 * DemonForged: sword and axe enchant with the existing chance system (10% + 5%
 * per level). A proc wears one random worn armor piece of a player victim by
 * {@code durabilityPoints} points, each point going through the normal wear
 * rules (Unbreaking, Reforged). The point count ships at 1, which is what the
 * enchant always did; only the item types changed.
 */
public record DemonForgedSettings(int durabilityPoints) {

    public static final int MAX_LEVEL = 4;

    public static final int MAX_POINTS = 16;

    public static final DemonForgedSettings DEFAULTS = new DemonForgedSettings(1);

    public DemonForgedSettings {
        durabilityPoints = (int) TuningConfig.BowValues.checkedNumber(durabilityPoints, 1.0, MAX_POINTS);
    }
}
