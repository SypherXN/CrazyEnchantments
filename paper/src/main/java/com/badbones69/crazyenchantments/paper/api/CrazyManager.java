package com.badbones69.crazyenchantments.paper.api;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.Dust;
import com.badbones69.crazyenchantments.paper.api.enums.Scrolls;
import com.badbones69.crazyenchantments.paper.api.enums.ShopOption;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.Enchant;
import com.badbones69.crazyenchantments.paper.api.managers.AllyManager;
import com.badbones69.crazyenchantments.paper.api.managers.ArmorEnchantmentManager;
import com.badbones69.crazyenchantments.paper.api.managers.BowEnchantmentManager;
import com.badbones69.crazyenchantments.paper.api.managers.ShopManager;
import com.badbones69.crazyenchantments.paper.api.managers.WingsManager;
import com.badbones69.crazyenchantments.paper.api.objects.CEBook;
import com.badbones69.crazyenchantments.paper.api.objects.CEPlayer;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.objects.gkitz.GKitz;
import com.badbones69.crazyenchantments.paper.api.objects.gkitz.GkitCoolDown;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.api.bleed.BleedSystem;
import com.badbones69.crazyenchantments.paper.api.utils.AttributeUtils;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.LoreLines;
import com.badbones69.crazyenchantments.paper.api.utils.MetaphysicalSettings;
import com.badbones69.crazyenchantments.paper.api.utils.NumberUtils;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.api.utils.WingsUtils;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.ProtectionCrystalSettings;
import com.badbones69.crazyenchantments.paper.enchantments.ArmorEnchantments;
import com.badbones69.crazyenchantments.paper.listeners.ScramblerListener;
import com.badbones69.crazyenchantments.paper.listeners.ScrollListener;
import com.badbones69.crazyenchantments.paper.listeners.SlotCrystalListener;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CrazyManager {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    private final Server server = this.plugin.getServer();
    
    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    // Settings.
    @NotNull
    private final ProtectionCrystalSettings protectionCrystalSettings = this.starter.getProtectionCrystalSettings();
    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // Listeners.
    @NotNull
    private final ScramblerListener scramblerListener = this.starter.getScramblerListener();
    @NotNull
    private final ScrollListener scrollListener = this.starter.getScrollListener();

    @NotNull
    private final SlotCrystalListener slotCrystalListener = this.starter.getSlotCrystalListener();

    @NotNull
    private final AllyManager allyManager = this.starter.getAllyManager();

    // Wings.
    @NotNull
    private final WingsManager wingsManager = this.starter.getWingsManager();

    @NotNull
    private final ShopManager shopManager = this.starter.getShopManager();
    
    @NotNull
    private final BowEnchantmentManager bowEnchantmentManager = this.starter.getBowEnchantmentManager();
    
    @NotNull
    private final ArmorEnchantmentManager armorEnchantmentManager = this.starter.getArmorEnchantmentManager();

    // Arrays.
    private volatile List<GKitz> gkitz = List.of();
    private final Map<UUID, CEPlayer> players = new ConcurrentHashMap<>();
    private volatile List<Material> blockList = List.of();
    private volatile Map<Material, Double> headMap = Map.of();
    private final ThreadLocal<List<CEnchantment>> registrationBatch = new ThreadLocal<>();
    private final Object playerDataLock = new Object();
    private ScheduledTask backupTask;

    private int rageMaxLevel;
    private boolean gkitzToggle;
    private boolean useUnsafeEnchantments;
    private boolean breakRageOnDamage;
    private boolean useRageBossBar;

    private double rageIncrement;
    private boolean maxEnchantmentCheck;
    private boolean checkVanillaLimit;

    private boolean dropBlocksBlast;
    private boolean dropBlocksVeinMiner;
    private int defaultLimit;
    private int defaultBaseLimit;
    private boolean useEnchantmentLimiter;
    private boolean useConfigLimits;

    private int CESuccessOverride;
    private int CEFailureOverride;

    /**
     * Loads everything for the Crazy Enchantments plugin.
     * Do not use unless needed.
     *
     * <p>The reload gate is set before the first tuning record is replaced and before the
     * registry is rebuilt, and opened again from a finally once everything is published.
     * Without it a hit on another region can match the old enabled entry, read the new
     * settings and survive reconciliation because its stamp already matches. The finally
     * also keeps a failed reload from leaving every armor proc switched off.</p>
     */
    public synchronized void load() {
        final ArmorEnchantments armor = this.plugin.getArmorEnchantments();

        if (armor != null) armor.beginReload();
        if (this.plugin.getAxeEnchantments() != null) this.plugin.getAxeEnchantments().clearCombatState();

        try {
            loadInternal();
        } finally {
            // Cleared here, not on the success path: a throw must not leave every proc off.
            if (armor != null) armor.endReload();
        }

        // Registry and managers are published only now, so this is the first point where the
        // armor listeners may reconcile the runs a reload replaced. On enable the listener does
        // not exist yet (it is constructed after this load), so the null check skips it.
        if (armor != null) armor.reconcileAfterReload();
    }

    private void loadInternal() {
        final FileConfiguration config = FileKeys.CONFIG.getConfiguration();
        final FileConfiguration gkit = FileKeys.GKITZ.getConfiguration();
        final FileConfiguration enchants = FileKeys.ENCHANTMENTS.getConfiguration();

        final FileConfiguration blocks = FileKeys.BLOCKLIST.getConfiguration();
        final FileConfiguration heads = FileKeys.HEADMAP.getConfiguration();

        // Rebuild the CosmicPvP fork tunables from Enchantments.yml, then drop every Bleed cadence on enable/reload.
        TuningConfig.load(config, enchants, message -> this.fusion.log(Level.WARNING, message));

        BleedSystem.shutdown();
        BleedSystem.cleanupStaleModifiers();

        // Published as fresh instances: region threads read these during a reload.
        final List<Material> nextBlockList = new ArrayList<>();
        final Map<Material, Double> nextHeadMap = new HashMap<>();

        // Loop through block list.
        blocks.getStringList("Block-List").forEach(id -> {
            try {
                nextBlockList.add(new ItemBuilder().setMaterial(id).getMaterial());
            } catch (Exception ignored) {}
        });

        ConfigurationSection headSec = heads.getConfigurationSection("HeadOdds");

        if (headSec == null) {
            this.fusion.log(Level.WARNING, "HeadOdds could not be found in HeadMap.yml!");
        } else {
            headSec.getKeys(false).forEach(id -> {
                try {
                    Material mat = new ItemBuilder().setMaterial(id).getMaterial();
                    nextHeadMap.put(mat, headSec.getDouble(id));
                } catch (Exception ignored) {}
            });
        }

        this.blockList = nextBlockList;
        this.headMap = nextHeadMap;

        Scrolls.getWhiteScrollProtectionName();

        this.enchantmentBookSettings.setEnchantmentBook(new ItemBuilder().setMaterial(config.getString("Settings.Enchantment-Book-Item", "BOOK")));
        this.useUnsafeEnchantments = config.getBoolean("Settings.EnchantmentOptions.UnSafe-Enchantments", true);
        this.maxEnchantmentCheck = config.getBoolean("Settings.EnchantmentOptions.MaxAmountOfEnchantmentsToggle", true);
        this.useConfigLimits = config.getBoolean("Settings.EnchantmentOptions.Limit.Check-Perms", false);
        this.defaultLimit = config.getInt("Settings.EnchantmentOptions.Limit.Default-Limit", 0);
        this.defaultBaseLimit = config.getInt("Settings.EnchantmentOptions.Limit.Default-Base-Limit", 0);
        this.useEnchantmentLimiter = config.getBoolean("Settings.EnchantmentOptions.Limit.Enable-SlotCrystal", true);
        this.checkVanillaLimit = config.getBoolean("Settings.EnchantmentOptions.IncludeVanillaEnchantments", false);
        this.gkitzToggle = !config.contains("Settings.GKitz.Enabled") || config.getBoolean("Settings.GKitz.Enabled", true);
        this.rageMaxLevel = config.getInt("Settings.EnchantmentOptions.MaxRageLevel", 4);
        this.breakRageOnDamage = config.getBoolean("Settings.EnchantmentOptions.Break-Rage-On-Damage", true);
        this.useRageBossBar = config.getBoolean("Settings.EnchantmentOptions.Rage-Boss-Bar", false);
        this.rageIncrement = config.getDouble("Settings.EnchantmentOptions.Rage-Increase", 0.1);
        setDropBlocksBlast(config.getBoolean("Settings.EnchantmentOptions.Drop-Blocks-For-Blast", true));
        setDropBlocksVeinMiner(config.getBoolean("Settings.EnchantmentOptions.Drop-Blocks-For-VeinMiner", true));

        this.CEFailureOverride = config.getInt("Settings.CEFailureOverride", -1);
        this.CESuccessOverride = config.getInt("Settings.CESuccessOverride", -1);

        // One vocabulary for the whole load: Rarity-Format picks it, and both
        // the categories and the enchant checks resolve through the same names.
        final TuningConfig.Rarities rarities = this.enchantmentBookSettings.rarities();

        final List<Category> nextCategories = this.enchantmentBookSettings.buildCategories(rarities);
        final List<CEnchantment> nextRegistry = new ArrayList<>();
        // Keep registerEnchantment's events and category/type linking, but stage its registry writes.
        this.registrationBatch.set(nextRegistry);
        try {
            loadEnchantments(enchants, nextCategories, rarities);
        } finally {
            this.registrationBatch.remove();
        }
        this.enchantmentBookSettings.publishRegistry(nextRegistry, nextCategories);

        loadGKitz(gkit);

        // Load all scroll types.
        Scrolls.loadScrolls();
        // Load all dust types.
        Dust.loadDust();

        // Loads the protection crystals.
        this.protectionCrystalSettings.loadProtectionCrystal();
        // Loads the scrambler.
        this.scramblerListener.loadScrambler();
        // Loads Slot Crystal.
        this.slotCrystalListener.load();
        // Loads the Scroll Control settings.
        this.scrollListener.loadScrollControl();

        // Loads the scrolls.
        Scrolls.loadScrolls();
        // Loads the dust.
        Dust.loadDust();

        // Loads the ShopOptions.
        ShopOption.loadShopOptions();

        // Loads the shop manager.
        this.shopManager.load();

        // Loads the settings for wings enchantment.
        this.wingsManager.load();

        // Loads the settings for the bow enchantments.
        this.bowEnchantmentManager.load();

        // Loads the settings for the armor enchantments.
        this.armorEnchantmentManager.load();

        // Loads the settings for the ally enchantments.
        this.allyManager.load();

        // Starts the wings task.
        WingsUtils.startWings();

        final boolean isPatchingHealth = config.getBoolean("Settings.Reset-Players-Max-Health", true);
        for (final Player player : this.server.getOnlinePlayers()) {
            player.getScheduler().execute(this.plugin, () -> {
                if (!player.isOnline()) return;

                loadCEPlayer(player);
                if (isPatchingHealth) AttributeUtils.setHealth(player, 0);
            }, null, 1L);
        }

        cancelBackupTask();
        final long fiveMinutes = 5L * 60L * 20L;
        this.backupTask = this.server.getGlobalRegionScheduler().runAtFixedRate(this.plugin,
                task -> backupOnlinePlayers(), fiveMinutes, fiveMinutes);
    }

    public synchronized void cancelBackupTask() {
        if (this.backupTask == null) return;

        this.backupTask.cancel();
        this.backupTask = null;
    }

    private void warn(final String message) {
        this.fusion.log(Level.WARNING, message);
    }

    private void loadEnchantments(final FileConfiguration enchants, final List<Category> categories,
                                  final TuningConfig.Rarities rarities) {
        final Set<String> known = new HashSet<>();
        for (CEnchantments cEnchantment : CEnchantments.values()) known.add(cEnchantment.getName());

        // Load-time checks: unknown keys, unknown Rarity values and Tuning blocks
        // with no enchant warn once and never throw.
        final ConfigurationSection section = enchants.getConfigurationSection("Enchantments");
        if (section != null) TuningConfig.validate(section.getKeys(false), known, enchants::get, rarities, this::warn);

        for (CEnchantments cEnchantment : CEnchantments.values()) {
            String name = cEnchantment.getName();
            String path = "Enchantments." + name;

            if (enchants.contains(path)) { // To make sure the enchantment isn't broken.
                CEnchantment enchantment = new CEnchantment(name)
                .setCustomName(enchants.getString(path + ".Name"))
                .setActivated(enchants.getBoolean(path + ".Enabled"))
                .setMaxLevel(enchants.getInt(path + ".MaxPower"))
                .setEnchantmentType(cEnchantment.getDefaultType())
                .setInfoName(enchants.getString(path + ".Info.Name"))
                .setInfoDescription(enchants.getStringList(path + ".Info.Description"))
                .setChance(cEnchantment.getChance())
                .setChanceIncrease(cEnchantment.getChanceIncrease())
                .setSound(enchants.getString(path + ".Sound"))
                .setConflicts(enchants.getStringList(path + ".Conflicts"));

                for (final String categoryName : enchants.getStringList(path + ".Categories")) {
                    final String identity = rarities.category(categoryName, path + ".Categories");

                    if (identity == null) continue;

                    for (final Category category : categories) {
                        if (category.getIdentity().equals(identity)) {
                            enchantment.addCategory(category);
                            break;
                        }
                    }
                }

                // An optional Rarity pins the tier of a single-category block.
                final String rarity = rarities.rarity(enchants.get(path + ".Rarity"), name);

                if (rarity != null) {
                    boolean matched = false;

                    for (final Category category : categories) {
                        if (category.getIdentity().equals(rarity)) {
                            enchantment.setRarity(category);
                            matched = true;
                            break;
                        }
                    }

                    // A tier named here but missing from config.yml would
                    // otherwise fall back to Categories in silence.
                    if (!matched) {
                        this.warn("Rarity '" + rarity + "' for Enchantments." + name
                                + " has no matching Categories section in config.yml; using its Categories.");
                    }
                }

                if (enchants.contains(path + ".Enchantment-Type")) enchantment.setEnchantmentType(this.methods.getFromName(enchants.getString(path + ".Enchantment-Type")));

                // After the tier is settled, so the book name and the gear lore
                // line both wear the tier colour.
                enchantment.applyRarityColour();

                if (cEnchantment.hasChanceSystem()) {
                    if (enchants.contains(path + ".Chance-System.Base")) {
                        enchantment.setChance(enchants.getInt(path + ".Chance-System.Base"));
                    } else {
                        enchantment.setChance(cEnchantment.getChance());
                    }

                    if (enchants.contains(path + ".Chance-System.Increase")) {
                        enchantment.setChanceIncrease(enchants.getInt(path + ".Chance-System.Increase"));
                    } else {
                        enchantment.setChanceIncrease(cEnchantment.getChanceIncrease());
                    }
                }

                enchantment.registerEnchantment();
            }
        }
    }

    private void loadGKitz(final FileConfiguration gkit) {
        final List<GKitz> nextGKitz = new ArrayList<>();
        if (this.gkitzToggle) {
            final ConfigurationSection section = gkit.getConfigurationSection("GKitz");

            if (section == null) {
                this.fusion.log(Level.WARNING, "The gkitz section cannot be found in gkitz.yml, It's possible the file is badly formatted!");
            } else {
                for (final String kit : section.getKeys(false)) {
                    String path = "GKitz." + kit + ".";

                    int slot = gkit.getInt(path + "Display.Slot");
                    String time = gkit.getString(path + "Cooldown");
                    boolean autoEquip = gkit.getBoolean(path + "Auto-Equip");

                    ItemStack displayItem = new ItemBuilder().setMaterial(gkit.getString(path + "Display.Item", ColorUtils.getRandomPaneColor().getName()))
                            .setItemModel(gkit.getString(path + "Display.Model.Namespace", ""), gkit.getString(path + "Display.Model.Key", ""))
                            .setName(gkit.getString(path + "Display.Name", "Error getting name."))
                            .setLore(gkit.getStringList(path + "Display.Lore"))
                            .setGlow(gkit.getBoolean(path + "Display.Glowing", false))
                            .addKey(DataKeys.gkit_type.getNamespacedKey(), kit).build();

                    List<String> commands = gkit.getStringList(path + "Commands");
                    List<String> itemStrings = gkit.getStringList(path + "Items");
                    List<ItemStack> previewItems = getInfoGKit(itemStrings);
                    previewItems.addAll(getInfoGKit(gkit.getStringList(path + "Fake-Items")));
                    nextGKitz.add(new GKitz(kit, slot, time, displayItem, previewItems, commands, itemStrings, autoEquip));
                }
            }
        }

        this.gkitz = List.copyOf(nextGKitz);
    }

    /**
     * Only needs used when the player joins the server.
     * This plugin does it automatically, so there is no need to use it unless you have to.
     * @param player The player you wish to load.
     */
    public void loadCEPlayer(Player player) {
        final UUID uuid = player.getUniqueId();
        final String asString = uuid.toString();

        final List<GkitCoolDown> gkitCoolDowns = new CopyOnWriteArrayList<>();

        synchronized (this.playerDataLock) {
            final FileConfiguration data = FileKeys.DATA.getConfiguration();
            for (GKitz kit : getGKitz()) {
                if (data.contains("Players." + asString + ".GKitz." + kit.getName())) {
                    Calendar coolDown = Calendar.getInstance();
                    coolDown.setTimeInMillis(data.getLong("Players." + asString + ".GKitz." + kit.getName()));
                    gkitCoolDowns.add(new GkitCoolDown(kit, coolDown));
                }
            }
            addCEPlayer(uuid, new CEPlayer(uuid, gkitCoolDowns));
        }
    }

    /**
     * Only needs used when the player leaves the server.
     * This plugin removes the player automatically, so don't use this method unless needed for some reason.
     * @param player Player you wish to remove.
     */
    public void unloadCEPlayer(Player player) {
        final UUID uuid = player.getUniqueId();

        synchronized (this.playerDataLock) {
            getCEPlayer(uuid).ifPresent(cePlayer -> {
                writeCEPlayer(FileKeys.DATA.getConfiguration(), cePlayer);
                FileKeys.DATA.save();
                removeCEPlayer(uuid);
            });
        }
    }

    /**
     * This backup all the players data stored by this plugin.
     * @param player The player you wish to back up.
     */
    public void backupCEPlayer(Player player) {
        synchronized (this.playerDataLock) {
            getCEPlayer(player).ifPresent(cePlayer -> {
                writeCEPlayer(FileKeys.DATA.getConfiguration(), cePlayer);
                FileKeys.DATA.save();
            });
        }
    }

    private void backupOnlinePlayers() {
        synchronized (this.playerDataLock) {
            if (this.players.isEmpty()) return;

            final FileConfiguration data = FileKeys.DATA.getConfiguration();
            this.players.values().forEach(cePlayer -> writeCEPlayer(data, cePlayer));
            FileKeys.DATA.save();
        }
    }

    private static void writeCEPlayer(final FileConfiguration data, final CEPlayer cePlayer) {
        final String uuid = cePlayer.getUuid().toString();

        for (GkitCoolDown gkitCooldown : cePlayer.getCoolDowns()) {
            data.set("Players." + uuid + ".GKitz." + gkitCooldown.getGKitz().getName(), gkitCooldown.getCoolDown().getTimeInMillis());
        }
    }

    public boolean checkVanillaLimit() {
        return this.checkVanillaLimit;
    }

    public boolean checkMaxEnchantments() {
        return this.maxEnchantmentCheck;
    }

    /**
     * Check if the gkitz option is enabled.
     * @return True if it is on and false if it is off.
     */
    public boolean isGkitzEnabled() {
        return this.gkitzToggle;
    }

    /**
     * Get a GKit from its name.
     * @param kitName The kit you wish to get.
     * @return The kit as a GKitz object.
     */
    public GKitz getGKitFromName(String kitName) {
        for (GKitz kit : getGKitz()) {
            if (kit.getName().equalsIgnoreCase(kitName)) return kit;
        }

        return null;
    }

    /**
     * Get all loaded gkitz.
     * @return All the loaded gkitz.
     */
    public List<GKitz> getGKitz() {
        return this.gkitz;
    }

    /**
     * This converts a normal Player into a CEPlayer that is loaded.
     * @param player The player you want to get as a CEPlayer.
     * @return The player but as a CEPlayer. Will return null if not found.
     */
    public Optional<CEPlayer> getCEPlayer(Player player) {
        return Optional.ofNullable(this.players.get(player.getUniqueId()));
    }

    public Optional<CEPlayer> getCEPlayer(UUID uuid) {
        return Optional.ofNullable(this.players.get(uuid));
    }

    /**
     * This gets all the CEPlayer's that are loaded.
     * @return All CEPlayer's that are loading and in a list.
     */
    public List<CEPlayer> getCEPlayers() {
        return this.players.values().stream().toList();
    }
    
    public CEBook getRandomEnchantmentBook(Category category) {
        return buildRandomEnchantmentBook(category, null);
    }

    /**
     * The same random enchantment and level as {@link #getRandomEnchantmentBook(Category)}, but
     * each rate is its own uniform roll over 0-100 inclusive instead of the category's bounds.
     * Used for direct shop purchases, where every chance should be equally likely.
     *
     * @param category The category to pick from.
     * @param random The random source for the two rate rolls.
     * @return The book, or null when the category has no enchantments.
     */
    public CEBook getRandomEnchantmentBook(Category category, Random random) {
        return buildRandomEnchantmentBook(category, random);
    }

    /**
     * One rate roll for a shop book. Each rate draws its own value.
     *
     * @param random The random source.
     * @return 0 through 100, inclusive.
     */
    static int rollBookRate(Random random) {
        return random.nextInt(101);
    }

    /**
     * Picks the enchantment and level, then builds the book.
     *
     * <p>Selection, the level roll and construction share one catch: a category with no
     * enchantments, a legacy config whose rates are inverted (percentPick rejects max &lt; min)
     * or any other construction failure must yield null, so a shop purchase can never charge
     * for a book it did not build.</p>
     *
     * @param category The category to pick from.
     * @param random When non-null, each rate is its own uniform 0-100 roll; when null, the
     *               book uses the category's configured bounds.
     * @return The book, or null when it could not be built.
     */
    private CEBook buildRandomEnchantmentBook(Category category, Random random) {
        try {
            List<CEnchantment> enchantments = category.getEnabledEnchantments();
            CEnchantment enchantment = enchantments.get(new Random().nextInt(enchantments.size()));
            int level = randomLevel(enchantment, category);

            if (random == null) return new CEBook(enchantment, level, 1, category);

            return new CEBook(enchantment, level, 1, rollBookRate(random), rollBookRate(random));
        } catch (Exception e) {
            this.plugin.getLogger().info("The category " + category.getName() + " has no enchantments."
            + " Please add enchantments to the category in the Enchantments.yml. If you do not wish to have the category feel free to delete it from the Config.yml.");
            return null;
        }
    }

    /**
     * Get all the current registered enchantments.
     * @return A list of all the registered enchantments in the plugin.
     */
    public List<CEnchantment> getRegisteredEnchantments() {
        return new ArrayList<>(this.enchantmentBookSettings.getRegisteredEnchantments());
    }

    /**
     * Get a CEnchantment enchantment from the name.
     * @param enchantmentString The name of the enchantment.
     * @return The enchantment as a CEnchantment but if not found will be null.
     */
    public CEnchantment getEnchantmentFromName(String enchantmentString) {
        // FORK: the input used to be stripped inside the loop, so only the first enchantment was
        // compared against the raw name and every later one against an already-stripped string.
        String stripped = enchantmentString.replaceAll("([&§]?#[0-9a-fA-F]{6}|[&§][1-9a-fA-Fk-or]| |_)", "");

        for (CEnchantment enchantment : this.enchantmentBookSettings.getRegisteredEnchantments()) {
            if (enchantment.getName().equalsIgnoreCase(enchantmentString)) return enchantment;
            if (enchantment.getName().replaceAll("([&§]?#[0-9a-fA-F]{6}|[&§][1-9a-fA-Fk-or]| |_)", "").equalsIgnoreCase(stripped)) return enchantment;

            if (enchantment.getCustomName().replaceAll("([&§]?#[0-9a-fA-F]{6}|[&§][1-9a-fA-Fk-or]| |_)", "").equalsIgnoreCase(stripped)) return enchantment;
        }

        return null;
    }

    /**
     * Register a new enchantment into the plugin.
     * @param enchantment The enchantment you wish to register.
     */
    public void registerEnchantment(CEnchantment enchantment) {
        final List<CEnchantment> batch = this.registrationBatch.get();
        if (batch != null) batch.add(enchantment);
        else this.enchantmentBookSettings.registerEnchantment(enchantment);
    }

    /**
     * Unregister an enchantment that is registered into plugin.
     * @param enchantment The enchantment you wish to unregister.
     */
    public void unregisterEnchantment(CEnchantment enchantment) {
        final List<CEnchantment> batch = this.registrationBatch.get();
        if (batch != null) batch.remove(enchantment);
        else this.enchantmentBookSettings.unregisterEnchantment(enchantment);
    }

    public void addEnchantment(final ItemStack item, final CEnchantment enchantment, final int level) {
        Map<CEnchantment, Integer> enchantments = new HashMap<>();

        enchantments.put(enchantment, level);

        addEnchantments(item, enchantments);
    }

    /**
     * Adds enchantments without reordering the item. An enchant already on the
     * item keeps its line where the player left it and a new enchant joins the
     * enchant block; only the transmog scroll sorts anything.
     *
     * @param itemStack The meta you want to add the enchantment to.
     * @param enchantments The enchantments to be added.
     */
    public void addEnchantments(final ItemStack itemStack, final Map<CEnchantment, Integer> enchantments) {
        String data = itemStack.getPersistentDataContainer().get(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING);
        final Enchant enchantData = data != null ? Methods.getGson().fromJson(data, Enchant.class) : new Enchant(new HashMap<>());

        final List<Component> lore = new ArrayList<>(itemStack.lore() != null ? itemStack.lore() : List.of());

        final List<CEnchantment> known = new ArrayList<>(this.enchantmentBookSettings.getEnchantments(itemStack).keySet());

        known.addAll(enchantments.keySet());

        final List<Component> additions = new ArrayList<>();

        for (Entry<CEnchantment, Integer> entry : enchantments.entrySet()) {
            CEnchantment enchantment = entry.getKey();
            int level = entry.getValue();

            final Component line = ColorUtils.legacyTranslateColourCodes(enchantment.getCustomName() + " " + NumberUtils.convertLevelString(level));

            if (!LoreLines.replaceFirst(lore, old -> EnchantmentBookSettings.matchesLore(old, enchantment, enchantData), line)) additions.add(line);

            enchantData.addEnchantment(enchantment.getName(), level);
        }

        LoreLines.insertAfterLastEnchant(lore, line -> known.stream().anyMatch(enchantment -> EnchantmentBookSettings.matchesLore(line, enchantment, enchantData)), additions);

        itemStack.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(lore).build());

        itemStack.editPersistentDataContainer(container -> container.set(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING, Methods.getGson().toJson(enchantData)));
    }

    /**
     *
     * @param itemStack The {@link ItemStack} of the item to change.
     * @param amount The amount to change the stored limiter by.
     * @return The altered {@link ItemStack}.
     */
    public ItemStack changeEnchantmentLimiter(@NotNull final ItemStack itemStack, final int amount) {
        final PersistentDataContainerView view = itemStack.getPersistentDataContainer();

        final int newAmount = view.getOrDefault(DataKeys.limit_reducer.getNamespacedKey(), PersistentDataType.INTEGER, 0) + amount;

        itemStack.editPersistentDataContainer(container -> {
            if (newAmount <= 0) {
                container.remove(DataKeys.limit_reducer.getNamespacedKey());

                return;
            }

            container.set(DataKeys.limit_reducer.getNamespacedKey(), PersistentDataType.INTEGER, newAmount);
        });

        return itemStack;
    }

    /**
     *
     * @param item The {@link ItemStack} to check.
     * @return The limit set on the item by slot crystals.
     */
    public int getEnchantmentLimiter(@NotNull ItemStack item) {
        if (!useEnchantmentLimiter) return 0;

        return item.getPersistentDataContainer().getOrDefault(DataKeys.limit_reducer.getNamespacedKey(), PersistentDataType.INTEGER, 0);
    }

    /**
     * Force an update of a players armor potion effects.
     * @param player The player you are updating the effects of.
     */
    public void updatePlayerEffects(Player player) { // TODO Remove this method.
        if (player == null) return;
        Set<CEnchantments> allEnchantPotionEffects = getEnchantmentPotions().keySet();

        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(armor);
            for (CEnchantments ench : allEnchantPotionEffects) {
                if (!enchantments.containsKey(ench.getEnchantment())) continue;
                final Set<PotionEffectType> hulk = new HashSet<>();
                Map<PotionEffectType, Integer> effects = getUpdatedEffects(player, armor, new ItemStack(Material.AIR), ench, hulk);
                checkPotions(effects, player, hulk);
            }
        }
    }

    public void checkPotions(Map<PotionEffectType, Integer> effects, Player player) { //TODO Remove this Method
        checkPotions(effects, player, Set.of());
    }

    private void checkPotions(Map<PotionEffectType, Integer> effects, Player player, Set<PotionEffectType> hulk) {
        final int hulkDuration = TuningConfig.fork().hulkDurationTicks();
        for (Map.Entry<PotionEffectType, Integer> type : effects.entrySet()) {
            int value = type.getValue();
            PotionEffectType key = type.getKey();

            player.removePotionEffect(key);
            if (value == 0) continue; //TODO check usage with new addition of infinity.
            if (hulkDuration != PotionEffect.INFINITE_DURATION && hulk.contains(key)) {
                final PotionEffect potionEffect = new PotionEffect(key, hulkDuration, value);
                MetaphysicalSettings.runSelfInflicted(() -> player.addPotionEffect(potionEffect));
            } else {
                player.addPotionEffect(new PotionEffect(key, PotionEffect.INFINITE_DURATION, value));
            }
        }
    }

    /**
     * @param player The player you are adding it to.
     * @param includedItem Include an item.
     * @param excludedItem Exclude an item.
     * @param enchantment The enchantment you want the max level effects from.
     * @return The list of all the max potion effects based on all the armor on the player.
     */
    public Map<PotionEffectType, Integer> getUpdatedEffects(Player player, ItemStack includedItem, ItemStack excludedItem, CEnchantments enchantment) { //TODO Remove this method.
        return getUpdatedEffects(player, includedItem, excludedItem, enchantment, new HashSet<>());
    }

    private Map<PotionEffectType, Integer> getUpdatedEffects(Player player, ItemStack includedItem, ItemStack excludedItem,
                                                             CEnchantments enchantment, Set<PotionEffectType> hulk) {
        Map<PotionEffectType, Integer> effects = new HashMap<>();
        List<ItemStack> items = new ArrayList<>(Arrays.asList(player.getEquipment().getArmorContents()));

        if (includedItem == null) includedItem = new ItemStack(Material.AIR);

        if (excludedItem == null) excludedItem = new ItemStack(Material.AIR);

        if (excludedItem.isSimilar(includedItem)) excludedItem = new ItemStack(Material.AIR);

        items.add(includedItem);
        Map<CEnchantments, HashMap<PotionEffectType, Integer>> armorEffects = getEnchantmentPotions();

        for (ItemStack armor : items) {
            if (armor == null || armor.isSimilar(excludedItem)) continue;
            Map<CEnchantment, Integer> ench = this.enchantmentBookSettings.getEnchantments(armor);
            for (Entry<CEnchantments, HashMap<PotionEffectType, Integer>> enchantments : armorEffects.entrySet()) {
                if (!ench.containsKey(enchantments.getKey().getEnchantment())) continue;
                int level = ench.get(enchantments.getKey().getEnchantment());
                if (!this.useUnsafeEnchantments && level > enchantments.getKey().getEnchantment().getMaxLevel()) level = enchantments.getKey().getEnchantment().getMaxLevel();

                for (PotionEffectType type : enchantments.getValue().keySet()) {
                    final int amplifier = level + enchantments.getValue().get(type);
                    if (effects.containsKey(type) && effects.get(type) >= amplifier) continue;
                    effects.put(type, amplifier);
                    if (enchantments.getKey() == CEnchantments.HULK) hulk.add(type);
                    else hulk.remove(type);
                }
            }
        }

        for (PotionEffectType type : armorEffects.get(enchantment).keySet()) {
            if (!effects.containsKey(type)) effects.put(type, 0); // -1 is now Infinity.
        }

        return effects;
    }

    /**
     *
     * @return All the effects for each enchantment that needs it.
     */
    public Map<CEnchantments, HashMap<PotionEffectType, Integer>> getEnchantmentPotions() {
        Map<CEnchantments, HashMap<PotionEffectType, Integer>> enchants = new HashMap<>();

        enchants.put(CEnchantments.GLOWING, new HashMap<>());
        enchants.get(CEnchantments.GLOWING).put(PotionEffectType.NIGHT_VISION, -1);

        enchants.put(CEnchantments.MERMAID, new HashMap<>());
        enchants.get(CEnchantments.MERMAID).put(PotionEffectType.WATER_BREATHING, -1);

        enchants.put(CEnchantments.BURNSHIELD, new HashMap<>());
        enchants.get(CEnchantments.BURNSHIELD).put(PotionEffectType.FIRE_RESISTANCE, -1);

        enchants.put(CEnchantments.DRUNK, new HashMap<>());
        enchants.get(CEnchantments.DRUNK).put(PotionEffectType.STRENGTH, -1);
        enchants.get(CEnchantments.DRUNK).put(PotionEffectType.MINING_FATIGUE, -1);
        enchants.get(CEnchantments.DRUNK).put(PotionEffectType.SLOWNESS, -1);

        enchants.put(CEnchantments.HULK, new HashMap<>());
        enchants.get(CEnchantments.HULK).put(PotionEffectType.STRENGTH, -1);
        enchants.get(CEnchantments.HULK).put(PotionEffectType.RESISTANCE, -1);
        enchants.get(CEnchantments.HULK).put(PotionEffectType.SLOWNESS, -1);

        // Valor is no longer a Resistance potion: it reduces incoming damage in DamageModifierListener.

        enchants.put(CEnchantments.OVERLOAD, new HashMap<>());
        enchants.get(CEnchantments.OVERLOAD).put(PotionEffectType.HEALTH_BOOST, -1);

        enchants.put(CEnchantments.NINJA, new HashMap<>());
        enchants.get(CEnchantments.NINJA).put(PotionEffectType.HEALTH_BOOST, -1);
        enchants.get(CEnchantments.NINJA).put(PotionEffectType.SPEED, -1);

        enchants.put(CEnchantments.INSOMNIA, new HashMap<>());
        enchants.get(CEnchantments.INSOMNIA).put(PotionEffectType.NAUSEA, -1);
        enchants.get(CEnchantments.INSOMNIA).put(PotionEffectType.MINING_FATIGUE, -1);
        enchants.get(CEnchantments.INSOMNIA).put(PotionEffectType.SLOWNESS, -1);

        // Jump Boost I is amplifier 0: Anti Gravity I starts at Jump Boost III.
        enchants.put(CEnchantments.ANTIGRAVITY, new HashMap<>());
        enchants.get(CEnchantments.ANTIGRAVITY).put(PotionEffectType.JUMP_BOOST, 1);

        enchants.put(CEnchantments.GEARS, new HashMap<>());
        enchants.get(CEnchantments.GEARS).put(PotionEffectType.SPEED, -1);

        enchants.put(CEnchantments.SPRINGS, new HashMap<>());
        enchants.get(CEnchantments.SPRINGS).put(PotionEffectType.JUMP_BOOST, -1);

        enchants.put(CEnchantments.CYBORG, new HashMap<>());
        enchants.get(CEnchantments.CYBORG).put(PotionEffectType.SPEED, -1);
        enchants.get(CEnchantments.CYBORG).put(PotionEffectType.STRENGTH, 0);
        enchants.get(CEnchantments.CYBORG).put(PotionEffectType.JUMP_BOOST, -1);

        return enchants;
    }

    /**
     *
     * @return true if the plugin uses limits listed in config.yml.
     */
    public boolean useConfigLimit() {
        return useConfigLimits;
    }

    /**
     * Get a players max amount of enchantments.
     * @param player The player you are checking.
     * @return The max amount of enchantments a player can have on an item.
     */
    public int getPlayerMaxEnchantments(Player player) {
        int limit = defaultLimit;

        if (useConfigLimits) return limit;

        for (PermissionAttachmentInfo Permission : player.getEffectivePermissions()) {
            String perm = Permission.getPermission().toLowerCase();

            if (perm.startsWith("crazyenchantments.limit.")) {
                perm = perm.replace("crazyenchantments.limit.", "");

                if (NumberUtils.isInt(perm) && limit < Integer.parseInt(perm)) limit = Integer.parseInt(perm);
            }
        }

        return limit;
    }

    /**
     * Based on config options, returns the base amount of enchants that the player can have on an.
     * @param player The {@link Player} to check.
     * @return The base amount of enchants the player can add to items.
     */
    public int getPlayerBaseEnchantments(@NotNull Player player) {
        int limit = defaultBaseLimit;

        if (useConfigLimits) return limit;

        for (PermissionAttachmentInfo Permission : player.getEffectivePermissions()) {
            String perm = Permission.getPermission().toLowerCase();

            if (perm.startsWith("crazyenchantments.base-limit.")) {
                perm = perm.replace("crazyenchantments.base-limit.", "");

                if (NumberUtils.isInt(perm) && limit < Integer.parseInt(perm)) limit = Integer.parseInt(perm);
            }
        }

        return limit;
    }

    /**
     * Checks if the player can add more enchants to the current item based on set limits.
     * @param player The {@link Player} that has the item.
     * @param item The {@link ItemStack} that they want to add the enchant to.
     * @return True if they are able to add more enchants.
     */
    public boolean canAddEnchantment(@NotNull Player player, @NotNull ItemStack item) {
        //todo() update permissions
        if (!this.maxEnchantmentCheck || player.hasPermission("crazyenchantments.bypass.limit")) return true;

        return this.enchantmentBookSettings.getEnchantmentAmount(item, this.checkVanillaLimit) <
                Math.min(getPlayerBaseEnchantments(player) - getEnchantmentLimiter(item), getPlayerMaxEnchantments(player));
    }

    /**
     * Checks if the player can add more enchants to the current item based on set limits without the enchant limiter.
     * @param player The {@link Player} that has the item.
     * @param cEnchantments The amount of crazy enchants on the item.
     * @param vanillaEnchantments The amount of vanilla enchantments on the item.
     * @return True if they are able to add more enchants.
     */
    public boolean canAddEnchantment(@NotNull Player player, int cEnchantments, int vanillaEnchantments) {
        if (!this.maxEnchantmentCheck || player.hasPermission("crazyenchantments.bypass.limit")) return true;

        int enchantAmount = cEnchantments;
        if (this.checkVanillaLimit) enchantAmount += vanillaEnchantments;

        return enchantAmount < getPlayerMaxEnchantments(player);
    }

    public int randomLevel(CEnchantment enchantment, Category category) {
        // FORK: book levels roll across the enchant's own 1..MaxPower range, not the tier's LvlRange, so each
        // enchant's full range is reachable regardless of which category it is in.
        int enchantmentMax = Math.max(1, enchantment.getMaxLevel());
        return 1 + new Random().nextInt(enchantmentMax);
    }

    /**
     * @return The head multiplier map for decapitation and headless.
     */
    public Map<Material, Double> getDecapitationHeadMap() {
        return this.headMap;
    }

    /**
     * @return The block list for blast.
     */
    public List<Material> getBlastBlockList() {
        return this.blockList;
    }

    /**
     * @return If the blast enchantment drops blocks.
     */
    public boolean isDropBlocksBlast() {
        return this.dropBlocksBlast;
    }

    /**
     * @return If the vein-miner enchantment drops blocks.
     */
    public boolean isDropBlocksVeinMiner() {
        return this.dropBlocksVeinMiner;
    }

    /**
     * @param dropBlocksBlast If the blast enchantment drops blocks.
     */
    public void setDropBlocksBlast(boolean dropBlocksBlast) {
        this.dropBlocksBlast = dropBlocksBlast;
    }

    /**
     * @param dropBlocksVeinMiner If the vein-miner enchantment drops blocks.
     */
    public void setDropBlocksVeinMiner(boolean dropBlocksVeinMiner) {
        this.dropBlocksVeinMiner = dropBlocksVeinMiner;
    }

    /**
     * @return The max rage stack level.
     */
    public int getRageMaxLevel() {
        return this.rageMaxLevel;
    }

    /**
     * Check if players lose their current rage stack on damage.
     * @return True if they do and false if not.
     */
    public boolean isBreakRageOnDamageOn() {
        return this.breakRageOnDamage;
    }

    /**
     * @return True if a boss bar will be used to display rage notifications.
     */
    public boolean useRageBossBar() {
        return this.useRageBossBar;
    }

    public double getRageIncrement() {
        return this.rageIncrement;
    }

    private void addCEPlayer(UUID uuid, CEPlayer player) {
        this.players.put(uuid, player);
    }

    private void removeCEPlayer(UUID uuid) {
        this.players.remove(uuid);
    }

    private List<ItemStack> getInfoGKit(List<String> itemStrings) {
        List<ItemStack> items = new ArrayList<>();

        for (String itemString : itemStrings) {
            // This is used to convert old v1.7- gkit files to use newer way.
            itemString = getNewItemString(itemString);

            ItemBuilder itemBuilder = ItemBuilder.convertString(itemString);
            List<String> customEnchantments = new ArrayList<>();
            HashMap<Enchantment, Integer> enchantments = new HashMap<>();

            for (String option : itemString.split(", ")) {
                try {
                    Enchantment enchantment = this.methods.getEnchantment(option.split(":")[0]);
                    CEnchantment cEnchantment = getEnchantmentFromName(option.split(":")[0]);
                    String level = option.split(":")[1];

                    if (enchantment != null) {
                        if (level.contains("-")) {
                            customEnchantments.add("&7" + option.split(":")[0] + " " + level);
                        } else {
                            enchantments.put(enchantment, Integer.parseInt(level));
                        }
                    } else if (cEnchantment != null) {
                        customEnchantments.add(cEnchantment.getCustomName() + " " + level);
                    }
                } catch (Exception ignore) {}
            }

            itemBuilder.getLore().addAll(0, customEnchantments.stream().map(ColorUtils::legacyTranslateColourCodes).toList());
            itemBuilder.setEnchantments(enchantments);

            items.add(itemBuilder.addKey(DataKeys.random_number.getNamespacedKey(), String.valueOf(methods.getRandomNumber(0, Integer.MAX_VALUE))).build());
            // This is done so items do not stack if there are multiple of the same.
        }

        return items;
    }

    public String getNewItemString(String itemString) {
        StringBuilder newItemString = new StringBuilder();

        for (String option : itemString.split(", ")) {
            if (option.toLowerCase().startsWith("enchantments:") || option.toLowerCase().startsWith("customenchantments:")) {
                StringBuilder newOption = new StringBuilder();

                for (String enchantment : option.toLowerCase().replace("customenchantments:", "").replace("enchantments:", "").split(",")) {
                    newOption.append(enchantment).append(", ");
                }

                option = newOption.substring(0, newOption.length() - 2);
            }

            newItemString.append(option).append(", ");
        }

        if (!newItemString.isEmpty()) itemString = newItemString.substring(0, newItemString.length() - 2);
        return itemString;
    }

    public int pickLevel(int min, int max) {
        return min + new Random().nextInt((max + 1) - min);
    }

    /** Gets the success override from the config. Default -1 means no override should be used */
    public int getCESuccessOverride() { return CESuccessOverride; }

    /** Gets the failure override from the config. Default -1 means no override should be used */
    public int getCEFailureOverride() { return CEFailureOverride; }

}
