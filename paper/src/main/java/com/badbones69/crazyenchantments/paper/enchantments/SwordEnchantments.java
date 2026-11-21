package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyManager;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.economy.CurrencyAPI;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.api.events.RageBreakEvent;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.api.utils.DamageModifiers;
import com.badbones69.crazyenchantments.paper.api.utils.DisintegrateSettings;
import com.badbones69.crazyenchantments.paper.api.utils.DoubleStrikeSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EnrageSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EntityUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.GreatswordSettings;
import com.badbones69.crazyenchantments.paper.api.utils.PacifySettings;
import com.badbones69.crazyenchantments.paper.api.utils.PacifySettings.PacifyState;
import com.badbones69.crazyenchantments.paper.api.utils.ShackleSupport;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.api.utils.VampireSettings;
import com.badbones69.crazyenchantments.paper.controllers.BossBarController;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SwordEnchantments implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    private final SupportUtils support = this.platform.getSupport();

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final CrazyManager crazyManager = this.starter.getCrazyManager();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    @NotNull
    private final BossBarController bossBarController = this.plugin.getBossBarController();

    // Double Strike: attacker -> millis of the last accepted proc.
    private final ConcurrentHashMap<UUID, Long> doubleStrikeCooldowns = new ConcurrentHashMap<>();

    // Vampire: wielder -> the token of the heal still owed. One entry per wielder; the delayed task
    // removes its own token, and a death, quit or world change removes it to cancel the heal.
    private final ConcurrentHashMap<UUID, Object> vampirePending = new ConcurrentHashMap<>();

    // Double Strike: a proc waits for MONITOR, so a hit cancelled after HIGHEST never deals the second hit.
    private final Map<EntityDamageByEntityEvent, PendingStrike> pendingDoubleStrike = new ConcurrentHashMap<>();

    // The record the cooldowns were claimed under; a reload publishes a new one.
    private volatile DoubleStrikeSettings doubleStrikeSettings;

    // The accepted second instance, in HP, folded into the event at MONITOR.
    private record PendingStrike(double amount) {}

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event) || EventUtils.isIgnoredUUID(event.getDamager().getUniqueId())) return;

        // Folia: the attacker's hand and enchants are only safe to read when this region owns
        // the attacker, so the ownership check precedes every provider call that reads it.
        if (event.getDamager() instanceof final Player owner && !Bukkit.isOwnedByCurrentRegion(owner)) return;

        if (this.support.isFriendly(event.getDamager(), event.getEntity())) return;

        if (this.crazyManager.isBreakRageOnDamageOn() && event.getEntity() instanceof Player player) {
            this.crazyManager.getCEPlayer(player).ifPresent(cePlayer -> {
                RageBreakEvent rageBreakEvent = new RageBreakEvent(player, event.getDamager(), this.methods.getItemInHand(player));
                this.plugin.getServer().getPluginManager().callEvent(rageBreakEvent);

                if (!rageBreakEvent.isCancelled() && cePlayer.hasRage()) {
                    cePlayer.getRageTask().cancel();
                    cePlayer.setRageMultiplier(0.0);
                    cePlayer.setRageLevel(0);
                    cePlayer.setRage(false);

                    rageInformPlayer(player, Messages.RAGE_DAMAGED, 0f);
                }
            });
        }

        if (!(event.getEntity() instanceof LivingEntity en)) return;
        if (!(event.getDamager() instanceof final Player damager)) return;

        ItemStack item = this.methods.getItemInHand(damager);

        if (event.getEntity().isDead()) return;

        // Base weapon damage: captured before any CE bonus multiplier in this handler.
        final double baseDamage = event.getDamage();

        Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

        this.crazyManager.getCEPlayer(damager).ifPresent(cePlayer -> {
            // Check if CEPlayer is null as plugins like citizen use Player objects.
            final var rageHit = PacifySettings.rageHit(PacifyState.remainingMillis(damager.getUniqueId()) > 0L, cePlayer.hasRage(),
                    levelOf(enchantments, CEnchantments.RAGE) > 0,
                    () -> EnchantUtils.isEventActive(CEnchantments.RAGE, damager, item, enchantments));
            if (rageHit != PacifySettings.RageHit.NONE) {
                if (cePlayer.hasRage()) {
                    cePlayer.getRageTask().cancel();

                    if (rageHit == PacifySettings.RageHit.GAIN) {
                        if (cePlayer.getRageMultiplier() <= this.crazyManager.getRageMaxLevel())
                            cePlayer.setRageMultiplier(cePlayer.getRageMultiplier() + (enchantments.get(CEnchantments.RAGE.getEnchantment()) * crazyManager.getRageIncrement()));

                        int rageUp = cePlayer.getRageLevel() + 1;

                        if (cePlayer.getRageMultiplier().intValue() >= rageUp) {
                            rageInformPlayer(damager, Messages.RAGE_RAGE_UP, Map.of("%Level%", String.valueOf(rageUp)), ((float) rageUp / (float) (this.crazyManager.getRageMaxLevel() + 1)));
                            cePlayer.setRageLevel(rageUp);
                        }
                    }

                    event.setDamage(event.getDamage() * cePlayer.getRageMultiplier());
                } else {
                    cePlayer.setRageMultiplier(1.0);
                    cePlayer.setRage(true);
                    cePlayer.setRageLevel(1);

                    rageInformPlayer(damager, Messages.RAGE_BUILDING, ((float) cePlayer.getRageLevel() / (float) this.crazyManager.getRageMaxLevel()));
                }

                cePlayer.setRageTask(new FoliaScheduler(this.plugin, null, damager) {
                    @Override
                    public void run() {
                        final long frozenMillis = PacifyState.remainingMillis(damager.getUniqueId());
                        if (frozenMillis > 0L) {
                            // Fusion's scheduler wrapper is one-shot; requeue this body on the same entity.
                            cePlayer.setRageTask(damager.getScheduler().runDelayed(SwordEnchantments.this.plugin,
                                    task -> this.run(), null, (frozenMillis + 49L) / 50L));
                            return;
                        }

                        cePlayer.setRageMultiplier(0.0);
                        cePlayer.setRage(false);
                        cePlayer.setRageLevel(0);

                        rageInformPlayer(damager, Messages.RAGE_COOLED_DOWN, 0f);
                    }
                }.runDelayed(40)); // Two seconds at 20 TPS.
            }
        });

        if (en instanceof Player player && Bukkit.isOwnedByCurrentRegion(player)
                && EnchantUtils.isEventActive(CEnchantments.SKILLSWIPE, damager, item, enchantments)) {
            int amount = 4 + enchantments.get(CEnchantments.SKILLSWIPE.getEnchantment());
            try {
                CurrencyAPI.transferExperiencePoints(player, damager, amount);
            } catch (RuntimeException failure) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Skill Swipe XP transfer failed from " + player.getUniqueId() + " to " + damager.getUniqueId(), failure);
            }
        }

        if (damager.getHealth() > 0 && EnchantUtils.isEventActive(CEnchantments.LIFESTEAL, damager, item, enchantments)) {
            int steal = enchantments.get(CEnchantments.LIFESTEAL.getEnchantment());

            final double entityHealth = damager.getHealth();

            Optional.ofNullable(damager.getAttribute(Attribute.MAX_HEALTH)).ifPresentOrElse(attribute -> {
                final double maxHealth = attribute.getValue();

                if (entityHealth + steal < maxHealth) damager.setHealth(entityHealth + steal);

                if (entityHealth + steal >= maxHealth) damager.setHealth(maxHealth);
            }, () -> this.fusion.log(Level.WARNING, "Player %s did not have the MAX_HEALTH attribute when using %s enchantment!", damager.getName(), "Lifesteal"));
        }

        if (EnchantUtils.isEventActive(CEnchantments.NUTRITION, damager, item, enchantments)) {
            if (damager.getSaturation() + (2 * enchantments.get(CEnchantments.NUTRITION.getEnchantment())) <= 20) damager.setSaturation(damager.getSaturation() + (2 * enchantments.get(CEnchantments.NUTRITION.getEnchantment())));

            if (damager.getSaturation() + (2 * enchantments.get(CEnchantments.NUTRITION.getEnchantment())) >= 20) damager.setSaturation(20);
        }

        // Vampire: a proc schedules one capped heal a few seconds after the strike. While a heal is
        // already pending the roll is skipped, so extra strikes neither proc nor fire the use event.
        final int vampireLevel = levelOf(enchantments, CEnchantments.VAMPIRE);

        if (vampireLevel > 0 && damager.getHealth() > 0 && !this.vampirePending.containsKey(damager.getUniqueId())
                && EnchantUtils.isEventActive(CEnchantments.VAMPIRE, damager, item, enchantments)) {
            scheduleVampireHeal(damager, vampireLevel);
        }

        if (EnchantUtils.isEventActive(CEnchantments.BLINDNESS, damager, item, enchantments)) {
            en.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3 * 20, enchantments.get(CEnchantments.BLINDNESS.getEnchantment()) - 1));
        }

        if (EnchantUtils.isEventActive(CEnchantments.FASTTURN, damager, item, enchantments)) {
            event.setDamage(event.getDamage() + (event.getDamage() / TuningConfig.fork().fastTurnBonusDivisor()));
        }

        if (EnchantUtils.isEventActive(CEnchantments.LIGHTWEIGHT, damager, item, enchantments)) {
            damager.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 5 * 20, enchantments.get(CEnchantments.LIGHTWEIGHT.getEnchantment()) - 1));
        }

        if (EnchantUtils.isEventActive(CEnchantments.OBLITERATE, damager, item, enchantments)
                && !ShackleSupport.suppressesKnockback(en, damager, item, enchantments)) {
            // A hand-set velocity never fires EntityKnockbackByEntityEvent, so Shackle's
            // rule is asked here directly instead of relying on that event.
            event.getEntity().setVelocity(damager.getLocation().getDirection().multiply(2).setY(1.25));
        }

        if (EnchantUtils.isEventActive(CEnchantments.PARALYZE, damager, item, enchantments)) {

            for (LivingEntity entity : this.methods.getNearbyLivingEntities(2D, damager)) {
                EntityDamageEvent damageByEntityEvent = new EntityDamageEvent(entity, EntityDamageEvent.DamageCause.MAGIC, DamageSource.builder(DamageType.INDIRECT_MAGIC).withDirectEntity(damager).build(), 5D);
                this.methods.entityEvent(damager, entity, damageByEntityEvent);
            }

            en.getWorld().strikeLightningEffect(en.getLocation());
            en.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, 2));
            en.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 3 * 20, 2));
        }

        if (EnchantUtils.isEventActive(CEnchantments.SLOWMO, damager, item, enchantments)) {
            en.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, enchantments.get(CEnchantments.SLOWMO.getEnchantment())));
        }

        if (EnchantUtils.isEventActive(CEnchantments.TRAP, damager, item, enchantments)) {
            en.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, 2));
        }

        if (EnchantUtils.isEventActive(CEnchantments.VIPER, damager, item, enchantments)) {
            en.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 5 * 20, enchantments.get(CEnchantments.VIPER.getEnchantment())));
        }

        if (EnchantUtils.isEventActive(CEnchantments.FAMISHED, damager, item, enchantments)) {
            final var settings = TuningConfig.fork().famished();
            en.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, settings.durationTicks(), settings.amplifier()));
        }

        // Insomnia: a direct sword swing (ENTITY_ATTACK only: no sweep, projectile or synthetic hit)
        // that deals positive damage to an enemy player. The material guard is hard-coded, so legacy
        // Insomnia lore on armor or any other held item is inert whatever the config says.
        if (en instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getFinalDamage() > 0.0 && isInsomniaWeapon(item) && EnchantUtils.isEventActive(CEnchantments.INSOMNIA, damager, item, enchantments)) {
            en.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, INSOMNIA_TICKS, INSOMNIA_AMPLIFIER));
            en.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, INSOMNIA_TICKS, INSOMNIA_AMPLIFIER));
            en.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, INSOMNIA_TICKS, 0));
        }

        applyDuelEnchants(event, damager, en, item, enchantments, baseDamage);
    }

    /** Insomnia debuff length: three seconds, the same as Ice Aspect, Trap and Paralyze. */
    static final int INSOMNIA_TICKS = 3 * 20;

    /** Slowness II and Mining Fatigue II; Nausea is level I. */
    static final int INSOMNIA_AMPLIFIER = 1;

    /** Insomnia only works from a sword; legacy armor carrying its lore never procs when held. */
    static boolean isInsomniaWeapon(final ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    private static int levelOf(final Map<CEnchantment, Integer> enchantments, final CEnchantments enchant) {
        final CEnchantment key = enchant.getEnchantment();

        return key == null ? 0 : enchantments.getOrDefault(key, 0);
    }

    /**
     * Assassin, Enrage, Greatsword, Thundering Blow and Double Strike on a direct
     * melee hit. Runs after the shared damage layer (registered before this
     * listener at HIGHEST), so the multipliers compose with Armored/Tank and
     * Deathbringer instead of replacing them. Assassin, Enrage and Greatsword are
     * always-on and multiply with each other, and Thundering Blow (chance-based)
     * adds its flat HP after the multipliers so it is never scaled. Every accepted
     * bonus draws on the one cumulative CE bonus budget
     * ({@link DamageModifiers#applyOutgoingBonus}), so no combination of lanes can
     * exceed the configured cap; Assassin's beyond-range penalty lowers the hit and
     * never draws on that budget. Double Strike (chance-based) adds a second
     * instance of the pre-multiplier damage once the hit has landed (see
     * {@link #onDoubleStrikeResolved}). The caller established that this region owns
     * the attacker.
     */
    private void applyDuelEnchants(
            final EntityDamageByEntityEvent event,
            final Player damager,
            final LivingEntity victim,
            final ItemStack item,
            final Map<CEnchantment, Integer> enchantments,
            final double baseDamage
    ) {
        final EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;

        final int assassinLevel = levelOf(enchantments, CEnchantments.ASSASSIN);
        final int enrageLevel = levelOf(enchantments, CEnchantments.ENRAGE);
        final int thunderingLevel = levelOf(enchantments, CEnchantments.THUNDERING_BLOW);
        final int doubleStrikeLevel = levelOf(enchantments, CEnchantments.DOUBLE_STRIKE);
        final int greatswordLevel = levelOf(enchantments, CEnchantments.GREATSWORD);
        final int executeLevel = levelOf(enchantments, CEnchantments.EXECUTE);

        if (assassinLevel < 1 && enrageLevel < 1 && thunderingLevel < 1 && doubleStrikeLevel < 1 && greatswordLevel < 1
                && executeLevel < 1) return;

        double multiplier = 1.0;

        if (assassinLevel > 0 && damager.getWorld().equals(victim.getWorld())) {
            final double distance = damager.getLocation().distance(victim.getLocation());

            if (EnchantUtils.isEventActive(CEnchantments.ASSASSIN, damager, item, enchantments)) {
                multiplier = DamageModifiers.multiply(multiplier, TuningConfig.assassin().multiplier(assassinLevel, distance));
            }
        }

        if (enrageLevel > 0) {
            final AttributeInstance maxHealth = damager.getAttribute(Attribute.MAX_HEALTH);
            final EnrageSettings enrage = TuningConfig.enrage();

            // Current and max health only (absorption is ignored). A full-health wielder gains
            // nothing, so skip the use event for that hit.
            final double enrageMultiplier = maxHealth == null ? 1.0 : enrage.multiplier(enrageLevel, damager.getHealth(), maxHealth.getValue());

            if (enrageMultiplier > 1.0 && EnchantUtils.isEventActive(CEnchantments.ENRAGE, damager, item, enchantments)) {
                multiplier = DamageModifiers.multiply(multiplier, enrageMultiplier);
            }
        }

        if (greatswordLevel > 0) {
            final EntityEquipment equipment = victim.getEquipment();
            final String held = equipment == null ? "" : equipment.getItemInMainHand().getType().name();

            if (GreatswordSettings.holdsBow(held)
                    && EnchantUtils.isEventActive(CEnchantments.GREATSWORD, damager, item, enchantments)) {
                multiplier = DamageModifiers.multiply(multiplier, TuningConfig.greatsword().multiplier(greatswordLevel, true));
            }
        }

        final double beforeMultipliers = event.getDamage();

        if (multiplier > 1.0) {
            // One cumulative cap for every lane: the shared layer fixed this event's
            // baseline first, so only what is left of the budget is accepted here.
            DamageModifiers.applyOutgoingBonus(event, baseDamage, beforeMultipliers * multiplier - beforeMultipliers);
        } else if (multiplier < 1.0) {
            // Assassin's beyond-range penalty lowers the hit. A penalty is not a bonus,
            // so it is applied directly and never draws on the shared bonus budget.
            event.setDamage(beforeMultipliers * multiplier);
        }

        if (executeLevel > 0) {
            final AttributeInstance victimMax = victim.getAttribute(Attribute.MAX_HEALTH);
            final double fraction = victimMax == null ? 0.0
                    : TuningConfig.execute().bonusFraction(executeLevel, victim.getHealth(), victimMax.getValue());

            // Only a low target gains anything, so a healthy one skips the use event entirely. The
            // bonus rides the shared outgoing budget and is a share of the hit so far, flat HP excluded.
            if (fraction > 0.0 && EnchantUtils.isEventActive(CEnchantments.EXECUTE, damager, item, enchantments)) {
                DamageModifiers.applyOutgoingBonus(event, baseDamage, event.getDamage() * fraction);
            }
        }

        if (thunderingLevel > 0 && EnchantUtils.isEventActive(CEnchantments.THUNDERING_BLOW, damager, item, enchantments)) {
            // Visual only: strikeLightningEffect sets no fire, damages nobody and the effect entity is short-lived.
            victim.getWorld().strikeLightningEffect(victim.getLocation());

            DamageModifiers.applyOutgoingBonus(event, baseDamage, TuningConfig.thunderingBlow().bonusHp(thunderingLevel));
        }

        // Keep the hit at two decimals like the shared layer does.
        if (Double.compare(event.getDamage(), beforeMultipliers) != 0) {
            event.setDamage(DamageModifiers.roundDamage(beforeMultipliers, event.getDamage()));
        }

        if (doubleStrikeLevel > 0) {
            final DoubleStrikeSettings doubleStrike = TuningConfig.doubleStrike();

            // A reload publishes a new settings record; cooldowns claimed under the old
            // one are dropped with it.
            if (doubleStrike != this.doubleStrikeSettings) {
                this.doubleStrikeSettings = doubleStrike;
                this.doubleStrikeCooldowns.clear();
            }

            final UUID attackerId = damager.getUniqueId();
            final long now = System.nanoTime() / 1_000_000L;

            // Defer both the second component and the cooldown claim until the hit survives to MONITOR.
            if (!doubleStrike.onCooldown(this.doubleStrikeCooldowns, attackerId, now)
                    && baseDamage > 0.0
                    && EnchantUtils.isEventActive(CEnchantments.DOUBLE_STRIKE, damager, item, enchantments)) {
                this.pendingDoubleStrike.put(event, new PendingStrike(baseDamage));
            }
        }
    }

    /**
     * Double Strike's second instance, once the outer hit has really landed. A hit
     * another plugin cancelled, or one that did no damage, adds nothing. The entry
     * is removed unconditionally so a pass that finds nothing to do cannot leave it
     * behind.
     *
     * <p>The second instance is folded into the same event instead of being dealt as
     * a nested hit. Paper dispatches the damage event before the victim's 20-tick
     * hurt window is claimed, so a nested damage call from this handler claims the
     * window first and the outer hit is then swallowed: the net result is one hit.
     * Clearing that window is not allowed. Adding the instance to the event means
     * the one hurt carries both, both are still reduced by armor and protection, and
     * neither can be absorbed. The instance's amount is the damage that entered the
     * sword handler, so it already includes the shared damage-modifier results.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDoubleStrikeResolved(EntityDamageByEntityEvent event) {
        final PendingStrike strike = this.pendingDoubleStrike.remove(event);

        if (strike == null || event.isCancelled() || !(event.getFinalDamage() > 0.0)) return;

        final DoubleStrikeSettings doubleStrike = TuningConfig.doubleStrike();
        final long now = System.nanoTime() / 1_000_000L;
        if (!doubleStrike.claim(this.doubleStrikeCooldowns, event.getDamager().getUniqueId(), now)) return;

        event.setDamage(event.getDamage() + strike.amount());
    }

    /**
     * Disintegrate: on a proc, every worn enemy armor piece loses durability.
     * Runs at MONITOR so the wear is based on the final damage of a hit that
     * really landed. Only a player victim has armor, and a piece is never
     * destroyed: the wear is clamped to one point of durability remaining before
     * it goes through vanilla, so Unbreaking, Hardened and PlayerItemDamageEvent
     * all apply to it. Folia: only entities this region owns are read or written.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisintegrate(EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        final EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player damager)) return;

        if (EventUtils.isIgnoredUUID(damager.getUniqueId())) return;

        // No live entity reads before ownership is known.
        if (!Bukkit.isOwnedByCurrentRegion(damager) || !Bukkit.isOwnedByCurrentRegion(victim)) return;
        if (victim.isDead() || event.getFinalDamage() <= 0.0) return;
        if (this.support.isFriendly(damager, victim)) return;

        final ItemStack item = this.methods.getItemInHand(damager);
        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

        if (levelOf(enchantments, CEnchantments.DISINTEGRATE) < 1) return;
        if (!EnchantUtils.isEventActive(CEnchantments.DISINTEGRATE, damager, item, enchantments)) return;

        wearArmor(victim, TuningConfig.disintegrate().points(event.getFinalDamage()));
    }

    private void wearArmor(final Player victim, final int points) {
        if (points <= 0) return;

        final EntityEquipment equipment = victim.getEquipment();

        if (equipment == null) return;

        for (final EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final ItemStack piece = equipment.getItem(slot);

            if (piece == null || piece.getType().isAir()) continue;

            final ItemMeta meta = piece.getItemMeta();

            if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) continue;

            final int maxDurability = damageable.hasMaxDamage() ? damageable.getMaxDamage() : piece.getType().getMaxDurability();

            if (maxDurability <= 0) continue;

            // Never destroyed: clamp to one point left, then let vanilla wear decide the rest.
            final int amount = Math.min(points, maxDurability - 1 - damageable.getDamage());

            if (amount <= 0) continue;

            // Vanilla wear, so Hardened, Unbreaking and PlayerItemDamageEvent all see it.
            final ItemStack worn = Methods.wearArmor(victim, piece, amount);

            if (!worn.equals(piece)) equipment.setItem(slot, worn);
        }
    }

    /**
     * Vampire: heals the wielder once, {@code delay} after the strike that proved it, by the
     * level's amount capped at max health. The heal runs on the wielder's own scheduler. A
     * death, quit or world change drops the pending entry, so the late task finds nothing to do.
     */
    private void scheduleVampireHeal(final Player wielder, final int level) {
        final UUID id = wielder.getUniqueId();
        final VampireSettings vampire = TuningConfig.vampire();
        final double heal = vampire.heal(level);
        final Object token = new Object();

        if (this.vampirePending.putIfAbsent(id, token) != null) return;

        try {
            final var task = wielder.getScheduler().runDelayed(this.plugin, scheduled -> {
                // Only the token this strike claimed may heal; a cleared entry means it was cancelled.
                if (!this.vampirePending.remove(id, token)) return;
                if (!wielder.isValid() || wielder.isDead()) return;

                final AttributeInstance maxHealth = wielder.getAttribute(Attribute.MAX_HEALTH);

                if (maxHealth == null) {
                    this.fusion.log(Level.WARNING, "Player %s did not have the MAX_HEALTH attribute when using %s enchantment!", wielder.getName(), "Vampire");

                    return;
                }

                wielder.setHealth(VampireSettings.healed(wielder.getHealth(), maxHealth.getValue(), heal));
            }, () -> this.vampirePending.remove(id, token), vampire.delayTicks());

            if (task == null) this.vampirePending.remove(id, token);
        } catch (final IllegalPluginAccessException exception) {
            this.vampirePending.remove(id, token);

            if (this.plugin.isEnabled()) throw exception;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.doubleStrikeCooldowns.remove(event.getPlayer().getUniqueId());
        this.vampirePending.remove(event.getPlayer().getUniqueId());
    }

    /** A respawn or a world change starts a fresh cooldown window. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.doubleStrikeCooldowns.remove(event.getPlayer().getUniqueId());
        this.vampirePending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // A respawn starts a fresh Double Strike window (a reload drops it too).
        this.doubleStrikeCooldowns.remove(event.getEntity().getUniqueId());

        // A heal Vampire still owes a wielder who died is cancelled.
        this.vampirePending.remove(event.getEntity().getUniqueId());

        if (event.getEntity().getKiller() == null) return;

        Player damager = event.getEntity().getKiller();
        Player player = event.getEntity();
        ItemStack item = this.methods.getItemInHand(damager);
        Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

        if (EnchantUtils.isEventActive(CEnchantments.HEADLESS, damager, item, enchantments)) {
            ItemStack head = new ItemBuilder().setMaterial("PLAYER_HEAD").setPlayerName(player.getName()).build();
            event.getDrops().add(head);
        }

        if (EnchantUtils.isEventActive(CEnchantments.REVENGE, damager, item, enchantments)) {
            final int radius = TuningConfig.fork().revengeRadius();
            for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (!this.support.isFriendly(entity, player)) continue;

                Player ally = (Player) entity; //todo() check if entity is player

                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 1));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 5 * 20, 1));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player damager = event.getEntity().getKiller();
            ItemStack item = this.methods.getItemInHand(damager);
            Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

            if (EnchantUtils.isEventActive(CEnchantments.INQUISITIVE, damager, item, enchantments)) {
                event.setDroppedExp(event.getDroppedExp() * (enchantments.get(CEnchantments.INQUISITIVE.getEnchantment()) + 1));
            }

            Material headMat = EntityUtils.getHeadMaterial(event.getEntity());
            if (headMat != null && !EventUtils.containsDrop(event, headMat)) {
                double multiplier = this.crazyManager.getDecapitationHeadMap().getOrDefault(headMat, 0.0);
                if (multiplier != 0.0 && EnchantUtils.isEventActive(CEnchantments.HEADLESS, damager, item, enchantments, multiplier)) {
                    ItemStack head = new ItemBuilder().setMaterial(headMat).build();
                    event.getDrops().add(head);
                }
			}

            // The entity that is killed is a player.
            if (event.getEntity() instanceof Player && EnchantUtils.isEventActive(CEnchantments.CHARGE, damager, item, enchantments)) {
                int radius = TuningConfig.fork().chargeBaseRadius() + enchantments.get(CEnchantments.CHARGE.getEnchantment());
                damager.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 1));

                damager.getNearbyEntities(radius, radius, radius).stream().filter(entity ->
                        this.support.isFriendly(entity, damager)).forEach(entity ->
                        ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 1)));
            }
        }
    }

    private void rageInformPlayer(Player player, Messages message, Map<String, String> placeholders, float progress) {
        if (message.getMessageNoPrefix().isBlank()) return;

        if (this.crazyManager.useRageBossBar()) {
            this.bossBarController.updateBossBar(player, message.getMessageNoPrefix(placeholders), progress);
        } else {
            // Action bar instead of chat: rage fires on every hit, so chat would flood.
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message.getMessageNoPrefix(placeholders)));
        }
    }

    private void rageInformPlayer(Player player, Messages message, float progress) {
        rageInformPlayer(player, message, Map.of(), progress);
    }
}
