package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.BowBlastContext;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.enchantments.ArmorEnchantments;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DefensiveArmorListener implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);
    private final SupportUtils support = this.plugin.getPlatform().getSupport();
    private final EnchantmentBookSettings books = this.plugin.getStarter().getEnchantmentBookSettings();
    private final Map<UUID, Long> nextArrowHit = new ConcurrentHashMap<>();
    private final Map<EntityDamageByEntityEvent, Integer> pendingArrowHits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEnderHeal = new ConcurrentHashMap<>();

    // The reload epoch this listener's per-player state was built under. A completed reload
    // replaces every tuning record these handlers read, so the state is dropped with it, on
    // the same lifecycle the armor listener clears its own state.
    private volatile long stateEpoch = -1L;

    /**
     * True while a reload has replaced the registry or its tuning records without having
     * republished them. Every handler below reads tuning, so it refuses like the armor
     * listener does.
     */
    private boolean reloading() {
        final ArmorEnchantments armor = this.plugin.getArmorEnchantments();

        return armor != null && armor.isReloading();
    }

    /**
     * Drops every per-player record once a reload has completed. Called at the start of each
     * handler: one integer read per event, and the maps are emptied on the first event after
     * the reload instead of waiting for a quit.
     */
    private void discardStateOnReload() {
        final ArmorEnchantments armor = this.plugin.getArmorEnchantments();

        if (armor == null) return;

        final long epoch = armor.reloadEpoch();

        if (epoch == this.stateEpoch) return;

        this.stateEpoch = epoch;
        this.nextArrowHit.clear();
        this.pendingArrowHits.clear();
        this.lastEnderHeal.clear();
    }

    // CE's sword proc also goes through Bukkit's potion-effect application event.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlindness(final EntityPotionEffectEvent event) {
        final PotionEffect effect = event.getNewEffect();
        if (effect == null || !PotionEffectType.BLINDNESS.equals(effect.getType())) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        discardStateOnReload();
        if (reloading()) return;

        final int level = activeLevel(wearer, CEnchantments.CLARITY, wearer.getEquipment().getArmorContents());
        if (TuningConfig.clarity().blocks(level, effect.getAmplifier())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowDamage(final EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.PROJECTILE || !(event.getDamager() instanceof AbstractArrow arrow)
                || arrow instanceof Trident) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof LivingEntity shooter) || shooter == wearer) return;

        discardStateOnReload();
        if (reloading()) return;

        // Friendship providers can read their inputs live. Unknown cross-region relationships
        // must pass through rather than risking a live read or blocking a friendly arrow.
        if (shooter instanceof Player player
                && (!Bukkit.isOwnedByCurrentRegion(player) || this.support.isFriendly(player, wearer))) return;

        final int level = activeLevel(wearer, CEnchantments.ARROW_DEFLECT, wearer.getEquipment().getArmorContents());
        if (level < 1) return;

        if (TuningConfig.arrowDeflect().blocks(this.nextArrowHit.get(wearer.getUniqueId()), System.nanoTime())) {
            if (event.getFinalDamage() > 0.0) event.setCancelled(true);
        } else {
            this.pendingArrowHits.put(event, level);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void recordArrowDamage(final EntityDamageByEntityEvent event) {
        discardStateOnReload();

        final Integer level = this.pendingArrowHits.remove(event);
        if (level == null || event.isCancelled() || !(event.getFinalDamage() > 0.0)) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final long now = System.nanoTime();
        this.nextArrowHit.compute(wearer.getUniqueId(),
                (id, next) -> TuningConfig.arrowDeflect().windowAfterHit(next, now, level));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageTick(final EntityDamageEvent event) {
        if (event.getCause() != DamageCause.POISON && event.getCause() != DamageCause.WITHER) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        discardStateOnReload();
        if (reloading()) return;

        final int level = activeLevel(wearer, CEnchantments.ENDER_WALKER, wearer.getEquipment().getBoots());
        if (level < 1) return;
        event.setCancelled(true);
        if (level < 4 || level > 5) return;

        final AttributeInstance attribute = wearer.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;

        final double maxHealth = attribute.getValue();
        final double health = wearer.getHealth();
        final long now = System.nanoTime();
        final UUID id = wearer.getUniqueId();
        final double heal = TuningConfig.enderWalker().healAmount(level, this.lastEnderHeal.get(id), now, health, maxHealth);
        if (heal <= 0.0) return;

        wearer.setHealth(Math.min(maxHealth, health + heal));
        this.lastEnderHeal.put(id, now);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    /**
     * Creeper Armor's folded splash. This listener is registered after the bow listener, and the
     * fold runs at MONITOR, so this is the first point where {@link BowBlastContext} can tell us
     * which part of this exact event was folded explosion splash. The reduction itself lives with
     * the rest of Creeper Armor; only its component is touched, never the arrow damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoldedBlastDamage(final EntityDamageByEntityEvent event) {
        final ArmorEnchantments armor = this.plugin.getArmorEnchantments();

        if (armor == null) return;

        armor.reduceFoldedBlast(event);
    }

    /**
     * A death ends the fight the arrow window and the Ender Walker heal belong to.
     */
    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        clear(event.getEntity().getUniqueId());
    }

    /**
     * A world change ends the same fight; nothing recorded for the old world follows.
     */
    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void clear(final UUID id) {
        this.nextArrowHit.remove(id);
        this.lastEnderHeal.remove(id);
    }

    private int activeLevel(final Player wearer, final CEnchantments enchant, final ItemStack... gear) {
        final CEnchantment key = enchant.getEnchantment();
        if (key == null || !key.isActivated()) return 0;

        int highest = 0;
        ItemStack piece = null;
        Map<CEnchantment, Integer> strongest = Map.of();
        for (final ItemStack item : gear) {
            final Map<CEnchantment, Integer> enchants = this.books.getEnchantments(item);
            final int level = enchants.getOrDefault(key, 0);
            if (level <= highest) continue;

            highest = level;
            piece = item;
            strongest = enchants;
        }

        return piece != null && EnchantUtils.isEventActive(enchant, wearer, piece, strongest) ? highest : 0;
    }
}
