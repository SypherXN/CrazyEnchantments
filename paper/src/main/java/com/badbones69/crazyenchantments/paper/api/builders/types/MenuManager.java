package com.badbones69.crazyenchantments.paper.api.builders.types;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.builders.types.blacksmith.BlackSmithManager;
import com.badbones69.crazyenchantments.paper.api.builders.types.blacksmith.BlackSmithMenu;
import com.badbones69.crazyenchantments.paper.api.builders.types.gkitz.KitsManager;
import com.badbones69.crazyenchantments.paper.api.builders.types.gkitz.KitsMenu;
import com.badbones69.crazyenchantments.paper.api.builders.types.gkitz.KitsPreviewMenu;
import com.badbones69.crazyenchantments.paper.api.builders.types.tinkerer.TinkererMenu;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.enchants.EnchantmentType;
import com.badbones69.crazyenchantments.paper.api.objects.gkitz.GKitz;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public class MenuManager {

    private static final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private static final CrazyPlatform platform = plugin.getPlatform();

    private static final FusionPaper fusion = platform.getFusion();

    private static final List<EnchantmentType> enchantmentTypes = new ArrayList<>();

    public static void load() {
        enchantmentTypes.clear();

        final FileConfiguration file = FileKeys.ENCHANTMENT_TYPES.getConfiguration();

        final ConfigurationSection section = file.getConfigurationSection("Types");

        if (section == null) {
            fusion.log(Level.WARNING, "The types section cannot be found in enchantment-types.yml, It's possible the file is badly formatted!");

            return;
        }

        for (String type : section.getKeys(false)) {
            enchantmentTypes.add(new EnchantmentType(type));
        }
    }

    public static List<EnchantmentType> getEnchantmentTypes() {
        return enchantmentTypes;
    }

    public static void openKitsMenu(Player player) {
        final FileConfiguration gkitz = FileKeys.GKITZ.getConfiguration();

        player.openInventory(new KitsMenu(player, gkitz.getInt("Settings.GUI-Size", 27), gkitz.getString("Settings.Inventory-Name", "&8List of all GKitz")).build().getInventory());
    }

    public static void openKitsPreviewMenu(Player player, int slots, GKitz kit) {
        player.openInventory(new KitsPreviewMenu(player, slots, ColorUtils.toLegacy(kit.getDisplayItem().displayName()), kit).build().getInventory());
    }

    public static void openInfoMenu(Player player) {
        player.openInventory(new BaseMenu(player, KitsManager.getInventorySize(), ColorUtils.toLegacy(KitsManager.getInventoryName())).build().getInventory());
    }

    public static void openInfoMenu(Player player, EnchantmentType type) {
        openInfoMenu(player, type, 0);
    }

    public static void openInfoMenu(Player player, EnchantmentType type, int page) {
        // Size from the books the page actually shows: a page gathers matching
        // enchantments from every type, not just its own declared list. The
        // bottom row is navigation, so each page holds size - 9 books.
        final int books = BaseMenu.matchedEnchantments(type).size();
        int slots = 18;

        while (books > slots - 9 && slots < 54) slots += 9;

        player.openInventory(new BaseMenu(player, slots, ColorUtils.toLegacy(KitsManager.getInventoryName()), page).setEnchantmentType(type).build().getInventory());
    }

    public static void openBlackSmithMenu(Player player) {
        player.openInventory(new BlackSmithMenu(player, 27, BlackSmithManager.getInventoryName()).build().getInventory());
    }

    public static void openTinkererMenu(Player player) {
        player.openInventory(new TinkererMenu(player, 54, FileKeys.TINKER.getConfiguration().getString("Settings.GUIName", "&7&lThe &4&lCrazy &c&lTinkerer")).build().getInventory());
    }

    public static int getInventorySize(FileConfiguration configuration) {
        return configuration.getInt("Settings.GUISize", 54);
    }
}