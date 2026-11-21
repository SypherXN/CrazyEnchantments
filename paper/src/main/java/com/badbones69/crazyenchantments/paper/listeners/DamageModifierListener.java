package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.DamageModifierSettings;
import com.badbones69.crazyenchantments.paper.api.utils.DamageModifiers;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.api.utils.ValorSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Applies the shared weapon-class damage modifiers once per hit, before the
 * existing CE enchant handlers (registered before them at
 * {@link EventPriority#HIGHEST}) so later lethality checks such as System
 * Reboot see the modified damage.
 *
 * <p>Incoming reductions are summed across the victim's worn pieces and applied
 * once. An arrow hit is arrow damage only; the attacker's main hand is read
 * only for direct melee. Outgoing boosts come from the attacker's worn pieces:
 * Marksman for the wearer's own arrows and Deathbringer for any hit the wearer
 * deals. This listener fixes the event's cumulative CE bonus budget first, so the
 * sword, axe and bow lanes draw on the same 20 HP cap instead of each capping its
 * own addition. Folia: only the owning region's entities are read, and entity-based
 * integrations are skipped for a remote shooter.</p>
 */
public class DamageModifierListener implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final Starter starter = this.plugin.getStarter();

    private final SupportUtils support = this.plugin.getPlatform().getSupport();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // Strength's vanilla +3 per level is already in the event damage, so trim the excess
    // at NORMAL: HIGHEST's multipliers (Rage, Marksman, Deathbringer) then scale the
    // reduced value, exactly as they would scale a hit from an unenchanted attacker.
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStrengthDamage(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        // Vanilla Strength raises melee damage only, so a player's own hit is the only case.
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!Bukkit.isOwnedByCurrentRegion(attacker)) return;

        final PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        if (strength == null) return;

        event.setDamage(DamageModifiers.strengthDamage(event.getDamage(), strength.getAmplifier() + 1,
                TuningConfig.damageModifiers().strengthDamagePerLevel()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        // The event fires on the victim's region; never read another region's entity.
        if (!Bukkit.isOwnedByCurrentRegion(event.getEntity())) return;

        final Entity damager = event.getDamager();
        final DamageCause cause = event.getCause();
        final boolean arrow = cause == DamageCause.PROJECTILE && damager instanceof AbstractArrow && !(damager instanceof Trident);
        final Entity source = damager instanceof Projectile ? event.getDamageSource().getCausingEntity() : damager;
        final Player attacker = source instanceof Player player ? player : null;
        final Player victim = event.getEntity() instanceof Player player ? player : null;

        final boolean attackerOwned = attacker != null && Bukkit.isOwnedByCurrentRegion(attacker);
        if (Bukkit.isOwnedByCurrentRegion(damager) && EventUtils.isIgnoredUUID(damager.getUniqueId())) return;

        // Entity-based integrations (friendly/team) need the owning region.
        // A cross-region arrow still gets Heavy on the victim's side without
        // querying the remote shooter.
        if (attackerOwned && (EventUtils.isIgnoredUUID(attacker.getUniqueId())
                || (victim != null && this.support.isFriendly(attacker, victim)))) return;

        final DamageModifierSettings settings = TuningConfig.damageModifiers();
        final double originalDamage = event.getDamage();

        // Fix this event's cumulative CE bonus baseline before the reduction. Every
        // lane that adds a bonus (Marksman, Deathbringer, the axe and bow lanes and the
        // sword multipliers) then draws on the same 20 HP budget instead of each
        // capping its own addition.
        DamageModifiers.applyOutgoingBonus(event, originalDamage, 0.0);

        double damage = originalDamage;

        if (victim != null) {
            final EntityEquipment meleeEquipment = (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK)
                    && damager instanceof LivingEntity living && Bukkit.isOwnedByCurrentRegion(living) ? living.getEquipment() : null;
            final double reductionPp = switch (DamageModifiers.reductionFor(arrow, meleeEquipment != null,
                    () -> meleeEquipment.getItemInMainHand().getType().name())) {
                case HEAVY -> wornPP(victim, CEnchantments.HEAVY, settings.heavyReductionPerLevelPp());
                case ARMORED -> wornPP(victim, CEnchantments.ARMORED, settings.armoredReductionPerLevelPp());
                case TANK -> wornPP(victim, CEnchantments.TANK, settings.tankReductionPerLevelPp());
                case NONE -> 0.0;
            };

            // Valor stacks with the class reduction above: it counts whatever the hit is, but only
            // while the wearer holds a sword. The victim is on this region (checked at the top).
            final double valorPp = ValorSettings.isSword(victim.getEquipment().getItemInMainHand().getType().name())
                    ? wornPP(victim, CEnchantments.VALOR, TuningConfig.valor().reductionPerLevelPp())
                    : 0.0;

            damage = DamageModifiers.reduceOnce(damage,
                    DamageModifiers.sumPP(settings.incomingReductionCapPp(), reductionPp, valorPp));
        }

        if (attackerOwned) {
            final double marksmanPp = arrow ? wornPP(attacker, CEnchantments.MARKSMAN, settings.marksmanBonusPerLevelPp()) : 0.0;
            final int deathbringerProcs = deathbringerProcs(attacker);

            final double multiplier = DamageModifiers.multiply(
                    DamageModifiers.outgoingMultiplier(DamageModifiers.sumPP(settings.marksmanBonusCapPp(), marksmanPp)),
                    DamageModifiers.deathbringerMultiplier(deathbringerProcs)
            );

            // The requested bonus is the rise over the reduced hit; the shared budget may
            // accept only part of it, and applies whatever it accepts itself.
            final double requested = damage * multiplier - damage;

            if (requested > 0.0) {
                event.setDamage(damage);
                damage += DamageModifiers.applyOutgoingBonus(event, originalDamage, requested);
            }
        }

        final double rounded = DamageModifiers.roundDamage(originalDamage, damage);
        if (Double.compare(rounded, originalDamage) != 0) event.setDamage(rounded);
    }

    /**
     * Percentage points the wearer's active pieces of {@code enchant} add up to.
     * Each piece is rolled on its own.
     */
    private double wornPP(final Player wearer, final CEnchantments enchant, final double perLevelPp) {
        final CEnchantment target = enchant.getEnchantment();

        if (target == null) return 0.0;

        double total = 0.0;

        for (final ItemStack armor : wearer.getEquipment().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;

            final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(armor);
            final Integer level = enchantments.get(target);

            if (level == null || level < 1) continue;
            if (!EnchantUtils.isEventActive(enchant, wearer, armor, enchantments)) continue;

            total += DamageModifiers.piecePP(perLevelPp, level);
        }

        return total;
    }

    /**
     * Rolls Deathbringer on every worn piece with an exact-percent roll, so a
     * declared 3% really is 3%. Any number of procs is still one multiplier.
     */
    private int deathbringerProcs(final Player wearer) {
        final CEnchantment target = CEnchantments.DEATHBRINGER.getEnchantment();

        if (target == null) return 0;

        int procs = 0;

        for (final ItemStack armor : wearer.getEquipment().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;

            final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(armor);
            final Integer level = enchantments.get(target);

            if (level == null || level < 1) continue;
            if (!EnchantUtils.isEventActive(CEnchantments.DEATHBRINGER, wearer, armor, enchantments)) continue;

            procs++;
        }

        return procs;
    }
}
