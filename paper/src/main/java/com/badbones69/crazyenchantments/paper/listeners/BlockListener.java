package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.BlockSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Block: when the sword wielder takes a direct melee hit, an accepted proc
 * negates that hit and the attacker takes a share of its pre-mitigation damage.
 *
 * <p>Registered before {@link DamageModifierListener} so the hit is judged on its
 * raw damage and a negated hit never reaches the shared damage layer or the
 * attacker's own enchants. The cancel happens at HIGHEST with
 * {@code ignoreCancelled}, so a hit another plugin already cancelled never
 * procs. The reflection is delivered by the follow-up callback the plugin
 * registers right after {@code SwordEnchantments}, at the same priority, and only
 * while the hit is still cancelled.</p>
 *
 * <p>The reflection goes through {@link SyntheticHit}: it is attributed to the
 * wielder and still passes protection plugins, but every CE handler (this one
 * included) ignores it, so it can never roll Block again, double reflect or
 * loop, even when the attacker also wields Block. Folia: both parties must be
 * owned by the current region; otherwise no proc happens at all, so the hit is
 * never negated without its reflection.</p>
 */
public class BlockListener implements Listener {

    private record Reflection(Player wielder, LivingEntity attacker, double amount) {}

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final SupportUtils support = this.plugin.getPlatform().getSupport();

    private final Methods methods = this.plugin.getStarter().getMethods();

    private final EnchantmentBookSettings books = this.plugin.getStarter().getEnchantmentBookSettings();

    // Weak keys: an entry whose follow-up pass never runs cannot pin the event. The
    // map is shared by every region, so it stays synchronized; the callbacks only
    // touch it for a player victim's direct melee hit.
    private final Map<EntityDamageByEntityEvent, Reflection> pending = Collections.synchronizedMap(new WeakHashMap<>());

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(final EntityDamageByEntityEvent event) {
        if (!BlockSettings.mayProc(SyntheticHit.isBleedTick(), event.isCancelled()) || EventUtils.isIgnoredEvent(event)) return;

        final DamageCause cause = event.getCause();

        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getEntity() instanceof Player wielder) || !(event.getDamager() instanceof LivingEntity attacker)) return;
        if (attacker == wielder || wielder.isDead() || attacker.isDead()) return;

        if (EventUtils.isIgnoredUUID(wielder.getUniqueId()) || EventUtils.isIgnoredUUID(attacker.getUniqueId())) return;

        // Ownership before any live read; see the class note on cross-region hits.
        if (!Bukkit.isOwnedByCurrentRegion(wielder) || !Bukkit.isOwnedByCurrentRegion(attacker)) return;
        if (attacker instanceof Player player && this.support.isFriendly(player, wielder)) return;

        final double damage = event.getDamage();

        if (!(damage > 0.0)) return;

        final ItemStack item = this.methods.getItemInHand(wielder);
        final Map<CEnchantment, Integer> enchantments = this.books.getEnchantments(item);
        final CEnchantment key = CEnchantments.BLOCK.getEnchantment();
        final int level = key == null ? 0 : enchantments.getOrDefault(key, 0);

        if (level < 1 || !EnchantUtils.isEventActive(CEnchantments.BLOCK, wielder, item, enchantments)) return;

        final double reflected = TuningConfig.block().reflectAmount(level, damage);

        if (reflected > 0.0) this.pending.put(event, new Reflection(wielder, attacker, reflected));

        event.setCancelled(true);
    }

    /**
     * The follow-up pass. The plugin registers this explicitly, right after
     * {@code SwordEnchantments} at HIGHEST, so it sees the hit after every CE
     * handler has had it; there is no annotation, so a later wholesale
     * registration cannot bind it to another priority.
     */
    public void onHitResolved(final EntityDamageByEntityEvent event) {
        // Fast reject before the shared map is touched: only a player victim's
        // direct melee hit is ever recorded.
        if (!(event.getEntity() instanceof Player)) return;

        final DamageCause cause = event.getCause();

        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;

        final Reflection reflection = this.pending.remove(event);

        if (reflection == null || !event.isCancelled()) return;

        final Player wielder = reflection.wielder();
        final LivingEntity attacker = reflection.attacker();

        if (!Bukkit.isOwnedByCurrentRegion(wielder) || !Bukkit.isOwnedByCurrentRegion(attacker)) return;
        if (!wielder.isOnline() || attacker.isDead() || !attacker.isValid()) return;
        if (attacker instanceof Player player && this.support.isFriendly(wielder, player)) return;

        SyntheticHit.damage(wielder, attacker, reflection.amount());
    }
}
