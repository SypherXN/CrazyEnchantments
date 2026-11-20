package com.badbones69.crazyenchantments.paper.api.utils;

/**
 * Valor: always-on armor enchant (no chance system). While the wearer holds a
 * sword, every worn piece adds {@code reductionPerLevelPp} percentage points per
 * level to the incoming reduction. The points sum across pieces and apply once,
 * under the shared incoming-reduction cap (see {@link DamageModifiers}).
 *
 * <p>DOC: Cosmic's Valor reduces damage by up to 22.5% at V while wielding a
 * sword and stacks per piece. FORK: 1.125 pp per level per piece, so a full set
 * of four V pieces is 22.5 pp.</p>
 */
public record ValorSettings(double reductionPerLevelPp) {

    public static final int MAX_LEVEL = 5;

    public static final ValorSettings DEFAULTS = new ValorSettings(1.125);

    public ValorSettings {
        reductionPerLevelPp = TuningConfig.BowValues.checkedNumber(reductionPerLevelPp, 0.0, 20.0);
    }

    /** Percentage points one worn piece contributes at {@code level}. */
    public double piecePp(final int level) {
        return DamageModifiers.piecePP(this.reductionPerLevelPp, level);
    }

    /** True when the material name is a sword, the only held item Valor counts. */
    public static boolean isSword(final String material) {
        return material != null && material.endsWith("_SWORD");
    }
}
