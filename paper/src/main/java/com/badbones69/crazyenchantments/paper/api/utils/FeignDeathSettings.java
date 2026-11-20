package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * Feign Death: armor enchant with the chance in Enchantments.yml. Pure data and
 * math, no Bukkit types.
 *
 * <p>A hit that leaves the wearer at {@code healthPp} percent of max health or
 * below starts the vanish for {@code durationSeconds}. It ends early once the
 * wearer lands {@code level} outgoing player hits ({@code [1,2,3,4]}), direct
 * or with a projectile they shot, and one {@code cooldownSeconds} window is
 * spent per wearer. The enchant never changes health or position.</p>
 */
public record FeignDeathSettings(double healthPp, double durationSeconds, double cooldownSeconds) {

    public static final int MAX_LEVEL = 4;
    public static final double MAX_DURATION_SECONDS = 6.0;

    public static final FeignDeathSettings DEFAULTS = new FeignDeathSettings(25.0, MAX_DURATION_SECONDS, 120.0);

    public FeignDeathSettings {
        healthPp = TuningConfig.BowValues.checkedNumber(healthPp, 0.0, 100.0);
        durationSeconds = TuningConfig.BowValues.checkedNumber(durationSeconds, 0.05, MAX_DURATION_SECONDS);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.0, 600.0);
    }

    /**
     * Whether a hit leaving {@code healthAfterHit} triggers the vanish. The cut
     * is inclusive, and a lethal hit (0 or less) is inside it.
     */
    public boolean triggers(final double healthAfterHit, final double maxHealth) {
        if (!(maxHealth > 0.0)) return false;

        return healthAfterHit <= maxHealth * this.healthPp / 100.0;
    }

    public long durationTicks() {
        return Math.max(1L, Math.round(this.durationSeconds * 20.0));
    }

    /**
     * Outgoing player hits that end the vanish at {@code level}; 0 for no level.
     * Pure level math, so the counter thread can call it without the settings.
     */
    public static int hitsToEnd(final int level) {
        if (level < 1) return 0;

        return Math.min(level, MAX_LEVEL);
    }

    /**
     * Whether {@code hits} landed player hits are enough to end the vanish.
     */
    public static boolean endsAfter(final int level, final int hits) {
        final int needed = hitsToEnd(level);

        return needed > 0 && hits >= needed;
    }

    /**
     * Whether {@code wearer} currently has an active vanish. Visibility is settled
     * against this state at execution time, never against a captured command, so a
     * run identity no longer participates in how a viewer is hidden.
     */
    public static <T> boolean active(final ConcurrentMap<UUID, T> runs, final UUID wearer) {
        return runs.get(wearer) != null;
    }

    /**
     * Whether a damage event counts as one of the wearer's outgoing hits: the
     * victim must be a player and the source a player, either hitting directly or
     * as the shooter of the projectile that landed.
     */
    public static boolean countsAsOutgoingHit(final boolean victimIsPlayer, final boolean directPlayerDamager,
                                              final boolean projectileShotByPlayer) {
        return victimIsPlayer && (directPlayerDamager || projectileShotByPlayer);
    }

    public boolean claim(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        return SpiritLinkSettings.claim(cooldowns, wearer, now, this.cooldownSeconds);
    }

    /**
     * Whether the cooldown has already elapsed. Claims nothing, so a vanish on cooldown
     * neither rolls nor fires the use event.
     */
    public boolean ready(final ConcurrentMap<UUID, Long> cooldowns, final UUID wearer, final long now) {
        final Long last = cooldowns.get(wearer);

        return last == null || now - last >= Math.round(this.cooldownSeconds * 1_000_000_000.0);
    }
}
