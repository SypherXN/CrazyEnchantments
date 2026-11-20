package com.badbones69.crazyenchantments.paper.api.utils;

/**
 * Sticky: always-on armor enchant (no chance system). This is the one shared
 * armor-removal gate: while a player wears any piece of Sticky, no armor can be
 * removed from them. Pure math, no Bukkit types.
 *
 * <p>The gate is binary on purpose: one worn piece at any level blocks every
 * removal, and wearing more pieces changes nothing. There is no curve and no
 * tunable value. Any current or future armor remover asks this one place
 * before taking a piece; Disarmor was its only caller and has been deleted, so
 * nothing calls it in-repo today.</p>
 */
public final class StickySettings {

    private StickySettings() {
    }

    /**
     * The armor-removal gate.
     *
     * @param wornStickyLevels the Sticky level on each worn piece, 0 for a slot
     *                         without it
     * @return {@code 0.0} when any piece carries Sticky (removal is blocked),
     *         {@code 1.0} when none does
     */
    public static double armorRemovalMultiplier(final int... wornStickyLevels) {
        for (final int level : wornStickyLevels) {
            if (level > 0) return 0.0;
        }

        return 1.0;
    }
}
