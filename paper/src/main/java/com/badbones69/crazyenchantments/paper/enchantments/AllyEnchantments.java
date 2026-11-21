package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.managers.AllyManager;
import com.badbones69.crazyenchantments.paper.api.objects.AllyMob;
import com.badbones69.crazyenchantments.paper.api.objects.AllyMob.AllyType;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AllyEnchantments implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final SupportUtils support = this.platform.getSupport();

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    // Settings.
    @NotNull
    private final EnchantmentBookSettings bookSettings = this.starter.getEnchantmentBookSettings();

    // Plugin Managers.
    @NotNull
    private final AllyManager allyManager = this.starter.getAllyManager();

    // Keyed per player and enchant, so a Tamer proc cannot swallow the
    // Bee Keeper proc that should have answered the same fight.
    // Concurrent: written from the region thread of whichever player was hit.
    private final Map<String, Calendar> allyCoolDown = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAllySpawn(EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        if (event.getEntity() instanceof Player player && event.getDamager() instanceof LivingEntity enemy) { // Player gets attacked
            // Being hit spawns allies too, whatever hit you.
            this.allyManager.setEnemy(player, enemy);
            checkAndSpawn(player, enemy);
        }

        if (event.getEntity() instanceof LivingEntity enemy && event.getDamager() instanceof Player player) { // Player attacks
            // If the player is trying to hurt their own ally stop the damage.
            if (this.allyManager.isAlly(player, enemy)) {
                event.setCancelled(true);
                return;
            }
            this.allyManager.setEnemy(player, enemy);

            // Checks armor and spawns allies when attacking
            checkAndSpawn(player, enemy);
        }
    }

    private void checkAndSpawn(Player player, LivingEntity enemy) {
        // Cooldowns are recorded once the whole scan is done, so several worn
        // pieces of the same ally enchant can still each answer the same hit.
        final ItemStack[] armor = player.getEquipment().getArmorContents();
        final Set<String> fired = new HashSet<>();
        final Set<CEnchantments> cooled = EnumSet.noneOf(CEnchantments.class);

        for (int slot = 0; slot < armor.length; slot++) {
            Map<CEnchantment, Integer> enchants = this.bookSettings.getEnchantments(armor[slot]);

            if (enchants.isEmpty()) continue;

            checkAllyType(enemy, player, enchants, armor[slot], slot, fired, cooled);
        }

        cooled.forEach(enchant -> markCoolDown(player, enchant));
    }

    private void checkAllyType(LivingEntity enemy, Player player, Map<CEnchantment, Integer> enchants, ItemStack item, int slot,
                               Set<String> fired, Set<CEnchantments> cooled) {
        final var settings = TuningConfig.fork();
        if (canSummon(player, CEnchantments.TAMER, slot, fired) && EnchantUtils.isEventActive(CEnchantments.TAMER, player, item, enchants)) {
            int power = enchants.get(CEnchantments.TAMER.getEnchantment());
            fired.add(pieceKey(CEnchantments.TAMER, slot));
            cooled.add(CEnchantments.TAMER);
            spawnAllies(player, enemy, AllyType.WOLF, power * settings.tamer().countPerLevel(), settings.tamer().durationSeconds());
        }

        if (canSummon(player, CEnchantments.GUARDS, slot, fired) && EnchantUtils.isEventActive(CEnchantments.GUARDS, player, item, enchants)) {
            int power = enchants.get(CEnchantments.GUARDS.getEnchantment());
            fired.add(pieceKey(CEnchantments.GUARDS, slot));
            cooled.add(CEnchantments.GUARDS);
            spawnAllies(player, enemy, AllyType.IRON_GOLEM, power);
        }

        if (canSummon(player, CEnchantments.BEEKEEPER, slot, fired) && EnchantUtils.isEventActive(CEnchantments.BEEKEEPER, player, item, enchants)) {
            int power = enchants.get(CEnchantments.BEEKEEPER.getEnchantment());
            fired.add(pieceKey(CEnchantments.BEEKEEPER, slot));
            cooled.add(CEnchantments.BEEKEEPER);
            spawnAllies(player, enemy, AllyType.BEE, power * settings.beeKeeper().countPerLevel(), settings.beeKeeper().durationSeconds());
        }

        if (canSummon(player, CEnchantments.NECROMANCER, slot, fired) && EnchantUtils.isEventActive(CEnchantments.NECROMANCER, player, item, enchants)) {
            int power = enchants.get(CEnchantments.NECROMANCER.getEnchantment());
            fired.add(pieceKey(CEnchantments.NECROMANCER, slot));
            cooled.add(CEnchantments.NECROMANCER);
            spawnAllies(player, enemy, AllyType.ZOMBIE, power * 2);
        }

        if (canSummon(player, CEnchantments.INFESTATION, slot, fired) && EnchantUtils.isEventActive(CEnchantments.INFESTATION, player, item, enchants)) {
            int power = enchants.get(CEnchantments.INFESTATION.getEnchantment());
            final int count = power * settings.infestation().countPerLevel();
            fired.add(pieceKey(CEnchantments.INFESTATION, slot));
            cooled.add(CEnchantments.INFESTATION);
            spawnAllies(player, enemy, AllyType.ENDERMITE, count, settings.infestation().durationSeconds());
            spawnAllies(player, enemy, AllyType.SILVERFISH, count, settings.infestation().durationSeconds());
        }
    }

    private static String pieceKey(final CEnchantments enchant, final int slot) {
        return enchant.getName() + "#" + slot;
    }

    private boolean canSummon(Player player, CEnchantments enchant, int slot, Set<String> fired) {
        return !fired.contains(pieceKey(enchant, slot)) && !inCoolDown(player, enchant);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAllyTarget(EntityTargetEvent event) {
        final Entity source = event.getTarget();

        if (source == null) return; // For when the entity forgets.

        final AllyMob allyMob = this.allyManager.getAllyMob(event.getEntity());
        final AllyMob target = this.allyManager.getAllyMob(source);

        if (allyMob == null || target == null) {
            return;
        }

        // Stop mob from attacking other mobs owned by the player.
        if (allyMob.getOwner().getUniqueId().equals(target.getOwner().getUniqueId())) {
            event.setCancelled(true);

            return;
        }

        // Stop mob from targeting party members.
        if (target instanceof Player playerTarget && this.support.isFriendly(allyMob.getOwner(), playerTarget)) {
            return;
        }

        // Stop your pets from targeting you.
        if (source instanceof Player player) {
            if (player.getUniqueId().equals(allyMob.getOwner().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAllyDeath(EntityDeathEvent event) {
        if (!this.allyManager.isAllyMob(event.getEntity())) return;

        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAllyDespawn(ChunkUnloadEvent event) {
        if (event.getChunk().getEntities().length < 1) return;

        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof LivingEntity livingEntity)) continue;

            if (this.allyManager.isAllyMob(livingEntity)) this.allyManager.getAllyMob(livingEntity).forceRemoveAlly();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerLeave(PlayerQuitEvent event) {
        this.allyManager.forceRemoveAllies(event.getPlayer());
    }

    private void spawnAllies(Player player, LivingEntity enemy, AllyType allyType, int amount) {
        spawnAllies(player, enemy, allyType, amount, 60);
    }

    private void spawnAllies(Player player, LivingEntity enemy, AllyType allyType, int amount, int durationSeconds) {
        for (int i = 0; i < amount; i++) {
            AllyMob ally = new AllyMob(player, allyType);
            ally.spawnAlly(durationSeconds);
            ally.attackEnemy(enemy);
        }
    }

    private void markCoolDown(Player player, CEnchantments enchant) {
        Calendar coolDown = Calendar.getInstance();
        coolDown.add(Calendar.MINUTE, 2);

        this.allyCoolDown.put(coolDownKey(player, enchant), coolDown);
    }

    private static String coolDownKey(Player player, CEnchantments enchant) {
        return player.getUniqueId() + ":" + enchant.getName();
    }

    private boolean inCoolDown(Player player, CEnchantments enchant) {
        final String key = coolDownKey(player, enchant);

        if (this.allyCoolDown.containsKey(key)) {
            // Right now is before the player's cool-down ends.
            if (Calendar.getInstance().before(this.allyCoolDown.get(key))) return true;

            // Remove the player because their cool-down is over.
            this.allyCoolDown.remove(key);
        }

        return false;
    }
}
