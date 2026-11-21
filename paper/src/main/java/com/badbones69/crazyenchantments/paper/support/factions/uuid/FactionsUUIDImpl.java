package com.badbones69.crazyenchantments.paper.support.factions.uuid;

import com.badbones69.crazyenchantments.paper.api.constants.Support;
import com.ryderbelserion.fusion.core.api.registry.mods.objects.Mod;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class FactionsUUIDImpl extends Mod {

    private static final String FACTIONS_API = "com.massivecraft.factions.FPlayers";

    public FactionsUUIDImpl() {
        super(Support.factions_uuid);
    }

    @Override
    public boolean isEnabled() {
        // Ally checks are driven by whichever factions provider is installed, not by one
        // hardcoded plugin name: SaberFactions registers as "Factions", FactionsUUID as
        // "FactionsUUID" and forked builds use their own names, but all of them expose the
        // FactionsUUID-class API this support reads. Probing that API also keeps the
        // support class from loading against a plugin that does not ship it.
        for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) continue;

            try {
                Class.forName(FACTIONS_API, false, plugin.getClass().getClassLoader());

                return true;
            } catch (final ClassNotFoundException | LinkageError ignored) {
                // Not a factions provider.
            }
        }

        return false;
    }

    @Override
    public Mod init() {
        if (isEnabled()) {
            try {
                new FactionsUUIDSupport().init();
            } catch (final LinkageError error) {
                // A fork can expose the Factions API marker without every class this
                // support uses; losing ally checks beats failing plugin startup.
                Bukkit.getLogger().warning("[CrazyEnchantments] Factions support could not load: " + error);
            }
        }

        return this;
    }
}
