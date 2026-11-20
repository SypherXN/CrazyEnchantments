package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.List;

/**
 * Validated Virus tuning. A proc extends the remaining duration of the victim's
 * Wither and Poison by a share of what is left, keeps the amplifier, and caps
 * the total remaining duration at twenty seconds per effect. The chance curve
 * stays in Enchantments.yml (Base 8, Increase 4).
 */
public record VirusSettings(List<Double> extensionPp, double maxRemainingSeconds) {

    public static final VirusSettings DEFAULTS = new VirusSettings(
            List.of(25.0, 50.0, 75.0, 100.0),
            20.0
    );

    public VirusSettings {
        extensionPp = TuningConfig.BowValues.checkedTable(extensionPp, 4, 0.0, 1_000.0);
        maxRemainingSeconds = TuningConfig.BowValues.checkedNumber(maxRemainingSeconds, 0.001, 600.0);
    }

    public int maxTicks() {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(this.maxRemainingSeconds * 20.0));
    }

    /**
     * The new remaining ticks for one effect at {@code level}, never shorter
     * than what is left. A missing effect (zero or negative ticks) stays
     * missing; a duration already past the cap is left alone rather than cut.
     */
    public int extendedTicks(final int remainingTicks, final int level) {
        if (remainingTicks <= 0) return 0;

        final double pp = this.extensionPp.get(Math.clamp(level, 1, this.extensionPp.size()) - 1);
        final long extended = remainingTicks + Math.round(remainingTicks * pp / 100.0);

        return (int) Math.max(remainingTicks, Math.min(extended, maxTicks()));
    }

    /**
     * A negative duration is the vanilla "infinite" marker, not a running
     * effect: it must never be extended, re-added or used as a reason to strip
     * another effect. A zero-tick effect is still a present (expiring) one.
     */
    public static boolean present(final int durationTicks) {
        return durationTicks >= 0;
    }
}
