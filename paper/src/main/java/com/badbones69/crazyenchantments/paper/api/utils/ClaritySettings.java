package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

public record ClaritySettings(int maxBlockedLevel) {

    public static final ClaritySettings DEFAULTS = new ClaritySettings(3);

    public boolean blocks(final int enchantLevel, final int amplifier) {
        // Bukkit amplifiers are zero-based; comparing directly also avoids overflow.
        return amplifier >= 0 && amplifier < Math.min(enchantLevel, this.maxBlockedLevel);
    }

    public static ClaritySettings validated(final int maxBlockedLevel, final Consumer<String> warn) {
        if (maxBlockedLevel >= 1 && maxBlockedLevel <= 256) return new ClaritySettings(maxBlockedLevel);

        if (warn != null) {
            warn.accept("Invalid CosmicEnchants.Clarity.Max-Blocked-Level value '" + maxBlockedLevel
                    + "'; using " + DEFAULTS.maxBlockedLevel() + ".");
        }

        return DEFAULTS;
    }
}
