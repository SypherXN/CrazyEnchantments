package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.EnchantedArrow;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BowUtils {

    // Plan S06: a shot snapshot lives 60 s at most and a shooter may hold 64.
    private static final long ARROW_TTL_MILLIS = 60_000L;

    private static final int MAX_ARROWS_PER_SHOOTER = 64;

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final SupportUtils support = this.platform.getSupport();

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // Sticky Shot
    private final List<Block> webBlocks = new ArrayList<>();

    // S06: per-shot records are read-mostly and written once at shoot time, so
    // they are keyed by projectile UUID instead of a list that has to be scanned
    // and can be left behind when the arrow never reports a hit.
    private final Map<UUID, TrackedArrow> enchantedArrows = new ConcurrentHashMap<>();

    private final AtomicLong lastArrowSweep = new AtomicLong(Long.MIN_VALUE);

    /**
     * A snapshot plus the two facts the sweep and the S06 cap need. The arrow
     * itself cannot carry the timestamp: {@link EnchantedArrow} is a public
     * record this class does not own.
     */
    private record TrackedArrow(EnchantedArrow arrow, UUID shooter, long expiresAtMillis) {

        boolean expired(final long nowMillis) {
            return nowMillis >= this.expiresAtMillis;
        }
    }

    public void addArrow(AbstractArrow arrow, ItemStack bow, Map<CEnchantment, Integer> enchantments) {
        if (arrow == null) return;

        final long now = nowMillis();

        sweep(now);

        final UUID shooter = shooterId(arrow);

        if (shooter != null) makeRoom(shooter, now);

        this.enchantedArrows.put(arrow.getUniqueId(),
                new TrackedArrow(new EnchantedArrow(arrow, bow, enchantments), shooter, now + ARROW_TTL_MILLIS));
    }

    public void removeArrow(EnchantedArrow enchantedArrow) {
        if (enchantedArrow == null || enchantedArrow.arrow() == null) return;

        this.enchantedArrows.remove(enchantedArrow.arrow().getUniqueId());
    }

    /**
     * Drops the snapshot of a projectile that leaves the world without a hit:
     * despawn, void, chunk unload or shooter quit. Without this the only removal
     * would be a {@code ProjectileHitEvent} that never comes.
     */
    public void removeArrow(UUID projectileId) {
        if (projectileId == null) return;

        this.enchantedArrows.remove(projectileId);
    }

    public boolean isBowEnchantActive(CEnchantments customEnchant, EnchantedArrow enchantedArrow) {
        return customEnchant.isActivated() &&
                enchantedArrow.hasEnchantment(customEnchant) &&
                customEnchant.chanceSuccessful(enchantedArrow.getLevel(customEnchant));
    }

    public boolean allowsCombat(Entity entity) {
        return this.support.isCombatEnabled(entity.getLocation());
    }

    public EnchantedArrow getEnchantedArrow(AbstractArrow arrow) {
        if (arrow == null) return null;

        final UUID projectileId = arrow.getUniqueId();
        final TrackedArrow tracked = this.enchantedArrows.get(projectileId);

        if (tracked == null) return null;

        // A missed record must never be handed out even if no sweep has run yet.
        if (tracked.expired(nowMillis())) {
            this.enchantedArrows.remove(projectileId, tracked);

            return null;
        }

        return tracked.arrow();
    }

    /**
     * Removes expired records, at most once per second so a lookup never turns
     * into a scan.
     */
    private void sweep(final long now) {
        final long previous = this.lastArrowSweep.get();

        if (previous != Long.MIN_VALUE && now - previous < 1_000L) return;
        if (!this.lastArrowSweep.compareAndSet(previous, now)) return;

        this.enchantedArrows.values().removeIf(tracked -> tracked.expired(now));
    }

    /**
     * S06's per-shooter limit: the oldest live record of that shooter frees the
     * slot, so spamming arrows never disables the newest one.
     */
    private void makeRoom(final UUID shooter, final long now) {
        UUID oldest = null;
        long oldestExpiry = Long.MAX_VALUE;
        int live = 0;

        // ponytail: O(n) over live snapshots per shot; bounded by 64 per shooter.
        for (final Map.Entry<UUID, TrackedArrow> entry : this.enchantedArrows.entrySet()) {
            final TrackedArrow tracked = entry.getValue();

            if (!shooter.equals(tracked.shooter())) continue;

            if (tracked.expired(now)) {
                this.enchantedArrows.remove(entry.getKey(), tracked);

                continue;
            }

            live++;

            if (tracked.expiresAtMillis() < oldestExpiry) {
                oldestExpiry = tracked.expiresAtMillis();
                oldest = entry.getKey();
            }
        }

        if (live >= MAX_ARROWS_PER_SHOOTER && oldest != null) this.enchantedArrows.remove(oldest);
    }

    private static UUID shooterId(final AbstractArrow arrow) {
        return arrow.getShooter() instanceof Entity shooter ? shooter.getUniqueId() : null;
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    // Multi Arrow Start!

    public AbstractArrow spawnArrows(LivingEntity shooter, AbstractArrow projectile, ItemStack bow) {
        Arrow spawnedArrow = shooter.getWorld().spawn(projectile.getLocation(), Arrow.class);

        // A spawned arrow has no weapon of its own, so every bow enchant that
        // reads getWeapon() would skip it. The shot came from this bow.
        spawnedArrow.setWeapon(bow);

        spawnedArrow.setShooter(shooter);

        final float spread = TuningConfig.fork().multiArrow().spread();
        Vector vector = new Vector(randomSpread(spread), 0, randomSpread(spread));

        spawnedArrow.setVelocity(projectile.getVelocity().add(vector));

        if (projectile.isCritical()) spawnedArrow.setCritical(true);

        if (projectile.getFireTicks() > 0) spawnedArrow.setFireTicks(projectile.getFireTicks());

        spawnedArrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

        addArrow(spawnedArrow, bow, this.enchantmentBookSettings.getEnchantments(bow));

        return spawnedArrow;
    }

    private float randomSpread(float spread) {
        return -spread + (float) (Math.random() * (spread * 2));
    }
    // Multi Arrow End!

    // Sticky Shot Start!
    public List<Block> getWebBlocks() {
        return this.webBlocks;
    }

    public void spawnWebs(Entity hitEntity, EnchantedArrow enchantedArrow) {
        if (enchantedArrow == null) return;

        AbstractArrow arrow = enchantedArrow.getArrow();

        if (!(EnchantUtils.isEventActive(CEnchantments.STICKY_SHOT, enchantedArrow.getShooter(), enchantedArrow.arrow().getWeapon(), enchantedArrow.getEnchantments()))) return;

        final int durationTicks = TuningConfig.fork().stickyShotDurationTicks();
        if (hitEntity == null) {
            Location entityLocation = arrow.getLocation();

            if (entityLocation.getBlock().getType() != Material.AIR) return;

            entityLocation.getBlock().setType(Material.COBWEB);
            this.webBlocks.add(entityLocation.getBlock());

            new FoliaScheduler(this.plugin, entityLocation) {
                @Override
                public void run() {
                    entityLocation.getBlock().setType(Material.AIR);
                    webBlocks.remove(entityLocation.getBlock());
                }
            }.runDelayed(durationTicks);
        } else {
            setWebBlocks(hitEntity, durationTicks);
        }

        arrow.remove();
    }

    private void setWebBlocks(Entity hitEntity, int durationTicks) {
        final Location location = hitEntity.getLocation();

        new FoliaScheduler(this.plugin, location) {
            @Override
            public void run() {
                for (final Block block : getCube(hitEntity.getLocation())) {

                    block.setType(Material.COBWEB);

                    webBlocks.add(block);

                    new FoliaScheduler(plugin, block.getLocation()) {
                        @Override
                        public void run() {
                            if (block.getType() == Material.COBWEB) {
                                block.setType(Material.AIR);
                                webBlocks.remove(block);
                            }
                        }
                    }.runDelayed(durationTicks);
                }
            }
        }.execute();
    }

    // Sticky Shot End!

    private List<Block> getCube(Location start) {
        List<Block> newBlocks = new ArrayList<>();

        for (double x = start.getX() - 1; x <= start.getX() + 1; x++) {
            for (double z = start.getZ() - 1; z <= start.getZ() + 1; z++) {
                Location loc = new Location(start.getWorld(), x, start.getY(), z);
                if (loc.getBlock().getType() == Material.AIR) newBlocks.add(loc.getBlock());
            }
        }

        return newBlocks;
    }
}
