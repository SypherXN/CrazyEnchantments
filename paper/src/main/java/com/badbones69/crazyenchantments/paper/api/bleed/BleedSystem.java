package com.badbones69.crazyenchantments.paper.api.bleed;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.api.events.BleedTickEvent;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Victim-owned Bleed state with one cadence per victim, an owned transient
 * movement-speed modifier, and full cleanup on death, quit, world change,
 * reload, disable and entity removal.
 *
 * <p>Each victim has one {@link BleedLease}: state, entity scheduler, entity
 * reference and the actual {@link ScheduledTask} handle. Lifetime is measured
 * in owner ticks. All victim/entity work runs on the victim's owning scheduler.
 * The tick damage is attributed only while the source is owned by the same
 * region; a source that moved away is hidden from listeners and the tick deals
 * no damage.</p>
 */
public final class BleedSystem implements Listener {

    private static final Map<UUID, BleedLease> LEASES = new ConcurrentHashMap<>();

    private static volatile NamespacedKey slowKey;

    /**
     * Current Bleed stacks on a victim, or 0 when it is not bleeding. Read-only
     * map lookup; safe from the victim's owning thread.
     */
    public static int stacks(@NotNull final LivingEntity victim) {
        final BleedLease lease = LEASES.get(victim.getUniqueId());

        return lease == null || lease.victim != victim || lease.stopRequested
                || lease.settings != TuningConfig.bleed() || lease.state.isExpired(lease.ownerTick) ? 0 : lease.state.getStacks();
    }

    /**
     * Whether the victim currently has at least one Bleed stack.
     */
    public static boolean isBleeding(@NotNull final LivingEntity victim) {
        return stacks(victim) > 0;
    }

    /**
     * Adds stacks, refreshes the duration and starts the victim's cadence.
     * Callers must be on the victim's owning thread and the source must share
     * that region.
     */
    public static void apply(@Nullable final Player source, @NotNull final LivingEntity victim, final int stacks) {
        if (source == null || stacks <= 0) return;
        if (!Bukkit.isOwnedByCurrentRegion(victim)) return;
        if (!Bukkit.isOwnedByCurrentRegion(source)) return;

        if (victim.isDead() || !victim.isValid()) return;

        final BleedSettings settings = TuningConfig.bleed();
        final UUID victimId = victim.getUniqueId();
        final BleedLease previous = LEASES.get(victimId);
        if (previous != null && (previous.victim != victim || previous.stopRequested || previous.settings != settings)) cleanup(previous);
        final BleedLease lease = LEASES.computeIfAbsent(victimId, id -> new BleedLease(id, victim, settings));

        try {
            if (lease.stopRequested) return;
            lease.state.addStacks(stacks, settings.stackCap(), settings.durationTicks(), lease.ownerTick);
            lease.state.setSource(source.getUniqueId());
            applySlow(victim, lease.state, settings);

            if (lease.task == null) start(lease);
        } catch (final Throwable throwable) {
            plugin().getLogger().log(Level.WARNING, "Could not apply Bleed for " + victimId, throwable);
            cleanup(lease);
        }
    }

    /**
     * Stops every cadence and removes the owned slow modifier. Called on
     * enable/reload and on disable.
     */
    public static void shutdown() {
        for (final BleedLease lease : new ArrayList<>(LEASES.values())) cleanup(lease);
    }

    /**
     * Removes a stale owned slow from online players that have no live lease.
     * Called on enable/reload; the player's own scheduler does the work.
     */
    public static void cleanupStaleModifiers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final UUID playerId = player.getUniqueId();

