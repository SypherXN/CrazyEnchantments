package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyManager;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.bleed.BleedTickLandedEvent;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.events.AuraActiveEvent;
import com.badbones69.crazyenchantments.paper.api.managers.ArmorEnchantmentManager;
import com.badbones69.crazyenchantments.paper.api.objects.AllyMob;
import com.badbones69.crazyenchantments.paper.api.objects.ArmorEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.PotionEffects;
import com.badbones69.crazyenchantments.paper.api.utils.AegisSettings;
import com.badbones69.crazyenchantments.paper.api.utils.AngelicSettings;
import com.badbones69.crazyenchantments.paper.api.utils.BloodLustSettings;
import com.badbones69.crazyenchantments.paper.api.utils.BowBlastContext;
import com.badbones69.crazyenchantments.paper.api.utils.CreeperArmorSettings;
import com.badbones69.crazyenchantments.paper.api.utils.CurseSettings;
import com.badbones69.crazyenchantments.paper.api.utils.DestructionSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnderShiftSettings;
import com.badbones69.crazyenchantments.paper.api.utils.FeignDeathSettings;
import com.badbones69.crazyenchantments.paper.api.utils.ImplantsSettings;
import com.badbones69.crazyenchantments.paper.api.utils.RocketEscapeSettings;
import com.badbones69.crazyenchantments.paper.api.utils.WitherSettings;
import com.badbones69.crazyenchantments.paper.api.utils.FeignVisibility;
import com.badbones69.crazyenchantments.paper.api.utils.LifebloomSettings;
import com.badbones69.crazyenchantments.paper.api.utils.MetaphysicalSettings;
import com.badbones69.crazyenchantments.paper.api.utils.PlagueCarrierSettings;
import com.badbones69.crazyenchantments.paper.api.utils.RagdollSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SpiritLinkSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SpiritsSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.ExplosionHelper;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.ProtectionCrystalSettings;
import com.badbones69.crazyenchantments.paper.support.SupportUtils;
import com.badbones69.crazyenchantments.paper.support.api.interfaces.VanishSupport;
import com.badbones69.crazyenchantments.paper.tasks.processors.ArmorProcessor;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.persistence.PersistentDataContainerView;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

