package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.managers.BowEnchantmentManager;
import com.badbones69.crazyenchantments.paper.api.objects.BowEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.EnchantedArrow;
import com.badbones69.crazyenchantments.paper.api.utils.BowBlastContext;
import com.badbones69.crazyenchantments.paper.api.utils.BowUtils;
import com.badbones69.crazyenchantments.paper.api.utils.DamageModifiers;
import com.badbones69.crazyenchantments.paper.api.utils.EagleEyeSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.ExplosionHelper;
import com.badbones69.crazyenchantments.paper.api.utils.ExplosionHelper.Blast;
import com.badbones69.crazyenchantments.paper.api.utils.ExplosiveSettings;
import com.badbones69.crazyenchantments.paper.api.utils.FarcastSettings;
import com.badbones69.crazyenchantments.paper.api.utils.HellfireSettings;
import com.badbones69.crazyenchantments.paper.api.utils.InfernalSettings;
import com.badbones69.crazyenchantments.paper.api.utils.PacifySettings.PacifyState;
import com.badbones69.crazyenchantments.paper.api.utils.SniperSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TeleportationSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.api.utils.UnfocusSettings.Effect;
import com.badbones69.crazyenchantments.paper.api.utils.VirusSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BowEnchantments implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    private final SupportUtils support = this.platform.getSupport();

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // Plugin Managers.
    @NotNull
    private final BowEnchantmentManager bowEnchantmentManager = this.starter.getBowEnchantmentManager();

    @NotNull
    private final BowUtils bowUtils = this.starter.getBowUtils();

    private final Map<UUID, Effect> unfocusEffects = new ConcurrentHashMap<>();
    private final Map<EntityDamageByEntityEvent, PendingLifesteal> pendingLifesteal =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<UUID, SniperSettings.Shot> drawnShots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> farcastCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> pendingTeleports = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastBowStateCleanup = new AtomicLong(Long.MIN_VALUE);
    private final Map<UUID, HellfireSettings.Window> hellfireWindows = new ConcurrentHashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record PendingLifesteal(Player shooter, ItemStack bow, int level) {}

    /*
     * Cross-region rule for every bow handler below: an effect runs only when
     * the shooter, the arrow and the victim are all owned by the calling
     * region. Folia gives no safe way to read or move an entity that another
     * region owns, so a shot across a region border is skipped. The skip is
     * written out at each site, never hidden in a helper, so no handler can
     * "apply" while the next one silently does not.
     */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBowShoot(final EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (EventUtils.isIgnoredEvent(event) || EventUtils.isIgnoredUUID(event.getEntity().getUniqueId())) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow instanceof Trident) return;

        final ItemStack bow = event.getBow();

        // Deliberately not gated on isCombatEnabled here: that check belongs to
        // the impact, not the shot. Gating the shot meant every bow enchant went
        // silently dead whenever the archer stood somewhere combat is disabled
        // (own faction land, a WorldGuard region, a safezone), including the
        // harmless ones like Multi Arrow. Each damaging path still checks the
        // impact location before it hurts anyone.

        Map<CEnchantment, Integer> enchants = this.enchantmentBookSettings.getEnchantments(bow);
        if (enchants.isEmpty()) return;

        // Add the arrow to the list.
        this.bowUtils.addArrow(arrow, bow, enchants);

        // Sniper needs the draw force at the moment of the shot; weak shots never headshot.
        final boolean sniper = storedLevel(CEnchantments.SNIPER_HEADSHOT, enchants) > 0;

        if (sniper) {
            pruneBowState(nowMillis());
            this.drawnShots.put(arrow.getUniqueId(), SniperSettings.Shot.inFlight(event.getForce(), nowMillis()));
        }

        // MultiArrow only code below.
        if (EnchantUtils.isEventActive(CEnchantments.MULTIARROW, player, bow, enchants)) {
            int power = enchants.get(CEnchantments.MULTIARROW.getEnchantment())
                    * TuningConfig.fork().multiArrow().extraArrowsPerLevel();

            for (int i = 1; i <= power; i++) {
                final AbstractArrow extra = this.bowUtils.spawnArrows(player, arrow, bow);

                // The draw force belongs to the shot, so the extra arrows headshot too.
                if (sniper) this.drawnShots.put(extra.getUniqueId(), SniperSettings.Shot.inFlight(event.getForce(), nowMillis()));
            }
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLand(ProjectileHitEvent event) {
        final Projectile entity = event.getEntity();

        if (!(entity.getShooter() instanceof Player shooter)) return;
        if (!(entity instanceof AbstractArrow entityArrow)) return;

        // Cross-region rule: a remote shooter skips the whole landing.
        if (!Bukkit.isOwnedByCurrentRegion(shooter) || !Bukkit.isOwnedByCurrentRegion(entityArrow)) return;

        // Capture the impact point here: by the damage event the arrow may have moved.
        // A living victim still has its damage event to come; a block or a
        // non-living entity never will, so the captured draw goes there.
        final Entity hitEntity = event.getHitEntity();

        if (hitEntity != null) {
            SniperSettings.recordImpact(this.drawnShots, entityArrow.getUniqueId(),
                    hitEntity instanceof LivingEntity, entityArrow.getLocation().getY());
        }

        if (!this.support.isCombatEnabled(entityArrow.getLocation())) return;

        EnchantedArrow enchantedArrow = this.bowUtils.getEnchantedArrow(entityArrow);

        if (enchantedArrow == null) return;

        // Spawn webs related to STICKY_SHOT.
        this.bowUtils.spawnWebs(event.getHitEntity(), enchantedArrow);

        if (EnchantUtils.isEventActive(CEnchantments.BOOM, shooter, enchantedArrow.bow(), enchantedArrow.enchantments())) {
            this.methods.explode(enchantedArrow.getShooter(), enchantedArrow.arrow());
            enchantedArrow.arrow().remove();
        }

        if (EnchantUtils.isEventActive(CEnchantments.LIGHTNING, shooter, enchantedArrow.bow(), enchantedArrow.enchantments())) {
            Location location = enchantedArrow.arrow().getLocation();

            location.getWorld().strikeLightningEffect(location);

            int lightningSoundRange = FileKeys.CONFIG.getConfiguration().getInt("Settings.EnchantmentOptions.Lightning-Sound-Range", 160);

            try {
                location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, (float) lightningSoundRange / 16f, 1);
            } catch (Exception ignore) {
            }

            final UUID shooterId = shooter.getUniqueId();
            final UUID directVictim = hitEntity == null ? null : hitEntity.getUniqueId();

            EventUtils.addIgnoredUUID(shooterId);

            try {
                for (LivingEntity livingEntity : this.methods.getNearbyLivingEntities(2D, enchantedArrow.arrow())) {
                    // The arrow's own victim is damaged by the arrow right after this
                    // event; a hit here would burn its i-frames and replace that hit
                    // with these 5 HP.
                    if (directVictim != null && directVictim.equals(livingEntity.getUniqueId())) continue;

                    if (this.support.isFriendly(shooter, livingEntity)) continue;
                    if (shooterId.equals(livingEntity.getUniqueId())) continue;

                    // The real attributed hit: other plugins can still cancel it.
                    SyntheticHit.damage(shooter, livingEntity, 5D);
                }
            } finally {
                // Never leave the shooter marked for the next, unrelated hit.
                EventUtils.removeIgnoredUUID(shooterId);
            }
        }

        new FoliaScheduler(this.plugin, null, entityArrow) {
            @Override
            public void run() {
                bowUtils.removeArrow(enchantedArrow);
            }
        }.runDelayed(5);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (EventUtils.isIgnoredEvent(event)) return;
        if (!(event.getDamager() instanceof AbstractArrow entityArrow)) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        // Consume the draw entry up front so an ally hit, a cancelled hit or a
        // re-entrant handler can never leave it to the TTL.
        final SniperSettings.Shot drawnShot = this.drawnShots.remove(entityArrow.getUniqueId());

        EnchantedArrow enchantedArrow = this.bowUtils.getEnchantedArrow(entityArrow);
        if (enchantedArrow == null) return;

        // Cross-region rule: no bow effect may read a remote arrow, shooter or victim.
        if (!Bukkit.isOwnedByCurrentRegion(entityArrow) || !Bukkit.isOwnedByCurrentRegion(entity)) return;

        final Entity arrowShooter = enchantedArrow.getShooter();

        if (arrowShooter == null || !Bukkit.isOwnedByCurrentRegion(arrowShooter)) return;

        if (!this.support.isCombatEnabled(enchantedArrow.arrow().getLocation())) return;

        // The damage entering this handler is the base the shared outgoing-bonus
        // budget is measured from; the first CE handler to ask fixes it for the event.
        final double baseDamage = event.getDamage();

        // Damaged player is friendly.

        if (EnchantUtils.isEventActive(CEnchantments.DOCTOR, arrowShooter, enchantedArrow.bow(), enchantedArrow.enchantments()) && this.support.isFriendly(arrowShooter, entity)) {
            int heal = 1 + enchantedArrow.getLevel(CEnchantments.DOCTOR);

            Optional.ofNullable(entity.getAttribute(Attribute.MAX_HEALTH)).ifPresentOrElse(attribute -> {
                final double maxHealth = attribute.getValue();

                final double entityHealth = entity.getHealth();

                if (entityHealth < maxHealth) {
                    if (entityHealth + heal < maxHealth) entity.setHealth(entityHealth + heal);
                    if (entityHealth + heal >= maxHealth) entity.setHealth(maxHealth);
                }
            }, () -> this.fusion.log(Level.WARNING, "Player %s did not have the MAX_HEALTH attribute when using %s enchantment!", entity.getName(), "Healing"));
        }

        // Damaged player is an enemy.
        final Player shooter = arrowShooter instanceof Player player ? player : null;

        if (this.support.isFriendly(arrowShooter, entity)) {
            if (shooter != null) tryTeleportation(shooter, entity, enchantedArrow.bow(), enchantedArrow.enchantments());

            return;
        }

        this.bowUtils.spawnWebs(event.getEntity(), enchantedArrow);

        if (EnchantUtils.isEventActive(CEnchantments.PULL, arrowShooter, enchantedArrow.bow(), enchantedArrow.enchantments())) {
            Vector v = arrowShooter.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize()
                    .multiply(TuningConfig.fork().pullStrength());
            entity.setVelocity(v);
        }

        for (BowEnchantment bowEnchantment : this.bowEnchantmentManager.getBowEnchantments()) {
            CEnchantments enchantment = bowEnchantment.getEnchantment();

            if (!EnchantUtils.isEventActive(enchantment, arrowShooter, enchantedArrow.bow(), enchantedArrow.enchantments())) continue;

            if (bowEnchantment.isPotionEnchantment()) {
                bowEnchantment.getPotionEffects().forEach(effect -> entity.addPotionEffect(new PotionEffect(effect.potionEffect(), effect.duration(),
                        (bowEnchantment.isLevelAddedToAmplifier() ? enchantedArrow.getLevel(enchantment) : 0) + effect.amplifier())));
            } else {
                event.setDamage(event.getDamage() * ((bowEnchantment.isLevelAddedToAmplifier() ? enchantedArrow.getLevel(enchantment) : 0) + bowEnchantment.getDamageAmplifier()));
            }
        }

        if (shooter == null) return;
        if (!eligibleArrowTarget(shooter, entity)) return;

        applyHeadshot(drawnShot, entity, shooter, enchantedArrow, event, baseDamage);
        applyVirus(entity, shooter, enchantedArrow);
    }

    /**
     * Sniper: a fully drawn arrow whose impact lands in the top of the victim's
     * box multiplies the hit. The bonus is requested from the shared outgoing
     * budget instead of capping the hit again on its own, so Sniper and Longbow
     * on the same arrow share one limit. The accepted amount is applied by the
     * shared helper and must never be added a second time.
     */
    private void applyHeadshot(final SniperSettings.Shot shot, final LivingEntity victim, final Player shooter,
                               final EnchantedArrow enchantedArrow, final EntityDamageByEntityEvent event,
                               final double baseDamage) {
        final long now = nowMillis();
        pruneBowState(now);

        if (shot == null || !shot.impactKnown()) return;

        final SniperSettings settings = TuningConfig.sniper();
        if (shot.expired(now, settings.forceTtlMillis())) return;
        if (!settings.fullyDrawn(shot.force())) return;
        if (!settings.headshot(shot.impactY(), victim.getBoundingBox().getMinY(), victim.getBoundingBox().getHeight())) return;

        final int level = activeLevel(CEnchantments.SNIPER_HEADSHOT, shooter, enchantedArrow.bow(), enchantedArrow.enchantments());
        if (level <= 0) return;

        DamageModifiers.applyOutgoingBonus(event, baseDamage, event.getDamage() * (settings.multiplier(level) - 1.0));
    }

    /**
     * Virus: extends the remaining Wither and Poison of the victim by a share of
     * what is left, capped at twenty seconds per effect, then strips Regeneration.
     * With neither debuff present the proc does nothing at all, so it never eats
     * a Regeneration the victim did not earn back.
     */
    private void applyVirus(final LivingEntity victim, final Player shooter, final EnchantedArrow enchantedArrow) {
        final int level = activeLevel(CEnchantments.VIRUS, shooter, enchantedArrow.bow(), enchantedArrow.enchantments());
        if (level <= 0) return;

        final PotionEffect wither = runningEffect(victim.getPotionEffect(PotionEffectType.WITHER));
        final PotionEffect poison = runningEffect(victim.getPotionEffect(PotionEffectType.POISON));

        if (wither == null && poison == null) return;

        final VirusSettings settings = TuningConfig.virus();

        extendEffect(victim, PotionEffectType.WITHER, wither, settings, level);
        extendEffect(victim, PotionEffectType.POISON, poison, settings, level);
        victim.removePotionEffect(PotionEffectType.REGENERATION);
    }

    /**
     * A negative duration is the vanilla infinite marker. It is not a running
     * timer, so it must never be extended, re-added with duration 0, or used as
     * a reason to strip Regeneration.
     */
    private static PotionEffect runningEffect(final PotionEffect effect) {
        if (effect == null) return null;

        return VirusSettings.present(effect.getDuration()) ? effect : null;
    }

    private static void extendEffect(final LivingEntity victim, final PotionEffectType type, final PotionEffect effect,
                                     final VirusSettings settings, final int level) {
        if (effect == null) return;

        final int ticks = settings.extendedTicks(effect.getDuration(), level);
        if (ticks <= effect.getDuration()) return;

        victim.addPotionEffect(new PotionEffect(type, ticks, effect.getAmplifier(),
                effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
    }    /**
     * Teleportation: an arrow that lands on an ally moves the shooter to the
     * nearest safe spot around that ally. Every precondition failing is a silent
     * no-op, and the per-shooter window is spent only by a real teleport.
     */
    private void tryTeleportation(final Player shooter, final LivingEntity ally, final ItemStack bow,
                                  final Map<CEnchantment, Integer> enchantments) {
        if (shooter.getUniqueId().equals(ally.getUniqueId())) return;
        if (!Bukkit.isOwnedByCurrentRegion(shooter) || !Bukkit.isOwnedByCurrentRegion(ally)) return;
        if (!shooter.isOnline() || shooter.isDead() || ally.isDead()) return;

        final World world = ally.getWorld();
        if (!world.equals(shooter.getWorld())) return;

        final long now = nowMillis();
        final TeleportationSettings settings = TuningConfig.teleportation();
        if (!settings.available(this.teleportCooldowns, shooter.getUniqueId(), now)) return;

        final double distance = shooter.getLocation().distance(ally.getLocation());
        if (!settings.withinRange(distance)) return;

        final int level = activeLevel(CEnchantments.TELEPORTATION, shooter, bow, enchantments);
        if (level <= 0) return;

        final Location landing = safeLandingNear(ally.getLocation(), shooter, settings);
        if (landing == null) return;

        final UUID shooterId = shooter.getUniqueId();

        try {
            // Teleporting must run on the shooter's own region, never the victim's.
            shooter.getScheduler().run(this.plugin, scheduled -> {
                if (!shooter.isOnline() || shooter.isDead()) return;
                if (!settings.available(this.teleportCooldowns, shooterId, nowMillis())) return;
                // Several arrows can land in one tick; only one teleport may be in flight.
                if (!this.pendingTeleports.add(shooterId)) return;

                final long claimedAt = nowMillis();

                if (!landing.isChunkLoaded() || (Bukkit.isOwnedByCurrentRegion(landing) && !landingIsSafe(landing))) {
                    this.pendingTeleports.remove(shooterId);

                    return;
                }

                // teleportAsync is the only Folia-safe teleport; the window is spent only when it succeeds.
                shooter.teleportAsync(landing).thenAccept(success -> {
                    this.pendingTeleports.remove(shooterId);

                    if (success) settings.claim(this.teleportCooldowns, shooterId, claimedAt);
                });
            }, null);
        } catch (final Throwable ignored) {
            // The shooter left or the server is shutting the scheduler down.
        }
    }

    private static Location safeLandingNear(final Location ally, final Player shooter, final TeleportationSettings settings) {
        final World world = ally.getWorld();

        for (final TeleportationSettings.Offset offset : settings.offsets()) {
            final Location candidate = ally.clone().add(offset.dx(), 0.0, offset.dz());

            // A two-block search can cross a region border; never read another region's blocks.
            if (!Bukkit.isOwnedByCurrentRegion(candidate)) continue;
            if (candidate.getY() <= world.getMinHeight()) continue;
            if (!landingIsSafe(candidate)) continue;

            final Location landing = candidate.getBlock().getLocation().add(0.5, 0.0, 0.5);
            landing.setYaw(shooter.getLocation().getYaw());
            landing.setPitch(shooter.getLocation().getPitch());

            return landing;
        }

        return null;
    }

    private static boolean landingIsSafe(final Location location) {
        final Block feet = location.getBlock();
        final Block head = feet.getRelative(BlockFace.UP);
        final Block below = feet.getRelative(BlockFace.DOWN);

        final boolean hazard = hazard(feet.getType()) || hazard(head.getType()) || hazard(below.getType());

        return TeleportationSettings.safeLanding(feet.getType().isSolid(), head.getType().isSolid(),
                below.getType().isSolid(), hazard);
    }

    private static boolean hazard(final Material material) {
        return material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE
                || material == Material.CACTUS || material == Material.POWDER_SNOW
                || material == Material.MAGMA_BLOCK || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE || material == Material.SWEET_BERRY_BUSH
                || material == Material.WITHER_ROSE || material == Material.COBWEB;
    }

    /**
     * Farcast: a melee hit on a bow-holding player pushes the attacker away with
     * no damage. Projectiles, thorns and allies never proc it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarcast(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        final DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;

        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (attacker.getUniqueId().equals(shooter.getUniqueId())) return;
        if (!Bukkit.isOwnedByCurrentRegion(shooter) || !Bukkit.isOwnedByCurrentRegion(attacker)) return;
        if (isIgnored(shooter) || isIgnored(attacker) || !shooter.isOnline() || shooter.isDead()) return;

        final ItemStack weapon = shooter.getInventory().getItemInMainHand();
        if (!isBow(weapon.getType())) return;
        if (this.support.isFriendly(attacker, shooter)) return;
        if (!this.support.isCombatEnabled(shooter.getLocation())) return;

        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(weapon);
        if (enchantments.isEmpty()) return;

        final FarcastSettings settings = TuningConfig.farcast();
        final long now = nowMillis();
        if (!settings.available(this.farcastCooldowns, shooter.getUniqueId(), now)) return;

        final AttributeInstance maxHealth = shooter.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        final double multiplier = settings.chanceMultiplier(shooter.getHealth(), maxHealth.getValue());
        if (!EnchantUtils.isEventActive(CEnchantments.FARCAST, shooter, weapon, enchantments, multiplier)) return;

        final Location from = shooter.getLocation();
        final Location to = attacker.getLocation();
        final FarcastSettings.Push push = settings.push(to.getX() - from.getX(), to.getZ() - from.getZ());
        if (push.x() == 0.0 && push.z() == 0.0) return;

        attacker.setVelocity(new Vector(push.x(), 0.0, push.z()));
        settings.claim(this.farcastCooldowns, shooter.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBowDamageModifiers(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event) || event.getCause() != DamageCause.PROJECTILE) return;
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) return;
        final Entity hit = event.getEntity();
        if (isIgnored(arrow) || isIgnored(shooter) || isIgnored(hit)) return;

        // Cross-region rule: same skip as every other bow handler.
        if (!Bukkit.isOwnedByCurrentRegion(arrow) || !Bukkit.isOwnedByCurrentRegion(shooter)
                || !Bukkit.isOwnedByCurrentRegion(hit)) return;

        // Captured before this handler touches the hit, so Longbow and Sniper can
        // share one outgoing-bonus budget measured from the same base.
        final double baseDamage = event.getDamage();

        final ItemStack bow = bowWeapon(arrow);
        if (bow == null) return;

        final UUID shooterId = shooter.getUniqueId();
        final Effect effect = this.unfocusEffects.get(shooterId);
        if (effect != null) {
            if (effect.active(nowMillis())) event.setDamage(event.getDamage() * effect.multiplier());
            else this.unfocusEffects.remove(shooterId, effect);
        }

        if (!(hit instanceof LivingEntity target)) return;
        final EntityEquipment equipment = target.getEquipment();
        if (equipment == null || !isBow(equipment.getItemInMainHand().getType())) return;
        if (!(shooter instanceof Player player) || !eligibleArrowTarget(player, target)
                || !this.support.isCombatEnabled(arrow.getLocation())) return;

        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(bow);
        final int level = activeLevel(CEnchantments.LONGBOW, player, bow, enchantments);

        // Requested, not applied: the shared helper caps the hit's total CE bonus.
        if (level > 0) DamageModifiers.applyOutgoingBonus(event, baseDamage, event.getDamage() * (TuningConfig.longbow().multiplier(level) - 1.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLandedBowDamage(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event) || event.getCause() != DamageCause.PROJECTILE) return;
        // Cross-region rule: the arrow, the shooter and the victim must all be here.
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !Bukkit.isOwnedByCurrentRegion(arrow)) return;
        if (!(arrow.getShooter() instanceof Player player) || !Bukkit.isOwnedByCurrentRegion(player)) return;
        if (!(event.getEntity() instanceof LivingEntity target) || !Bukkit.isOwnedByCurrentRegion(target)) return;
        if (isIgnored(arrow) || isIgnored(player) || isIgnored(target) || !player.isOnline() || target.isDead()) return;

        // An absorbed hit still lands, but lifesteal only counts damage that will remove health.
        if (!(event.getFinalDamage() - event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION) > 0.0)) return;

        final ItemStack bow = bowWeapon(arrow);
        final Location impact = arrow.getLocation();
        if (bow == null || !this.support.isCombatEnabled(impact)) return;

        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(bow);
        if (enchantments.isEmpty()) return;

        // Fold eligible direct splash into this hit, never a second hit during the target's i-frames.
        final double arrowDamage = event.getDamage();
        final double combinedDamage = ExplosionHelper.explode(player, impact, rollExplosions(player, bow, enchantments),
                target, arrowDamage, this.support);
        // Bystander damage events can let another plugin move one of the original participants.
        if (!Bukkit.isOwnedByCurrentRegion(player) || !Bukkit.isOwnedByCurrentRegion(target)
                || !Bukkit.isOwnedByCurrentRegion(arrow)) return;

        if (combinedDamage > arrowDamage) {
            // The fold is the only place the splash reaches this event: the armor
            // side reads the extra HP through BowBlastContext while it is applied.
            BowBlastContext.fold(event, combinedDamage - arrowDamage, () -> event.setDamage(combinedDamage));
        }

        if (!eligibleArrowTarget(player, target)) return;

        final double finalDamage = event.getFinalDamage();
        final UUID targetId = target.getUniqueId();
        final long now = nowMillis();
        final int unfocus = activeLevel(CEnchantments.UNFOCUS, player, bow, enchantments);
        if (unfocus > 0) applyUnfocus(targetId, unfocus, now);

        if (target instanceof Player) {
            final int pacify = activeLevel(CEnchantments.PACIFY, player, bow, enchantments);
            if (pacify > 0) {
                pruneBowState(now);
                PacifyState.apply(targetId, TuningConfig.pacify(), pacify, now);
            }
        }

        if (finalDamage > 0.0 && event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) >= 0.0) {
            final int level = activeLevel(CEnchantments.ARROW_LIFESTEAL, player, bow, enchantments);
            if (level > 0) this.pendingLifesteal.put(event, new PendingLifesteal(player, bow, level));
        }

        if (activeLevel(CEnchantments.EAGLE_EYE, player, bow, enchantments) > 0) {
            final Location origin = arrow.getOrigin();
            if (origin != null && origin.isWorldLoaded() && origin.getWorld().equals(impact.getWorld())) {
                wearArmor(target, TuningConfig.eagleEye().wearPoints(origin.distance(impact)));
            }
        }
    }

    // Explicitly registered at MONITOR after Creeper Armor and Feign Death, including cancelled hits.
    public void onArrowLifestealResolved(final EntityDamageByEntityEvent event) {
        final PendingLifesteal pending = this.pendingLifesteal.remove(event);
        if (pending == null || event.isCancelled() || SyntheticHit.isSynthetic() || EventUtils.isIgnoredEvent(event)) return;

        final double finalDamage = event.getFinalDamage();
        if (!(finalDamage > 0.0 && event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) >= 0.0)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        final Player player = pending.shooter();
        if (!Bukkit.isOwnedByCurrentRegion(player) || !Bukkit.isOwnedByCurrentRegion(target)
                || !Bukkit.isOwnedByCurrentRegion(event.getDamager())) return;
        if (!player.isOnline() || player.isDead() || target.isDead()) return;

        final var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        final double health = player.getHealth();
        final double dealt = Math.min(finalDamage, target.getHealth());
        final double healed = TuningConfig.arrowLifesteal().healedHealth(health, maxHealth.getValue(), dealt);
        if (healed > health) player.setHealth(healed);
    }

    private void applyUnfocus(final UUID targetId, final int level, final long now) {
        pruneBowState(now);
        final var settings = TuningConfig.unfocus();
        this.unfocusEffects.compute(targetId, (id, oldEffect) -> settings.refresh(oldEffect, level, now));
    }

    private void pruneBowState(final long now) {
        final long previous = this.lastBowStateCleanup.get();
        if ((previous == Long.MIN_VALUE || now - previous >= 1_000L)
                && this.lastBowStateCleanup.compareAndSet(previous, now)) {
            // Expire idle mobs that never shoot again and never fire a player-quit event.
            this.unfocusEffects.values().removeIf(effect -> !effect.active(now));
            PacifyState.prune(now);
            this.drawnShots.values().removeIf(shot -> shot.expired(now, TuningConfig.sniper().forceTtlMillis()));
            this.teleportCooldowns.values().removeIf(last -> now - last >= TuningConfig.teleportation().cooldownMillis());
            this.farcastCooldowns.values().removeIf(last -> now - last >= TuningConfig.farcast().cooldownMillis());
            // Keep a recent window even across logout so reconnecting does not reset the limit.
            this.hellfireWindows.values().removeIf(window -> window.expired(now));
        }
    }

    /**
     * Eagle Eye wears the victim's armor through the vanilla damage path, so
     * Unbreaking, PlayerItemDamageEvent hooks (Reforged, Hardened) and the
     * breaking of a piece all behave. The returned stack may be empty and is
     * what the slot must hold afterwards.
     */
    private static void wearArmor(final LivingEntity target, final int points) {
        if (points <= 0) return;

        final EntityEquipment equipment = target.getEquipment();
        if (equipment == null) return;

        if (!(target instanceof Player player)) {
            // Mobs have no PlayerItemDamageEvent and no Hardened hook: raw wear.
            for (final EquipmentSlot slot : ARMOR_SLOTS) {
                final ItemStack piece = equipment.getItem(slot);
                if (piece.getType().isAir() || !(piece.getItemMeta() instanceof Damageable meta) || meta.isUnbreakable()) continue;

                final int max = meta.hasMaxDamage() ? meta.getMaxDamage() : piece.getType().getMaxDurability();
                final int worn = EagleEyeSettings.wornDamage(meta.getDamage(), max, points);
                if (worn <= meta.getDamage()) continue;

                meta.setDamage(worn);
                piece.setItemMeta(meta);
                equipment.setItem(slot, piece);
            }

            return;
        }

        for (final EquipmentSlot slot : ARMOR_SLOTS) {
            final ItemStack piece = equipment.getItem(slot);
            if (piece.getType().isAir()) continue;

            final ItemStack worn = Methods.wearArmor(player, piece, points);

            // An empty result means the piece broke in this hit.
            if (worn != piece) equipment.setItem(slot, worn);
        }
    }

    /**
     * The captured draw belongs to one hit. This runs for every arrow damage
     * event, cancelled or not, so an i-frame hit or a handler that returned
     * early cannot leave the entry for a later hit or the TTL.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrowDrawConsumed(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractArrow arrow) this.drawnShots.remove(arrow.getUniqueId());
    }

    /**
     * A shot snapshot must not outlive its projectile: despawn, void, chunk
     * unload and shooter quit never fire a hit this class could clean up on.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileRemoved(final EntityRemoveEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow) this.bowUtils.removeArrow(arrow.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowBlockImpact(final ProjectileHitEvent event) {
        // A stuck arrow is an arrow removal: drop its captured draw force. Entity
        // impacts keep the entry until the damage event consumes it.
        if (event.getHitEntity() == null && event.getEntity() instanceof AbstractArrow grounded) {
            this.drawnShots.remove(grounded.getUniqueId());
        }

        // Entity impacts use the damage event so protection and arrow cancellation win.
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)
                || event.getHitEntity() != null || event.getHitBlock() == null) return;
        // Cross-region rule: the arrow and the shooter must both be here.
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !Bukkit.isOwnedByCurrentRegion(arrow)) return;
        if (!(arrow.getShooter() instanceof Player player) || !Bukkit.isOwnedByCurrentRegion(player)) return;
        if (isIgnored(arrow) || isIgnored(player) || !player.isOnline()) return;

        final ItemStack bow = bowWeapon(arrow);
        final Location impact = arrow.getLocation();
        if (bow == null || !this.support.isCombatEnabled(impact)) return;

        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(bow);
        ExplosionHelper.explode(player, impact, rollExplosions(player, bow, enchantments), null, 0.0, this.support);
    }

    private List<Blast> rollExplosions(final Player player, final ItemStack bow, final Map<CEnchantment, Integer> enchantments) {
        if (enchantments.isEmpty()) return List.of();

        final int infernal = activeLevel(CEnchantments.INFERNAL, player, bow, enchantments);
        final int explosive = activeLevel(CEnchantments.EXPLOSIVE, player, bow, enchantments);
        final int hellfire = activeLevel(CEnchantments.HELLFIRE, player, bow, enchantments);
        if (infernal == 0 && explosive == 0 && hellfire == 0) return List.of();

        final List<Blast> blasts = new ArrayList<>(3);
        if (infernal > 0) {
            final InfernalSettings fire = TuningConfig.infernal();
            blasts.add(new Blast(fire.radius(), fire.damageHp(), fire.fireTicks(infernal), fire.fireTicks(infernal)));
        }
        if (explosive > 0) {
            final ExplosiveSettings blast = TuningConfig.explosive();
            blasts.add(new Blast(blast.radius(), blast.damageHp(explosive), 0, 0));
        }
        if (hellfire > 0 && claimHellfire(player.getUniqueId())) {
            final HellfireSettings hell = TuningConfig.hellfire();
            blasts.add(new Blast(hell.radius(), hell.damageHp(hellfire), 0, hell.fireTicks()));
        }
        return blasts;
    }

    private boolean claimHellfire(final UUID shooter) {
        pruneBowState(nowMillis());
        final boolean[] accepted = {false};
        this.hellfireWindows.compute(shooter, (id, previous) -> {
            final HellfireSettings.Window current = previous == null ? HellfireSettings.Window.EMPTY : previous;
            final HellfireSettings.Window next = current.claim(nowMillis());
            accepted[0] = next != current;
            return next;
        });
        return accepted[0];
    }

    private boolean eligibleArrowTarget(final Player player, final LivingEntity target) {
        return !isIgnored(target) && !target.isDead() && !player.getUniqueId().equals(target.getUniqueId())
                && !this.support.isFriendly(player, target);
    }

    private static int activeLevel(final CEnchantments enchant, final Player player, final ItemStack bow,
                                   final Map<CEnchantment, Integer> enchantments) {
        final CEnchantment key = enchant.getEnchantment();
        final int level = key == null ? 0 : enchantments.getOrDefault(key, 0);
        return level > 0 && EnchantUtils.isEventActive(enchant, player, bow, enchantments) ? level : 0;
    }

    private static int storedLevel(final CEnchantments enchant, final Map<CEnchantment, Integer> enchantments) {
        final CEnchantment key = enchant.getEnchantment();

        return key == null ? 0 : Math.max(0, enchantments.getOrDefault(key, 0));
    }

    /**
     * The bow behind an arrow hit. A MultiArrow arrow is spawned without its own
     * weapon on some paths, so the shot snapshot's bow is the fallback; the
     * caller must already own the arrow's region.
     */
    private ItemStack bowWeapon(final AbstractArrow arrow) {
        final ItemStack weapon = arrow.getWeapon();

        if (weapon != null && isBow(weapon.getType())) return weapon;

        final EnchantedArrow tracked = this.bowUtils.getEnchantedArrow(arrow);

        if (tracked == null || tracked.bow() == null) return null;

        return isBow(tracked.bow().getType()) ? tracked.bow() : null;
    }

    private static boolean isBow(final Material material) {
        return material == Material.BOW || material == Material.CROSSBOW;
    }

    private static boolean isIgnored(final Entity entity) {
        return EventUtils.isIgnoredUUID(entity.getUniqueId());
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWebBreak(BlockBreakEvent event) {
        if (!EventUtils.isIgnoredEvent(event) && this.bowUtils.getWebBlocks().contains(event.getBlock())) event.setCancelled(true);
    }
}
