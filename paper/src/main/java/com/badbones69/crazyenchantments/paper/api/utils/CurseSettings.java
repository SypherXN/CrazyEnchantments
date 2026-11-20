package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Curse: always-on CHESTPLATE enchant (no chance system). Pure data and math,
 * no Bukkit types.
 *
 * <p>While the wearer's health is at or below {@code triggerPp[level - 1]}
 * percent of max health the wearer keeps Strength I, Slowness I and Resistance I
 * (Resistance II at level V), refreshed every {@link #REFRESH_TICKS} ticks and
 * lasting {@link #EFFECT_TICKS} ticks so a refresh never leaves a gap. Once
 * health rises above the trigger the enchant only re-arms after health is at
 * least {@code hysteresisPp} points above the trigger, so a wearer hovering on
 * the line cannot flicker.</p>
 *
 * <p>Potion effects carry no source tag, so ownership cannot be proven and
 * Curse never tries: it fills a gap only. An effect of any type that is already
 * present, whoever applied it, is never overwritten, refreshed or removed — and
 * that includes Curse's own effect, which is simply not renewed. The accepted
 * cost is that the buff may lapse for up to {@link #EFFECT_TICKS} ticks (two
 * seconds) after the wearer leaves the threshold or takes the chestplate off.</p>
 */
public record CurseSettings(List<Double> triggerPp, double hysteresisPp) {

    public static final int MAX_LEVEL = 5;

    /** Ticks between refreshes (~1 second). */
    public static final int REFRESH_TICKS = 20;

    /** Ticks every applied effect lasts; twice the refresh so a late tick never drops it. */
    public static final int EFFECT_TICKS = 40;

    public static final CurseSettings DEFAULTS = new CurseSettings(List.of(20.0, 25.0, 30.0, 35.0, 40.0), 5.0);

    public CurseSettings {
        triggerPp = TuningConfig.BowValues.checkedTable(triggerPp, MAX_LEVEL, 0.0, 100.0);
        hysteresisPp = TuningConfig.BowValues.checkedNumber(hysteresisPp, 0.0, 100.0);
    }

    /**
     * Health cut in percent of max health; 0 for no level.
     */
    public double triggerPp(final int level) {
        if (level < 1) return 0.0;

        return this.triggerPp.get(Math.min(level, MAX_LEVEL) - 1);
    }

    /**
     * Whether the effect should be kept: health at or below the level's cut.
     */
    public boolean below(final int level, final double health, final double maxHealth) {
        if (level < 1 || !(maxHealth > 0.0)) return false;

        return health <= maxHealth * triggerPp(level) / 100.0;
    }

    /**
     * Whether the enchant may arm again: health at least {@code hysteresisPp}
     * points above the level's cut.
     */
    public boolean rearmed(final int level, final double health, final double maxHealth) {
        if (level < 1 || !(maxHealth > 0.0)) return false;

        return health >= maxHealth * (triggerPp(level) + this.hysteresisPp) / 100.0;
    }

    /**
     * Resistance is II at the top level, I below it.
     */
    public static int resistanceAmplifier(final int level) {
        return level >= MAX_LEVEL ? 1 : 0;
    }

    /**
     * Whether Curse may apply an effect of some type: only when the wearer has no
     * effect of that type at all. A present effect, of any amplifier or duration,
     * belongs either to another source that cannot be told apart from Curse's own
     * or to a previous Curse window; either way it is left exactly as it is.
     */
    public static boolean mayApply(final boolean typePresent) {
        return !typePresent;
    }

    /**
     * Whether an armed cycle counts as applied: at least one effect addition really
     * succeeded. A cycle whose every slot was already occupied must not set the
     * latch, or the first real application would be locked out until the wearer
     * climbs past the hysteresis band.
     */
    public static boolean anyApplied(final boolean strength, final boolean slowness, final boolean resistance) {
        return strength || slowness || resistance;
    }
}
