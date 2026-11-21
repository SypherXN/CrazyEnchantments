package com.badbones69.crazyenchantments.paper.controllers.settings;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.economy.Currency;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.Enchant;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.EnchantedBook;
import com.badbones69.crazyenchantments.paper.api.objects.CEBook;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.objects.LostBook;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.LoreLines;
import com.badbones69.crazyenchantments.paper.api.utils.NumberUtils;
import com.badbones69.crazyenchantments.paper.api.utils.RegistrySnapshot;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnchantmentBookSettings {

    /**
     * The poison enchant's current name, and the label its lore carried before
     * the display rename. The old label is also the headshot enchant's name
     * today, so it is only ever read as poison where the poison level says so.
     */
    private static final String POISON_SHOT = "Poison Shot";

    private static final String SNIPER = "Sniper";

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    private ItemBuilder enchantmentBook;

    private record Registry(RegistrySnapshot<CEnchantment> enchantments, List<Category> categories) {}

    private volatile Registry registry = new Registry(new RegistrySnapshot<>(CEnchantment::getName), List.of());

    /**
     *
     * @return True if unsafe enchantments are enabled.
     */
    public boolean useUnsafeEnchantments() {
        final FileConfiguration config = FileKeys.CONFIG.getConfiguration();

        return config.getBoolean("Settings.EnchantmentOptions.UnSafe-Enchantments", true);
    }

    /**
     * This method converts an ItemStack into a CEBook.
     * @param book The ItemStack you are converting.
     * @return If the book is a CEBook it will return the CEBook object and if not it will return null.
     */
    @Nullable
    public CEBook getCEBook(final ItemStack book) {
        final PersistentDataContainerView view = book.getPersistentDataContainer();

        if (!view.has(DataKeys.stored_enchantments.getNamespacedKey())) return null;

        EnchantedBook data = EnchantedBook.fromJson(view.get(DataKeys.stored_enchantments.getNamespacedKey(), PersistentDataType.STRING));

        CEnchantment enchantment = null;

        for (final CEnchantment enchant : getRegisteredEnchantments()) {
            if (enchant.getName().equalsIgnoreCase(data.getName())) {
                enchantment = enchant;

                break;
            }
        }

        return new CEBook(enchantment, data.getLevel(), book.getAmount()).setSuccessRate(data.getSuccessChance()).setDestroyRate(data.getDestroyChance());
    }

    /**
     * Get a new book that has been scrambled.
     * @param book The old book.
     * @return A new scrambled book.
     */
    @Nullable
    public ItemStack getNewScrambledBook(final ItemStack book) {
        final PersistentDataContainerView view = book.getPersistentDataContainer();

        final EnchantedBook data = EnchantedBook.fromJson(view.get(DataKeys.stored_enchantments.getNamespacedKey(), PersistentDataType.STRING));

        CEnchantment enchantment = null;

        int bookLevel = 0;

        for (final CEnchantment enchantment1 : getRegisteredEnchantments()) {
            if (!enchantment1.getName().equalsIgnoreCase(data.getName())) continue;

            enchantment = enchantment1;

            bookLevel = data.getLevel();
        }

        if (enchantment == null) return null;

        return new CEBook(enchantment, bookLevel, EnchantUtils.getHighestEnchantmentCategory(enchantment)).buildBook();
    }

    /**
     * Check if an itemstack is an enchantment book.
     * @param book The item you are checking.
     * @return True if it is and false if not.
     */
    public boolean isEnchantmentBook(final ItemStack book) {
        if (book == null) return false;

        final PersistentDataContainerView view = book.getPersistentDataContainer();

        if (!view.has(DataKeys.stored_enchantments.getNamespacedKey())) return false;

        final String dataString = view.get(DataKeys.stored_enchantments.getNamespacedKey(), PersistentDataType.STRING);
        final EnchantedBook data = EnchantedBook.fromJson(dataString);

        for (final CEnchantment enchantment : getRegisteredEnchantments()) {
            if (enchantment.getName().equalsIgnoreCase(data.getName())) return true;
        }

        return false;
    }

    /**
     *
     * @return The current immutable registry snapshot.
     */
    @NotNull
    public List<CEnchantment> getRegisteredEnchantments() {
        return this.registry.enchantments().entries();
    }

    public CEnchantment getRegisteredEnchantment(final String name) {
        return this.registry.enchantments().byName(name);
    }

    public synchronized void publishRegistry(final List<CEnchantment> enchantments, final List<Category> categories) {
        final var next = new RegistrySnapshot<>(CEnchantment::getName);
        next.publish(enchantments);
        this.registry = new Registry(next, List.copyOf(categories));
    }

    public synchronized void registerEnchantment(final CEnchantment enchantment) {
        this.registry.enchantments().add(enchantment);
    }

    public synchronized void unregisterEnchantment(final CEnchantment enchantment) {
        this.registry.enchantments().remove(enchantment);
    }

    /**
     *
     * @return itemBuilder for an enchanted book.
     */
    public ItemBuilder getNormalBook() {
        return new ItemBuilder(this.enchantmentBook);
    }

    /**
     * @return the itemstack of the enchantment book.
     */
    @NotNull
    public ItemStack getEnchantmentBookItem() {
        return new ItemBuilder(this.enchantmentBook).build();
    }

    /**
     *
     * @param enchantmentBook The book data to use for the itemBuilder.
     */
    public void setEnchantmentBook(@NotNull ItemBuilder enchantmentBook) {
        this.enchantmentBook = enchantmentBook;
    }

    /**
     * Note: If the enchantment is not active it will not be added to the Map.
     * @param item Item you want to get the enchantments from.
     * @return A Map of all enchantments and their levels on the item.
     */
    @NotNull
    public Map<CEnchantment, Integer> getEnchantments(@Nullable final ItemStack item) {
        if (item == null) return Collections.emptyMap();

        final PersistentDataContainerView view = item.getPersistentDataContainer();

        final String data = view.get(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING);

        if (data == null) return Collections.emptyMap();

        final Enchant enchants = Enchant.fromJson(data);

        if (enchants.isEmpty()) return Collections.emptyMap();

        final Map<CEnchantment, Integer> enchantments = new HashMap<>();

        for (final CEnchantment enchantment : getRegisteredEnchantments()) {
            if (!enchantment.isActivated()) continue;

            if (enchants.hasEnchantment(enchantment.getName())) enchantments.put(enchantment, enchants.getLevel(enchantment.getName()));
        }

        return enchantments;
    }

    public List<CEnchantment> getEnchantmentsOnItem(final ItemStack item) {
        return new ArrayList<>(getEnchantments(item).keySet());
    }

    /**
     *
     * @param item to check.
     * @return Amount of enchantments on the item.
     */
    public int getEnchantmentAmount(@NotNull ItemStack item, boolean includeVanillaEnchantments) {
        int amount = getEnchantments(item).size();

        if (includeVanillaEnchantments && item.hasData(DataComponentTypes.ENCHANTMENTS)) {
            amount += item.getEnchantments().size();
        }

        return amount;
    }

    /**
     * Get all the categories that can be used.
     * @return List of all the categories.
     */
    @NotNull
    public List<Category> getCategories() {
        return this.registry.categories();
    }

    /**
     * Loads in all config options.
     */
    public synchronized void populateMaps() {
        this.registry = new Registry(this.registry.enchantments(), List.copyOf(buildCategories(rarities())));
    }

    /**
     * @return The vocabulary one load reads both files with.
     */
    public TuningConfig.Rarities rarities() {
        return new TuningConfig.Rarities(
                FileKeys.ENCHANTMENTS.getConfiguration().get(TuningConfig.RARITY_FORMAT),
                message -> this.fusion.log(Level.WARNING, message));
    }

    /**
     * @param rarities The vocabulary of this load, shared with the enchant
     *                 loader so the two can never disagree or warn twice.
     * @return The categories config.yml declares.
     */
    public List<Category> buildCategories(final TuningConfig.Rarities rarities) {
        final FileConfiguration config = FileKeys.CONFIG.getConfiguration();

        final ConfigurationSection section = config.getConfigurationSection("Categories");

        if (section == null) {
            this.fusion.log(Level.WARNING, "The categories section cannot be found in config.yml, It's possible the file is badly formatted!");

            return List.of();
        }

        final List<Category> categories = new ArrayList<>();
        for (String category : section.getKeys(false)) {
            String path = "Categories." + category;

            final String identity = rarities.category(category, path);

            // A name this Rarity-Format does not know was warned about; keeping
            // it would publish a tier no loader could resolve.
            if (identity == null) continue;

            LostBook lostBook = new LostBook(
                    config.getInt(path + ".LostBook.Slot", -1),
                    config.getBoolean(path + ".LostBook.InGUI", false),
                    new ItemBuilder()
                            .setMaterial(config.getString(path + ".LostBook.Item", "BOOK"))
                            .setItemModel(config.getString(path + ".LostBook.Model.Namespace", ""), config.getString(path + ".LostBook.Model.Key", ""))
                            .setPlayerName(config.getString(path + ".LostBook.Player", ""))
                            .setName(config.getString(path + ".LostBook.Name", "Error getting name."))
                            .setLore(config.getStringList(path + ".LostBook.Lore"))
                            .setGlow(config.getBoolean(path + ".LostBook.Glowing", true)),
                    config.getInt(path + ".LostBook.Cost"),
                    Currency.getCurrency(config.getString(path + ".LostBook.Currency")),
                    config.getBoolean(path + ".LostBook.FireworkToggle", false),
                    getColors(config.getString(path + ".LostBook.FireworkColors", "Red, White, Blue")),
                    config.getBoolean(path + ".LostBook.Sound-Toggle", false),
                    config.getString(path + ".LostBook.Sound", "BLOCK_ANVIL_PLACE"));

            categories.add(new Category(
                    category,
                    identity,
                    config.getInt(path + ".Slot"),
                    config.getBoolean(path + ".InGUI", true),
                    new ItemBuilder()
                            .setMaterial(config.getString(path + ".Item", ColorUtils.getRandomPaneColor().getName()))
                            .setItemModel(config.getString(path + ".Model.Namespace", ""), config.getString(path + ".Model.Key", ""))
                            .setPlayerName(config.getString(path + ".Player", ""))
                            .setName(config.getString(path + ".Name", "Error getting name."))
                            .setLore(config.getStringList(path + ".Lore"))
                            .setGlow(config.getBoolean(path + ".Glowing", false)),
                    config.getInt(path + ".Cost"),
                    Currency.getCurrency(config.getString(path + ".Currency")),
                    config.getInt(path + ".Rarity"),
                    lostBook,
                    config.getInt(path + ".EnchOptions.SuccessPercent.Max"),
                    config.getInt(path + ".EnchOptions.SuccessPercent.Min"),
                    config.getInt(path + ".EnchOptions.DestroyPercent.Max"),
                    config.getInt(path + ".EnchOptions.DestroyPercent.Min"),
                    config.getBoolean(path + ".EnchOptions.MaxLvlToggle"),
                    config.getInt(path + ".EnchOptions.LvlRange.Max"),
                    config.getInt(path + ".EnchOptions.LvlRange.Min")));
        }
        return categories;
    }

    /**
     * @param name The name of the category you want, in either the file's
     *             spelling or the canonical rarity name.
     * @return The category object.
     */
    @Nullable
    public Category getCategory(String name) {
        for (Category category : getCategories()) {
            if (category.getName().equalsIgnoreCase(name)) return category;
        }

        // The file's own spelling wins, so a persisted category name such as a
        // lost book's "Legendary" still means the tier it was written for.
        for (Category category : getCategories()) {
            if (category.getIdentity().equalsIgnoreCase(name)) return category;
        }

        return null;
    }

    private List<Color> getColors(String string) {
        List<Color> colors = new ArrayList<>();
        ColorUtils.color(colors, string);

        return colors;
    }

    /**
     * @param item Item you are getting the level from.
     * @param enchant The enchantment you want the level from.
     * @return The level the enchantment has.
     */
    public int getLevel(@NotNull final ItemStack item, @NotNull final CEnchantment enchant) {
        final String data = item.getPersistentDataContainer().get(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING);

        int level = data == null ? 0 : Enchant.fromJson(data).getLevel(enchant.getName());

        if (!useUnsafeEnchantments() && level > enchant.getMaxLevel()) level = enchant.getMaxLevel();

        return level;
    }

    /**
     * G9: the poison enchant used to display as "Sniper" and now shows as
     * "Poison Shot". Items written before the display rename still carry the
     * old label, so removal matches both spellings for that enchant only. This
     * is a single rename exception, not a general alias system.
     */
    public static boolean matchesLore(final Component line, final CEnchantment enchant, final Enchant stored) {
        return matchesLore(ColorUtils.toPlainText(line), enchant.getName(), ColorUtils.stripStringColour(enchant.getCustomName()), stored);
    }

    /**
     * The colour-free body of {@link #matchesLore(Component, CEnchantment, Enchant)},
     * so the rename rules can be pinned without a CEnchantment.
     *
     * @param plain The line's text with colour codes removed.
     * @param enchantName The enchant's registry name.
     * @param strippedCustomName The enchant's display name with colour codes removed.
     * @param stored The item's stored enchantments, already migrated.
     * @return True when the line belongs to the enchant.
     */
    static boolean matchesLore(final String plain, final String enchantName, final String strippedCustomName, final Enchant stored) {
        // The headshot's display name is also the poison enchant's label before
        // the display rename, so both enchants' lines read "Sniper <level>". When
        // the item stores poison and not the headshot, such a line is the poison
        // line and the headshot must not claim it.
        if (SNIPER.equals(enchantName) && stored.hasEnchantment(POISON_SHOT) && !stored.hasEnchantment(SNIPER)) return false;

        if (LoreLines.isEnchantLine(plain, strippedCustomName)) return true;

        return POISON_SHOT.equals(enchantName) && isLegacyPoisonLine(plain, stored.getLevel(POISON_SHOT));
    }

    private static boolean isLegacyPoisonLine(final String plain, final Integer storedLevel) {
        return storedLevel != null && plain.equals(SNIPER + " " + NumberUtils.convertLevelString(storedLevel));
    }

    private boolean rewriteLegacyPoisonLore(final List<Component> lore, final Enchant stored) {
        final Integer level = stored.getLevel(POISON_SHOT);
        if (level == null) return false;

        // "Sniper <level>" is the headshot enchant's own line today. When that
        // enchant is on the item the line is its, so rewriting it would trade
        // the headshot's line for a duplicate poison one.
        if (stored.hasEnchantment(SNIPER)) return false;

        final CEnchantment poison = getRegisteredEnchantment(POISON_SHOT);
        if (poison == null) return false;

        boolean changed = false;
        for (int i = 0; i < lore.size(); i++) {
            if (!isLegacyPoisonLine(ColorUtils.toPlainText(lore.get(i)), level)) continue;
            lore.set(i, ColorUtils.legacyTranslateColourCodes(poison.getCustomName() + " " + NumberUtils.convertLevelString(level)));
            changed = true;
        }
        return changed;
    }

    /**
     * @param itemStack Item you want to remove the enchantment from.
     * @param enchant Enchantment you want removed.
     * @return Item without the enchantment.
     */
    public @NotNull ItemStack removeEnchantment(@NotNull final ItemStack itemStack, @NotNull final CEnchantment enchant) {
        removeEnchantments(itemStack, List.of(enchant));
        return itemStack;
    }

    public void removeEnchantments(@NotNull ItemStack itemStack, @NotNull List<CEnchantment> enchants) {
        final PersistentDataContainerView view = itemStack.getPersistentDataContainer();

        final Enchant data;

        if (view.has(DataKeys.enchantments.getNamespacedKey())) {
            data = Enchant.fromJson(view.get(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING));
        } else {
            data = new Enchant(new HashMap<>());
        }

        final List<Component> lore = itemStack.lore();
        boolean loreChanged = false;
        for (final CEnchantment enchant : enchants) {
            if (lore != null) loreChanged |= LoreLines.removeFirst(lore, line -> matchesLore(line, enchant, data));
            data.removeEnchantment(enchant.getName());
        }
        if (lore != null && (rewriteLegacyPoisonLore(lore, data) || loreChanged)) {
            itemStack.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(lore).build());
        }

        if (data.isEmpty() && view.has(DataKeys.enchantments.getNamespacedKey())) {
            itemStack.editPersistentDataContainer(container -> {
                container.remove(DataKeys.enchantments.getNamespacedKey());
            });
        } else {
            itemStack.editPersistentDataContainer(container -> {
                container.set(DataKeys.enchantments.getNamespacedKey(), PersistentDataType.STRING, Methods.getGson().toJson(data));
            });
        }
    }
}