            try {
                player.getScheduler().run(plugin(), scheduled -> {
                    if (LEASES.containsKey(playerId)) return;

                    removeSlowNow(player);
                }, null);
            } catch (final Throwable ignored) {
                // The player is leaving or the scheduler is shutting down.
            }
        }
    }

    private static void start(@NotNull final BleedLease lease) {
        lease.task = lease.scheduler.runAtFixedRate(plugin(), scheduled -> tick(lease), () -> retire(lease), 1L, 1L);
        if (lease.task == null) cleanup(lease);
    }

    private static void tick(@NotNull final BleedLease lease) {
        if (LEASES.get(lease.victimId) != lease || lease.stopRequested || lease.settings != TuningConfig.bleed()) {
            cleanupOnOwner(lease);
            return;
        }

        final long now = ++lease.ownerTick;

        try {
            if (now == lease.nextDamageTick) {
                lease.nextDamageTick += lease.settings.cadenceTicks();
                final LivingEntity victim = lease.victim;
                if (victim.isDead() || !victim.isValid()) {
                    cleanupOnOwner(lease);
                    return;
                }
                applySlow(victim, lease.state, lease.settings);
                dealTick(lease, victim, lease.settings);
            }
        } catch (final Throwable throwable) {
            plugin().getLogger().log(Level.WARNING, "Bleed tick failed for " + lease.victimId, throwable);
        } finally {
            // A cadence exactly at the deadline is the final tick. Damage or its
            // listeners can move the victim, so terminal cleanup rechecks ownership.
            if (lease.state.isExpired(now)) cleanup(lease);
        }
    }

    private static void dealTick(@NotNull final BleedLease lease, @NotNull final LivingEntity victim, @NotNull final BleedSettings settings) {
        final double damage = lease.state.tickDamage(settings);

        if (damage <= 0.0) return;

        final UUID sourceId = lease.state.getSourceId();
        final Player source = sourceId == null ? null : Bukkit.getPlayer(sourceId);

        // Ownership first, then the cheap online flag. Do not expose a
        // cross-region player to listeners or use it for attribution.
        if (source == null || !Bukkit.isOwnedByCurrentRegion(source) || !source.isOnline()
                || !source.getWorld().equals(victim.getWorld())) return;

        // Team protections are checked before the event so listeners only see a
        // tick that may actually deal attributed damage.
        if (plugin().getPlatform().getSupport().isFriendly(source, victim)) return;

        final int stacks = lease.state.getStacks();
        final BleedTickEvent event = new BleedTickEvent(victim, source, damage, stacks);

        try {
            plugin().getServer().getPluginManager().callEvent(event);
        } catch (final Throwable throwable) {
            plugin().getLogger().log(Level.WARNING, "A BleedTickEvent listener threw for " + lease.victimId, throwable);
        }

        if (event.isCancelled() || lease.stopRequested || LEASES.get(lease.victimId) != lease
                || !Bukkit.isOwnedByCurrentRegion(victim) || !Bukkit.isOwnedByCurrentRegion(source)
                || !source.isOnline() || !source.getWorld().equals(victim.getWorld())) return;

        // ponytail: attributed damage needs the source entity, so it stays same-region.
        // A source that moved away deals no tick damage; there is no unattributed fallback.
        // Vanilla i-frames are intentionally untouched; a tick inside the victim's hurt
        // window can be swallowed. No global i-frame reset is used.
        if (SyntheticHit.damage(source, victim, damage)
                && Bukkit.isOwnedByCurrentRegion(victim) && Bukkit.isOwnedByCurrentRegion(source)) {
            new BleedTickLandedEvent(victim, source, damage, stacks).callEvent();
        }
    }

    private static void applySlow(
            @NotNull final LivingEntity victim,
            @NotNull final BleedState state,
            @NotNull final BleedSettings settings
    ) {
        if (!Bukkit.isOwnedByCurrentRegion(victim)) return;

        final AttributeInstance attribute = victim.getAttribute(Attribute.MOVEMENT_SPEED);

        if (attribute == null) return;

        final NamespacedKey key = slowKey();
        final AttributeModifier current = attribute.getModifier(key);
        final double amount = state.slowFraction(settings);

        if (amount == 0.0) {
            if (current != null) attribute.removeModifier(key);

            return;
        }

        if (current != null && current.getAmount() == amount && current.getOperation() == AttributeModifier.Operation.ADD_SCALAR) return;

        if (current != null) attribute.removeModifier(key);

        // Transient: never persisted to player data.
        attribute.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
    }

    private static void removeSlowNow(@NotNull final LivingEntity victim) {
        final AttributeInstance attribute = victim.getAttribute(Attribute.MOVEMENT_SPEED);

        if (attribute != null && attribute.getModifier(slowKey()) != null) attribute.removeModifier(slowKey());
    }

    private static void cleanup(@NotNull final BleedLease lease) {
        lease.stopRequested = true;
        if (Bukkit.isOwnedByCurrentRegion(lease.victim)) {
            cleanupOnOwner(lease);
            return;
        }

        // The public scheduler refuses disabled plugins. Keep the lease and task
        // until this terminal owner hop (or an already-queued pulse) can clean up.
        try {
            if (!((CraftEntity) lease.victim).taskScheduler.schedule(
                    entity -> cleanupOnOwner(lease), entity -> retire(lease), 1L)) retire(lease);
        } catch (final RuntimeException exception) {
            plugin().getLogger().log(Level.WARNING, "Could not queue Bleed cleanup for " + lease.victimId, exception);
        }
    }

    private static void cleanupOnOwner(@NotNull final BleedLease lease) {
        if (!LEASES.remove(lease.victimId, lease)) return;
        lease.stopRequested = true;

        try {
            if (lease.victim.isValid()) removeSlowNow(lease.victim);
        } finally {
            cancel(lease.task);
            lease.task = null;
        }
    }

    private static void retire(@NotNull final BleedLease lease) {
        LEASES.remove(lease.victimId, lease);
    }

    private static void cancel(@Nullable final ScheduledTask task) {
        if (task == null) return;

        try {
            task.cancel();
        } catch (final Throwable ignored) {
            // Retirement and disable can race the scheduler; cancellation is best effort.
        }
    }

    private static void clear(@NotNull final Player player) {
        final BleedLease lease = LEASES.get(player.getUniqueId());

        if (lease != null) cleanup(lease);
    }

    private static NamespacedKey slowKey() {
        NamespacedKey key = slowKey;

        if (key == null) {
            key = new NamespacedKey(plugin(), "bleed_slow");
            slowKey = key;
        }

        return key;
    }

    private static CrazyEnchantments plugin() {
        return JavaPlugin.getPlugin(CrazyEnchantments.class);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final EntityDeathEvent event) {
        final BleedLease lease = LEASES.get(event.getEntity().getUniqueId());

        if (lease != null) cleanup(lease);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();

        if (LEASES.containsKey(playerId)) return;

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            removeSlowNow(player);

            return;
        }

        try {
            player.getScheduler().run(plugin(), scheduled -> {
                if (!LEASES.containsKey(playerId)) removeSlowNow(player);
            }, null);
        } catch (final Throwable ignored) {
            // The player is leaving or the scheduler is shutting down.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoveFromWorld(final EntityRemoveEvent event) {
        final BleedLease lease = LEASES.get(event.getEntity().getUniqueId());

        if (lease != null) cleanup(lease);
    }

    private static final class BleedLease {

        private final UUID victimId;
        private final EntityScheduler scheduler;
        private final LivingEntity victim;
        private final BleedSettings settings;
        private final BleedState state = new BleedState();

        private volatile boolean stopRequested;
        private volatile long ownerTick;
        private long nextDamageTick;
        private ScheduledTask task;

        private BleedLease(final UUID victimId, final LivingEntity victim, final BleedSettings settings) {
            this.victimId = victimId;
            this.scheduler = victim.getScheduler();
            this.victim = victim;
            this.settings = settings;
            this.nextDamageTick = settings.cadenceTicks();
        }
    }
}
