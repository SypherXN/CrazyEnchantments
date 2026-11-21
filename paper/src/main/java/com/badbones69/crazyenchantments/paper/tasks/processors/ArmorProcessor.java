package com.badbones69.crazyenchantments.paper.tasks.processors;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Movement-driven armor checks.
 *
 * <p>Every pass reads the player's inventory, attributes and health, so it must run
 * on the region thread that owns the player. Movement fires once per block, so the
 * pass is also coalesced: at most one pass per player is queued at a time.</p>
 */
public class ArmorProcessor {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    private final SupportUtils support = this.platform.getSupport();

    private final Starter starter = this.plugin.getStarter();

    private final Methods methods = this.starter.getMethods();

    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // One queued pass per player. The entry is dropped when the pass runs, when the
    // player is gone or when the scheduler refuses the task.
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public ArmorProcessor() {}

    public void add(UUID id){
        if (!this.pending.add(id)) return;

        final Player player = this.plugin.getServer().getPlayer(id);

        if (player == null) {
            this.pending.remove(id);

            return;
        }

        // Paper's entity scheduler runs the task on the region that owns the player and
        // retires it when that player is gone, which is the only supported way to reach
        // the owning thread.
        try {
            final ScheduledTask task = player.getScheduler().run(this.plugin, scheduled -> {
                this.pending.remove(id);

                process(id);
            }, () -> this.pending.remove(id));

            if (task == null) this.pending.remove(id);
        } catch (final IllegalPluginAccessException exception) {
            this.pending.remove(id);

            if (this.plugin.isEnabled()) throw exception;
        }
    }

    public void process(UUID playerId) {
        Player player = this.plugin.getServer().getPlayer(playerId);

        if (player == null) return;

        for (final ItemStack armor : Objects.requireNonNull(player.getEquipment()).getArmorContents()) {
            Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(armor);

            if (enchantments.isEmpty()) continue;

            final Optional<AttributeInstance> instance = Optional.ofNullable(player.getAttribute(Attribute.MAX_HEALTH));

            final double health = player.getHealth();

            instance.ifPresentOrElse(action -> {
                final double attribute = action.getValue();

                if (attribute > health && health > 0) {
                    checkNursery(armor, player, enchantments, attribute);
                }
            }, () -> this.fusion.log(Level.WARNING, "Player %s did not have the MAX_HEALTH attribute!", player.getName()));

            // Implants runs on its own interval in ArmorEnchantments, not on movement.

            checkCommander(armor, player, enchantments);

            checkAngel(armor, player, enchantments);

            useHellForge(player, armor, enchantments);
        }

        PlayerInventory inv = player.getInventory();

        useHellForge(player, inv.getItemInMainHand(), this.enchantmentBookSettings.getEnchantments(inv.getItemInMainHand()));
        useHellForge(player, inv.getItemInOffHand(), this.enchantmentBookSettings.getEnchantments(inv.getItemInOffHand()));
    }

    private void checkCommander(ItemStack armor, Player player, Map<CEnchantment, Integer> enchantments) {
        if (!EnchantUtils.isMoveEventActive(CEnchantments.COMMANDER, player, enchantments)) return;

        int radius = 4 + enchantments.get(CEnchantments.COMMANDER.getEnchantment());

        new FoliaScheduler(this.plugin, null, player) {
            @Override
            public void run() {
                if (EnchantUtils.normalEnchantEvent(CEnchantments.COMMANDER, player, armor)) {
                    final PotionEffect fastDigging = new PotionEffect(PotionEffectType.HASTE, 3 * 20, 1);

                    // The wearer is their own closest ally. Ally providers only describe
                    // faction/party mates, so without one (or while solo) the old code handed
                    // Haste to nobody and the proc looked dead.
                    player.addPotionEffect(fastDigging);

                    for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                        new FoliaScheduler(plugin, null, entity) {
                            @Override
                            public void run() {
                                if (entity instanceof Player otherPlayer && support.isFriendly(player, otherPlayer)) {
                                    otherPlayer.addPotionEffect(fastDigging);
                                }
                            }
                        }.runNextTick();
                    }
                }
            }
        }.runNextTick();

    }

    /**
     * Angel. Nothing is collected here: every target's regeneration is applied by a task
     * on that target's own region, after re-checking it there. A piece without Angel is
     * not a level 0 proc, it is simply no proc, so the level is read before anything else
     * and the rest of the pass keeps running.
     */
    private void checkAngel(ItemStack armor, Player player, Map<CEnchantment, Integer> enchantments) {
        final Integer angel = enchantments.get(CEnchantments.ANGEL.getEnchantment());

        if (angel == null || angel < 1) return;

        if (!EnchantUtils.isMoveEventActive(CEnchantments.ANGEL, player, enchantments)) return;

        final int radius = 4 + angel;

        new FoliaScheduler(this.plugin, null, player) {
            @Override
            public void run() {
                if (!EnchantUtils.normalEnchantEvent(CEnchantments.ANGEL, player, armor)) return;

                final PotionEffect regeneration = new PotionEffect(PotionEffectType.REGENERATION, 3 * 20, 0);

                // The wearer is their own closest ally: without an ally provider the loop
                // below finds nobody, so the enchant never did anything for a solo player.
                player.addPotionEffect(regeneration);

                for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (!(entity instanceof Player otherPlayer)) continue;

                    new FoliaScheduler(plugin, null, otherPlayer) {
                        @Override
                        public void run() {
                            if (!otherPlayer.isOnline() || otherPlayer.isDead()) return;
                            if (support.isFriendly(player, otherPlayer)) otherPlayer.addPotionEffect(regeneration);
                        }
                    }.runNextTick();
                }
            }
        }.runNextTick();
    }

    private void checkNursery(ItemStack armor, Player player, Map<CEnchantment, Integer> enchantments, double maxHealth) {
        final var settings = TuningConfig.fork().nursery();
        if (!EnchantUtils.isMoveEventActive(CEnchantments.NURSERY, player, enchantments, settings.intervalTicks())) return;

        final double health = player.getHealth();
        final double heal = settings.healHp();

        new FoliaScheduler(this.plugin, null, player) {
            @Override
            public void run() {
                if (EnchantUtils.normalEnchantEvent(CEnchantments.NURSERY, player, armor)) {
                    if (health + heal <= maxHealth) {
                        player.setHealth(health + heal);
                    }

                    if (health + heal >= maxHealth) {
                        player.setHealth(maxHealth);
                    }
                }
            }
        }.runNextTick();
    }

    private void useHellForge(Player player, ItemStack item, Map<CEnchantment, Integer> enchantments) {
        if (!EnchantUtils.isMoveEventActive(CEnchantments.HELLFORGED, player, enchantments)) return;

        new FoliaScheduler(this.plugin, null, player) {
            @Override
            public void run() {
                if (!EnchantUtils.normalEnchantEvent(CEnchantments.HELLFORGED, player, item)) return;

                final int armorDurability = methods.getDurability(item);

                if (armorDurability <= 0) return;

                int finalArmorDurability = armorDurability;

                finalArmorDurability -= enchantments.get(CEnchantments.HELLFORGED.getEnchantment());

                methods.setDurability(item, finalArmorDurability);
            }
        }.runNextTick();
    }
}
