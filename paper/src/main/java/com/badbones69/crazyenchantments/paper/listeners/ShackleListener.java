package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.ShackleSupport;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Shackle: cancels the knockback a wielder causes to a spawner-spawned mob.
 * Only the entity that caused the knockback is compared, and only a mob that
 * carries the spawner tag (or, for an untagged mob, still reports
 * {@link org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason#SPAWNER}) is
 * affected; see {@link ShackleSupport}. Melee uses the wielder's main hand;
 * ranged knockback uses the projectile's firing weapon. The listener reads
 * entities only on their owning Folia region.
 */
public class ShackleListener implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final Methods methods = this.plugin.getStarter().getMethods();

    private final EnchantmentBookSettings books = this.plugin.getStarter().getEnchantmentBookSettings();

    private final NamespacedKey fireworkWeapon = new NamespacedKey(this.plugin, "shackle_firework_weapon");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFireworkShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Firework firework)
                || !Bukkit.isOwnedByCurrentRegion(firework)) return;

        // Crossbow rockets don't expose their firing weapon through the projectile API.
        final ItemStack weapon = event.getBow();
        if (weapon != null && weapon.getType() == Material.CROSSBOW
                && this.books.getEnchantments(weapon).containsKey(CEnchantments.SHACKLE.getEnchantment())) {
            firework.getPersistentDataContainer().set(this.fireworkWeapon, PersistentDataType.BYTE_ARRAY, weapon.serializeAsBytes());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!Bukkit.isOwnedByCurrentRegion(victim)) return;
        if (victim.isDead() || !victim.isValid()) return;

        if (!ShackleSupport.isSpawnerMob(victim)) return;

        final Entity source = event.getSourceEntity();
        if (!Bukkit.isOwnedByCurrentRegion(source)) return;
        final var attacker = source instanceof Projectile projectile ? projectile.getShooter() : source;
        if (!(attacker instanceof Player wielder) || !Bukkit.isOwnedByCurrentRegion(wielder)) return;

        final ItemStack item = switch (source) {
            case Trident trident -> trident.getItemStack();
            case AbstractArrow arrow -> arrow.getWeapon();
            case Firework firework -> {
                final byte[] bytes = firework.getPersistentDataContainer().get(this.fireworkWeapon, PersistentDataType.BYTE_ARRAY);
                yield bytes == null ? null : ItemStack.deserializeBytes(bytes);
            }
            case Projectile ignored -> null;
            default -> this.methods.getItemInHand(wielder);
        };
        if (item == null || item.isEmpty()) return;
        final Map<CEnchantment, Integer> enchantments = this.books.getEnchantments(item);

        if (!ShackleSupport.suppressesKnockback(victim, wielder, item, enchantments)) return;

        event.setCancelled(true);
    }
}
