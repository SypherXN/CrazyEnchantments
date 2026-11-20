package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.KillAuraLogic;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.bgsoftware.wildstacker.api.events.EntityUnstackEvent;
import com.bgsoftware.wildstacker.api.objects.StackedEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class KillAuraListener implements Listener {

    private static final long RECENT_DAMAGE_WINDOW_MILLIS = KillAuraLogic.RECENT_DAMAGE_WINDOW_MILLIS;

    private final CrazyEnchantments plugin;
    private final Methods methods;
    private final EnchantmentBookSettings books;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>> recentDamage = new ConcurrentHashMap<>();

    public KillAuraListener(CrazyEnchantments plugin) {
        this.plugin = plugin;
        this.methods = plugin.getStarter().getMethods();
        this.books = plugin.getStarter().getEnchantmentBookSettings();
        // This sweep only touches UUIDs and timestamps, never entities on another region.
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> pruneRecentDamage(), 100L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnstack(EntityUnstackEvent event) {
        if (event.isAsynchronous()) return;
        if (!(event.getUnstackSource() instanceof Player player)) return;
        if (!Bukkit.isOwnedByCurrentRegion(player)) return;

        final ItemStack item = this.methods.getItemInHand(player);
        final Map<CEnchantment, Integer> enchantments = this.books.getEnchantments(item);
        final CEnchantment key = CEnchantments.KILL_AURA.getEnchantment();
        if (key == null) return;

        final int level = enchantments.getOrDefault(key, 0);
        if (level <= 0) return;

        final StackedEntity stack = event.getEntity();
        // WildStacker cannot tell a kill unstack from an interaction/command one, so require a recent hit.
        if (stack == null || !recentlyDamaged(player, stack)) return;

        final int stackAmount;
        try {
            stackAmount = stack.getStackAmount();
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.FINE, "Kill Aura could not read the WildStacker stack size", exception);
            return;
        }

        final int extra = KillAuraLogic.extraKills(level, stackAmount, event.getAmount());
        if (extra <= 0) return;

        // Roll last so a proc is never spent on a stack with nothing left to take.
        if (!EnchantUtils.isEventActive(CEnchantments.KILL_AURA, player, item, enchantments)) return;

        event.setAmount(event.getAmount() + extra);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (event.isAsynchronous()) return;
        if (!Bukkit.isOwnedByCurrentRegion(entity)) return;

        final Player attacker;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        } else {
            return;
        }

        final long now = System.currentTimeMillis();
        final UUID victim = entity.getUniqueId();
        this.recentDamage.compute(attacker.getUniqueId(), (id, victims) -> {
            if (victims == null) victims = new ConcurrentHashMap<>();
            victims.merge(victim, now, Math::max);
            return victims;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.recentDamage.remove(event.getPlayer().getUniqueId());
    }

    private void pruneRecentDamage() {
        this.recentDamage.forEach((attacker, ignored) -> this.recentDamage.computeIfPresent(attacker, (id, victims) -> {
            final long now = System.currentTimeMillis();
            victims.values().removeIf(recordedAt -> !isRecent(recordedAt, now, RECENT_DAMAGE_WINDOW_MILLIS));
            return victims.isEmpty() ? null : victims;
        }));
    }

    private boolean recentlyDamaged(Player player, StackedEntity stack) {
        final ConcurrentHashMap<UUID, Long> victims = this.recentDamage.get(player.getUniqueId());
        if (victims == null) return false;

        final long now = System.currentTimeMillis();
        // WildStacker caches the original Bukkit UUID on the stack. Accept the current representative's UUID
        // as well: after a restack either one may be the entity hit, and they still identify this same stack.
        try {
            if (recentlyDamaged(victims, stack.getUniqueId(), now)) return true;
        } catch (RuntimeException | LinkageError exception) {
            this.plugin.getLogger().log(Level.FINE, "Kill Aura could not read the WildStacker stack UUID", exception);
        }

        try {
            final LivingEntity living = stack.getLivingEntity();
            return living != null && recentlyDamaged(victims, living.getUniqueId(), now);
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.FINE, "Kill Aura could not read the WildStacker living entity", exception);
            return false;
        }
    }

    private static boolean recentlyDamaged(Map<UUID, Long> victims, UUID victim, long now) {
        final Long recordedAt = victim == null ? null : victims.get(victim);
        return recordedAt != null && isRecent(recordedAt, now, RECENT_DAMAGE_WINDOW_MILLIS);
    }

    public static boolean isRecent(long recordedAt, long now, long windowMillis) {
        return KillAuraLogic.isRecent(recordedAt, now, windowMillis);
    }
}
