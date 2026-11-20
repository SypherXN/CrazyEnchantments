package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.builders.types.BaseMenu;
import com.badbones69.crazyenchantments.paper.api.builders.types.blacksmith.BlackSmithMenu;
import com.badbones69.crazyenchantments.paper.api.builders.types.gkitz.KitsMenu;
import com.badbones69.crazyenchantments.paper.api.builders.types.tinkerer.TinkererMenu;
import com.badbones69.crazyenchantments.paper.api.bleed.BleedSystem;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.commands.*;
import com.badbones69.crazyenchantments.paper.commands.api.CommandManager;
import com.badbones69.crazyenchantments.paper.controllers.BossBarController;
import com.badbones69.crazyenchantments.paper.controllers.LostBookController;
import com.badbones69.crazyenchantments.paper.enchantments.AllyEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.ArmorEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.AxeEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.BootEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.BowEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.HoeEnchantments;
import com.badbones69.crazyenchantments.paper.api.utils.AutoSellSupport;
import com.badbones69.crazyenchantments.paper.enchantments.PickaxeEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.SwordEnchantments;
import com.badbones69.crazyenchantments.paper.enchantments.ToolEnchantments;
import com.badbones69.crazyenchantments.paper.listeners.AuraListener;
import com.badbones69.crazyenchantments.paper.listeners.BlockListener;
import com.badbones69.crazyenchantments.paper.listeners.DamageModifierListener;
import com.badbones69.crazyenchantments.paper.listeners.DefensiveArmorListener;
import com.badbones69.crazyenchantments.paper.listeners.DustControlListener;
import com.badbones69.crazyenchantments.paper.listeners.FeignDeathListener;
import com.badbones69.crazyenchantments.paper.listeners.FireworkDamageListener;
import com.badbones69.crazyenchantments.paper.listeners.InversionListener;
import com.badbones69.crazyenchantments.paper.listeners.KillAuraListener;
import com.badbones69.crazyenchantments.paper.listeners.McMMOExperienceListener;
import com.badbones69.crazyenchantments.paper.listeners.MiscListener;
import com.badbones69.crazyenchantments.paper.listeners.ProtectionCrystalListener;
import com.badbones69.crazyenchantments.paper.listeners.ShackleListener;
import com.badbones69.crazyenchantments.paper.listeners.ShopListener;
import com.badbones69.crazyenchantments.paper.listeners.server.WorldSwitchListener;
import com.ryderbelserion.fusion.core.api.enums.Level;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class CrazyEnchantments extends JavaPlugin {

    private Starter starter;

    // Plugin Listeners.
    public final PluginManager pluginManager = getServer().getPluginManager();

    private FireworkDamageListener fireworkDamageListener;
    private ArmorEnchantments armorEnchantments;
    private AxeEnchantments axeEnchantments;

    private final BossBarController bossBarController = new BossBarController(this);

    private CrazyPlatform platform;

    @Override
    public void onEnable() {
        this.platform = new CrazyPlatform();
        this.platform.init();

        this.starter = new Starter();
        this.starter.run();

        this.starter.getCurrencyAPI().loadCurrency();

        final FileConfiguration config = FileKeys.CONFIG.getConfiguration();
        final FileConfiguration tinker = FileKeys.TINKER.getConfiguration();

        boolean isSave = false;

        if (!config.contains("Settings.CESuccessOverride")) {
            config.set("Settings.CESuccessOverride", "-1");

            isSave = true;
        }

        if (!config.contains("Settings.CESuccessOverride")) {
            config.set("Settings.CEFailureOverride", "-1");

            isSave = true;
        }

        if (!config.contains("Settings.Toggle-Metrics")) {
            config.set("Settings.Toggle-Metrics", false);

            isSave = true;
        }

        if (!config.contains("Settings.Refresh-Potion-Effects-On-World-Change")) {
            config.set("Settings.Refresh-Potion-Effects-On-World-Change", false);

            isSave = true;
        }

        if (!tinker.contains("Settings.Tinker-Version")) {
            tinker.set("Settings.Tinker-Version", 1.0);

            isSave = true;
        }

        if (isSave) {
            FileKeys.CONFIG.save();
        }

        if (config.getBoolean("Settings.Toggle-Metrics", false)) new Metrics(this, 4494);

        this.pluginManager.registerEvents(this.fireworkDamageListener = new FireworkDamageListener(), this);
        this.pluginManager.registerEvents(new ShopListener(), this);

        // Load what we need to properly enable the plugin.
        this.starter.getCrazyManager().load();

        this.pluginManager.registerEvents(new MiscListener(), this);
        this.pluginManager.registerEvents(new DustControlListener(), this);

        this.pluginManager.registerEvents(new BlackSmithMenu.BlackSmithListener(), this);
        this.pluginManager.registerEvents(new KitsMenu.KitsListener(), this);
        this.pluginManager.registerEvents(new TinkererMenu.TinkererListener(), this);
        this.pluginManager.registerEvents(new BaseMenu.InfoMenuListener(), this);

        // Block goes first so a negated hit never reaches the shared damage layer or the attacker's enchants.
        final BlockListener block = new BlockListener();
        // Register explicitly: the annotated MONITOR callback must not be registered.
        this.pluginManager.registerEvent(EntityDamageByEntityEvent.class, block, EventPriority.HIGHEST, (listener, event) -> {
            if (event instanceof EntityDamageByEntityEvent hit && hit.getEntity() instanceof Player) block.onHit(hit);
        }, this, true);

        // Inversion cancels the hit before the attacker's sword handler can apply its procs.
        this.pluginManager.registerEvents(new InversionListener(), this);

        // Register first at HIGHEST so System Reboot checks lethality against the modified damage.
        this.pluginManager.registerEvents(new DamageModifierListener(), this);

        final PickaxeEnchantments pickaxeEnchantments = new PickaxeEnchantments();
        this.pluginManager.registerEvents(pickaxeEnchantments, this);
        this.pluginManager.registerEvents(new SwordEnchantments(), this);

        this.pluginManager.registerEvent(EntityDamageByEntityEvent.class, block, EventPriority.HIGHEST, (listener, event) -> {
            if (event instanceof EntityDamageByEntityEvent hit && hit.getEntity() instanceof Player) block.onHitResolved(hit);
        }, this, false);

        // See sword bonuses at HIGHEST, before armor handlers and all MONITOR observers.
        final FeignDeathListener feignDeath = new FeignDeathListener(
                event -> this.armorEnchantments != null && this.armorEnchantments.tryFeignDeath(event));
        this.pluginManager.registerEvents(feignDeath, this);

        this.pluginManager.registerEvents(this.armorEnchantments = new ArmorEnchantments(), this);
        this.pluginManager.registerEvents(new AllyEnchantments(), this);
        this.pluginManager.registerEvents(new ToolEnchantments(), this);
        this.pluginManager.registerEvents(new BootEnchantments(), this);
        this.pluginManager.registerEvents(this.axeEnchantments = new AxeEnchantments(), this);
        final BowEnchantments bow = new BowEnchantments();
        this.pluginManager.registerEvents(bow, this);
        this.pluginManager.registerEvents(new HoeEnchantments(), this);
        this.pluginManager.registerEvents(new ShackleListener(), this);

        if (Bukkit.getPluginManager().isPluginEnabled("WildStacker")) {
            this.pluginManager.registerEvents(new KillAuraListener(this), this);
        }

        // Auto Sell needs EconomyShopGUI's prices; without it no drop is ever touched.
        if (Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI") || Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI-Premium")) {
            try {
                pickaxeEnchantments.setAutoSeller(new AutoSellSupport(this));
            } catch (LinkageError | RuntimeException failure) {
                getLogger().log(java.util.logging.Level.WARNING, "Auto Sell disabled: could not load the EconomyShopGUI API.", failure);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            this.pluginManager.registerEvents(new McMMOExperienceListener(this), this);
        }

        this.pluginManager.registerEvents(new DefensiveArmorListener(), this);

        // At MONITOR, resolve Feign after the sword/bow folds and Creeper Armor's reduction.
        this.pluginManager.registerEvent(EntityDamageEvent.class, feignDeath, EventPriority.MONITOR, (listener, event) -> {
            if (event instanceof EntityDamageEvent hit) feignDeath.onDamageResolved(hit);
        }, this, true);
        this.pluginManager.registerEvent(EntityDamageEvent.class, feignDeath, EventPriority.MONITOR, (listener, event) -> {
            if (event instanceof EntityDamageEvent hit && hit.isCancelled()) feignDeath.clearDamage(hit);
        }, this, false);
        // Run even on cancelled hits to consume their pending lifesteal without healing.
        this.pluginManager.registerEvent(EntityDamageByEntityEvent.class, bow, EventPriority.MONITOR, (listener, event) -> {
            if (event instanceof EntityDamageByEntityEvent hit) bow.onArrowLifestealResolved(hit);
        }, this, false);

        this.pluginManager.registerEvents(new ProtectionCrystalListener(), this);
        this.pluginManager.registerEvents(new FireworkDamageListener(), this);
        this.pluginManager.registerEvents(new AuraListener(), this);

        this.pluginManager.registerEvents(new LostBookController(), this);

        this.pluginManager.registerEvents(new WorldSwitchListener(), this);
        this.pluginManager.registerEvents(new BleedSystem(), this);

        if (this.starter.getCrazyManager().isGkitzEnabled()) {
            this.platform.getFusion().log(Level.WARNING, "G-Kitz Support is now enabled!");

            this.pluginManager.registerEvents(new KitsMenu.KitsListener(), this);
        }

        CommandManager.load();
    }

    @Override
    public void onDisable() {
        final Server server = getServer();

        server.getGlobalRegionScheduler().cancelTasks(this);
        server.getAsyncScheduler().cancelTasks(this);

        if (this.starter != null) {
            final var manager = this.starter.getCrazyManager();
            if (manager != null) {
                manager.cancelBackupTask();
                server.getOnlinePlayers().forEach(manager::unloadCEPlayer);
            }

            this.bossBarController.removeAllBossBars();

            if (this.starter.getNameTagListener() != null) this.starter.getNameTagListener().clearAll();

            BleedSystem.shutdown();

            if (this.armorEnchantments != null) this.armorEnchantments.stop();

            if (this.starter.getAllyManager() != null) this.starter.getAllyManager().forceRemoveAllies();
        }
    }

    public Starter getStarter() {
        return this.starter;
    }

    /**
     * The armor listener, or null during enable before it is constructed.
     */
    public ArmorEnchantments getArmorEnchantments() {
        return this.armorEnchantments;
    }

    public AxeEnchantments getAxeEnchantments() {
        return this.axeEnchantments;
    }

    // Plugin Listeners.
    public FireworkDamageListener getFireworkDamageListener() {
        return this.fireworkDamageListener;
    }

    public PluginManager getPluginManager() {
        return this.pluginManager;
    }

    public BossBarController getBossBarController() {
        return bossBarController;
    }

    public @NotNull final CrazyPlatform getPlatform() {
        return this.platform;
    }
}
