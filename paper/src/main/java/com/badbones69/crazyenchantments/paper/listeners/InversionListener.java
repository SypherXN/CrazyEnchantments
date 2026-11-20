package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.InversionSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inversion: while the sword is held in the main hand, an accepted proc cancels
 * an incoming direct melee hit and heals the wearer. It lives in its own
 * listener, registered immediately after {@link BlockListener} and therefore
 * before {@code SwordEnchantments}, so its HIGHEST handler always runs before
 * the attacker's damage handler; a hit the wearer negates can never carry the
 * attacker's procs. {@code ignoreCancelled} means a hit another plugin already
 * cancelled never procs. Projectiles are never a direct melee cause and are
 * ignored. Folia: the event runs on the wearer's region; both parties are only
 * read when this region owns them.
 */
public class InversionListener implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final SupportUtils support = this.plugin.getPlatform().getSupport();

    private final Methods methods = this.plugin.getStarter().getMethods();

    private final EnchantmentBookSettings books = this.plugin.getStarter().getEnchantmentBookSettings();

    // Inversion: wearer -> millis of the last accepted proc.
    private final ConcurrentHashMap<UUID, Long> inversionCooldowns = new ConcurrentHashMap<>();

    // The record the cooldowns were claimed under; a reload publishes a new one.
    private volatile InversionSettings inversionSettings;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInversion(EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        final EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        if (EventUtils.isIgnoredUUID(victim.getUniqueId()) || EventUtils.isIgnoredUUID(attacker.getUniqueId())) return;

        // Ownership before any live read; see the class note on cross-region hits.
        if (!Bukkit.isOwnedByCurrentRegion(victim) || !Bukkit.isOwnedByCurrentRegion(attacker)) return;
        if (victim.isDead()) return;
        if (attacker instanceof Player player && this.support.isFriendly(player, victim)) return;

        // A hit that deals nothing must not spend the cooldown or heal.
        if (!(event.getDamage() > 0.0)) return;

        final ItemStack item = this.methods.getItemInHand(victim);
        final Map<CEnchantment, Integer> enchantments = this.books.getEnchantments(item);
        final CEnchantment key = CEnchantments.INVERSION.getEnchantment();
        final int level = key == null ? 0 : enchantments.getOrDefault(key, 0);

        if (level < 1) return;

        final InversionSettings inversion = TuningConfig.inversion();

        // A reload publishes a new settings record; cooldowns claimed under the old
        // one are dropped with it.
        if (inversion != this.inversionSettings) {
            this.inversionSettings = inversion;
            this.inversionCooldowns.clear();
        }

        final UUID wearerId = victim.getUniqueId();
        final long now = System.nanoTime() / 1_000_000L;

        if (inversion.onCooldown(this.inversionCooldowns, wearerId, now)) return;
        if (!EnchantUtils.isEventActive(CEnchantments.INVERSION, victim, item, enchantments)) return;
        if (!inversion.claim(this.inversionCooldowns, wearerId, now)) return;

        final AttributeInstance maxHealth = victim.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth == null) return;

        event.setCancelled(true);

        victim.setHealth(inversion.healedHealth(level, victim.getHealth(), maxHealth.getValue()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.inversionCooldowns.remove(event.getPlayer().getUniqueId());
    }

    /** A respawn or a world change starts a fresh cooldown window. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        this.inversionCooldowns.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.inversionCooldowns.remove(event.getPlayer().getUniqueId());
    }
}
