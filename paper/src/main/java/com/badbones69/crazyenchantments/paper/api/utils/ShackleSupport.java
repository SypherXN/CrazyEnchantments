package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The spawner-mob marker and the one Shackle knockback rule.
 *
 * <p>Plan #45 keys Shackle on the spawner tag rather than the live spawn reason:
 * the reason is only valid while the mob is being spawned, and a mob moved,
 * reloaded or re-created by another plugin reports a different one. The tag is
 * written by the spawner lane through {@link #tagSpawnerMob(Entity)}; a mob that
 * predates the tag still matches through the vanilla reason fallback.</p>
 *
 * <p>The rule is published here because two paths knock a mob back: the vanilla
 * {@code EntityKnockbackByEntityEvent} and code that sets a velocity by hand
 * (Obliterate), which never fires that event. Both ask
 * {@link #suppressesKnockback} so they cannot disagree.</p>
 */
public final class ShackleSupport {

    private static final String KEY = "spawner_mob";

    private static volatile NamespacedKey spawnerKey;

    private ShackleSupport() {}

    /**
     * Marks a mob as spawner-spawned. Safe to call repeatedly.
     */
    public static void tagSpawnerMob(@NotNull final Entity entity) {
        entity.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * True when the mob carries the spawner tag, or, for mobs that predate it,
     * when vanilla still reports a spawner spawn.
     */
    public static boolean isSpawnerMob(@NotNull final Entity entity) {
        if (entity.getPersistentDataContainer().has(key(), PersistentDataType.BYTE)) return true;

        return entity.getEntitySpawnReason() == SpawnReason.SPAWNER;
    }

    /**
     * Whether Shackle cancels this knockback: the victim is a spawner mob and the
     * wielder's weapon carries an active Shackle for this hit.
     */
    public static boolean suppressesKnockback(
            @NotNull final LivingEntity victim,
            @NotNull final Player wielder,
            @NotNull final ItemStack weapon,
            @NotNull final Map<CEnchantment, Integer> enchantments
    ) {
        return isSpawnerMob(victim) && EnchantUtils.isEventActive(CEnchantments.SHACKLE, wielder, weapon, enchantments);
    }

    /**
     * The key needs an enabled plugin, so it is built on first use, not at class load.
     */
    private static NamespacedKey key() {
        NamespacedKey current = spawnerKey;

        if (current == null) {
            current = new NamespacedKey(JavaPlugin.getPlugin(CrazyEnchantments.class), KEY);
            spawnerKey = current;
        }

        return current;
    }
}
