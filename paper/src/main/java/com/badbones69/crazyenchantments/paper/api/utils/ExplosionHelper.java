package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class ExplosionHelper {

    private ExplosionHelper() {}

    public record Blast(double radius, double damageHp, int areaFireTicks, int directFireTicks) {}

    record Hit(double damageHp, int fireTicks) {
        boolean hasEffect() {
            return this.damageHp > 0.0 || this.fireTicks > 0;
        }
    }

    record Impact<T>(Hit direct, Map<T, Hit> bystanders) {
        double arrowDamage(final double baseDamage) {
            return baseDamage + this.direct.damageHp;
        }
    }

    static <T> Impact<T> planImpact(final List<Blast> blasts, final T directTarget,
                                    final Map<T, Double> bystanderDistancesSquared) {
        final Hit direct = directTarget == null ? new Hit(0.0, 0) : total(blasts, true, 0.0);
        final Map<T, Hit> bystanders = new LinkedHashMap<>();
        bystanderDistancesSquared.forEach((id, distanceSquared) -> {
            if (Objects.equals(id, directTarget)) return;
            final Hit hit = total(blasts, false, distanceSquared);
            if (hit.hasEffect()) bystanders.put(id, hit);
        });
        return new Impact<>(direct, bystanders);
    }

    private static Hit total(final List<Blast> blasts, final boolean direct, final double distanceSquared) {
        double damage = 0.0;
        int fireTicks = 0;
        for (final Blast blast : blasts) {
            if (!direct && distanceSquared > blast.radius * blast.radius) continue;
            damage += blast.damageHp;
            fireTicks = Math.max(fireTicks, direct ? blast.directFireTicks : blast.areaFireTicks);
        }
        return new Hit(damage, fireTicks);
    }

    public static double explode(final Player source, final Location impact, final List<Blast> blasts,
                                 final LivingEntity directTarget, final double arrowDamage, final SupportUtils support) {
        return explode(source, impact, blasts, directTarget, arrowDamage, support, target -> true);
    }

    /**
     * Same as the six argument overload, but only targets {@code included}
     * accepts are hit. Plague Carrier passes its ally predicate here, so the
     * blast and the poison ring share one definition of an ally.
     */
    public static double explode(final Player source, final Location impact, final List<Blast> blasts,
                                 final LivingEntity directTarget, final double arrowDamage, final SupportUtils support,
                                 final Predicate<LivingEntity> included) {
        final boolean combat = support.isCombatEnabled(impact);

        if (blasts.isEmpty() || SyntheticHit.isSynthetic() || !Bukkit.isOwnedByCurrentRegion(source)
                || !Bukkit.isOwnedByCurrentRegion(impact)) {
            return arrowDamage;
        }
        if (!source.isOnline() || EventUtils.isIgnoredUUID(source.getUniqueId()) || !combat) {
            return arrowDamage;
        }

        final World world = impact.getWorld();

        if (!world.equals(source.getWorld())) {
            return arrowDamage;
        }

        world.spawnParticle(Particle.EXPLOSION, impact, 1);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        final double radius = blasts.stream().mapToDouble(Blast::radius).max().orElseThrow();
        final LivingEntity direct = eligible(source, directTarget, world, support, included) ? directTarget : null;
        final Map<LivingEntity, Double> distancesSquared = new LinkedHashMap<>();
        for (final LivingEntity target : world.getNearbyLivingEntities(impact, radius)) {
            if (!eligible(source, target, world, support, included)) continue;
            if (directTarget != null && directTarget.getUniqueId().equals(target.getUniqueId())) continue;
            distancesSquared.put(target, target.getLocation().distanceSquared(impact));
        }

        final Impact<LivingEntity> hits = planImpact(blasts, direct, distancesSquared);
        if (direct != null) ignite(direct, hits.direct().fireTicks());
        hits.bystanders().forEach((target, hit) -> {
            // A plugin handling a previous hit may have moved or removed a later target.
            if (!eligible(source, target, world, support, included)) return;
            ignite(target, hit.fireTicks());
            if (hit.damageHp() > 0.0) SyntheticHit.damageBlast(source, target, hit.damageHp());
        });
        // Bow/armor follow-up: expose the folded blast component on the arrow event, not as a second hit.
        return hits.arrowDamage(arrowDamage);
    }

    private static boolean eligible(final Player source, final LivingEntity target, final World world,
                                    final SupportUtils support, final Predicate<LivingEntity> included) {
        return target != null && !(target instanceof ArmorStand)
                && Bukkit.isOwnedByCurrentRegion(source) && Bukkit.isOwnedByCurrentRegion(target)
                && world.equals(source.getWorld()) && world.equals(target.getWorld()) && target.isValid()
                && !target.isDead() && !source.getUniqueId().equals(target.getUniqueId())
                && !EventUtils.isIgnoredUUID(target.getUniqueId()) && !support.isFriendly(source, target)
                && included.test(target) && support.isCombatEnabled(target.getLocation());
    }

    private static void ignite(final LivingEntity target, final int fireTicks) {
        if (fireTicks > 0) target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
    }
}