public class ArmorEnchantments implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    private final SupportUtils support = this.platform.getSupport();

    private final VanishSupport vanishSupport = this.support.getVanishSupport();

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    @NotNull
    private final CrazyManager crazyManager = this.starter.getCrazyManager();

    // Settings.
    @NotNull
    private final ProtectionCrystalSettings protectionCrystalSettings = this.starter.getProtectionCrystalSettings();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // Plugin Managers.
    @NotNull
    private final ArmorEnchantmentManager armorEnchantmentManager = this.starter.getArmorEnchantmentManager();

    private final ArmorProcessor armorProcessor = new ArmorProcessor();

    private final List<UUID> fallenPlayers = new ArrayList<>();

    private final ConcurrentHashMap<UUID, Long> bloodLustCooldowns = new ConcurrentHashMap<>();

    // Aegis: wearer -> (distinct attacker -> hit times inside the window). At most
    // TuningConfig.MAX_ANTI_GANK_HISTORY attackers are kept per wearer and an entry that
    // goes idle is dropped on the next read; the whole entry is cleared on death, world
    // change, reload and quit.
    private final ConcurrentHashMap<UUID, Map<UUID, Deque<Long>>> aegisHits = new ConcurrentHashMap<>();

    // Ragdoll: wearer -> nanoTime of the last push. Cleared by the same lifecycle as Aegis.
    private final ConcurrentHashMap<UUID, Long> ragdollCooldowns = new ConcurrentHashMap<>();

    // Ragdoll: wearer -> the session a queued push belongs to. A death, world change,
    // reload or rejoin drops the entry, so the in-flight push finds no session and is not
    // applied to whoever comes back.
    private final ConcurrentHashMap<UUID, Object> ragdollSessions = new ConcurrentHashMap<>();

    // Creeper Armor: wearer -> nanoTime of the last heal. Cleared by the same lifecycle as Aegis.
    private final ConcurrentHashMap<UUID, Long> creeperHeals = new ConcurrentHashMap<>();

    private final NamespacedKey spiritBlazeKey = new NamespacedKey(this.plugin, "spirit_blaze");
    private final ConcurrentHashMap<UUID, SupportRun> angelicRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SupportRun> spiritsRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> spiritsCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> spiritLinkCooldowns = new ConcurrentHashMap<>();
    private volatile boolean supportStopped;

    // True from the moment CrazyManager#load starts replacing the tuning records and
    // clearing the registry until it has republished both. A proc admitted in that
    // window could match the old enabled entry, read the new settings and survive
    // reconciliation because its stamp already holds the new record, so every entry
    // point refuses while this is set. Cleared in a finally, so a failed reload cannot
    // leave every armor proc switched off.
    private volatile boolean registryUnpublished;

    // Bumped once per completed reload. Listeners that read tuning but live outside this
    // class (Arrow Deflect, Ender Walker) compare it to discard their per-player state on
    // the same lifecycle points that clear this class's state.
    private final AtomicLong reloadEpoch = new AtomicLong();

    /**
     * Closes proc admission for a reload. Called by CrazyManager#load before the first
     * tuning record is replaced or the registry is cleared; must be paired with
     * {@link #endReload()} in a finally.
     */
    public void beginReload() {
        this.registryUnpublished = true;
    }

    /**
     * Opens proc admission again, after the registry and every manager are published.
     * Called from a finally at the end of CrazyManager#load. Each completed reload also
     * starts a new state epoch, so listeners outside this class discard state built on the
     * tuning records the reload replaced.
     */
    public void endReload() {
        this.registryUnpublished = false;
        this.reloadEpoch.incrementAndGet();
    }

    /**
     * True while a reload has replaced the registry or its tuning records without having
     * republished them. Defensive listeners outside this class that read tuning must refuse
     * procs for the same window this class refuses them.
     */
    public boolean isReloading() {
        return this.registryUnpublished;
    }

    /**
     * Increases once per completed reload. A listener that caches per-player state compares
     * it to know the state was built on replaced tuning.
     */
    public long reloadEpoch() {
        return this.reloadEpoch.get();
    }

    /**
     * True when the reload state moved since a proc began, or a reload is still open.
     * Captured before eligibility work and re-checked after every plugin callback (the
     * cancellable use event and the ally/visibility providers): a reload can begin and
     * finish inside such a callback, so {@link #isReloading()} alone would be open again
     * while the decision was made against the registry the reload replaced.
     */
    private boolean staleProc(final long epoch) {
        return this.registryUnpublished || this.reloadEpoch.get() != epoch;
    }

    // Curse, Destruction, Plague Carrier and Feign Death. Every run is read and
    // mutated only on the wearer's own region thread.
    private final NamespacedKey plagueCreeperKey = new NamespacedKey(this.plugin, "plague_creeper");
    private final ConcurrentHashMap<UUID, TickingRun> curseRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TickingRun> destructionRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TickingRun> plagueTicking = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TickingRun> implantsRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TickingRun> enderShiftRuns = new ConcurrentHashMap<>();

    // Rocket Escape: wearer -> nanoTime of the last accepted launch. Cleared by the combat-state lifecycle.
    private final ConcurrentHashMap<UUID, Long> rocketCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlagueRun> plagueRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FeignRun> feignRuns = new ConcurrentHashMap<>();

    // Feign Death: one entry per viewer session, holding whether this plugin hides
    // each target from it. A rejoin builds a new session, so a record is never
    // inherited across clients.
    private final ConcurrentHashMap<UUID, FeignView> feignViews = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, Long> plagueCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> feignCooldowns = new ConcurrentHashMap<>();

    // Plague Carrier's "already triggered, waiting for recovery" latch, timestamped so a
    // player who disconnects without recovering cannot pin it forever.
    private final ConcurrentHashMap<UUID, Long> plagueSpent = new ConcurrentHashMap<>();

    // AllyManager keeps its mobs in a plain ArrayList that other regions add to and remove
    // from. Copying it per candidate (Angelic, Spirit Link, Spirits, Destruction, the Plague
    // burst and its poison) is both O(allies) and unsafe while it is mutated, so the exact
    // ally is resolved through a bounded index rebuilt at most once per tick. The index is
    // local to this handler; AllyManager itself is not changed.
    private static final long ALLY_INDEX_NANOS = 50_000_000L; // one tick
    private volatile AllyIndex allyIndex;

    private record AllyIndex(Map<UUID, AllyMob> byAlly, long takenAt) {}

    private static final class SupportRun {
        private final List<Blaze> blazes = new CopyOnWriteArrayList<>();
        private volatile ScheduledTask task;
        private long age;
    }

    /**
     * One wearer's repeating always-on enchant (Curse, Destruction, Plague Carrier).
     */
    private static final class TickingRun {
        private final Player wearer;

        // The settings snapshot this run was built with. A reload builds a new record, so a
        // reconcile retires a run whose snapshot is no longer the current one.
        private Object settings;
        private volatile ScheduledTask task;
        private long age;
        private long lastRefresh = -1_000L;
        private boolean armed = true;
        private boolean applied;
        private int level;
        private Runnable finished = () -> {};

        // Implants: the run age of the last restore. A run starts at zero, so the first restore
        // comes one full interval after the helmet went on.
        private long lastFire;

        // Ender Shift: whether the shift is active, and the effects this run added and still owns.
        private boolean shifted;
        private boolean speedOwned;
        private boolean boostOwned;
        private int speedAmplifier;
        private int boostAmplifier;
        private int effectTicks;

        private TickingRun(final Player wearer) {
            this.wearer = wearer;
        }
    }

    /**
     * Plague Carrier: the creepers and fuse tasks of one trigger.
     */
    private static final class PlagueRun {
        private final Player wearer;
        private final List<Creeper> creepers = new CopyOnWriteArrayList<>();
        private final List<ScheduledTask> fuses = new CopyOnWriteArrayList<>();

        // The settings snapshot this burst was started under. A reload builds a new
        // record, so reconciliation retires a burst whose stamp is no longer current.
        private PlagueCarrierSettings settings;
        // Atomic: a fuse callback, a world change, a death and a reload can all claim the
        // retirement at once, and exactly one of them must win.
        private final AtomicBoolean retired = new AtomicBoolean();

        private PlagueRun(final Player wearer) {
            this.wearer = wearer;
        }
    }

    /**
     * Feign Death: the vanish of one wearer.
     */
    private static final class FeignRun {
        private final Player wearer;

        // The settings snapshot this vanish was built with.
        private Object settings;
        private volatile ScheduledTask task;
        private int level;
        private int hits;

        private FeignRun(final Player wearer) {
            this.wearer = wearer;
        }
    }

    /**
     * One connected client session. Hides are recorded per target for this exact
     * session, so a rejoin starts with nothing hidden and a replacement vanish can
     * never double-hide the same target.
     */
    private static final class FeignView {
        private final Player player;
        private final FeignVisibility visibility;

        private FeignView(final Player player, final FeignVisibility.Visibility visibility) {
            this.player = player;
            this.visibility = new FeignVisibility(visibility);
        }
    }

    /**
     * Production visibility: Paper's per-player hide/show, which is scoped to this
     * plugin, the viewer and the target.
     */
    private final FeignVisibility.Visibility bukkitVisibility = new FeignVisibility.Visibility() {
        @Override
        public void hide(final Player viewer, final Player target) {
            viewer.hideEntity(ArmorEnchantments.this.plugin, target);
        }

        @Override
        public void show(final Player viewer, final Player target) {
            viewer.showEntity(ArmorEnchantments.this.plugin, target);
        }
    };

    public ArmorEnchantments() {
        // Reload reconciliation is triggered by CrazyManager#load after it publishes the new
        // registry and managers, so it can never run against a half-rebuilt registry.

        // A plugin enabled while players are already connected never sees their join, so the
        // per-viewer session every hide is recorded in would not exist and plays would step
        // over them until they reconnected. Seed one session per connected player here: a
        // session is a plain component swap on a concurrent map, and every visibility call
        // made with it is still dispatched to that viewer's owning region.
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isConnected()) continue;

            this.feignViews.putIfAbsent(player.getUniqueId(), new FeignView(player, this.bukkitVisibility));
        }
    }

    public void stop() {
        this.supportStopped = true;
        this.angelicRuns.forEach((id, run) -> finishSupportRun(this.angelicRuns, id, run, true));
        this.spiritsRuns.forEach((id, run) -> finishSupportRun(this.spiritsRuns, id, run, true));
        this.spiritsCooldowns.clear();
        this.spiritLinkCooldowns.clear();
        this.aegisHits.clear();
        this.ragdollCooldowns.clear();
        this.ragdollSessions.clear();
        this.creeperHeals.clear();
        this.curseRuns.forEach((id, run) -> finishRun(this.curseRuns, id, run));
        this.destructionRuns.forEach((id, run) -> finishRun(this.destructionRuns, id, run));
        this.plagueTicking.forEach((id, run) -> finishRun(this.plagueTicking, id, run));
        this.implantsRuns.forEach((id, run) -> finishRun(this.implantsRuns, id, run));
        this.enderShiftRuns.forEach((id, run) -> finishRun(this.enderShiftRuns, id, run));
        this.rocketCooldowns.clear();
        this.plagueRuns.keySet().forEach(id -> stopPlague(id, false));
        this.feignRuns.forEach((id, run) -> endFeign(id, run));

        // Release every hide still recorded, then drop the sessions. A viewer whose client
        // is already gone cannot be shown; clearing the map leaves no record behind.
        for (final FeignView view : this.feignViews.values()) releaseAll(view);

        this.feignViews.clear();
        this.plagueCooldowns.clear();
        this.feignCooldowns.clear();
        this.plagueSpent.clear();
        this.bloodLustCooldowns.clear();
    }

    @EventHandler
    public void onDeath(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack air = new ItemStack(Material.AIR);

        new FoliaScheduler(this.plugin, null, player) {
            @Override
            public void run() {
                updateEffects(player, air, air);
            }
        }.runDelayed(10);
    }

    @EventHandler
    public void onEquip(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // updateEffects fires the armor use events for the pieces it touches, and a reload can
        // begin and finish inside one of them. The admission below is therefore made only for a
        // decision that saw the same registry epoch.
        final long epoch = this.reloadEpoch.get();

        final NamespacedKey key = DataKeys.enchantments.getNamespacedKey();

        event.getEquipmentChanges().forEach((slot, action) -> {
            if (slot.isHand()) return;

            final ItemStack newItem = action.newItem();
            final ItemStack oldItem = action.oldItem();

            final PersistentDataContainerView newView = newItem.getPersistentDataContainer();
            final PersistentDataContainerView oldView = oldItem.getPersistentDataContainer();

            if (!newView.has(key) && !oldView.has(key)) return;

            if (Objects.equals(newView.get(key, PersistentDataType.STRING), oldView.get(key, PersistentDataType.STRING))) return;

            updateEffects(player, newItem, oldItem);
        });

        if (staleProc(epoch)) return;

        startWornRuns(player);
    }

    /**
     * Checks the players current armor and updates any needed effects that are created by CrazyEnchantments.
     * Removes all effects that should no longer be on the player and adds the highest level for the others
     * based on their armor.
     * @param player The player for whom to update effects.
     * @param newItem The new item equipped.
     * @param oldItem The item that had previously been equipped.
     */
    private void updateEffects(@NotNull Player player, @NotNull ItemStack newItem, @NotNull ItemStack oldItem) {
        final Map<CEnchantment, Integer> topEnchants = getCurrentEnchants(player, newItem);

        // Remove all effects that they no longer should have from the armor.
        if (!oldItem.isEmpty()) {
            getTopPotionEffects(this.enchantmentBookSettings.getEnchantments(oldItem)
                    .entrySet().stream()
                    .filter(enchant -> !topEnchants.containsKey(enchant.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)))
                    .keySet()
                    .forEach(player::removePotionEffect);
        }

        // Add all new effects that said player should now have.
        final int hulkDuration = TuningConfig.fork().hulkDurationTicks();
        for (final Map.Entry<PotionEffectType, WornPotion> effect : getTopPotionEffects(topEnchants).entrySet()) {
            final int amplifier = effect.getValue().level() - 1;
            for (final PotionEffect currentEffect : player.getActivePotionEffects()) {
                if (!currentEffect.getType().equals(effect.getKey())) continue;
                if (currentEffect.getAmplifier() <= amplifier) break;

                player.removePotionEffect(effect.getKey());

                break;
            }

            // The wearer's own armor effect (Hulk, Drunk, Insomnia slow): Metaphysical must never resist it.
            final int duration = effect.getValue().hulk() ? hulkDuration : PotionEffect.INFINITE_DURATION;
            MetaphysicalSettings.runSelfInflicted(() -> player.addPotionEffect(new PotionEffect(effect.getKey(), duration, amplifier)));
        }
    }

    /**
     * Pulls the data off of all of the enchantments provided and filters out the worst ones.
     * @param topEnchants A list of {@link CEnchantment}'s to filter.
     * @return Returns a list of top potion effects from the provided list of enchantments.
     */
    @NotNull
    private Map<PotionEffectType, WornPotion> getTopPotionEffects(@NotNull Map<CEnchantment, Integer> topEnchants) {
        Map<CEnchantments, HashMap<PotionEffectType, Integer>> enchantmentPotions = this.crazyManager.getEnchantmentPotions();
        HashMap<PotionEffectType, WornPotion> topPotions = new HashMap<>();

        topEnchants.forEach((key, level) -> enchantmentPotions.forEach((enchant, potions) -> {
            if (!enchant.getEnchantment().equals(key)) return;

            potions.forEach((type, offset) -> {
                final WornPotion current = topPotions.get(type);

                // Jump Boost is the only effect the offset decides: Anti Gravity must outrank a
                // same-level Springs, so the highest effective amplifier wins. The stored level
                // stays "amplifier + 1", the value updateEffects turns back into the amplifier.
                if (type.equals(PotionEffectType.JUMP_BOOST)) {
                    final int amplifier = jumpAmplifier(level, offset);

                    if (current == null || current.level() - 1 < amplifier) {
                        topPotions.put(type, new WornPotion(amplifier + 1, false));
                    }

                    return;
                }

                // Every other effect keeps the worn selection it had before: the raw level, with
                // the first entry for a type winning.
                if (current != null && !(current.level() != -1 && current.level() <= offset)) return;

                topPotions.put(type, new WornPotion(level, enchant == CEnchantments.HULK));
            });
        }));

        return topPotions;
    }

    /**
     * The Jump Boost amplifier a worn level grants, using the offset in
     * {@link CrazyManager#getEnchantmentPotions()}. Jump Boost I is amplifier 0.
     */
    static int jumpAmplifier(final int level, final int offset) {
        return level + offset;
    }

    private record WornPotion(int level, boolean hulk) {}

    /**
     *
     * @param player The player to check.
     * @param newItem The equipped item.
     * @return Returns a map of all current active enchants on the specified player.
     */
    @NotNull
    private Map<CEnchantment, Integer> getCurrentEnchants(@NotNull Player player, @NotNull ItemStack newItem) {
        Map<CEnchantment, Integer> toAdd = getUpperEnchants(player);

        if (!newItem.isEmpty()) {
            this.enchantmentBookSettings.getEnchantments(newItem).entrySet().stream()
                    .filter(ench -> !toAdd.containsKey(ench.getKey()) || toAdd.get(ench.getKey()) <= ench.getValue())
                    .filter(ench -> {
                        CEnchantments type = CEnchantments.fromName(ench.getKey().getName());

                        // Names such as "Creeper Armor" contain spaces, so valueOf() cannot be used here.
                        return type != null && EnchantUtils.isArmorEventActive(player, type, newItem);
                    })
                    .forEach(ench -> toAdd.put(ench.getKey(), ench.getValue()));
        }

        return toAdd;
    }

    /**
     *
     * @param player The player to check for {@link CEnchantments}.
     * @return A list of {@link CEnchantments}'s on the player.
     */
    @NotNull
    private HashMap<CEnchantment, Integer> getUpperEnchants(@NotNull Player player) {
        HashMap<CEnchantment, Integer> topEnchants = new HashMap<>();

        Arrays.stream(player.getEquipment().getArmorContents())
                .map(this.enchantmentBookSettings::getEnchantments)
                .forEach(enchantments -> enchantments.entrySet().stream()
                        .filter(ench -> !topEnchants.containsKey(ench.getKey()) || topEnchants.get(ench.getKey()) <= ench.getValue())
                        .forEach(ench -> topEnchants.put(ench.getKey(), ench.getValue())));

        return topEnchants;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event) || EventUtils.isIgnoredUUID(event.getDamager().getUniqueId())) return;
        if (this.registryUnpublished) return;

        if (!(event.getDamager() instanceof LivingEntity damager) || !(event.getEntity() instanceof Player player)) return;

        if (this.support.isFriendly(damager, player)) return;

        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            Map<CEnchantment, Integer> enchants = this.enchantmentBookSettings.getEnchantments(armor);
            if (enchants.isEmpty()) continue;

            for (ArmorEnchantment armorEnchantment : this.armorEnchantmentManager.getArmorEnchantments()) {
                CEnchantments enchantment = armorEnchantment.getEnchantment();

                if (EnchantUtils.isEventActive(enchantment, player, armor, enchants)) {

                    if (armorEnchantment.isPotionEnchantment()) {
                        for (PotionEffects effect : armorEnchantment.getPotionEffects()) {
                            final PotionEffect applied = new PotionEffect(effect.potionEffect(), effect.duration(), (armorEnchantment.isLevelAddedToAmplifier() ? enchants.get(enchantment.getEnchantment()) : 0) + effect.amplifier());

                            // The attacker can be owned by another region; the effect is applied there.
                            runOnOwner(damager, () -> damager.addPotionEffect(applied));
                        }
                    } else {
                        event.setDamage(event.getDamage() * ((armorEnchantment.isLevelAddedToAmplifier() ? enchants.get(enchantment.getEnchantment()) : 0) + armorEnchantment.getDamageAmplifier()));
                    }
                }
            }

            if (EnchantUtils.isEventActive(CEnchantments.MANEUVER, player, armor, enchants)) {
                event.setCancelled(true);
                return;
            }

            if (player.isSneaking() && EnchantUtils.isEventActive(CEnchantments.CROUCH, player, armor, enchants)) {
                double percentageReduced = TuningConfig.fork().crouch().reduction(enchants.get(CEnchantments.CROUCH.getEnchantment()));
                double newDamage = event.getFinalDamage() * (1 - percentageReduced);

                if (newDamage < 0) newDamage = 0;

                event.setDamage(newDamage);
            }

            if (EnchantUtils.isEventActive(CEnchantments.SHOCKWAVE, player, armor, enchants)) {
                // The direction is read from the wearer this thread owns; the push itself is
                // applied on the attacker's own region.
                final Vector push = player.getLocation().getDirection().multiply(2).setY(1.25);

                runOnOwner(damager, () -> {
                    if (damager.isValid()) damager.setVelocity(push);
                });
            }

            if (player.getHealth() <= event.getFinalDamage() && EnchantUtils.isEventActive(CEnchantments.SYSTEMREBOOT, player, armor, enchants)) {
                Optional.ofNullable(player.getAttribute(Attribute.MAX_HEALTH)).ifPresentOrElse(attribute -> {
                    player.setHealth(attribute.getValue());

                    event.setCancelled(true);
                }, () -> this.fusion.log(Level.WARNING, "Player %s did not have the MAX_HEALTH attribute when using %s enchantment!", player.getName(), "System Reboot"));

                return;
            }

            // Rocket Escape: hit while at or below the trigger cut, off cooldown, launches the wearer up.
            final CEnchantment rocket = CEnchantments.ROCKET.getEnchantment();
            final int rocketLevel = rocket == null ? 0 : enchants.getOrDefault(rocket, 0);

            if (rocketLevel > 0 && rocketEscapeReady(player, rocketLevel)
                    && EnchantUtils.isEventActive(CEnchantments.ROCKET, player, armor, enchants)) {
                launchRocketEscape(player, rocketLevel);
            }

            // Wither: a worn piece that procs on a hit withers the attacker. The effect is the one the
            // enchant always had; only the item type moved from swords to armor.
            if (EnchantUtils.isEventActive(CEnchantments.WITHER, player, armor, enchants)) {
                final WitherSettings wither = TuningConfig.wither();
                final PotionEffect withered = new PotionEffect(PotionEffectType.WITHER, wither.durationTicks(), wither.amplifier());

                // The attacker can be owned by another region; the effect is applied there.
                runOnOwner(damager, () -> damager.addPotionEffect(withered));
            }

            if (player.getHealth() > 0 && EnchantUtils.isEventActive(CEnchantments.ENLIGHTENED, player, armor, enchants)) {
                double heal = enchants.get(CEnchantments.ENLIGHTENED.getEnchantment());

                Optional.ofNullable(player.getAttribute(Attribute.MAX_HEALTH)).ifPresentOrElse(attribute -> {
                    final double maxHealth = attribute.getValue();

                    final double playerHealth = player.getHealth();

                    if (playerHealth + heal < maxHealth) player.setHealth(playerHealth + heal);

                    if (playerHealth + heal >= maxHealth) player.setHealth(maxHealth);
                }, () -> this.fusion.log(Level.WARNING, "Player %s did not have the MAX_HEALTH attribute when using %s enchantment!", player.getName(), "Enlightened"));
            }

            if (EnchantUtils.isEventActive(CEnchantments.INSOMNIA, player, armor, enchants)) {
                final double retaliation = event.getDamage() + enchants.get(CEnchantments.INSOMNIA.getEnchantment());

                // Retaliation damages the attacker, so it is applied on the attacker's region;
                // the wearer's own hit above stays synchronous.
                runOnOwner(damager, () -> {
                    if (damager.isValid() && !damager.isDead()) damager.damage(retaliation);
                });
            }

            if (EnchantUtils.isEventActive(CEnchantments.MOLTEN, player, armor, enchants)) {
                final int fireTicks = (enchants.get(CEnchantments.MOLTEN.getEnchantment()) * 2) * 20;

                runOnOwner(damager, () -> {
                    if (damager.isValid()) damager.setFireTicks(fireTicks);
                });
            }

            if (EnchantUtils.isEventActive(CEnchantments.SAVIOR, player, armor, enchants)) event.setDamage(event.getDamage() / 2);

            if (EnchantUtils.isEventActive(CEnchantments.CACTUS, player, armor, enchants)) {
                final double cactus = enchants.get(CEnchantments.CACTUS.getEnchantment());

                runOnOwner(damager, () -> {
                    if (damager.isValid() && !damager.isDead()) damager.damage(cactus);
                });
            }

            if (EnchantUtils.isEventActive(CEnchantments.STORMCALLER, player, armor, enchants)) {
                // The bolt is struck at the attacker and the splash is read around the wearer,
                // so the whole proc belongs to this region. A hit from another region is
                // skipped instead of reading that attacker from here.
                if (Bukkit.isOwnedByCurrentRegion(damager)) {
                    Entity lightning = this.methods.lightning(damager);

                    for (LivingEntity en : this.methods.getNearbyLivingEntities(2D, player)) {
                        if (!Bukkit.isOwnedByCurrentRegion(en)) continue;

                        EntityDamageEvent damageByEntityEvent = new EntityDamageEvent(en, DamageCause.LIGHTNING, DamageSource.builder(DamageType.LIGHTNING_BOLT).withCausingEntity(player).withDirectEntity(lightning).build(), 5D);
                        this.methods.entityEvent(player, en, damageByEntityEvent);
                    }

                    damager.damage(5D);
                }
            }
        }

        if (!(damager instanceof Player)) return;

        for (ItemStack armor : Objects.requireNonNull(damager.getEquipment()).getArmorContents()) {
            Map<CEnchantment, Integer> enchants = this.enchantmentBookSettings.getEnchantments(armor);
            if (!enchants.containsKey(CEnchantments.LEADERSHIP.getEnchantment())) continue;

            int radius = 4 + enchants.get(CEnchantments.LEADERSHIP.getEnchantment());
            int players = (int) damager.getNearbyEntities(radius, radius, radius).stream().filter(entity -> entity instanceof Player && this.support.isFriendly(damager, entity)).count();

            if (players > 0 && EnchantUtils.isEventActive(CEnchantments.LEADERSHIP, player, armor, enchants)) {
                event.setDamage(event.getDamage() + (players / 2d));
            }
        }
    }

    /**
     * Blood Lust heals from the landed event only: the pre-damage
     * {@link com.badbones69.crazyenchantments.paper.api.events.BleedTickEvent} can still be
     * reduced or cancelled by armor and protection, so a heal there would pay out for damage
     * that never happened.
     *
     * <p>The check order is the cost order: activation, then the wearer's chestplate and
     * health, then the cooldown, and only then the enchant's roll and its cancellable use
     * event. A server without Blood Lust, a full-health wearer and a wearer on cooldown
     * therefore never scan their surroundings or fire a proc event. Only players the
     * victim's region owns are read, and the exact block footprint is unchanged.</p>
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBleedTickLanded(final BleedTickLandedEvent event) {
        if (this.registryUnpublished) return;

        // Activation first: every Bleed tick reaches here, so a disabled enchant must not
        // pay for a player scan at all.
        final CEnchantment bloodLust = CEnchantments.BLOODLUST.getEnchantment();

        if (bloodLust == null || !bloodLust.isActivated()) return;

        final LivingEntity victim = event.getVictim();

        if (!Bukkit.isOwnedByCurrentRegion(victim)) return;

        final BloodLustSettings settings = TuningConfig.bloodLust();
        final Location victimLocation = victim.getLocation();

        final int victimX = victimLocation.getBlockX();
        final int victimY = victimLocation.getBlockY();
        final int victimZ = victimLocation.getBlockZ();

        // A local prefilter, never a change of the footprint: the corner of the 7x7x7 block
        // box is radius*sqrt(3) away, so this radius cannot drop a player the exact
        // withinFootprint check would accept, and it does not read every player in the world.
        final double scanRadius = settings.radius() * Math.sqrt(3.0) + 0.5;

        final long now = System.currentTimeMillis();

        for (final Player player : victim.getWorld().getNearbyPlayers(victimLocation, scanRadius)) {
            if (victim.getUniqueId().equals(player.getUniqueId())) continue;
            if (!Bukkit.isOwnedByCurrentRegion(player)) continue;
            if (player.isDead() || !player.isConnected()) continue;

            final Location location = player.getLocation();

            if (!settings.withinFootprint(
                    location.getBlockX() - victimX,
                    location.getBlockY() - victimY,
                    location.getBlockZ() - victimZ
            )) continue;

            if (this.support.isFriendly(player, victim)) continue;

            final ItemStack chestplate = player.getEquipment().getChestplate();

            if (chestplate == null || chestplate.isEmpty()) continue;

            final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(chestplate);
            final Integer level = enchantments.get(bloodLust);

            if (level == null || level < 1) continue;

            final AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);

            if (attribute == null) continue;

            final double maxHealth = attribute.getValue();

            // Full health cannot be healed: reject it before the roll and the use event.
            if (player.getHealth() >= maxHealth) continue;
            // On cooldown: the same, and the claim is the authoritative check below.
            if (!settings.ready(this.bloodLustCooldowns, player.getUniqueId(), now)) continue;

            if (!EnchantUtils.isEventActive(CEnchantments.BLOODLUST, player, chestplate, enchantments, settings.chanceMultiplier())) continue;

            if (!settings.claim(this.bloodLustCooldowns, player.getUniqueId(), now)) continue;

            player.setHealth(Math.min(maxHealth, player.getHealth() + settings.heal()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAura(AuraActiveEvent event) {
        if (this.registryUnpublished) return;

        final Player player = event.getPlayer();
        final Player other = event.getOther();
        final CEnchantments enchant = event.getEnchantment();
        final int level = event.getLevel();
        final Map<CEnchantment, Integer> enchantments = Map.of(enchant.getEnchantment(), level);

        // The wearer's own side of the gate, including the aura roll and its cooldown, is read
        // here, on the thread that fired the event. Only the other player's side is deferred.
        if (this.vanishSupport.isVanished(player) || !player.canSee(other)) return;
        if (!EnchantUtils.isAuraActive(player, enchant, enchantments)) return;
        final var settings = TuningConfig.fork().auras().get(enchant.getName());
        if (settings == null) return;

        // Every read and mutation of the other player happens on the region that owns it.
        runOnOwner(other, () -> {
            if (!other.isConnected() || other.isDead() || !other.isValid()) return;

            if (!other.canSee(player) || !player.hasLineOfSight(other) || !other.hasLineOfSight(player)) return;

            if (this.vanishSupport.isVanished(other)) return;

            if (!this.support.isCombatEnabled(other.getLocation()) || this.support.isFriendly(player, other) || this.methods.hasPermission(other, "bypass.aura", false)) return;

            switch (enchant) {
                case BLIZZARD -> other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, settings.durationTicks(), settings.amplifierAt(level)));

                case INTIMIDATE -> other.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, settings.durationTicks(), settings.amplifierAt(level)));

                case ACIDRAIN -> other.addPotionEffect(new PotionEffect(PotionEffectType.POISON, settings.durationTicks(), settings.amplifierAt(level)));

                case SANDSTORM -> other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, settings.durationTicks(), settings.amplifierAt(level)));

                case RADIANT -> other.setFireTicks(settings.durationTicks());
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMovement(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

        armorProcessor.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (player.getKiller() == null) return;

        Player killer = player.getKiller();

        if (!this.support.isCombatEnabled(player.getLocation())) return;

        for (ItemStack item : player.getEquipment().getArmorContents()) {
            Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

            if (EnchantUtils.isEventActive(CEnchantments.SELFDESTRUCT, player, item, enchantments)) {
                this.methods.explode(player);

                List<ItemStack> items = event.getDrops().stream().filter(drop -> ProtectionCrystalSettings.isProtected(drop.getPersistentDataContainer()) && this.protectionCrystalSettings.isProtectionSuccessful(player)).toList();

                event.getDrops().clear();
                event.getDrops().addAll(items);
            }

            if (EnchantUtils.isEventActive(CEnchantments.RECOVER, player, item, enchantments)) {
                killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 8 * 20, 2));
                killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (!this.fallenPlayers.contains(player.getUniqueId())) return;

        if (!DamageCause.FALL.equals(event.getCause())) return;

        event.setCancelled(true);
    }

    // ---------------------------------------------------------------------------------------------
    // Sticky, Aegis, Creeper Armor, Hardened, Ragdoll and Metaphysical. All six are always-on.
    // ---------------------------------------------------------------------------------------------

    private static boolean isDirectMelee(final DamageCause cause) {
        return cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
    }

    /**
     * Levels of the worn pieces of {@code enchant} that are active, one entry per enchanted piece
     * (so they can be summed). Each piece goes through {@link EnchantUtils#isEventActive}, which
     * also honours the deny permission and fires the use event.
     */
    private int[] pieceLevels(final Player wearer, final CEnchantments enchant, final ItemStack... gear) {
        final CEnchantment key = enchant.getEnchantment();

        if (key == null || !key.isActivated()) return new int[0];

        final int[] levels = new int[gear.length];
        int found = 0;

        for (final ItemStack item : gear) {
            if (item == null || item.isEmpty()) continue;

            final Map<CEnchantment, Integer> enchants = this.enchantmentBookSettings.getEnchantments(item);
            final int level = enchants.getOrDefault(key, 0);

            if (level < 1 || !EnchantUtils.isEventActive(enchant, wearer, item, enchants)) continue;

            levels[found++] = level;
        }

        return Arrays.copyOf(levels, found);
    }

    /**
     * One attempt for an enchant worn on several pieces. The per-piece odds combine into the
     * probability that at least one piece procs, then a single roll and a single use event
     * decide the whole proc, so one cancelled event vetoes it. Only enchant reads that treat
     * a set of worn pieces as one proc use this; per-piece procs roll through
     * {@link #pieceLevels}.
     *
     * @return the highest level among the pieces that carry the enchant, or 0 when the single
     *         attempt did not proc or the wearer denied it
     */
    private int combinedPieceAttempt(final Player wearer, final CEnchantments enchant, final ItemStack... gear) {
        final CEnchantment key = enchant.getEnchantment();

        if (key == null || !key.isActivated()) return 0;
        if (wearer.hasPermission("crazyenchantments.%s.deny".formatted(enchant.getName()))) return 0;

        final double[] chances = new double[gear.length];
        int found = 0;
        int highest = 0;
        ItemStack strongest = null;

        for (final ItemStack item : gear) {
            if (item == null || item.isEmpty()) continue;

            final Map<CEnchantment, Integer> enchants = this.enchantmentBookSettings.getEnchantments(item);
            final int level = enchants.getOrDefault(key, 0);

            if (level < 1) continue;

            if (level > highest) {
                highest = level;
                strongest = item;
            }

            chances[found++] = AngelicSettings.procChancePp(key.getChance(), key.getChanceIncrease(), level);
        }

        if (highest < 1 || strongest == null) return 0;

        // A single roll decides the combined attempt, including for operators.
        if (ThreadLocalRandom.current().nextDouble()
                >= AngelicSettings.combinedChance(Arrays.copyOf(chances, found))) return 0;

        if (!EnchantUtils.normalEnchantEvent(enchant, wearer, strongest)) return 0;

        return highest;
    }

    private int highest(final int... levels) {
        int highest = 0;

        for (final int level : levels) highest = Math.max(highest, level);

        return highest;
    }

    /**
     * Highest level of an always-on enchant across the given pieces, without rolling a
     * chance or firing a use event. Bookkeeping that must not proc (Aegis history) uses
     * this; chance enchants roll through {@link #pieceLevels}.
     */
    private int wornHighest(final Player wearer, final CEnchantments enchant, final ItemStack... gear) {
        int highest = 0;

        for (final ItemStack item : gear) highest = Math.max(highest, wornLevel(wearer, enchant, item));

        return highest;
    }

    /**
     * Drops a wearer's Aegis history once nothing in it is still inside the window, so an
     * idle entry does not outlive the fight that built it.
     */
    private void forgetIdleAegis(final UUID wearer, final Map<UUID, Deque<Long>> hits) {
        if (hits.isEmpty()) this.aegisHits.remove(wearer, hits);
    }

    /**
     * Aegis, step 1. Stage: HIGH on {@link EntityDamageByEntityEvent}, which edits the BASE damage
     * before vanilla armor, resistance and absorption are applied, like the Armored, Heavy and Tank
     * reductions, and before the HIGHEST handlers (so System Reboot sees the halved hit). The wearer's
     * highest worn level sets the threshold and the hit is halved once, never stacked. Only players who
     * hit with direct melee are tracked, matching Anti Gank. The attacker is ranked as if it were
     * already recorded: a new attacker ranks last. Count and rank come from one pass over the history,
     * which also expires it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAegisHit(final EntityDamageByEntityEvent event) {
        if (this.registryUnpublished || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (!isDirectMelee(event.getCause())) return;
        if (!(event.getEntity() instanceof Player wearer) || !(event.getDamager() instanceof Player attacker)) return;
        if (attacker.getUniqueId().equals(wearer.getUniqueId()) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final UUID id = wearer.getUniqueId();
        final Map<UUID, Deque<Long>> hits = this.aegisHits.get(id);

        // No history means at most one attacker, which can never pass the threshold.
        if (hits == null) return;

        // Friendship providers read live state, so they run only for an attacker this region owns.
        if (Bukkit.isOwnedByCurrentRegion(attacker) && this.support.isFriendly(attacker, wearer)) return;

        final int level = highest(pieceLevels(wearer, CEnchantments.AEGIS, wearer.getEquipment().getArmorContents()));

        if (level < 1) return;

        final AegisSettings aegis = TuningConfig.aegis();
        final AegisSettings.Reach reach = aegis.reach(hits, attacker.getUniqueId(), System.nanoTime());
        final double multiplier = aegis.multiplier(level, reach.count(), reach.rank());

        if (multiplier < 1.0) event.setDamage(event.getDamage() * multiplier);

        forgetIdleAegis(id, hits);
    }

    /**
     * Aegis, step 2: records a landed melee hit for a wearer who actually wears Aegis. A victim
     * without the enchant has nothing to rank, so no history is built for them. The history is
     * bounded to {@link TuningConfig#MAX_ANTI_GANK_HISTORY} distinct attackers by the tracker
     * itself. Synthetic hits are not attacks.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAegisRecord(final EntityDamageByEntityEvent event) {
        if (this.registryUnpublished || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (!isDirectMelee(event.getCause()) || !(event.getFinalDamage() > 0.0)) return;
        if (!(event.getEntity() instanceof Player wearer) || !(event.getDamager() instanceof Player attacker)) return;
        if (attacker.getUniqueId().equals(wearer.getUniqueId()) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        // No roll and no use event here: the history is bookkeeping, not a proc.
        if (wornHighest(wearer, CEnchantments.AEGIS, wearer.getEquipment().getArmorContents()) < 1) return;

        if (Bukkit.isOwnedByCurrentRegion(attacker) && this.support.isFriendly(attacker, wearer)) return;

        final UUID id = wearer.getUniqueId();
        final Map<UUID, Deque<Long>> hits = this.aegisHits.computeIfAbsent(id, key -> new ConcurrentHashMap<>());

        TuningConfig.aegis().record(hits, attacker.getUniqueId(), System.nanoTime());
    }

    /**
     * Creeper Armor, damage. Stage: HIGHEST on {@link EntityDamageEvent}, which also covers block
     * explosions. The pieces add up (capped at 100 pp) and the result is applied once: the BASE damage
     * is searched so that the FINAL damage after armor and absorption is cut by exactly that share. A
     * full cut cancels the hit. At the top level the wearer also heals, at most once per cooldown.
     *
     * <p>Blast provenance is read before the cause: a synthetic blast is CE's own handoff and can
     * arrive under a cause other than the vanilla explosion causes, so it is reduced like any other
     * blast. Every other synthetic hit is still skipped.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(final EntityDamageEvent event) {
        final DamageCause cause = event.getCause();
        final boolean blast = SyntheticHit.isBlast();

        if (this.registryUnpublished || EventUtils.isIgnoredEvent(event)) return;
        if (!blast && SyntheticHit.isBleedTick()) return;
        if (!blast && cause != DamageCause.ENTITY_EXPLOSION && cause != DamageCause.BLOCK_EXPLOSION) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final double finalDamage = event.getFinalDamage();

        if (!(finalDamage > 0.0)) return;

        final int[] levels = pieceLevels(wearer, CEnchantments.CREEPER_ARMOR, wearer.getEquipment().getArmorContents());

        if (levels.length == 0) return;

        final CreeperArmorSettings settings = TuningConfig.creeperArmor();
        final double pp = settings.totalPp(levels);

        if (pp > 0.0) {
            final double target = CreeperArmorSettings.reducedFinal(finalDamage, pp);

            if (target <= 0.0) {
                event.setCancelled(true);
            } else {
                event.setDamage(CreeperArmorSettings.baseForFinal(event.getDamage(), target, candidate -> {
                    event.setDamage(candidate);

                    return event.getFinalDamage();
                }));
            }
        }

        final AttributeInstance maxHealth = wearer.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth == null) return;

        final long now = System.nanoTime();
        final double heal = settings.healAmount(highest(levels), this.creeperHeals.get(wearer.getUniqueId()), now, wearer.getHealth(), maxHealth.getValue());

        if (heal <= 0.0) return;

        wearer.setHealth(Math.min(maxHealth.getValue(), wearer.getHealth() + heal));
        this.creeperHeals.put(wearer.getUniqueId(), now);
    }

    /**
     * Creeper Armor, folded splash. Called at MONITOR by
     * {@link com.badbones69.crazyenchantments.paper.listeners.DefensiveArmorListener}, which is
     * registered after the bow listener, because the fold has not happened yet at HIGHEST and the
     * splash is only published for this exact event once the bow handler has applied it.
     *
     * <p>Only the folded component is reduced. The event also carries the arrow damage the player
     * was meant to take, so scaling or replacing the whole event would eat the arrow; removing the
     * component's share leaves that arrow exactly as the bow wrote it.</p>
     */
    public void reduceFoldedBlast(final EntityDamageByEntityEvent event) {
        if (this.registryUnpublished || this.supportStopped) return;

        final double blastHp = BowBlastContext.blastComponent(event);

        if (!(blastHp > 0.0)) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final int[] levels = pieceLevels(wearer, CEnchantments.CREEPER_ARMOR, wearer.getEquipment().getArmorContents());

        if (levels.length == 0) return;

        // The component is part of the event's base damage, so the share comes off the base.
        final double removed = blastHp - CreeperArmorSettings.reducedFinal(blastHp, TuningConfig.creeperArmor().totalPp(levels));

        if (!(removed > 0.0)) return;

        event.setDamage(event.getDamage() - removed);

        // Level III heals on a landed blast, exactly as the standalone explosion path does.
        final AttributeInstance maxHealth = wearer.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth == null) return;

        final long now = System.nanoTime();
        final double heal = TuningConfig.creeperArmor().healAmount(highest(levels), this.creeperHeals.get(wearer.getUniqueId()), now, wearer.getHealth(), maxHealth.getValue());

        if (heal <= 0.0) return;

        wearer.setHealth(Math.min(maxHealth.getValue(), wearer.getHealth() + heal));
        this.creeperHeals.put(wearer.getUniqueId(), now);
    }

    /**
     * Creeper Armor, knockback: at the top level the wearer takes no explosion knockback.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionKnockback(final EntityKnockbackEvent event) {
        if (this.registryUnpublished) return;
        if (event.getCause() != EntityKnockbackEvent.Cause.EXPLOSION) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final int level = highest(pieceLevels(wearer, CEnchantments.CREEPER_ARMOR, wearer.getEquipment().getArmorContents()));

        if (TuningConfig.creeperArmor().cancelsKnockback(level)) event.setCancelled(true);
    }

    /**
     * Hardened: every wear point on a worn armor piece rolls its own skip chance from that piece's level.
     * Only armor is considered: the damaged item must be armor for slot X and must be the stack the player
     * wears in slot X. The live slot stack is compared by identity, or by content when the getter hands
     * back a copy, so equipping the same kind of piece cannot dodge the roll. Anything else (tools, weapons
     * and every other held item) is Reforged's business.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorWear(final PlayerItemDamageEvent event) {
        if (this.registryUnpublished || event.getDamage() <= 0 || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;

        final Player wearer = event.getPlayer();
        final ItemStack item = event.getItem();

        if (item.isEmpty() || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final EquipmentSlot slot = item.getType().getEquipmentSlot();

        if (!slot.isArmor()) return;

        // The getter is allowed to copy, so identity alone would miss the worn stack. An identical
        // stack in another slot is still rejected: the slot is the item type's own slot.
        final ItemStack worn = wearer.getEquipment().getItem(slot);

        if (worn == null || worn.isEmpty() || (worn != item && !worn.isSimilar(item))) return;

        final CEnchantment hardened = CEnchantments.HARDENED.getEnchantment();

        if (hardened == null || !hardened.isActivated()) return;

        final Map<CEnchantment, Integer> enchants = this.enchantmentBookSettings.getEnchantments(worn);
        final int level = enchants.getOrDefault(hardened, 0);

        if (level < 1 || !EnchantUtils.isEventActive(CEnchantments.HARDENED, wearer, worn, enchants)) return;

        final int surviving = TuningConfig.hardened().survivingPoints(event.getDamage(), level, ThreadLocalRandom.current()::nextDouble);

        if (surviving >= event.getDamage()) return;

        if (surviving <= 0) event.setCancelled(true);
        else event.setDamage(surviving);
    }

    /**
     * Ragdoll. Stage: MONITOR, so only a hit that really landed pushes. Direct melee only: the cause
     * must be an entity attack or sweep (not projectile, thorns or explosion) and the damager must be
     * the living entity that caused it. Allies, self hits and a damager owned by another region are
     * skipped. The push is added to the wearer's velocity one tick later, after vanilla knockback; a
     * death, world change, reload or rejoin in between drops it with the session it belongs to.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRagdollHit(final EntityDamageByEntityEvent event) {
        if (this.registryUnpublished || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (!isDirectMelee(event.getCause()) || !(event.getFinalDamage() > 0.0)) return;
        if (!(event.getEntity() instanceof Player wearer) || !(event.getDamager() instanceof LivingEntity damager)) return;
        if (damager.getUniqueId().equals(wearer.getUniqueId())) return;

        final Entity causing = event.getDamageSource().getCausingEntity();

        if (causing == null || !causing.getUniqueId().equals(damager.getUniqueId())) return;
        if (!Bukkit.isOwnedByCurrentRegion(wearer) || !Bukkit.isOwnedByCurrentRegion(damager)) return;
        if (this.support.isFriendly(damager, wearer)) return;

        final int level = highest(pieceLevels(wearer, CEnchantments.RAGDOLL, wearer.getEquipment().getArmorContents()));

        if (level < 1) return;

        final UUID id = wearer.getUniqueId();
        final RagdollSettings ragdoll = TuningConfig.ragdoll();
        final RagdollSettings.Push push = ragdoll.push(level, wearer.getX() - damager.getX(), wearer.getZ() - damager.getZ());

        if (push.isNone() || !ragdoll.claim(this.ragdollCooldowns, id, System.nanoTime())) return;

        final Object session = this.ragdollSessions.computeIfAbsent(id, key -> new Object());

        wearer.getScheduler().runDelayed(this.plugin, task -> {
            if (wearer.isDead() || !wearer.isValid()) return;
            if (this.ragdollSessions.get(id) != session) return;

            wearer.setVelocity(wearer.getVelocity().add(new Vector(push.x(), push.y(), push.z())));
        }, null, 1L);
    }

    /**
     * Metaphysical. Only the boots slot counts. Slowness added by another source is cancelled with the
     * level's chance; every other effect, and a slow that is not resisted, is left exactly as it is.
     * CE's own Snare, Pummel and aura slows arrive as plugin effects and are resisted.
     * Not "another source": the wearer's own potion, milk, commands and expiry, the permanent armor
     * effects CE puts on the wearer (marked in {@link #updateEffects}, or infinite plugin effects from
     * the older refresh paths), and removals.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSlowApplied(final EntityPotionEffectEvent event) {
        final PotionEffect effect = event.getNewEffect();

        if (effect == null || !PotionEffectType.SLOWNESS.equals(effect.getType())) return;
        if (this.registryUnpublished) return;
        if (!(event.getEntity() instanceof Player wearer) || !Bukkit.isOwnedByCurrentRegion(wearer)) return;

        final int level = highest(pieceLevels(wearer, CEnchantments.METAPHYSICAL, wearer.getEquipment().getBoots()));

        if (level < 1) return;

        final boolean selfInflicted = MetaphysicalSettings.isSelfInflicted()
                || (event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN && effect.getDuration() == PotionEffect.INFINITE_DURATION);

        if (TuningConfig.metaphysical().resists(true, event.getAction().name(), event.getCause().name(), selfInflicted, level, ThreadLocalRandom.current().nextDouble())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID id = player.getUniqueId();

        // The quitting client is gone, so its session and every record it holds are
        // discarded: no show is needed or possible. A record for a target that is merely
        // offline while its viewer is online is kept instead - that rule lives in
        // FeignVisibility#release and is not touched here.
        this.feignViews.computeIfPresent(id, (key, view) -> view.player == player ? null : view);

        stopSupportRuns(id);
        stopRuns(id);
        clearCombatState(id);
        // Plague Carrier and Feign Death cooldowns, and Plague's re-arm flag, survive the
        // reconnect: clearing them let a player drop and rejoin to bypass both cooldowns and
        // the required recovery. Summons and visibility changes still end above.
    }

    /**
     * Ends everything the wearer's own fight built: Aegis's attacker history, the Creeper
     * Armor heal window, the Ragdoll cooldown and the session its queued push belongs to.
     * Called from the same four lifecycle points everywhere - quit, death, world change and
     * reload - so no state can outlive the fight it belongs to.
     */
    private void clearCombatState(final UUID id) {
        this.aegisHits.remove(id);
        this.creeperHeals.remove(id);
        this.ragdollCooldowns.remove(id);
        this.ragdollSessions.remove(id);
        this.bloodLustCooldowns.remove(id);
        this.rocketCooldowns.remove(id);
    }

    /**
     * A world change ends the fight and every armor run with it: the attacker history names
     * players left behind, the Ragdoll push in flight belongs to the old world, a Feign Death
     * vanish must not follow the wearer into the destination, a Spirits or Plague summon
     * belongs to the world that was left, and the tickers' region is gone. The always-on
     * tickers are re-armed on the new owning region afterwards, so Curse, Destruction and
     * Plague Carrier keep working without waiting for the next hit.
     */
    @EventHandler
    public void onArmorWorldChange(final PlayerChangedWorldEvent event) {
        clearCombatState(event.getPlayer().getUniqueId());

        final Player player = event.getPlayer();

        stopSupportRuns(player.getUniqueId());
        stopRuns(player.getUniqueId());

        rearmWornRuns(player);
    }

    /**
     * Starts the always-on runs once the player is owned by the destination region. The
     * world-change event is not guaranteed to run on that region, so the same owner hop the
     * join path uses is taken when it is not.
     */
    private void rearmWornRuns(final Player player) {
        if (this.supportStopped || !player.isConnected()) return;

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            startWornRuns(player);

            return;
        }

        try {
            player.getScheduler().run(this.plugin, task -> startWornRuns(player), null);
        } catch (final IllegalPluginAccessException ignored) {
            // The player is already leaving or the scheduler is shutting down.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSupportDamage(final EntityDamageEvent event) {
        if (this.registryUnpublished || this.supportStopped || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (!(event.getFinalDamage() > 0.0) || !(event.getEntity() instanceof Player wearer)) return;
        if (!Bukkit.isOwnedByCurrentRegion(wearer) || wearer.isDead()) return;

        // A use event or an ally provider can begin and finish a reload while this handler
        // runs. The epoch is captured now and re-checked after each of them, so a proc
        // decided against the replaced registry is dropped instead of surviving.
        final long epoch = this.reloadEpoch.get();

        final Entity attacker = attacker(event);
        final UUID attackerId = attacker == null ? null : attacker.getUniqueId();
        if (attackerId != null && EventUtils.isIgnoredUUID(attackerId)) return;

        final UUID id = wearer.getUniqueId();
        final ItemStack[] armor = wearer.getEquipment().getArmorContents();

        // One hostile-source rule for Angelic, Spirit Link and Spirits: a living attacker that is
        // owned by this region, is not the wearer and is not an ally. Nonliving sources (TNT, end
        // crystals) and self-inflicted damage never qualify. The ally provider is a lazy argument,
        // so an attacker that is the wearer or owned by another region never reaches it at all.
        final boolean hostile = attacker instanceof LivingEntity && SpiritsSettings.hostileSource(
                id.equals(attackerId), Bukkit.isOwnedByCurrentRegion(attacker), () -> isSupportAlly(wearer, attacker));

        // Re-check after the eligibility work: the gate can close while this handler runs, and every
        // branch below reads tuning and would otherwise mix a new settings record with an old decision.
        if (staleProc(epoch)) return;

        if (hostile) {
            // One attempt for the whole set of pieces, one roll and one use event: the pieces
            // are alternatives, so a cancelled event now vetoes the proc instead of one of
            // several rolls. No second run is started while one is already healing.
            if (!this.angelicRuns.containsKey(id) && combinedPieceAttempt(wearer, CEnchantments.ANGELIC, armor) > 0
                    && !staleProc(epoch)) {
                startAngelic(wearer, id);
            }

            final SpiritLinkSettings settings = TuningConfig.spiritLink();
            final long now = System.nanoTime();

            // Eligibility before the roll: an enchant on cooldown must neither roll nor fire
            // its use event. The claim only happens once the roll accepted the proc.
            if (settings.ready(this.spiritLinkCooldowns, id, now)) {
                final int link = highest(pieceLevels(wearer, CEnchantments.SPIRIT_LINK, wearer.getEquipment().getChestplate()));

                if (link > 0 && !staleProc(epoch) && settings.claim(this.spiritLinkCooldowns, id, now)) {
                    healAllies(wearer, wearer, attackerId, settings.radius(), settings.heal(link), false);
                }
            }
        }

        // Spirits only answers damage from another entity: fall, fire, void and self-inflicted
        // damage must not summon blazes or burn the 30 second cooldown. The cooldown is checked
        // before anything rolls or fires a use event, and the whole worn set is one attempt:
        // one roll and one cancellable event for the selected piece.
        if (hostile && !this.spiritsRuns.containsKey(id)) {
            final SpiritsSettings settings = TuningConfig.spirits();
            final long now = System.nanoTime();

            if (settings.ready(this.spiritsCooldowns, id, now)) {
                final int spirits = combinedPieceAttempt(wearer, CEnchantments.SPIRITS, armor);
                if (spirits > 0 && !staleProc(epoch)) startSpirits(wearer, id, attackerId, spirits);
            }
        }
    }

    private static Entity attacker(final EntityDamageEvent event) {
        final Entity causing = event.getDamageSource().getCausingEntity();
        if (causing != null) return causing;
        return event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSupportDeath(final PlayerDeathEvent event) {
        final Player wearer = event.getEntity();
        stopSupportRuns(wearer.getUniqueId());
        if (this.registryUnpublished || this.supportStopped || EventUtils.isIgnoredEvent(event)) return;
        if (pieceLevels(wearer, CEnchantments.LIFEBLOOM, wearer.getEquipment().getLeggings()).length == 0) return;

        final Entity causing = event.getDamageSource().getCausingEntity();
        final Player killer = causing == null ? wearer.getKiller() : null;
        final UUID attacker = causing != null ? causing.getUniqueId()
                : killer != null && Bukkit.isOwnedByCurrentRegion(killer) ? killer.getUniqueId() : null;
        healAllies(wearer, wearer, attacker,
                TuningConfig.lifebloom().radius(), Double.POSITIVE_INFINITY, false);
    }

    private void startAngelic(final Player wearer, final UUID id) {
        if (this.supportStopped || !wearer.isValid() || wearer.isDead()) return;
        final AngelicSettings settings = TuningConfig.angelic();
        final SupportRun run = new SupportRun();
        if (!AngelicSettings.claim(this.angelicRuns, id, run)) return;

        scheduleSupportRun(wearer, id, this.angelicRuns, run, settings.durationTicks(),
                () -> settings == TuningConfig.angelic(), age -> {
                    if (age % AngelicSettings.PULSE_TICKS == 0) heal(wearer, settings.healPerSecond());
                });
    }

    private void startSpirits(final Player wearer, final UUID id, final UUID attacker, final int level) {
        if (this.supportStopped || !wearer.isValid() || wearer.isDead()) return;
        final SpiritsSettings settings = TuningConfig.spirits();
        final long now = System.nanoTime();
        if (!settings.claim(this.spiritsCooldowns, id, now)) return;

        final SupportRun run = new SupportRun();
        if (this.spiritsRuns.putIfAbsent(id, run) != null) return;

        try {
            spawnSpirits(wearer.getLocation(), id, run, settings.blazeCount(level));
            if (!run.blazes.isEmpty()) {
                scheduleSupportRun(wearer, id, this.spiritsRuns, run, settings.lifetimeTicks(),
                        () -> settings == TuningConfig.spirits(), age -> {
                            if (age % settings.cadenceTicks() != 0) return;

                            // The blazes are invulnerable, so the only way one disappears is a
                            // chunk unload or another plugin removing it. Retire the run once the
                            // last one is gone instead of holding the registry entry and the
                            // cooldown until the lifetime expires.
                            boolean living = false;

                            for (final Blaze blaze : run.blazes) {
                                if (!Bukkit.isOwnedByCurrentRegion(blaze)) {
                                    // A blaze another region owns cannot be read here. It still
                                    // belongs to this run, so this pulse does not retire it.
                                    living = true;
                                    continue;
                                }

                                if (!blaze.isValid() || blaze.isDead()) {
                                    run.blazes.remove(blaze);
                                    continue;
                                }

                                living = true;

                                healAllies(wearer, blaze, attacker, settings.radius(), settings.heal(), true);
                            }

                            if (!living) finishSupportRun(this.spiritsRuns, id, run, true);
                        });
            } else {
                this.spiritsCooldowns.remove(id, now);
            }
        } finally {
            if (run.task == null) finishSupportRun(this.spiritsRuns, id, run, true);
        }
    }

    private void scheduleSupportRun(final Player wearer, final UUID id, final ConcurrentHashMap<UUID, SupportRun> active,
                                    final SupportRun run, final long duration, final BooleanSupplier current,
                                    final LongConsumer pulse) {
        try {
            if (this.supportStopped || active.get(id) != run) return;
            run.task = wearer.getScheduler().runAtFixedRate(this.plugin, task -> {
                if (this.supportStopped || active.get(id) != run || !current.getAsBoolean()
                        || wearer.isDead() || !wearer.isValid()) {
                    finishSupportRun(active, id, run, true);
                    return;
                }

                // No heal pulse may straddle registry publication, matching the always-on runs.
                if (this.registryUnpublished) return;

                try {
                    pulse.accept(++run.age);
                } finally {
                    if (run.age >= duration) finishSupportRun(active, id, run, true);
                }
            }, () -> finishSupportRun(active, id, run, false), 1L, 1L);
        } catch (final IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
        } finally {
            if (this.supportStopped || run.task == null) finishSupportRun(active, id, run, true);
            if (active.get(id) != run && run.task != null) run.task.cancel();
        }
    }

    private void spawnSpirits(final Location center, final UUID id, final SupportRun run, final int count) {
        final var world = center.getWorld();
        final int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (final int[] offset : offsets) {
            if (run.blazes.size() >= count || this.supportStopped || this.spiritsRuns.get(id) != run) return;
            for (final int dy : new int[]{0, 1, -1, 2}) {
                final Location at = new Location(world, center.getBlockX() + offset[0] + 0.5,
                        center.getBlockY() + dy, center.getBlockZ() + offset[1] + 0.5);
                if (at.getBlockY() < world.getMinHeight() || at.getBlockY() + 1 >= world.getMaxHeight()) continue;
                if (!Bukkit.isOwnedByCurrentRegion(at) || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
                if (!at.getBlock().getType().isAir() || !at.getBlock().getRelative(0, 1, 0).getType().isAir()) continue;

                final Blaze blaze = world.spawn(at, Blaze.class, SpawnReason.CUSTOM, false, this::configureSpirit);
                if (blaze == null || !blaze.isValid()) continue; // another plugin can cancel the spawn, or Paper returns null
                run.blazes.add(blaze);
                if (this.supportStopped || this.spiritsRuns.get(id) != run) {
                    removeSpirit(blaze, true);
                    return;
                }
                break;
            }
        }
    }

    private void configureSpirit(final Blaze blaze) {
        blaze.getPersistentDataContainer().set(this.spiritBlazeKey, PersistentDataType.BOOLEAN, true);
        blaze.setAI(false);
        blaze.setAware(false);
        blaze.setTarget(null);
        blaze.setAggressive(false);
        blaze.setInvulnerable(true);
        blaze.setCollidable(false);
        blaze.setGravity(false);
        blaze.setRemoveWhenFarAway(false);
        blaze.setPersistent(false);
        blaze.setLootTable(null);
    }

    private boolean isSpirit(final Entity entity) {
        return entity instanceof Blaze && Bukkit.isOwnedByCurrentRegion(entity)
                && entity.getPersistentDataContainer().has(this.spiritBlazeKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpiritTarget(final EntityTargetEvent event) {
        if (event.getTarget() != null && (isSpirit(event.getEntity()) || isSpirit(event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpiritDamage(final EntityDamageEvent event) {
        if (isSpirit(event.getEntity()) || isSpirit(attacker(event))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpiritDeath(final EntityDeathEvent event) {
        if (!isSpirit(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private void stopSupportRuns(final UUID id) {
        final SupportRun angelic = this.angelicRuns.get(id);
        if (angelic != null) finishSupportRun(this.angelicRuns, id, angelic, true);
        final SupportRun spirits = this.spiritsRuns.get(id);
        if (spirits != null) finishSupportRun(this.spiritsRuns, id, spirits, true);
    }

    private static void finishSupportRun(final ConcurrentHashMap<UUID, SupportRun> active, final UUID id,
                                         final SupportRun run, final boolean mayRemoveDirectly) {
        if (!active.remove(id, run)) return;
        if (run.task != null) run.task.cancel();
        for (final Blaze blaze : run.blazes) removeSpirit(blaze, mayRemoveDirectly);
    }

    private static void removeSpirit(final Blaze blaze, final boolean mayRemoveDirectly) {
        if (mayRemoveDirectly && Bukkit.isOwnedByCurrentRegion(blaze)) {
            if (blaze.isValid()) blaze.remove();
        } else {
            // The public scheduler rejects new work from a disabled plugin. Queue only this
            // terminal removal on the owning thread, even during disable or scheduler retirement.
            ((CraftEntity) blaze).taskScheduler.schedule(entity -> entity.getBukkitEntity().remove(), null, 1L);
        }
    }

    private void healAllies(final Player wearer, final LivingEntity center, final UUID attacker, final double radius,
                            final double amount, final boolean includeWearer) {
        if (!Bukkit.isOwnedByCurrentRegion(wearer) || !Bukkit.isOwnedByCurrentRegion(center)) return;

        final Location origin = center.getLocation();
        final double radiusSquared = radius * radius;
        for (final LivingEntity target : origin.getWorld().getNearbyLivingEntities(origin, radius)) {
            if (!Bukkit.isOwnedByCurrentRegion(target) || (target == center && target != wearer)) continue;
            if (target.getLocation().distanceSquared(origin) > radiusSquared) continue;
            final boolean dead = target.isDead() || target.getHealth() <= 0.0;
            final boolean self = target == wearer;
            // Self-inflicted triggers pass the wearer as the attacker; that must not exclude the wearer from their own heal.
            final boolean hitBy = !self && target.getUniqueId().equals(attacker);
            final boolean friendly = !dead && !hitBy && !self && isSupportAlly(wearer, target);
            if (LifebloomSettings.eligible(dead, hitBy, self, includeWearer, friendly)) heal(target, amount);
        }
    }

    private boolean isSupportAlly(final Player wearer, final Entity target) {
        if (this.support.isFriendly(wearer, target)) return true;
        if (target instanceof Player) return false;

        if (target instanceof Tameable pet) {
            final UUID ownerId = pet.getOwnerUniqueId();
            if (ownerId != null) {
                if (ownerId.equals(wearer.getUniqueId())) return true;
                final Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && Bukkit.isOwnedByCurrentRegion(owner) && this.support.isFriendly(wearer, owner)) return true;
            }
        }

        // The older ally registry is shared and its iterator fails fast across Folia regions.
        // Resolve only the exact entity through the bounded per-tick index, which is built from
        // one snapshot of that list and never read the list on the hot path.
        final AllyMob ally = allyIndex().get(target.getUniqueId());

        if (ally == null) return false;

        final Player owner = ally.getOwner();

        return owner == wearer || (owner != null && Bukkit.isOwnedByCurrentRegion(owner) && this.support.isFriendly(wearer, owner));
    }

    /**
     * The ally mobs by their entity's UUID, rebuilt from one snapshot of
     * {@link com.badbones69.crazyenchantments.paper.api.managers.AllyManager#getAllyMobs()}
     * at most once per tick. The list is mutated from other regions, so it is copied once
     * here instead of once per candidate, and a snapshot that cannot be taken leaves the
     * previous index in place.
     */
    private Map<UUID, AllyMob> allyIndex() {
        final long now = System.nanoTime();
        final AllyIndex current = this.allyIndex;

        if (current != null && now - current.takenAt() < ALLY_INDEX_NANOS) return current.byAlly();

        final Map<UUID, AllyMob> byAlly = new HashMap<>();

        try {
            for (final Object entry : this.starter.getAllyManager().getAllyMobs().toArray()) {
                if (!(entry instanceof AllyMob ally)) continue;

                final LivingEntity entity = ally.getAlly();

                if (entity != null) byAlly.putIfAbsent(entity.getUniqueId(), ally);
            }
        } catch (final RuntimeException mutation) {
            // A concurrent add or remove can make the plain list throw mid-copy. The previous
            // index is still correct enough for this tick; the next pulse tries again.
            return current == null ? Map.of() : current.byAlly();
        }

        final AllyIndex next = new AllyIndex(Map.copyOf(byAlly), now);

        this.allyIndex = next;

        return next.byAlly();
    }

    private static void heal(final LivingEntity entity, final double amount) {
        if (!Bukkit.isOwnedByCurrentRegion(entity) || entity.isDead()) return;
        final double health = entity.getHealth();
        final double healed = LifebloomSettings.healedHealth(false, health, entity.getMaxHealth(), amount);
        if (healed > health) entity.setHealth(healed);
    }

    // ---------------------------------------------------------------------------------------------
    // Curse, Destruction, Plague Carrier and Feign Death.
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // A rejoin is a new client session: nothing hidden from a previous session carries over.
        this.feignViews.put(player.getUniqueId(), new FeignView(player, this.bukkitVisibility));

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            hideActiveVanishFrom(player);
            startWornRuns(player);

            return;
        }

        try {
            player.getScheduler().run(this.plugin, task -> {
                hideActiveVanishFrom(player);
                startWornRuns(player);
            }, null);
        } catch (final IllegalPluginAccessException ignored) {
            // The player is already leaving or the scheduler is shutting down.
        }
    }

    /**
     * A viewer who joins while a vanish is running must not see the wearer: every
     * active run reconciles its hide onto the joining viewer, on that viewer's region.
     */
    private void hideActiveVanishFrom(final Player viewer) {
        for (final FeignRun run : this.feignRuns.values()) {
            if (run.wearer.getUniqueId().equals(viewer.getUniqueId())) continue;

            reconcileViewer(run.wearer, viewer);
        }
    }

    /**
     * Reconciles the always-on runs after a reload.
     *
     * <p>{@link com.badbones69.crazyenchantments.paper.api.CrazyManager#load()} calls this
     * only after it republished the registry and every manager, so reconciliation can never
     * run against a half-rebuilt registry. Each run carries the settings snapshot it was
     * built with, so only the runs a reload actually replaced are retired; a run started
     * after the reload carries the current record and is left alone. Each player's work is
     * queued on that player's own scheduler, so it runs on the thread that owns those runs.</p>
     */
    public void reconcileAfterReload() {
        if (this.supportStopped) return;

        for (final Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.getScheduler().run(this.plugin, task -> reconcileWornRuns(player), null);
            } catch (final IllegalPluginAccessException ignored) {
                // The player is already leaving or the scheduler is shutting down.
            }
        }
    }

    private void reconcileWornRuns(final Player player) {
        if (this.supportStopped || !player.isConnected()) return;

        final UUID id = player.getUniqueId();

        // A reload replaces the tuning every queued or cached defensive decision was made
        // with, so the per-player combat state is dropped with it.
        clearCombatState(id);

        // Retire only the runs whose settings snapshot is no longer the current record.
        final FeignRun feign = this.feignRuns.get(id);

        if (feign != null && feign.settings != TuningConfig.feignDeath()) endFeign(id, feign);

        final TickingRun curse = this.curseRuns.get(id);

        if (curse != null && curse.settings != TuningConfig.curse()) finishRun(this.curseRuns, id, curse);

        final TickingRun destruction = this.destructionRuns.get(id);

        if (destruction != null && destruction.settings != TuningConfig.destruction()) finishRun(this.destructionRuns, id, destruction);

        final TickingRun plague = this.plagueTicking.get(id);

        if (plague != null && plague.settings != TuningConfig.plagueCarrier()) finishRun(this.plagueTicking, id, plague);

        final TickingRun implants = this.implantsRuns.get(id);

        if (implants != null && implants.settings != TuningConfig.implants()) finishRun(this.implantsRuns, id, implants);

        final TickingRun enderShift = this.enderShiftRuns.get(id);

        if (enderShift != null && enderShift.settings != TuningConfig.enderShift()) finishRun(this.enderShiftRuns, id, enderShift);

        // A live burst started under the old record must go too: leaving it registered
        // would reject the next proc (the registry entry is per wearer) and its creepers
        // would outlive the reload. Retirement is idempotent and removes the entry first,
        // so a fresh burst is admitted immediately afterwards.
        final PlagueRun burst = this.plagueRuns.get(id);

        if (burst != null && burst.settings != TuningConfig.plagueCarrier()) {
            stopPlague(id, true);
            // stopPlague is idempotent but returns early once another thread claimed retirement, so a
            // claimed-but-not-yet-removed entry could survive and reject the next burst. Detach the
            // exact entry we checked; a different run inserted meanwhile is left alone.
            this.plagueRuns.remove(id, burst);
        }

        startWornRuns(player);
    }

    /**
     * Starts whatever always-on run the worn gear should have and is not already
     * running. Starting one first checks the worn piece.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWornDamage(final EntityDamageEvent event) {
        if (this.supportStopped || !(event.getEntity() instanceof Player wearer)) return;
        if (!Bukkit.isOwnedByCurrentRegion(wearer)) return;

        startWornRuns(wearer);
    }

    private void startWornRuns(final Player player) {
        // Admission is part of the reload gate: a run admitted while the registry is being
        // replaced would be stamped with the new settings and then survive reconciliation,
        // even though its eligibility was decided against the old registry.
        if (this.supportStopped || this.registryUnpublished || player.isDead() || !player.isValid()) return;

        final UUID id = player.getUniqueId();

        if (!this.curseRuns.containsKey(id)) startCurse(player);
        if (!this.destructionRuns.containsKey(id)) startDestruction(player);
        if (!this.plagueTicking.containsKey(id)) startPlagueTicker(player);
        if (!this.implantsRuns.containsKey(id)) startImplants(player);
        if (!this.enderShiftRuns.containsKey(id)) startEnderShift(player);
    }

    /**
     * Level of an always-on enchant on a worn piece, without rolling a chance or
     * firing the use event. Chance enchants roll through {@link #pieceLevels}.
     */
    private int wornLevel(final Player wearer, final CEnchantments enchant, final ItemStack item) {
        if (item == null || item.isEmpty()) return 0;

        final CEnchantment key = enchant.getEnchantment();

        if (key == null || !key.isActivated()) return 0;

        return this.enchantmentBookSettings.getEnchantments(item).getOrDefault(key, 0);
    }

    /**
     * Runs {@code pulse} once per tick on the wearer's own scheduler (Folia), so
     * the work never leaves the region that owns the wearer. The run ends when
     * the settings record is replaced by a reload, when the wearer dies, or when
     * the pulse ends it; {@code run.finished} runs exactly once, then.
     */
    private void scheduleRun(final Player wearer, final UUID id, final ConcurrentHashMap<UUID, TickingRun> active,
                             final TickingRun run, final BooleanSupplier current, final LongConsumer pulse) {
        try {
            if (this.supportStopped || active.get(id) != run) return;

            run.task = wearer.getScheduler().runAtFixedRate(this.plugin, task -> {
                if (this.supportStopped || active.get(id) != run || !current.getAsBoolean()
                        || wearer.isDead() || !wearer.isValid()) {
                    finishRun(active, id, run);
                    return;
                }

                // No proc may straddle registry publication; the run stays alive and
                // simply skips its pulses until the reload gate opens.
                if (this.registryUnpublished) return;

                pulse.accept(++run.age);
            }, () -> finishRun(active, id, run), 1L, 1L);
        } catch (final IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
        } finally {
            if (this.supportStopped || run.task == null) finishRun(active, id, run);
            if (active.get(id) != run && run.task != null) run.task.cancel();
        }
    }

    private void finishRun(final ConcurrentHashMap<UUID, TickingRun> active, final UUID id, final TickingRun run) {
        if (!active.remove(id, run)) return;

        if (run.task != null) run.task.cancel();

        run.finished.run();
    }

    /**
     * Runs {@code action} on the entity's owning region thread, now when this is
     * that thread. The fallback is the same terminal hop {@link #removeSpirit}
     * uses: the public scheduler refuses new work from a disabled plugin.
     */
    private static void runOnOwner(final Entity entity, final Runnable action) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            action.run();

            return;
        }

        ((CraftEntity) entity).taskScheduler.schedule(scheduled -> action.run(), null, 1L);
    }

    // ---------------------------------------------------------------------------------------------
    // Curse: always-on chestplate. Health cut with 5 points of hysteresis.
    // ---------------------------------------------------------------------------------------------

    private void startCurse(final Player wearer) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        // Captured before the eligibility read. If a reload replaces the registry under that
        // read or the record stamped below, the admission is dropped instead of reconciled as
        // a run that already looks current.
        final long epoch = this.reloadEpoch.get();

        if (wornLevel(wearer, CEnchantments.CURSE, wearer.getEquipment().getChestplate()) < 1) return;

        final CurseSettings settings = TuningConfig.curse();
        final TickingRun run = new TickingRun(wearer);

        // Stamp the record this run will actually use, not a value read later.
        run.settings = settings;

        if (this.curseRuns.putIfAbsent(wearer.getUniqueId(), run) != null) return;

        if (staleProc(epoch)) {
            finishRun(this.curseRuns, wearer.getUniqueId(), run);

            return;
        }

        scheduleRun(wearer, wearer.getUniqueId(), this.curseRuns, run, () -> settings == TuningConfig.curse(),
                age -> tickCurse(wearer, settings, run));
    }

    private void tickCurse(final Player wearer, final CurseSettings settings, final TickingRun run) {
        final long epoch = this.reloadEpoch.get();
        final boolean refresh = run.age - run.lastRefresh >= CurseSettings.REFRESH_TICKS;

        if (refresh) {
            run.lastRefresh = run.age;

            final int worn = wornLevel(wearer, CEnchantments.CURSE, wearer.getEquipment().getChestplate());

            if (worn < 1) {
                // Piece removed: the run ends. Nothing is removed by hand, so the effects
                // simply lapse within one refresh window and no foreign effect is touched.
                finishRun(this.curseRuns, wearer.getUniqueId(), run);

                return;
            }

            run.level = worn;
        }

        if (run.level < 1) return;

        final double maxHealth = wearer.getMaxHealth();
        final double health = wearer.getHealth();

        if (!settings.below(run.level, health, maxHealth)) {
            // Disarm only when an active Curse ends. Merely stepping into the
            // hysteresis band (for example 42% at V) must not disarm it, or dropping back to
            // 39% would do nothing until the wearer first climbed to 45%.
            if (run.armed && run.applied) {
                run.armed = false;
                run.applied = false;
            }

            if (settings.rearmed(run.level, health, maxHealth)) run.armed = true;

            return;
        }

        if (!run.armed || !refresh) return;

        // The roll this enchant is allowed to make, plus the deny permission.
        final int level = highest(pieceLevels(wearer, CEnchantments.CURSE, wearer.getEquipment().getChestplate()));

        if (level < 1) return;

        // The use event inside pieceLevels can run a reload to completion. Adding the effects
        // then would mix the new registry with a decision made against the old one.
        if (staleProc(epoch)) return;

        applyCurse(wearer, level, run);
    }

    private void applyCurse(final Player wearer, final int level, final TickingRun run) {
        final boolean strength = applyCurseEffect(wearer, PotionEffectType.STRENGTH, 0);
        final boolean slowness = applyCurseEffect(wearer, PotionEffectType.SLOWNESS, 0);
        final boolean resistance = applyCurseEffect(wearer, PotionEffectType.RESISTANCE, CurseSettings.resistanceAmplifier(level));

        // The cycle only counts as applied when at least one addition really succeeded.
        // Setting the latch on an all-occupied cycle would lock out the first real
        // application until the wearer climbed past the hysteresis band.
        if (CurseSettings.anyApplied(strength, slowness, resistance)) run.applied = true;
    }

    /**
     * Fills a gap only. Ownership of a potion effect cannot be proven (there is no
     * source tag), so an effect of this type that is already present is never
     * overwritten, refreshed or replaced - neither another plugin's buff nor the one
     * Curse itself applied a moment ago.
     *
     * @return whether an effect was actually added
     */
    private boolean applyCurseEffect(final Player wearer, final PotionEffectType type, final int amplifier) {
        if (!CurseSettings.mayApply(wearer.hasPotionEffect(type))) return false;

        final boolean[] added = {false};

        // Slowness is the wearer's own armor effect, so Metaphysical must not resist it.
        MetaphysicalSettings.runSelfInflicted(() -> added[0] = wearer.addPotionEffect(
                new PotionEffect(type, CurseSettings.EFFECT_TICKS, amplifier, false, false, true)));

        return added[0];
    }

    // ---------------------------------------------------------------------------------------------
    // Rocket Escape: boots. Hit at low health, off cooldown, launches the wearer straight up.
    // ---------------------------------------------------------------------------------------------

    /**
     * Cheap pre-check, before any use event: low enough health and the cooldown is over. The
     * cooldown is only claimed by {@link #launchRocketEscape}, once the proc was accepted.
     */
    private boolean rocketEscapeReady(final Player wearer, final int level) {
        return TuningConfig.rocketEscape().triggers(wearer.getHealth(), wearer.getMaxHealth())
                && TuningConfig.rocketEscape().ready(this.rocketCooldowns.get(wearer.getUniqueId()), System.nanoTime(), level);
    }

    private void launchRocketEscape(final Player wearer, final int level) {
        final RocketEscapeSettings settings = TuningConfig.rocketEscape();
        final UUID id = wearer.getUniqueId();
        final double power = settings.velocity(level);

        this.rocketCooldowns.put(id, System.nanoTime());

        // The hit's own knockback lands after this event, so the launch waits one tick to win.
        // Only the wearer is touched, on the wearer's own region; the attacker is never read.
        new FoliaScheduler(this.plugin, null, wearer) {
            @Override
            public void run() {
                if (!wearer.isValid() || wearer.isDead()) return;

                wearer.setVelocity(wearer.getVelocity().setY(power));
            }
        }.runDelayed(1);

        // The launch must not end in fall damage at the low health that triggered it.
        this.fallenPlayers.add(id);

        wearer.getWorld().spawnParticle(Particle.EXPLOSION, wearer.getLocation(), 1);

        new FoliaScheduler(this.plugin, null, wearer) {
            @Override
            public void run() {
                fallenPlayers.remove(id);
            }
        }.runDelayed(8 * 20);
    }

    // ---------------------------------------------------------------------------------------------
    // Implants: always-on helmet. Heals and feeds the wearer on a fixed interval, capped.
    // ---------------------------------------------------------------------------------------------

    private void startImplants(final Player wearer) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        // Same admission rule as Curse: an eligibility read against the old registry must not
        // produce a run stamped with the record a reload just published.
        final long epoch = this.reloadEpoch.get();

        if (wornLevel(wearer, CEnchantments.IMPLANTS, wearer.getEquipment().getHelmet()) < 1) return;

        final ImplantsSettings settings = TuningConfig.implants();
        final TickingRun run = new TickingRun(wearer);

        run.settings = settings;

        if (this.implantsRuns.putIfAbsent(wearer.getUniqueId(), run) != null) return;

        if (staleProc(epoch)) {
            finishRun(this.implantsRuns, wearer.getUniqueId(), run);

            return;
        }

        scheduleRun(wearer, wearer.getUniqueId(), this.implantsRuns, run,
                () -> settings == TuningConfig.implants(), age -> tickImplants(wearer, settings, run));
    }

    private void tickImplants(final Player wearer, final ImplantsSettings settings, final TickingRun run) {
        if (run.age % ImplantsSettings.POLL_TICKS != 0) return;

        final ItemStack helmet = wearer.getEquipment().getHelmet();
        final int level = wornLevel(wearer, CEnchantments.IMPLANTS, helmet);

        if (level < 1) {
            // Helmet removed: the run ends.
            finishRun(this.implantsRuns, wearer.getUniqueId(), run);

            return;
        }

        if (run.age - run.lastFire < settings.intervalTicks(level)) return;

        final double maxHealth = wearer.getMaxHealth();
        final double health = wearer.getHealth();
        final int food = wearer.getFoodLevel();

        // Nothing to restore: no proc, no use event, and the interval keeps running.
        if (!settings.needs(health, maxHealth, food)) return;

        final long epoch = this.reloadEpoch.get();

        // The deny permission and the cancellable use event.
        if (highest(pieceLevels(wearer, CEnchantments.IMPLANTS, helmet)) < 1) return;

        // The use event can run a reload to completion; the restore must belong to the current registry.
        if (staleProc(epoch)) return;

        run.lastFire = run.age;

        final double healed = settings.healed(health, maxHealth);

        if (healed != health) wearer.setHealth(healed);

        final int fed = settings.fed(food);

        if (fed != food) wearer.setFoodLevel(fed);
    }

    // ---------------------------------------------------------------------------------------------
    // Ender Shift: always-on helmet/boots. Speed and Health Boost at low health, removed above the
    // clear cut. Only effects this run added are ever removed.
    // ---------------------------------------------------------------------------------------------

    private void startEnderShift(final Player wearer) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        final long epoch = this.reloadEpoch.get();

        if (wornHighest(wearer, CEnchantments.ADRENALINE, wearer.getEquipment().getHelmet(), wearer.getEquipment().getBoots()) < 1) return;

        final EnderShiftSettings settings = TuningConfig.enderShift();
        final TickingRun run = new TickingRun(wearer);

        run.settings = settings;
        // Ends the shift on every exit path (piece removed, death, quit, world change, reload, disable).
        run.finished = () -> endEnderShift(wearer, run);

        if (this.enderShiftRuns.putIfAbsent(wearer.getUniqueId(), run) != null) return;

        if (staleProc(epoch)) {
            finishRun(this.enderShiftRuns, wearer.getUniqueId(), run);

            return;
        }

        scheduleRun(wearer, wearer.getUniqueId(), this.enderShiftRuns, run,
                () -> settings == TuningConfig.enderShift(), age -> tickEnderShift(wearer, settings, run));
    }

    private void tickEnderShift(final Player wearer, final EnderShiftSettings settings, final TickingRun run) {
        if (run.age % EnderShiftSettings.REFRESH_TICKS != 0) return;

        final long epoch = this.reloadEpoch.get();
        final ItemStack helmet = wearer.getEquipment().getHelmet();
        final ItemStack boots = wearer.getEquipment().getBoots();
        final int level = wornHighest(wearer, CEnchantments.ADRENALINE, helmet, boots);

        if (level < 1) {
            // Both pieces off: the run ends, and with it the effects it added.
            finishRun(this.enderShiftRuns, wearer.getUniqueId(), run);

            return;
        }

        if (!settings.nextActive(run.shifted, wearer.getHealth(), wearer.getMaxHealth())) {
            // Above the clear cut: the shift ends and takes its own effects with it.
            if (run.shifted) endEnderShift(wearer, run);

            return;
        }

        if (!run.shifted) {
            // Activation is the proc: the deny permission and the cancellable use event.
            if (highest(pieceLevels(wearer, CEnchantments.ADRENALINE, helmet, boots)) < 1) return;
            if (staleProc(epoch)) return;

            run.shifted = true;
        }

        run.effectTicks = settings.effectTicks();

        run.speedOwned = shiftEffect(wearer, PotionEffectType.SPEED, settings.speedAmplifier(level), run.effectTicks,
                run.speedOwned, run.speedAmplifier);
        run.speedAmplifier = settings.speedAmplifier(level);
        run.boostOwned = shiftEffect(wearer, PotionEffectType.HEALTH_BOOST, settings.healthBoostAmplifier(), run.effectTicks,
                run.boostOwned, run.boostAmplifier);
        run.boostAmplifier = settings.healthBoostAmplifier();
    }

    /**
     * Fills a gap or refreshes this run's own effect. A potion effect carries no source tag, so the
     * only effect treated as ours is one this run added and the wearer still has at the amplifier it
     * set and a finite duration; anything else, another plugin's buff included, is left alone.
     *
     * @return whether the effect on the wearer is now this run's
     */
    private boolean shiftEffect(final Player wearer, final PotionEffectType type, final int amplifier, final int ticks,
                                final boolean owned, final int ownedAmplifier) {
        final PotionEffect present = wearer.getPotionEffect(type);

        if (present != null && !(owned && ownsEffect(present, ownedAmplifier, ticks))) return false;

        return wearer.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, false, true));
    }

    private static boolean ownsEffect(final PotionEffect effect, final int amplifier, final int ticks) {
        return effect.getAmplifier() == amplifier && effect.getDuration() != -1 && effect.getDuration() <= ticks;
    }

    /** Ends the shift: removes only the effects this run added and still owns, on the wearer's region. */
    private void endEnderShift(final Player wearer, final TickingRun run) {
        final boolean speed = run.speedOwned;
        final boolean boost = run.boostOwned;
        final int speedAmplifier = run.speedAmplifier;
        final int boostAmplifier = run.boostAmplifier;
        final int ticks = run.effectTicks;

        run.shifted = false;
        run.speedOwned = false;
        run.boostOwned = false;

        if (!speed && !boost) return;

        runOnOwner(wearer, () -> {
            final PotionEffect currentSpeed = wearer.getPotionEffect(PotionEffectType.SPEED);

            if (speed && currentSpeed != null && ownsEffect(currentSpeed, speedAmplifier, ticks)) {
                wearer.removePotionEffect(PotionEffectType.SPEED);
            }

            final PotionEffect currentBoost = wearer.getPotionEffect(PotionEffectType.HEALTH_BOOST);

            if (boost && currentBoost != null && ownsEffect(currentBoost, boostAmplifier, ticks)) {
                wearer.removePotionEffect(PotionEffectType.HEALTH_BOOST);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Destruction: helmet roll on a fixed cadence, one random enemy.
    // ---------------------------------------------------------------------------------------------

    private void startDestruction(final Player wearer) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        // Same admission rule as Curse: an eligibility read against the old registry must not
        // produce a run stamped with the record a reload just published.
        final long epoch = this.reloadEpoch.get();

        if (wornLevel(wearer, CEnchantments.DESTRUCTION, wearer.getEquipment().getHelmet()) < 1) return;

        final DestructionSettings settings = TuningConfig.destruction();
        final TickingRun run = new TickingRun(wearer);

        // Stamp the record this run will actually use, not a value read later.
        run.settings = settings;

        if (this.destructionRuns.putIfAbsent(wearer.getUniqueId(), run) != null) return;

        if (staleProc(epoch)) {
            finishRun(this.destructionRuns, wearer.getUniqueId(), run);

            return;
        }

        scheduleRun(wearer, wearer.getUniqueId(), this.destructionRuns, run,
                () -> settings == TuningConfig.destruction(), age -> tickDestruction(wearer, settings, run));
    }

    private void tickDestruction(final Player wearer, final DestructionSettings settings, final TickingRun run) {
        final long epoch = this.reloadEpoch.get();

        if (run.age % settings.cadenceTicks() != 0) return;

        final ItemStack helmet = wearer.getEquipment().getHelmet();

        if (wornLevel(wearer, CEnchantments.DESTRUCTION, helmet) < 1) {
            finishRun(this.destructionRuns, wearer.getUniqueId(), run);

            return;
        }

        final int level = highest(pieceLevels(wearer, CEnchantments.DESTRUCTION, helmet));

        // The chance roll failed, or the wearer denied the enchant.
        if (level < 1) return;

        // The use event inside pieceLevels can run a reload to completion; the target scan and
        // the hit must then belong to the current registry or not happen at all.
        if (staleProc(epoch)) return;

        final LivingEntity target = pickDestructionTarget(wearer, settings);

        if (target == null) return;

        // The optional slow belongs to this exact hit: a hit that was cancelled or lost no
        // health/absorption must not slow its victim.
        final boolean landed = SyntheticHit.damage(wearer, target, settings.damageHp());

        if (landed && settings.applySlowness()) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, settings.slownessTicks(), 0));
        }
    }

    private LivingEntity pickDestructionTarget(final Player wearer, final DestructionSettings settings) {
        final Location center = wearer.getLocation();
        final UUID id = wearer.getUniqueId();
        final List<LivingEntity> targets = new ArrayList<>();

        for (final LivingEntity candidate : center.getWorld().getNearbyLivingEntities(center, settings.radiusBlocks())) {
            // Never read another region's entity, not even its health.
            if (!Bukkit.isOwnedByCurrentRegion(candidate)) continue;

            // A protected area (WorldGuard PVP deny and friends) is never hit, so the
            // optional slow is skipped with the damage it belongs to.
            if (!this.support.isCombatEnabled(candidate.getLocation())) continue;

            final boolean self = id.equals(candidate.getUniqueId());
            final boolean dead = candidate.isDead() || !candidate.isValid();
            final boolean ignored = EventUtils.isIgnoredUUID(candidate.getUniqueId());
            final boolean ally = !self && !dead && isSupportAlly(wearer, candidate);
            final double distanceSquared = self ? -1.0 : candidate.getLocation().distanceSquared(center);

            if (!settings.mayHit(self, ally, dead, true, ignored, distanceSquared)) continue;

            targets.add(candidate);
        }

        if (targets.isEmpty()) return null;

        return targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
    }

    // ---------------------------------------------------------------------------------------------
    // Plague Carrier: the wearer's own tick watches health, then tagged passive creepers burst.
    // ---------------------------------------------------------------------------------------------

    /**
     * Arms the health watcher for a wearer who has the leggings on. Health is read from the
     * wearer's own tick, so equipping at low health or crossing the cut through a max-health
     * change fires just like an incoming hit does.
     */
    private void startPlagueTicker(final Player wearer) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        // Same admission rule as Curse and Destruction.
        final long epoch = this.reloadEpoch.get();

        if (wornLevel(wearer, CEnchantments.PLAGUE_CARRIER, wearer.getEquipment().getLeggings()) < 1) return;

        final PlagueCarrierSettings settings = TuningConfig.plagueCarrier();
        final TickingRun run = new TickingRun(wearer);

        // Stamp the record this run will actually use, not a value read later.
        run.settings = settings;

        if (this.plagueTicking.putIfAbsent(wearer.getUniqueId(), run) != null) return;

        if (staleProc(epoch)) {
            finishRun(this.plagueTicking, wearer.getUniqueId(), run);

            return;
        }

        scheduleRun(wearer, wearer.getUniqueId(), this.plagueTicking, run,
                () -> settings == TuningConfig.plagueCarrier(), age -> tickPlague(wearer, settings, run));
    }

    private void tickPlague(final Player wearer, final PlagueCarrierSettings settings, final TickingRun run) {
        final long epoch = this.reloadEpoch.get();

        final ItemStack leggings = wearer.getEquipment().getLeggings();

        // Cheap per-tick check: the slot still holds an enchanted item. The level itself, and
        // the Gson parse plus registry scan behind it, is only read on the refresh cadence, so
        // the registry is no longer walked on every tick. A removed piece ends the run at once;
        // a swapped piece can keep the old level for at most REFRESH_TICKS ticks, which cannot
        // change the creepers count of a burst by more than one step.
        final boolean present = leggings != null && !leggings.isEmpty()
                && leggings.getPersistentDataContainer().has(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING);

        if (run.lastRefresh < 0 || run.age - run.lastRefresh >= PlagueCarrierSettings.REFRESH_TICKS) {
            run.lastRefresh = run.age;

            if (present) {
                run.level = wornLevel(wearer, CEnchantments.PLAGUE_CARRIER, leggings);
            } else {
                run.level = 0;
            }
        }

        if (!present || run.level < 1) {
            // Piece removed: the watcher ends. Neither a recovery nor a cooldown is honoured
            // without the piece actually worn.
            finishRun(this.plagueTicking, wearer.getUniqueId(), run);

            return;
        }

        final int level = run.level;

        final UUID id = wearer.getUniqueId();
        final double maxHealth = wearer.getMaxHealth();
        final double health = wearer.getHealth();

        final long now = System.nanoTime();
        final Long spentAt = this.plagueSpent.get(id);

        if (spentAt != null) {
            // Only a wearer who healed strictly back above the re-arm cut may fire again; a
            // latch that outlived the bounded window is forgotten.
            if (!settings.rearmed(health, maxHealth) && !PlagueCarrierSettings.expired(spentAt, now)) return;

            this.plagueSpent.remove(id, spentAt);
        }

        if (!settings.triggers(health, maxHealth)) return;
        // Eligibility before spending: an enchant on cooldown neither fires its use event nor
        // rolls anything, and the claim below is only reached once both checks accepted.
        if (!settings.ready(this.plagueCooldowns, id, now)) return;

        // The deny permission and exactly one cancellable use event for the selected proc.
        // Plague Carrier is always-on, so this is its only use-event and its only veto point.
        if (wearer.hasPermission("crazyenchantments.%s.deny".formatted(CEnchantments.PLAGUE_CARRIER.getName()))) return;
        if (!EnchantUtils.normalEnchantEvent(CEnchantments.PLAGUE_CARRIER, wearer, leggings)) return;

        // The use event can run a reload to completion; the claim and the burst must then
        // either belong to the current registry or not happen at all.
        if (staleProc(epoch)) return;

        if (!settings.claim(this.plagueCooldowns, id, now)) return;

        // Prune on write, so the latch map stays bounded by the players who triggered recently.
        this.plagueSpent.entrySet().removeIf(entry -> PlagueCarrierSettings.expired(entry.getValue(), now));
        this.plagueSpent.put(id, now);

        firePlague(wearer, level, settings);
    }

    /**
     * One trigger: the creepers are best effort, the poison is not. The poison origin is
     * captured before anything spawns, so a refused or cancelled spawn can never suppress
     * the separate poison effect after the cooldown was already spent.
     */
    private void firePlague(final Player wearer, final int level, final PlagueCarrierSettings settings) {
        final Location origin = wearer.getLocation();

        spawnPlagueBurst(wearer, level, settings);
        poisonEnemies(wearer, origin, settings);
    }

    private void spawnPlagueBurst(final Player wearer, final int level, final PlagueCarrierSettings settings) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        final PlagueRun run = new PlagueRun(wearer);

        // Stamp the exact record this burst started under; a reload replaces it.
        run.settings = settings;

        if (this.plagueRuns.putIfAbsent(wearer.getUniqueId(), run) != null) return;

        spawnPlagueCreepers(run, settings.creepers(level));

        // A spawn callback can retire the run before spawn returns; the detached run must
        // never schedule a fuse, and the creepers it did spawn must come back off.
        if (this.plagueRuns.get(wearer.getUniqueId()) != run || run.retired.get() || run.creepers.isEmpty()) {
            retirePlagueRun(run, true);

            return;
        }

        for (final Creeper creeper : run.creepers) scheduleFuse(wearer, run, creeper, settings);
    }

    private void spawnPlagueCreepers(final PlagueRun run, final int count) {
        final Location center = run.wearer.getLocation();
        final var world = center.getWorld();
        final int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

        for (final int[] offset : offsets) {
            if (run.creepers.size() >= count || this.supportStopped || run.retired.get()
                    || this.plagueRuns.get(run.wearer.getUniqueId()) != run) return;

            for (final int dy : new int[]{0, 1, -1, 2}) {
                final Location at = new Location(world, center.getBlockX() + offset[0] + 0.5,
                        center.getBlockY() + dy, center.getBlockZ() + offset[1] + 0.5);

                if (at.getBlockY() < world.getMinHeight() || at.getBlockY() + 1 >= world.getMaxHeight()) continue;
                if (!Bukkit.isOwnedByCurrentRegion(at) || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
                if (!at.getBlock().getType().isAir() || !at.getBlock().getRelative(0, 1, 0).getType().isAir()) continue;

                final Creeper creeper = world.spawn(at, Creeper.class, SpawnReason.CUSTOM, false, this::configurePlagueCreeper);

                if (creeper == null || !creeper.isValid()) continue;

                run.creepers.add(creeper);

                // The spawn callback may have retired the run; this creeper is then nobody's
                // and must come straight back off rather than be left standing.
                if (run.retired.get() || this.plagueRuns.get(run.wearer.getUniqueId()) != run) {
                    run.creepers.remove(creeper);

                    removeSummon(creeper, true);

                    return;
                }

                break;
            }
        }
    }

    private void scheduleFuse(final Player wearer, final PlagueRun run, final Creeper creeper, final PlagueCarrierSettings settings) {
        try {
            // The retirement callback is the only cleanup when the creeper disappears (killed,
            // consumed, chunk unloaded, plugin disabled) before its fuse fires. Without it the
            // run would stay registered and suppress every later burst.
            final ScheduledTask task = creeper.getScheduler().runDelayed(this.plugin, fuse -> {
                // A reload builds a new record, a retired run is already gone, a stale creeper
                // is no longer ours and a wearer who left the region (teleport or world change)
                // invalidates the burst: each of them retires instead of detonating.
                if (!PlagueCarrierSettings.mayDetonate(this.supportStopped, settings == TuningConfig.plagueCarrier(),
                        this.plagueRuns.get(wearer.getUniqueId()) == run, run.retired.get(), creeper.isValid(),
                        Bukkit.isOwnedByCurrentRegion(wearer))) {
                    retirePlagueCreeper(run, creeper, true);

                    return;
                }

                explodePlagueCreeper(wearer, run, creeper, settings);
            }, () -> retirePlagueCreeper(run, creeper, false), settings.fuseTicks());

            if (task == null) {
                // The entity scheduler refused the task; the creeper must not be left standing.
                retirePlagueCreeper(run, creeper, true);

                return;
            }

            run.fuses.add(task);

            // Retirement may have swept the fuses between the task being created and this add.
            // The handle would then be registered after the sweep and outlive it, so it is
            // cancelled here and its creeper (if it is still ours) is retired.
            if (run.retired.get()) {
                task.cancel();
                run.fuses.remove(task);
                retirePlagueCreeper(run, creeper, true);
            }
        } catch (final IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;

            retirePlagueCreeper(run, creeper, true);
        }
    }

    /**
     * The visual half of the burst: {@link ExplosionHelper} draws the particle
     * and plays the sound, then damages only enemies inside the radius. It never
     * calls a world explosion, so no block breaks and no fire starts. The ally
     * filter is the same predicate the poison uses, so a wearer's pet is neither
     * damaged nor poisoned, and a protected area is skipped.
     */
    private void explodePlagueCreeper(final Player wearer, final PlagueRun run, final Creeper creeper,
                                      final PlagueCarrierSettings settings) {
        try {
            // Identity is re-checked here too: the run may have been retired between the fuse
            // being scheduled and it firing, and only the current run may blast.
            final boolean live = this.plagueRuns.get(wearer.getUniqueId()) == run && !run.retired.get()
                    && Bukkit.isOwnedByCurrentRegion(creeper) && creeper.isValid() && !creeper.isDead();

            if (live) {
                ExplosionHelper.explode(wearer, creeper.getLocation(),
                        List.of(new ExplosionHelper.Blast(settings.explosionRadius(), settings.explosionDamageHp(), 0, 0)),
                        null, 0.0, this.support, target -> !isSupportAlly(wearer, target));
            }
        } finally {
            // Cleanup is guaranteed even when the blast throws: the run must never stay
            // registered, or the wearer could not fire again.
            retirePlagueCreeper(run, creeper, true);
        }
    }

    /**
     * Takes one creeper out of its run, exactly once: the list identity check makes
     * this idempotent, so a late fuse, a rejected fuse and a stop can all call it.
     */
    private void retirePlagueCreeper(final PlagueRun run, final Creeper creeper, final boolean mayRemoveDirectly) {
        if (!run.creepers.remove(creeper)) return;

        removeSummon(creeper, mayRemoveDirectly);

        if (run.creepers.isEmpty()) retirePlagueRun(run, mayRemoveDirectly);
    }

    /**
     * Terminal cleanup of a whole run, idempotent: exactly one caller claims the retirement,
     * drops the map entry, cancels the fuses and removes every creeper; later callers do
     * nothing. The claim is a compare-and-set, so a fuse callback, a world change, a death
     * and a reload cannot both think they retired the run.
     */
    private void retirePlagueRun(final PlagueRun run, final boolean mayRemoveDirectly) {
        if (!run.retired.compareAndSet(false, true)) return;

        this.plagueRuns.remove(run.wearer.getUniqueId(), run);

        for (final ScheduledTask fuse : run.fuses) {
            if (fuse != null) fuse.cancel();
        }

        run.fuses.clear();

        for (final Creeper creeper : run.creepers) removeSummon(creeper, mayRemoveDirectly);

        run.creepers.clear();
    }

    private void poisonEnemies(final Player wearer, final Location center, final PlagueCarrierSettings settings) {
        final UUID id = wearer.getUniqueId();

        for (final LivingEntity target : center.getWorld().getNearbyLivingEntities(center, settings.poisonRadius())) {
            if (!Bukkit.isOwnedByCurrentRegion(target)) continue;
            if (id.equals(target.getUniqueId()) || target.isDead() || !target.isValid()) continue;
            if (!settings.withinPoison(target.getLocation().distanceSquared(center))) continue;
            // The same ally rule as the burst, plus the project's protection check.
            if (isSupportAlly(wearer, target)) continue;
            if (!this.support.isCombatEnabled(target.getLocation())) continue;

            // Read on the target's own region (the loop skips every other region above) and
            // fill a gap only: an existing Poison, from any source, is never overwritten.
            if (!PlagueCarrierSettings.mayPoison(target.hasPotionEffect(PotionEffectType.POISON))) continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, settings.poisonTicks(), 0));
        }
    }

    private void stopPlague(final UUID id, final boolean mayRemoveDirectly) {
        final PlagueRun run = this.plagueRuns.get(id);

        if (run == null) return;

        retirePlagueRun(run, mayRemoveDirectly);
    }

    private void configurePlagueCreeper(final Creeper creeper) {
        creeper.getPersistentDataContainer().set(this.plagueCreeperKey, PersistentDataType.BOOLEAN, true);
        creeper.setAI(false);
        creeper.setAware(false);
        creeper.setTarget(null);
        creeper.setInvulnerable(true);
        creeper.setCollidable(false);
        creeper.setRemoveWhenFarAway(false);
        creeper.setPersistent(false);
        creeper.setLootTable(null);
        creeper.setExplosionRadius(0);
        creeper.setPowered(false);
    }

    private boolean isPlagueCreeper(final Entity entity) {
        return entity instanceof Creeper && Bukkit.isOwnedByCurrentRegion(entity)
                && entity.getPersistentDataContainer().has(this.plagueCreeperKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlagueTarget(final EntityTargetEvent event) {
        if (event.getTarget() != null && (isPlagueCreeper(event.getEntity()) || isPlagueCreeper(event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlagueDamage(final EntityDamageEvent event) {
        if (isPlagueCreeper(event.getEntity()) || isPlagueCreeper(attacker(event))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlagueDeath(final EntityDeathEvent event) {
        if (!isPlagueCreeper(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private static void removeSummon(final Entity summon, final boolean mayRemoveDirectly) {
        // Validity is entity state and may only be read on the region that owns the entity.
        // A retirement from another region (a world change, a death, disable or a reload)
        // therefore queues the removal and lets the owning thread decide there; reading
        // isValid() here could reach into a region this thread does not own.
        if (Bukkit.isOwnedByCurrentRegion(summon)) {
            if (!summon.isValid()) return;

            if (mayRemoveDirectly) {
                summon.remove();

                return;
            }
        }

        // Same terminal hop as removeSpirit: queue only the removal on the owner.
        ((CraftEntity) summon).taskScheduler.schedule(entity -> entity.getBukkitEntity().remove(), null, 1L);
    }

    // ---------------------------------------------------------------------------------------------
    // Feign Death: hide the wearer, never heal or move them.
    // ---------------------------------------------------------------------------------------------

    /**
     * The trigger, called by {@link com.badbones69.crazyenchantments.paper.listeners.FeignDeathListener}
     * at HIGHEST and, for a hit that was not yet lethal, again at MONITOR.
     *
     * @return true only if the damage has not reached the threshold and the wearer is
     * eligible to roll later; a failed or vetoed roll is final for this hit
     */
    public boolean tryFeignDeath(final EntityDamageEvent event) {
        if (this.registryUnpublished || this.supportStopped || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return false;
        if (!(event.getEntity() instanceof Player wearer)) return false;
        if (!Bukkit.isOwnedByCurrentRegion(wearer) || wearer.isDead()) return false;

        // Same rule as the other single-proc armor enchants: the epoch is captured before the
        // eligibility work, because the use event can run a reload to completion.
        final long epoch = this.reloadEpoch.get();

        final UUID id = wearer.getUniqueId();

        if (this.feignRuns.containsKey(id)) return false;

        final FeignDeathSettings settings = TuningConfig.feignDeath();
        final long now = System.nanoTime();

        // Eligibility without spending: on cooldown, nothing rolls and no use event fires.
        if (!settings.ready(this.feignCooldowns, id, now)) return false;

        final double finalDamage = event.getFinalDamage();
        final ItemStack[] armor = wearer.getEquipment().getArmorContents();

        if (!settings.triggers(wearer.getHealth() - finalDamage, wearer.getMaxHealth())) {
            return !wearer.hasPermission("crazyenchantments.%s.deny".formatted(CEnchantments.FEIGN_DEATH.getName()))
                    && wornHighest(wearer, CEnchantments.FEIGN_DEATH, armor) > 0;
        }
        if (!(finalDamage > 0.0)) return false;

        // One attempt over the whole worn set: one roll and exactly one cancellable use event
        // for the selected piece, instead of one event per worn piece.
        final int level = combinedPieceAttempt(wearer, CEnchantments.FEIGN_DEATH, armor);

        if (level < 1 || event.isCancelled()) return false;
        if (staleProc(epoch) || this.feignRuns.containsKey(id)) return false;
        if (!settings.claim(this.feignCooldowns, id, now)) return false;

        // Feign Death means surviving the killing blow and slipping away: negate this hit entirely
        // so the wearer keeps the health they had before it. No healing, no teleport.
        event.setCancelled(true);
        event.setDamage(0.0);

        startFeign(wearer, id, level, settings);

        return false;
    }

    private void startFeign(final Player wearer, final UUID id, final int level, final FeignDeathSettings settings) {
        if (this.supportStopped || wearer.isDead() || !wearer.isValid()) return;

        final FeignRun run = new FeignRun(wearer);

        run.level = level;
        // The settings record this vanish was started with; a reload replaces it.
        run.settings = settings;

        if (this.feignRuns.putIfAbsent(id, run) != null) return;

        // The expiry is established before the fan-out: a viewer's visibility call that throws
        // must still leave a run that reaches its timer and reveals everyone.
        try {
            run.task = wearer.getScheduler().runDelayed(this.plugin, task -> endFeign(id, run), null, settings.durationTicks());
        } catch (final IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
        } finally {
            // A refused task (or a rethrown failure) must never leave a run with no expiry.
            if (run.task == null) endFeign(id, run);
        }

        if (run.task == null) return;

        try {
            hideFromOthers(wearer);
        } catch (final RuntimeException failure) {
            // One viewer's visibility call failed; the timer established above still ends the
            // vanish and releases every hide that was recorded.
            this.fusion.log(Level.WARNING, "Feign Death could not hide %s from every viewer: %s", wearer.getName(), failure.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFeignHit(final EntityDamageByEntityEvent event) {
        if (this.supportStopped || SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;
        if (!(event.getFinalDamage() > 0.0)) return;

        final Entity damager = event.getDamager();
        final Entity causing = event.getDamageSource().getCausingEntity();
        final boolean directPlayer = damager instanceof Player;
        final boolean shotByPlayer = damager instanceof Projectile && causing instanceof Player;

        // A landed arrow or trident counts as its shooter's hit; a projectile with no player
        // shooter (a dispenser, a skeleton) does not.
        if (!FeignDeathSettings.countsAsOutgoingHit(event.getEntity() instanceof Player, directPlayer, shotByPlayer)) return;

        final Player attacker = directPlayer ? (Player) damager : (Player) causing;
        final FeignRun run = this.feignRuns.get(attacker.getUniqueId());

        if (run == null) return;

        // The counter belongs to the wearer's run, so it is updated on the wearer's region thread
        // even though this event fires on the victim's.
        runOnOwner(run.wearer, () -> {
            if (this.feignRuns.get(attacker.getUniqueId()) != run) return;

            if (FeignDeathSettings.endsAfter(run.level, ++run.hits)) endFeign(attacker.getUniqueId(), run);
        });
    }

    private void endFeign(final UUID id, final FeignRun run) {
        // Remove first: every queued callback then reads "not active" and settles to shown.
        if (!this.feignRuns.remove(id, run)) return;

        if (run.task != null) run.task.cancel();

        revealToAll(run.wearer);
    }

    /**
     * Feign Death hiding. {@link VanishSupport} only exposes {@code isVanished}
     * and {@code isEnabled}, so it cannot make a player vanish; the documented
     * fallback is hiding the entity from every other player's client. That is a
     * client visibility change only: no health, position or entity state is
     * touched, and the wearer's own invisibility flag is never read or written, so
     * invisibility from another source survives untouched.
     *
     * <p>Each viewer is settled on that viewer's own region, inside one task, which
     * reads the current desired state when it runs instead of replaying a captured
     * "hide" or "show" command.</p>
     */
    private void hideFromOthers(final Player wearer) {
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(wearer.getUniqueId())) continue;

            reconcileViewer(wearer, viewer);
        }
    }

    /**
     * Settles one viewer session towards the state it should be in for one target.
     * The whole decision - the record, the desired state and the visibility call -
     * runs in this single task on the viewer's owning region, so two callbacks can
     * never interleave and a replacement vanish cannot hide the same target twice.
     */
    private void reconcileViewer(final Player target, final Player viewer) {
        if (viewer == target || !viewer.isConnected()) return;

        runOnOwner(viewer, () -> {
            // Bind to this captured session: never resolve a fresh Player object here.
            if (!viewer.isConnected()) return;

            final FeignView view = viewOf(viewer);

            // No session means the viewer quit and its record was discarded: a late callback
            // must not recreate one.
            if (view == null) return;

            // The desired state is re-read at execution time and again after a hide, so a
            // stale queued callback is a no-op and one that raced the end of the vanish is
            // released in the same task.
            view.visibility.reconcile(viewer, target,
                    () -> FeignDeathSettings.active(this.feignRuns, target.getUniqueId()), target.isConnected());
        });
    }

    /**
     * The session record for a viewer. Only touched on that viewer's owning region.
     * A callback never creates a session: if the viewer's record was discarded on quit,
     * that session is gone and a late callback must not resurrect it.
     */
    private FeignView viewOf(final Player viewer) {
        final FeignView existing = this.feignViews.get(viewer.getUniqueId());

        return existing != null && existing.player == viewer ? existing : null;
    }

    /**
     * Fans reconciliation out over every online viewer session after the run was
     * removed, so no viewer keeps a hide this plugin no longer wants. Each session
     * checks its own record and the current state on its own region; a session with
     * no record does nothing. The target object is only used to address the entity.
     */
    private void revealToAll(final Player target) {
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) continue;

            reconcileViewer(target, viewer);
        }
    }

    /**
     * Releases every hide a viewer session still records. Used on disable, where the
     * public scheduler rejects new work: {@link #runOnOwner} uses the internal entity
     * scheduler, the same terminal hop already used for entity removal during disable.
     * If the viewer's region never runs again its client session is going away anyway
     * and the server discards those hides with it; the map is cleared regardless.
     */
    private void releaseAll(final FeignView view) {
        final Player viewer = view.player;

        runOnOwner(viewer, () -> {
            // Read and release on the viewer's own region, in one task.
            for (final Player target : view.visibility.targets()) {
                view.visibility.release(viewer, target, target.isConnected());
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Shared run cleanup.
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRunDeath(final PlayerDeathEvent event) {
        final UUID id = event.getEntity().getUniqueId();

        // A death ends the fight: same state cleanup as a quit, a world change and a reload.
        clearCombatState(id);

        // Ends the three worn tickers immediately instead of leaving them registered until
        // their next pulse notices the dead wearer, plus the Plague burst and the Feign
        // vanish. Support runs are ended by onSupportDeath.
        stopRuns(id);
    }

    private void stopRuns(final UUID id) {
        final TickingRun curse = this.curseRuns.get(id);

        if (curse != null) finishRun(this.curseRuns, id, curse);

        final TickingRun destruction = this.destructionRuns.get(id);

        if (destruction != null) finishRun(this.destructionRuns, id, destruction);

        final TickingRun plague = this.plagueTicking.get(id);

        if (plague != null) finishRun(this.plagueTicking, id, plague);

        final TickingRun implants = this.implantsRuns.get(id);

        if (implants != null) finishRun(this.implantsRuns, id, implants);

        final TickingRun enderShift = this.enderShiftRuns.get(id);

        if (enderShift != null) finishRun(this.enderShiftRuns, id, enderShift);

        stopPlague(id, true);

        final FeignRun feign = this.feignRuns.get(id);

        if (feign != null) endFeign(id, feign);
    }
}
