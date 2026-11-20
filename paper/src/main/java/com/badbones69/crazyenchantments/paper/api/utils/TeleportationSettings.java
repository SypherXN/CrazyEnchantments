package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validated Teleportation tuning. The chance curve stays in Enchantments.yml
 * (Base 8, Increase 4); this holds the reach, the landing search and the
 * per-shooter cooldown. Claim rules live here so they stay testable without a
 * server.
 */
public record TeleportationSettings(double rangeBlocks, double searchRadiusBlocks, double cooldownSeconds) {

    public static final TeleportationSettings DEFAULTS = new TeleportationSettings(30.0, 2.0, 10.0);

    public TeleportationSettings {
        rangeBlocks = TuningConfig.BowValues.checkedNumber(rangeBlocks, 1.0, 1_000.0);
        searchRadiusBlocks = TuningConfig.BowValues.checkedNumber(searchRadiusBlocks, 0.5, 16.0);
        cooldownSeconds = TuningConfig.BowValues.checkedNumber(cooldownSeconds, 0.0, 600.0);
    }

    public long cooldownMillis() {
        return Math.round(this.cooldownSeconds * 1_000.0);
    }

    /**
     * Distance from the shooter to the hit ally, inclusive at the boundary. An
     * unknown or negative distance never qualifies.
     */
    public boolean withinRange(final double distance) {
        return distance >= 0.0 && distance <= this.rangeBlocks;
    }

    public boolean available(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID shooter, final long nowMillis) {
        final Long last = cooldowns.get(shooter);

        return last == null || nowMillis - last >= cooldownMillis();
    }

    /**
     * Called only after a teleport actually happened, so a failed attempt never
     * spends the window.
     */
    public void claim(final ConcurrentHashMap<UUID, Long> cooldowns, final UUID shooter, final long nowMillis) {
        cooldowns.put(shooter, nowMillis);
    }

    /**
     * Horizontal block offsets around the ally, nearest first and never the
     * ally's own block. Sorted by distance, then by a fixed tie-break so the
     * search order is deterministic.
     */
    public List<Offset> offsets() {
        final int radius = (int) Math.floor(this.searchRadiusBlocks);
        final List<Offset> offsets = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz > radius * radius) continue;

                offsets.add(new Offset(dx, dz));
            }
        }

        offsets.sort(Comparator
                .comparingInt((Offset offset) -> offset.dx() * offset.dx() + offset.dz() * offset.dz())
                .thenComparingInt(offset -> Math.abs(offset.dx()) + Math.abs(offset.dz()))
                .thenComparingInt(Offset::dx)
                .thenComparingInt(Offset::dz));

        return List.copyOf(offsets);
    }

    /**
     * A landing is safe when both body blocks are free, the floor is solid, and
     * neither block is a hazard the player would fall or burn into.
     */
    public static boolean safeLanding(final boolean feetSolid, final boolean headSolid,
                                      final boolean belowSolid, final boolean hazard) {
        return !feetSolid && !headSolid && belowSolid && !hazard;
    }

    public record Offset(int dx, int dz) {}
}
