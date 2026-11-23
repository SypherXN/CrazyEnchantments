package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Temporary diagnostics for the bow chain: names the first place a shot stops.
 * Off by default, toggled with /crazyenchantments bowdebug, never reads config.
 */
public final class BowDebug {

    private static final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private static volatile boolean enabled = false;

    private BowDebug() {}

    public static void toggle() {
        enabled = !enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void log(final String message) {
        if (!enabled) return;

        plugin.getLogger().info("[bowdebug] " + message);
    }
}
