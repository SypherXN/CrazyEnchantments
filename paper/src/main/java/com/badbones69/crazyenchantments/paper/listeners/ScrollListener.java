package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.builders.types.MenuManager;
import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.api.enums.Scrolls;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.Enchant;
import com.badbones69.crazyenchantments.paper.api.objects.CEBook;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.objects.enchants.EnchantmentType;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.LoreLines;
import com.badbones69.crazyenchantments.paper.api.utils.NumberUtils;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.ProtectionCrystalSettings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import org.apache.commons.text.WordUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

public class ScrollListener implements Listener {

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final Starter starter = this.plugin.getStarter();

    private final Methods methods = this.starter.getMethods();

    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    private String suffix;
    private boolean countVanillaEnchantments;
    private boolean useSuffix;
    private boolean blackScrollChanceToggle;
    private int blackScrollChance;

    public void loadScrollControl() {
        final FileConfiguration config = FileKeys.CONFIG.getConfiguration();

        this.suffix = config.getString("Settings.TransmogScroll.Amount-of-Enchantments", " &7[&6&n%amount%&7]");
        this.countVanillaEnchantments = config.getBoolean("Settings.TransmogScroll.Count-Vanilla-Enchantments", true);
        this.useSuffix = config.getBoolean("Settings.TransmogScroll.Amount-Toggle", true);
        this.blackScrollChance = config.getInt("Settings.BlackScroll.Chance", 75);
        this.blackScrollChanceToggle = config.getBoolean("Settings.BlackScroll.Chance-Toggle", false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onScrollUse(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        ItemStack scroll = event.getCursor();

        if (item == null || item.getType().isAir() || scroll.getType().isAir()) return;

        InventoryType.SlotType slotType = event.getSlotType();

        if (slotType != InventoryType.SlotType.ARMOR && slotType != InventoryType.SlotType.CONTAINER && slotType != InventoryType.SlotType.QUICKBAR) return;

        Scrolls type = Scrolls.getFromPDC(scroll);
        if (type == null) return;

        if (scroll.getAmount() > 1) {
            // A stacked transmog scroll is still a scroll-on-target gesture, so
            // cancel rather than let the vanilla click swap it with the item.
            if (type == Scrolls.TRANSMOG_SCROLL) event.setCancelled(true);

            player.sendMessage(Messages.NEED_TO_UNSTACK_ITEM.getMessage());
            return;
        }

        switch (type.getConfigName()) {
            case "BlackScroll" -> {
                if (this.methods.isInventoryFull(player)) return;

                List<CEnchantment> enchantments = this.enchantmentBookSettings.getEnchantmentsOnItem(item);
                if (!enchantments.isEmpty()) { // Item has enchantments
                    event.setCancelled(true);
                    player.setItemOnCursor(this.methods.removeItem(scroll));

                    if (this.blackScrollChanceToggle && !this.methods.randomPicker(this.blackScrollChance, 100)) {
                        player.sendMessage(Messages.BLACK_SCROLL_UNSUCCESSFUL.getMessage());
                        return;
                    }

                    Random random = new Random();

                    CEnchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));
                    player.getInventory().addItem(new CEBook(enchantment, this.enchantmentBookSettings.getLevel(item, enchantment), 1).buildBook());
                    event.setCurrentItem(this.enchantmentBookSettings.removeEnchantment(item, enchantment));
                }
            }

            case "WhiteScroll" -> {
                if (Scrolls.hasWhiteScrollProtection(item)) return;
                for (EnchantmentType enchantmentType : MenuManager.getEnchantmentTypes()) {
                    if (enchantmentType.getEnchantableMaterials().contains(item.getType())) {
                        event.setCancelled(true);
                        event.setCurrentItem(Scrolls.addWhiteScrollProtection(item));
                        player.setItemOnCursor(this.methods.removeItem(scroll));
                        return;
                    }
                }
            }

            case "TransmogScroll" -> {
                // Dropping the scroll on an item is the intended gesture whether
                // or not it can act; cancel the vanilla swap either way and say
                // why, so a no-op never trades the scroll for the item.
                event.setCancelled(true);

                if (this.enchantmentBookSettings.getEnchantments(item).isEmpty()) {
                    player.sendMessage(ColorUtils.legacyTranslateColourCodes("&7This item has no enchantments to organize."));

                    return;
                }

                ItemStack orderedItem = newOrderNewEnchantments(item.clone());

                if (item.isSimilar(orderedItem)) {
                    player.sendMessage(ColorUtils.legacyTranslateColourCodes("&7This item is already organized."));

                    return;
                }

                event.setCurrentItem(orderedItem);
                player.setItemOnCursor(this.methods.removeItem(scroll));
            }
        }
    }

