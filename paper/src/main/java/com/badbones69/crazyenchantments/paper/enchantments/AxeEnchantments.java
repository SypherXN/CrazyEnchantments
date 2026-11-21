package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyManager;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.events.MassBlockBreakEvent;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.api.bleed.BleedSystem;
import com.badbones69.crazyenchantments.paper.api.utils.AntiGankSettings;
import com.badbones69.crazyenchantments.paper.api.utils.BarbarianSettings;
import com.badbones69.crazyenchantments.paper.api.utils.BlacksmithSettings;
import com.badbones69.crazyenchantments.paper.api.utils.CleaveSettings;
import com.badbones69.crazyenchantments.paper.api.utils.DamageModifiers;
import com.badbones69.crazyenchantments.paper.api.utils.DeepWoundsSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EntityUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.InsanitySettings;
import com.badbones69.crazyenchantments.paper.api.utils.PummelSettings;
import com.badbones69.crazyenchantments.paper.api.utils.RogueSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class AxeEnchantments implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final SupportUtils support = this.platform.getSupport();

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    @NotNull
    private final CrazyManager crazyManager = this.starter.getCrazyManager();

    private volatile CombatState combatState = new CombatState();

    private static final class CombatState {
        // Each inner map is accessed only on its wielder's owning region.
        private final Map<UUID, Map<UUID, Long>> recentAttackers = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, Long> rogueCooldowns = new ConcurrentHashMap<>();
        // Weak keys: an event whose MONITOR pass never runs cannot pin these entries.
        private final Map<EntityDamageEvent, HitItem> pendingHits = Collections.synchronizedMap(new WeakHashMap<>());
        private final Map<EntityDamageEvent, Integer> pendingBlacksmith = Collections.synchronizedMap(new WeakHashMap<>());
    }

    private record HitItem(ItemStack item, Map<CEnchantment, Integer> enchantments, boolean canBleed) {}

    public void clearCombatState() {
        // In-flight hits can finish against their old state without repopulating the new maps.
        this.combatState = new CombatState();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTreeFeller(BlockBreakEvent event) {
        if (!event.isDropItems() || EventUtils.isIgnoredEvent(event)) return;

        Player player = event.getPlayer();
        ItemStack currentItem = methods.getItemInHand(player);

        Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(currentItem);
        if (!EnchantUtils.isMassBlockBreakActive(player, CEnchantments.TREEFELLER, enchantments)) return;

        final var settings = TuningConfig.fork().treeFeller();
        Set<Block> blockList = getTree(event.getBlock(), settings.logLimitPerLevel()
                * enchantments.get(CEnchantments.TREEFELLER.getEnchantment()), settings.horizontalRadius());
        boolean damage = FileKeys.CONFIG.getConfiguration().getBoolean("Settings.EnchantmentOptions.TreeFeller-Full-Durability", true);

        if (!new MassBlockBreakEvent(player, blockList).callEvent()) return;

        for (Block block : blockList) {
            if (block == event.getBlock()) continue;
            if (this.methods.playerBreakBlock(player, block, currentItem, true)) continue;
            if (damage) this.methods.removeDurability(currentItem, player);
        }

        if (!damage) this.methods.removeDurability(currentItem, player);

    }

    private Set<Block> getTree(Block startBlock, int maxBlocks, int radius) {
        Set<Block> checkedBlocks = new HashSet<>(), tree = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(startBlock);
        checkedBlocks.add(startBlock);
        int startX = startBlock.getX(), startZ = startBlock.getZ();

        while (!queue.isEmpty()) {
            Block currentBlock = queue.poll();

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = -1; y <= 1; y++) {
                        if (tree.size() > maxBlocks) break;
                        if (x == 0 && y == 0 && z == 0) continue; // Skip initial block.

                        Block neighbor = currentBlock.getRelative(x, y, z);
                        if (neighbor.isEmpty() || checkedBlocks.contains(neighbor)) continue;
                        if (notInRange(startX, neighbor.getX(), radius) || notInRange(startZ, neighbor.getZ(), radius)) continue;

                        String neighborType = neighbor.getType().toString();

                        if ((neighborType.endsWith("LOG") || neighborType.endsWith("LEAVES"))) {
                            if (neighborType.endsWith("LOG")) tree.add(neighbor);
                            checkedBlocks.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return tree;
    }

    private boolean notInRange(int startPos, int pos2, int range) {
        return pos2 > (startPos + range) || pos2 < (startPos - range);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!Bukkit.isOwnedByCurrentRegion(damager) || !Bukkit.isOwnedByCurrentRegion(entity)) return;

        final CombatState state = this.combatState;
        if (this.support.isFriendly(damager, entity)) return;

        final ItemStack item = this.methods.getItemInHand(damager);

        if (entity.isDead() || item.isEmpty()) return;

        final CEnchantment bleed = isDirectMelee(event) ? CEnchantments.BLEED.getEnchantment() : null;
        final boolean canBleed = bleed != null && bleed.canEnchantItem(item);
        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);
        if (enchantments.isEmpty()) return;

        if (EnchantUtils.isEventActive(CEnchantments.BERSERK, damager, item, enchantments)) {
            final int berserkLevel = enchantments.get(CEnchantments.BERSERK.getEnchantment());
            // Strength and Mining Fatigue share one amplifier per level (I-III).
            final int berserkTicks = (berserkLevel + 5) * 20;
            damager.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, berserkTicks, berserkLevel - 1));
            damager.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, berserkTicks, berserkLevel - 1));
        }

        if (EnchantUtils.isEventActive(CEnchantments.BLESSED, damager, item, enchantments)) removeBadPotions(damager);

        if (EnchantUtils.isEventActive(CEnchantments.FEEDME, damager, item, enchantments) && damager.getFoodLevel() < 20) {
            // Restore real hunger from the current food level; saturation never sets the bar.
            final int food = 2 * enchantments.get(CEnchantments.FEEDME.getEnchantment());

            damager.setFoodLevel(Math.min(20, damager.getFoodLevel() + food));
        }

        if (EnchantUtils.isEventActive(CEnchantments.REKT, damager, item, enchantments)) event.setDamage(event.getDamage() * TuningConfig.fork().rektMultiplier());

        if (EnchantUtils.isEventActive(CEnchantments.CURSED, damager, item, enchantments)) {
            final var settings = TuningConfig.fork().cursed();
            entity.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    settings.durationTicks(enchantments.get(CEnchantments.CURSED.getEnchantment())), settings.amplifier()));
        }

        if (EnchantUtils.isEventActive(CEnchantments.CONFUSION, damager, item, enchantments))
            entity.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (enchantments.get(CEnchantments.CONFUSION.getEnchantment()) + 9) * 20, 0));

        if (EnchantUtils.isEventActive(CEnchantments.BATTLECRY, damager, item, enchantments) && Bukkit.isOwnedByCurrentRegion(damager)) {
            final var settings = TuningConfig.fork().battleCry();
            final double radius = settings.radius();
            final Location origin = damager.getLocation();
            final Vector originVector = origin.toVector();

            for (final Entity nearbyEntity : damager.getNearbyEntities(radius, radius, radius)) {
                // Live friendship providers need both entities on this region.
                if (!Bukkit.isOwnedByCurrentRegion(nearbyEntity) || this.support.isFriendly(damager, nearbyEntity)) continue;

                // Push away from the caster. Subtracting a normalised world position
                // produced enormous velocities, which froze the victim mid-air.
                final Vector direction = nearbyEntity.getLocation().toVector().subtract(originVector);
                if (direction.lengthSquared() < 1.0E-4) continue;

                final Vector velocity = direction.normalize().multiply(settings.strength()).setY(settings.verticalOffset());
                nearbyEntity.getScheduler().run(this.plugin, task -> {
                    if (!nearbyEntity.isValid() || !nearbyEntity.getWorld().equals(origin.getWorld())) return;

                    nearbyEntity.setVelocity(velocity);
                }, null);
            }
        }

        if (EnchantUtils.isEventActive(CEnchantments.DEMONFORGED, damager, item, enchantments) && entity instanceof Player player) {

            ItemStack armorItem = switch (this.methods.percentPick(4, 0)) {
                case 1 -> player.getEquipment().getHelmet();
                case 2 -> player.getEquipment().getChestplate();
                case 3 -> player.getEquipment().getLeggings();
                default -> player.getEquipment().getBoots();
            };

            // Works with whichever sword or axe is in hand: this handler reads the held item, not its type.
            // A piece already gone (empty slot) has nothing to wear.
            if (armorItem != null && !armorItem.isEmpty()) {
                final int points = TuningConfig.demonForged().durabilityPoints();

                for (int point = 0; point < points; point++) this.methods.removeDurability(armorItem, player);
            }
        }

        if (!isDirectMelee(event)) return;

        final double baseDamage = event.getDamage();
        final int devourLevel = levelOf(enchantments, CEnchantments.DEVOUR);
        final boolean devour = devourLevel > 0 && BleedSystem.isBleeding(entity)
                && EnchantUtils.isEventActive(CEnchantments.DEVOUR, damager, item, enchantments);

        applyAxeBonuses(event, damager, entity, item, enchantments, state);

        if (devour) {
            DamageModifiers.applyOutgoingBonus(event, baseDamage, event.getDamage() * TuningConfig.devour().bonusFraction(devourLevel));
        }

        if ((canBleed && enchantments.containsKey(bleed)) || levelOf(enchantments, CEnchantments.PUMMEL) > 0
                || levelOf(enchantments, CEnchantments.CLEAVE) > 0) {
            state.pendingHits.put(event, new HitItem(item, enchantments, canBleed));
        }
    }

    private static int levelOf(final Map<CEnchantment, Integer> enchantments, final CEnchantments enchant) {
        final CEnchantment key = enchant.getEnchantment();

        return key == null ? 0 : enchantments.getOrDefault(key, 0);
    }

    private static boolean isDirectMelee(final EntityDamageEvent event) {
        final EntityDamageEvent.DamageCause cause = event.getCause();

        return cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
    }

    /**
     * Blacksmith, Anti Gank, Barbarian, Insanity and Rogue on a direct melee hit.
     * The weapon-matchup bonuses use the shared budget. Blacksmith's repair waits
     * for {@link #onBlacksmithRepair}.
     */
    private void applyAxeBonuses(
            final EntityDamageByEntityEvent event,
            final Player damager,
            final LivingEntity victim,
            final ItemStack item,
            final Map<CEnchantment, Integer> enchantments,
            final CombatState state
    ) {
        if (!isDirectMelee(event)) return;

        final int blacksmithLevel = levelOf(enchantments, CEnchantments.BLACKSMITH);
        final int antiGankLevel = levelOf(enchantments, CEnchantments.ANTI_GANK);
        final int barbarianLevel = levelOf(enchantments, CEnchantments.BARBARIAN);
        final int insanityLevel = levelOf(enchantments, CEnchantments.INSANITY);
        final int rogueLevel = levelOf(enchantments, CEnchantments.ROGUE);

        if (blacksmithLevel < 1 && antiGankLevel < 1 && barbarianLevel < 1 && insanityLevel < 1 && rogueLevel < 1) return;

        final double baseDamage = event.getDamage();
        double multiplier = 1.0;
        double matchup = 1.0;

        if (blacksmithLevel > 0 && victim instanceof Player && EnchantUtils.isEventActive(CEnchantments.BLACKSMITH, damager, item, enchantments)) {
            DamageModifiers.applyOutgoingBonus(event, baseDamage, 0.0);
            multiplier = DamageModifiers.multiply(multiplier, TuningConfig.blacksmith().damageMultiplier());

            state.pendingBlacksmith.put(event, blacksmithLevel);
        }

        if (antiGankLevel > 0) {
            final Map<UUID, Long> hits = state.recentAttackers.get(damager.getUniqueId());

            if (hits != null) {
                final AntiGankSettings antiGank = TuningConfig.antiGank();
                final double bonus = antiGank.multiplier(antiGankLevel, antiGank.count(hits, System.nanoTime()));

                if (bonus > 1.0 && EnchantUtils.isEventActive(CEnchantments.ANTI_GANK, damager, item, enchantments)) {
                    multiplier = DamageModifiers.multiply(multiplier, bonus);
                }
            }
        }

        if (barbarianLevel > 0 || insanityLevel > 0) {
            final EntityEquipment equipment = victim.getEquipment();
            final String held = equipment == null ? "" : equipment.getItemInMainHand().getType().name();

            if (barbarianLevel > 0 && BarbarianSettings.isAxe(held)
                    && EnchantUtils.isEventActive(CEnchantments.BARBARIAN, damager, item, enchantments)) {
                matchup = TuningConfig.barbarian().multiplier(barbarianLevel, true);
            }

            if (insanityLevel > 0 && InsanitySettings.isSword(held)
                    && EnchantUtils.isEventActive(CEnchantments.INSANITY, damager, item, enchantments)) {
                matchup = TuningConfig.insanity().multiplier(insanityLevel, true);
            }
        }

        if (rogueLevel > 0 && damager.getWorld().equals(victim.getWorld())) {
            final RogueSettings rogue = TuningConfig.rogue();
            final UUID attackerId = damager.getUniqueId();
            final long now = System.nanoTime() / 1_000_000L;

            if (!rogue.onCooldown(state.rogueCooldowns, attackerId, now)) {
                final Location from = victim.getLocation();
                final Location at = damager.getLocation();
                final Vector facing = from.getDirection();

                // Horizontal plane only: the Y components are dropped before the angle is taken.
                if (rogue.isBehind(facing.getX(), facing.getZ(), at.getX() - from.getX(), at.getZ() - from.getZ())
                        && EnchantUtils.isEventActive(CEnchantments.ROGUE, damager, item, enchantments)
                        && rogue.claim(state.rogueCooldowns, attackerId, now)) {
                    multiplier = DamageModifiers.multiply(multiplier, rogue.multiplier(rogueLevel));
                }
            }
        }

        if (multiplier != 1.0) {
            final double original = event.getDamage();

            event.setDamage(DamageModifiers.roundDamage(original, original * multiplier));
        }

        if (matchup > 1.0) {
            DamageModifiers.applyOutgoingBonus(event, baseDamage, event.getDamage() * (matchup - 1.0));
        }
    }

    /**
     * Blacksmith's armor repair, once the hit really landed. A hit another plugin cancelled,
     * or one that did no damage, repairs nothing (the damage cut is moot then anyway).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlacksmithRepair(final EntityDamageByEntityEvent event) {
        final Integer level = this.combatState.pendingBlacksmith.remove(event);

        if (level == null || event.isCancelled() || !(event.getFinalDamage() > 0.0)) return;
        if (!(event.getDamager() instanceof Player damager) || !Bukkit.isOwnedByCurrentRegion(damager)) return;

        repairMostDamagedArmor(damager, TuningConfig.blacksmith().repairPoints(level));
    }

    private void repairMostDamagedArmor(final Player player, final int points) {
        if (points <= 0) return;

        final EntityEquipment equipment = player.getEquipment();

        final EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        final int[] maxDurability = new int[slots.length];
        final int[] damageTaken = new int[slots.length];

        for (int i = 0; i < slots.length; i++) {
            final ItemStack piece = equipment.getItem(slots[i]);

            if (piece.getType().isAir() || !(piece.getItemMeta() instanceof Damageable meta)) continue;

            maxDurability[i] = meta.hasMaxDamage() ? meta.getMaxDamage() : piece.getType().getMaxDurability();
            damageTaken[i] = meta.getDamage();
        }

        final int index = BlacksmithSettings.mostDamaged(maxDurability, damageTaken);

        // No damaged piece: nothing to repair, the damage cut still stands.
        if (index < 0) return;

        final ItemStack piece = equipment.getItem(slots[index]).clone();
        final ItemMeta meta = piece.getItemMeta();

        if (!(meta instanceof Damageable damageable)) return;

        damageable.setDamage(BlacksmithSettings.repairedDamage(damageTaken[index], points));
        piece.setItemMeta(damageable);

        equipment.setItem(slots[index], piece);
    }

    /**
     * Pummel and Cleave, once the hit really landed (MONITOR, so the splash is based on the
     * final damage and a cancelled hit does nothing). Both are chance-based and each rolls
     * once per hit. Cleave's splash goes through {@link SyntheticHit#damage}: every CE damage
     * handler ignores a hit while that marker is set, so splash damage can never roll Cleave
     * again or trigger any other enchant (depth 1), and it respects armor and protections.
     * Folia: only entities this region owns are read or touched; a remote one is skipped.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPummelCleave(final EntityDamageByEntityEvent event) {
        final HitItem hitItem = this.combatState.pendingHits.remove(event);
        if (hitItem == null || event.isCancelled()) return;
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event) || !isDirectMelee(event)) return;

        if (!(event.getEntity() instanceof LivingEntity victim) || !(event.getDamager() instanceof Player damager)) return;

        // No live entity reads before ownership is known.
        if (!Bukkit.isOwnedByCurrentRegion(damager) || !Bukkit.isOwnedByCurrentRegion(victim)) return;
        if (event.getFinalDamage() <= 0.0) return;
        if (this.support.isFriendly(damager, victim)) return;

        final ItemStack item = hitItem.item();
        final Map<CEnchantment, Integer> enchantments = hitItem.enchantments();
        if (hitItem.canBleed() && !victim.isDead()) onBleedDamage(damager, victim, item, enchantments);
        if (!Bukkit.isOwnedByCurrentRegion(damager) || !Bukkit.isOwnedByCurrentRegion(victim)) return;
        if (EventUtils.isIgnoredUUID(damager.getUniqueId())) return;

        final int pummelLevel = levelOf(enchantments, CEnchantments.PUMMEL);
        final int cleaveLevel = levelOf(enchantments, CEnchantments.CLEAVE);

        if (pummelLevel < 1 && cleaveLevel < 1) return;

        final boolean pummel = pummelLevel > 0 && EnchantUtils.isEventActive(CEnchantments.PUMMEL, damager, item, enchantments);
        final boolean cleave = cleaveLevel > 0 && EnchantUtils.isEventActive(CEnchantments.CLEAVE, damager, item, enchantments);

        if ((!pummel && !cleave) || !Bukkit.isOwnedByCurrentRegion(damager) || !Bukkit.isOwnedByCurrentRegion(victim)) return;

        final PummelSettings pummelSettings = TuningConfig.pummel();
        final CleaveSettings cleaveSettings = TuningConfig.cleave();

        final double pummelRadius = pummel ? pummelSettings.radius() : 0.0;
        final double cleaveRadius = cleave ? cleaveSettings.radius(cleaveLevel) : 0.0;
        final double search = Math.max(pummelRadius, cleaveRadius);

        final Location center = victim.getLocation();
        final double searchSquared = search * search;
        final double pummelSquared = pummelRadius * pummelRadius;
        final double cleaveSquared = cleaveRadius * cleaveRadius;
        final int pummelLimit = PummelSettings.MAX_TARGETS - 1; // reserve the primary victim
        final PriorityQueue<NearbyTarget> pummelTargets = new PriorityQueue<>(Comparator.reverseOrder());
        final List<NearbyTarget> cleaveTargets = new ArrayList<>();

        for (final Entity candidate : victim.getNearbyEntities(search, search, search)) {
            if (!(candidate instanceof LivingEntity living) || candidate instanceof ArmorStand) continue;

            // By UUID, never by reference: the wielder and the primary victim must be skipped even if a wrapper hands out another instance.
            if (living.getUniqueId().equals(damager.getUniqueId()) || living.getUniqueId().equals(victim.getUniqueId())) continue;
            if (!Bukkit.isOwnedByCurrentRegion(living) || living.isDead() || !living.isValid()) continue;

            final Location location = living.getLocation();
            if (!location.getWorld().equals(center.getWorld())) continue;

            final double distanceSquared = location.distanceSquared(center);
            if (distanceSquared > searchSquared || this.support.isFriendly(damager, living)
                    || !this.support.isCombatEnabled(location)) continue;

            final NearbyTarget target = new NearbyTarget(living, distanceSquared, living.getUniqueId());
            if (pummel && distanceSquared <= pummelSquared) {
                if (pummelTargets.size() < pummelLimit) {
                    pummelTargets.add(target);
                } else if (target.compareTo(pummelTargets.element()) < 0) {
                    pummelTargets.remove();
                    pummelTargets.add(target);
                }
            }
            if (cleave && distanceSquared <= cleaveSquared) cleaveTargets.add(target);
        }

        if (pummel) {
            final PotionEffect slow = new PotionEffect(PotionEffectType.SLOWNESS, pummelSettings.durationTicks(pummelLevel), pummelSettings.amplifier());

            // The primary victim is always included.
            if (victim.isValid()) victim.addPotionEffect(slow);

            for (final NearbyTarget target : pummelTargets) {
                if (Bukkit.isOwnedByCurrentRegion(target.entity())) target.entity().addPotionEffect(slow);
            }
        }

        if (cleave) {
            final double splash = cleaveSettings.splashDamage(event.getFinalDamage());

            if (splash <= 0.0) return;

            // Heapify once and only rank as far as the landed-hit cap requires.
            final PriorityQueue<NearbyTarget> nearest = new PriorityQueue<>(cleaveTargets);
            int hit = 0;

            while (hit < cleaveSettings.maxTargets() && !nearest.isEmpty()) {
                final LivingEntity living = nearest.remove().entity();
                if (!Bukkit.isOwnedByCurrentRegion(living) || !Bukkit.isOwnedByCurrentRegion(damager)) continue;

                // Never the primary victim: it is filtered out of the nearby list by UUID above.
                if (SyntheticHit.damage(damager, living, splash)) hit++;
            }
        }
    }

    private record NearbyTarget(LivingEntity entity, double distanceSquared, UUID id) implements Comparable<NearbyTarget> {
        @Override
        public int compareTo(final NearbyTarget other) {
            final int distance = Double.compare(this.distanceSquared, other.distanceSquared);
            return distance == 0 ? this.id.compareTo(other.id) : distance;
        }
    }

    /**
     * Arrow Break: while the axe is in the victim's main hand, a chance to cancel an incoming
     * arrow's damage and remove the arrow. Registered before Arrow Deflect's handler, so it
     * sees the hit first; a hit that is already cancelled (Arrow Deflect's window, another
     * plugin) or an arrow that is already gone is never consumed a second time.
     * Folia: skipped unless this region owns both the victim and the arrow.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowBreak(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (event.isCancelled() || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) return;

        final Entity arrow = event.getDamager();

        if (!(arrow instanceof Arrow) && !(arrow instanceof SpectralArrow)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (!Bukkit.isOwnedByCurrentRegion(victim) || !Bukkit.isOwnedByCurrentRegion(arrow)) return;
        if (arrow.isDead() || !arrow.isValid()) return;

        final ItemStack item = this.methods.getItemInHand(victim);
        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

        if (levelOf(enchantments, CEnchantments.ARROW_BREAK) < 1) return;
        if (!EnchantUtils.isEventActive(CEnchantments.ARROW_BREAK, victim, item, enchantments)) return;

        event.setCancelled(true);
        arrow.remove();
    }

    /**
     * Records a landed, known-hostile hit only while the victim is wielding Anti Gank.
     * In particular the first hit by a new attacker counts when the wielder retaliates.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAntiGankTrack(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (!(event.getEntity() instanceof Player wielder) || !Bukkit.isOwnedByCurrentRegion(wielder)) return;
        if (!CEnchantments.ANTI_GANK.isActivated() || !(event.getFinalDamage() > 0.0)) return;

        final CombatState state = this.combatState;
        final Entity source = event.getDamageSource().getCausingEntity();

        if (!(source instanceof Player attacker) || !Bukkit.isOwnedByCurrentRegion(attacker)
                || attacker.getUniqueId().equals(wielder.getUniqueId())) return;

        final ItemStack item = this.methods.getItemInHand(wielder);
        final CEnchantment antiGank = CEnchantments.ANTI_GANK.getEnchantment();
        if (item.isEmpty() || antiGank == null || !antiGank.canEnchantItem(item)) return;
        if (this.enchantmentBookSettings.getEnchantments(item).getOrDefault(antiGank, 0) < 1
                || (!wielder.isOp() && wielder.hasPermission("crazyenchantments.%s.deny".formatted(CEnchantments.ANTI_GANK.getName())))
                || this.support.isFriendly(attacker, wielder)) return;

        final Map<UUID, Long> hits = state.recentAttackers.computeIfAbsent(wielder.getUniqueId(), id -> new HashMap<>());

        TuningConfig.antiGank().record(hits, attacker.getUniqueId(), System.nanoTime());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        clearPlayerState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatDeath(final PlayerDeathEvent event) {
        clearPlayerState(event.getEntity());
    }

    private void clearPlayerState(final Player player) {
        final CombatState state = this.combatState;
        final UUID id = player.getUniqueId();
        state.recentAttackers.remove(id);
        state.rogueCooldowns.remove(id);
    }

    private void onBleedDamage(final Player damager, final LivingEntity entity, final ItemStack item,
                               final Map<CEnchantment, Integer> enchantments) {
        final CEnchantment bleed = CEnchantments.BLEED.getEnchantment();
        final int bleedLevel = bleed == null ? 0 : enchantments.getOrDefault(bleed, 0);
        if (bleedLevel < 1) return;

        final CEnchantment deepWounds = CEnchantments.DEEP_WOUNDS.getEnchantment();
        final int deepWoundsLevel = deepWounds != null && deepWounds.canEnchantItem(item) ? enchantments.getOrDefault(deepWounds, 0) : 0;
        final int baseChance = bleed.getChance() + bleed.getChanceIncrease() * (bleedLevel - 1);
        final DeepWoundsSettings settings = TuningConfig.deepWounds();

        if (EnchantUtils.isEventActive(CEnchantments.BLEED, damager, item, enchantments, settings.chanceMultiplier(baseChance, deepWoundsLevel))) {
            BleedSystem.apply(damager, entity, 1);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (player.getKiller() == null) return;

        if (!this.support.isCombatEnabled(player.getLocation())) return;

        Player damager = player.getKiller();
        ItemStack item = this.methods.getItemInHand(damager);

        if (EnchantUtils.isEventActive(CEnchantments.DECAPITATION, damager, item, this.enchantmentBookSettings.getEnchantments(item))) {
            event.getDrops().add(new ItemBuilder().setMaterial(Material.PLAYER_HEAD).setPlayerName(player.getName()).build());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        if (killer == null) return;

        ItemStack item = this.methods.getItemInHand(killer);
        Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);
        Material headMat = EntityUtils.getHeadMaterial(event.getEntity());

        if (headMat != null && !EventUtils.containsDrop(event, headMat)) {
            double multiplier = this.crazyManager.getDecapitationHeadMap().getOrDefault(headMat, 0.0);

            if (multiplier != 0.0 && EnchantUtils.isEventActive(CEnchantments.DECAPITATION, killer, item, enchantments, multiplier)) {
                final ItemBuilder headBuilder = new ItemBuilder().setMaterial(headMat);

                // Player heads need a profile, or the drop renders as an ownerless head.
                if (event.getEntity() instanceof Player killed) headBuilder.setPlayerName(killed.getName());

                event.getDrops().add(headBuilder.build());
            }
        }
    }

    private void removeBadPotions(Player player) {
        final List<PotionEffectType> bad = List.of(
                PotionEffectType.BLINDNESS,
                PotionEffectType.NAUSEA,
                PotionEffectType.HUNGER,
                PotionEffectType.POISON,
                PotionEffectType.SLOWNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.WEAKNESS,
                PotionEffectType.WITHER
        );

        // Bleed's movement-speed attribute modifier is independent of potion Slowness.
        bad.forEach(player::removePotionEffect);
    }
}