    @EventHandler()
    public void onScrollClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (checkScroll(player.getInventory().getItemInMainHand(), player, event)) return;

        checkScroll(player.getInventory().getItemInOffHand(), player, event);

    }

    private boolean checkScroll(final ItemStack scroll, final Player player, final PlayerInteractEvent event) {
        if (scroll.isEmpty()) return false;

        final PersistentDataContainerView container = scroll.getPersistentDataContainer();

        if (!container.has(DataKeys.scroll.getNamespacedKey())) return false;

        final String data = container.get(DataKeys.scroll.getNamespacedKey(), PersistentDataType.STRING);

        if (data == null) return false;

        if (data.equalsIgnoreCase(Scrolls.BLACK_SCROLL.getConfigName())) {
            event.setCancelled(true);

            player.sendMessage(Messages.RIGHT_CLICK_BLACK_SCROLL.getMessage());

            return true;
        } else if (data.equalsIgnoreCase(Scrolls.WHITE_SCROLL.getConfigName()) || data.equalsIgnoreCase(Scrolls.TRANSMOG_SCROLL.getConfigName())) {
            event.setCancelled(true);

            return true;
        }

        return false;
    }

    private ItemStack newOrderNewEnchantments(ItemStack item) {
        final FileConfiguration configuration = FileKeys.CONFIG.getConfiguration();

        final List<Component> lore = item.lore();

        final PersistentDataContainerView container = item.getPersistentDataContainer();

        final Enchant data = Enchant.fromJson(container.get(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING));

        if (data == null) return item; // Only order if it has CE_Enchants

        final Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

        if (enchantments.isEmpty()) return item;

        final List<CEnchantment> newEnchantmentOrder = new ArrayList<>();

        for (final CEnchantment enchantment : this.enchantmentBookSettings.getRegisteredEnchantments()) {
            if (enchantments.containsKey(enchantment)) newEnchantmentOrder.add(enchantment);
        }

        orderByRarity(newEnchantmentOrder);

        final List<Component> enchantLore = newEnchantmentOrder.stream().map(enchantment ->
                ColorUtils.legacyTranslateColourCodes("%s %s".formatted(enchantment.getCustomName(), NumberUtils.convertLevelString(enchantments.get(enchantment))))).collect(Collectors.toList());

        final List<Component> normalLore = stripNonNormalLore(lore, newEnchantmentOrder, data, container);

        final List<Component> protectionLore = getAllProtectionLore(container);

        final List<Component> newLore = new ArrayList<>();

        final boolean addSpaces = configuration.getBoolean("Settings.TransmogScroll.Add-Blank-Lines", true);

        boolean wasEmpty = true;

        for (String selection : LoreLines.sectionOrder(configuration.getStringList("Settings.TransmogScroll.Lore-Order"))) {
            final List<Component> sectionLore = switch (selection) {
                case "CE_Enchantments" -> enchantLore;
                case "Protection" -> protectionLore;
                case "Normal_Lore" -> normalLore;
                default -> List.of();
            };

            if (addSpaces && !wasEmpty && !sectionLore.isEmpty()) newLore.add(Component.text(""));

            newLore.addAll(sectionLore);

            wasEmpty = sectionLore.isEmpty();
        }

        useSuffix(item, newEnchantmentOrder.size());

        item.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(newLore).build());

        return item;
    }

    /**
     * The scroll's whole job: highest rarity first, so Legendary sits above
     * Simple. The order comes from each category's configured {@code Rarity},
     * and enchants of equal rarity keep the registry's order.
     *
     * @param enchantments The enchantments on the item, edited in place.
     */
    private static void orderByRarity(final List<CEnchantment> enchantments) {
        final Map<String, Integer> rarity = new HashMap<>();
        final Map<String, CEnchantment> byName = new LinkedHashMap<>();

        for (final CEnchantment enchantment : enchantments) {
            int highest = Integer.MIN_VALUE;

            for (final Category category : enchantment.getCategories()) highest = Math.max(highest, category.getRarity());

            rarity.put(enchantment.getName(), highest);
            byName.put(enchantment.getName(), enchantment);
        }

        final List<CEnchantment> ordered = new ArrayList<>();

        for (final String name : LoreLines.highestRarityFirst(new ArrayList<>(byName.keySet()), rarity)) ordered.add(byName.get(name));

        enchantments.clear();
        enchantments.addAll(ordered);
    }

    private List<Component> getAllProtectionLore(@NotNull PersistentDataContainerView container) {
        List<Component> lore = new ArrayList<>();

        final YamlConfiguration configuration = FileKeys.CONFIG.getConfiguration();

        if (Scrolls.hasWhiteScrollProtection(container)) lore.add(ColorUtils.legacyTranslateColourCodes(configuration.getString("Settings.WhiteScroll.ProtectedName", "&b&lPROTECTED")));
        if (ProtectionCrystalSettings.isProtected(container)) lore.add(ColorUtils.legacyTranslateColourCodes(configuration.getString("Settings.ProtectionCrystal.Protected", "&6Ancient Protection")));

        return lore;
    }

    private List<Component> stripNonNormalLore(final List<Component> source, final List<CEnchantment> enchantments, final Enchant stored, @NotNull final PersistentDataContainerView container) {
        final List<Component> lore = new ArrayList<>(source == null ? List.of() : source);

        // Remove blank lines
        lore.removeIf(loreComponent -> ColorUtils.toPlainText(loreComponent).replaceAll(" ", "").isEmpty());

        // Remove CE enchantment lore
        enchantments.forEach(enchant -> lore.removeIf(loreComponent ->
                EnchantmentBookSettings.matchesLore(loreComponent, enchant, stored)
        ));

        // Only the protection line the item actually carries is ours to move;
        // ordinary lore that happens to read the same is left where it is.
        if (Scrolls.hasWhiteScrollProtection(container)) {
            final String whiteScrollName = ColorUtils.stripStringColour(FileKeys.CONFIG.getConfiguration().getString("Settings.WhiteScroll.ProtectedName", "&b&lPROTECTED"));

            lore.removeIf(loreComponent -> LoreLines.isMarkerLine(ColorUtils.toPlainText(loreComponent), whiteScrollName));
        }

        if (ProtectionCrystalSettings.isProtected(container)) {
            final String protectionName = ColorUtils.stripStringColour(FileKeys.CONFIG.getConfiguration().getString("Settings.ProtectionCrystal.Protected", "&6Ancient Protection"));

            lore.removeIf(loreComponent -> LoreLines.isMarkerLine(ColorUtils.toPlainText(loreComponent), protectionName));
        }

        return lore;
    }

    private void useSuffix(final ItemStack item, final int enchantmentAmount) {
        if (!this.useSuffix) return;

        final String amount = String.valueOf(this.countVanillaEnchantments ? enchantmentAmount + item.getEnchantments().size() : enchantmentAmount);

        // CUSTOM_NAME is an anvil rename and outranks ITEM_NAME, so the count is
        // written where the player actually sees it; with neither, name the item
        // from its material. Nothing else on the item is touched.
        final boolean hasCustomName = item.hasData(DataComponentTypes.CUSTOM_NAME);

        final Component current = hasCustomName ? item.getData(DataComponentTypes.CUSTOM_NAME) : item.getData(DataComponentTypes.ITEM_NAME);

        final String base = current != null ? ColorUtils.toLegacy(current) : "&b" + WordUtils.capitalizeFully(item.getType().toString().replace("_", " "));

        final String suffix = LoreLines.renderedSuffix(this.suffix, amount);
        final String stripped = LoreLines.withoutSuffix(base, this.suffix);

        // The name already shows this count, so leave it (and the scroll) alone.
        if ((stripped + suffix).equals(base)) return;

        item.setData(hasCustomName ? DataComponentTypes.CUSTOM_NAME : DataComponentTypes.ITEM_NAME, ColorUtils.legacyTranslateColourCodes(stripped + suffix));
    }
}
