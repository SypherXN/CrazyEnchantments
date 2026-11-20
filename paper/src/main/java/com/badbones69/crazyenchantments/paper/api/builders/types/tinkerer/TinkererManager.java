package com.badbones69.crazyenchantments.paper.api.builders.types.tinkerer;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.economy.Currency;
import com.badbones69.crazyenchantments.paper.api.economy.CurrencyAPI;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.objects.CEBook;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public class TinkererManager {

    private static CrazyEnchantments plugin() {
        return JavaPlugin.getPlugin(CrazyEnchantments.class);
    }

    private static Starter starter() {
        return plugin().getStarter();
    }

    private static Methods methods() {
        return starter().getMethods();
    }

    private static CurrencyAPI currencyAPI() {
        return starter().getCurrencyAPI();
    }

    public static boolean useExperience(Player player, PlayerInteractEvent event, boolean mainHand, FileConfiguration configuration) {
        PlayerInventory inventory = player.getInventory();

        ItemStack item = mainHand ? inventory.getItemInMainHand() : inventory.getItemInOffHand();

        if (item.isEmpty()) return false;

        final PersistentDataContainerView container = item.getPersistentDataContainer();

        if (!container.has(DataKeys.experience.getNamespacedKey())) return false;

        int amount = Integer.parseInt(container.getOrDefault(DataKeys.experience.getNamespacedKey(), PersistentDataType.STRING, "0"));

        event.setCancelled(true);

        if (mainHand) {
            inventory.setItemInMainHand(methods().removeItem(item));
        } else {
            inventory.setItemInOffHand(methods().removeItem(item));
        }

        if (Currency.isCurrency(configuration.getString("Settings.Currency"))) {
            currencyAPI().giveCurrency(player, Currency.getCurrency(configuration.getString("Settings.Currency")), amount);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

        return true;
    }

    /**
     * @param amount Amount of XP to store.
     * @return XP Bottle with custom amount of xp stored in it.
     */
    public static ItemStack getXPBottle(String amount, final FileConfiguration config) {
        String id = config.getString("Settings.BottleOptions.Item", "EXPERIENCE_BOTTLE");
        String name = config.getString("Settings.BottleOptions.Name", "");
        List<String> lore = new ArrayList<>();

        for (String l : config.getStringList("Settings.BottleOptions.Lore")) {
            lore.add(l.replace("%Total%", amount).replace("%total%", amount));
        }

        return new ItemBuilder().setMaterial(id).setName(name).setItemModel(config.getString("Settings.BottleOptions.Model.Namespace", ""), config.getString("Settings.BottleOptions.Model.Key", "")).setLore(lore).addKey(DataKeys.experience.getNamespacedKey(), amount).build();
    }

    public static int getTotalXP(ItemStack item, final FileConfiguration config) {
        int total = 0;

        Map<CEnchantment, Integer> ceEnchants = starter().getEnchantmentBookSettings().getEnchantments(item);

        if (!ceEnchants.isEmpty()) { // CrazyEnchantments
            for (Map.Entry<CEnchantment, Integer> enchantment : ceEnchants.entrySet()) {
                String[] values = config.getString("Tinker.Crazy-Enchantments." + enchantment.getKey().getName() + ".Items", "0").replaceAll(" ", "").split(",");
                int baseAmount = Integer.parseInt(values[0]);
                int multiplier = values.length < 2 ? 0 : Integer.parseInt(values[1]);
                int enchantmentLevel = enchantment.getValue();

                total += baseAmount + enchantmentLevel * multiplier;
            }
        }

        //todo() test data component usage here
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) { // Vanilla Enchantments
            for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
                String[] values = config.getString("Tinker.Vanilla-Enchantments." + convertToLegacy(enchantment.getKey().getKey().value()).toUpperCase(), "0").replaceAll(" ", "").split(",");
                int baseAmount = Integer.parseInt(values[0]); // TODO add converter to convert legacy to new enchant names.
                int multiplier = values.length < 2 ? 0 : Integer.parseInt(values[1]);
                int enchantmentLevel = enchantment.getValue();
                total += baseAmount + enchantmentLevel * multiplier;
            }
        }

        return total;
    }

    private static String convertLegacy(String from) { // Stolen from Enchantment class -TDL
        if (from == null) {
            return null;
        }

        return switch (from.toLowerCase()) {
            case "protection_environmental" -> "protection";
            case "protection_fire" -> "fire_protection";
            case "protection_fall" -> "feather_falling";
            case "protection_explosions" -> "blast_protection";
            case "protection_projectile" -> "projectile_protection";
            case "oxygen" -> "respiration";
            case "water_worker" -> "aqua_affinity";
            case "damage_all" -> "sharpness";
            case "damage_undead" -> "smite";
            case "damage_arthropods" -> "bane_of_arthropods";
            case "loot_bonus_mobs" -> "looting";
            case "sweeping_edge" -> "sweeping";
            case "dig_speed" -> "efficiency";
            case "durability" -> "unbreaking";
            case "loot_bonus_blocks" -> "fortune";
            case "arrow_damage" -> "power";
            case "arrow_knockback" -> "punch";
            case "arrow_fire" -> "flame";
            case "arrow_infinite" -> "infinity";
            case "luck" -> "luck_of_the_sea";
            default -> from;
        };

    }

    private static String convertToLegacy(String from) { // Stolen inverse of the above method. -TDL
        if (from == null) {
            return null;
        }

        return switch (from.toLowerCase()) {
            case "protection" -> "protection_environmental";
            case "fire_protection" -> "protection_fire";
            case  "feather_falling" -> "protection_fall";
            case  "blast_protection" -> "protection_explosions";
            case  "projectile_protection" -> "protection_projectile";
            case  "respiration" -> "oxygen";
            case  "aqua_affinity" -> "water_worker";
            case  "sharpness" -> "damage_all";
            case  "smite" -> "damage_undead";
            case  "bane_of_arthropods" -> "damage_arthropods";
            case  "looting" -> "loot_bonus_mobs";
            case  "sweeping" -> "sweeping_edge";
            case  "efficiency" -> "dig_speed";
            case  "unbreaking" -> "durability";
            case  "fortune" -> "loot_bonus_blocks";
            case  "power" -> "arrow_damage";
            case  "punch" -> "arrow_knockback";
            case  "flame" -> "arrow_fire";
            case  "infinity" -> "arrow_infinite";
            case  "luck_of_the_sea" -> "luck";
            default -> from;
        };

    }

    /**
     * Path of the optional rarity-to-potency-cap overrides in Tinker.yml. Each
     * key is a canonical rarity identity and each value the highest percent the
     * Mystery Dust from a book of that rarity may roll (0..50). An explicit
     * entry always wins; a missing or invalid entry scales by XP-point price.
     */
    private static final String TIER_CAP_PATH = "Settings.Tier-Potency-Cap";

    /** Potency is a percent; the dust consumption path clamps rates at 100. */
    private static final int MAX_DUST_PERCENT = 100;

    /**
     * The highest potency a tinkered book's Mystery Dust may roll. The price
     * scale is relative to this value: the most expensive XP-point tier always
     * caps here and cheaper tiers cap proportionally lower, down to zero.
     */
    private static final int MAX_PRICE_DUST = 50;

    public static int getMaxDustLevelFromBook(CEBook book, FileConfiguration config) {
        final List<String> identities = new ArrayList<>();
        for (final Category category : book.getEnchantment().getCategories()) identities.add(category.getIdentity());

        // The rarity is resolved before any value is read, so a multi-category
        // book cannot borrow the highest tier's dust.
        return chooseTinkerDustCap(resolvedRarity(identities),
                getTierCaps(config),
                getPriceDustCaps(starter().getEnchantmentBookSettings().getCategories()),
                () -> getLegacyMaxDustLevelFromBook(book, config, MAX_DUST_PERCENT));
    }

    /**
     * Caps each canonical tier by its share of the highest XP-point shop price:
     * {@code round(50 * price / highest)}. The shipped 400/800/2500/5000/25000
     * prices therefore yield 1/2/5/10/50. Only XP-total prices take part: XP
     * levels and Vault money are different units and never set the scale, and
     * a tier without a positive XP-total price is left to the legacy formula.
     */
    static Map<String, Integer> getPriceDustCaps(final List<Category> categories) {
        final Map<String, Integer> prices = new HashMap<>();

        if (categories != null) {
            for (final Category category : categories) {
                if (category.getCurrency() != Currency.XP_TOTAL) continue;
                if (category.getCost() <= 0) continue;
                if (!TuningConfig.RARITIES.contains(category.getIdentity())) continue;

                prices.merge(category.getIdentity(), category.getCost(), Math::max);
            }
        }

        return capsFromPrices(prices);
    }

    /**
     * Turns one price per rarity into its 0..50 potency cap. Half-up rounding
     * keeps the requested endpoints: an equal-to-highest price caps at 50 and a
     * price too small to round up caps at 0, which is a legal no-boost roll.
     */
    static Map<String, Integer> capsFromPrices(final Map<String, Integer> prices) {
        final Map<String, Integer> caps = new HashMap<>();

        if (prices == null) return caps;

        long highest = 0;

        for (final Integer price : prices.values()) {
            if (price != null && price > highest) highest = price;
        }

        if (highest <= 0) return caps;

        for (final Map.Entry<String, Integer> entry : prices.entrySet()) {
            final Integer price = entry.getValue();

            if (price == null || price <= 0) continue;

            final long cap = (MAX_PRICE_DUST * (long) price + highest / 2) / highest;

            caps.put(entry.getKey(), (int) Math.max(0, Math.min(MAX_PRICE_DUST, cap)));
        }

        return caps;
    }

    /**
     * The rarity a book's enchantment resolves to: the single canonical tier it
     * was registered under, or null when its categories are ambiguous. The
     * highest category is deliberately never used, so a book listed in several
     * tiers cannot cash in the top tier's dust.
     */
    static String resolvedRarity(final List<String> categoryIdentities) {
        return categoryIdentities != null && categoryIdentities.size() == 1 ? categoryIdentities.get(0) : null;
    }

    /**
     * An explicit tier override wins, including an explicit 0. Without one the
     * rarity's XP-point price sets the cap; failing that the legacy per-book
     * formula is clamped to the same 0..50 ceiling so no fallback path can hand
     * out dust stronger than the price policy allows.
     */
    static int chooseTinkerDustCap(final String rarity, final Map<String, Integer> tierCaps,
                                   final Map<String, Integer> priceCaps, final IntSupplier legacyCap) {
        final Integer tierCap = rarity == null ? null : tierCaps.get(rarity);

        if (tierCap != null) return tierCap;

        final Integer priceCap = rarity == null ? null : priceCaps.get(rarity);

        if (priceCap != null) return priceCap;

        return Math.min(MAX_PRICE_DUST, Math.max(0, legacyCap.getAsInt()));
    }

    /**
     * The value of one configured override, or null when it is not a whole
     * number in 0..50. Null means "ignored", so a malformed or out-of-range
     * entry falls through to the price cap instead of being coerced into an
     * explicit zero. Fractional values are rejected rather than truncated.
     */
    static Integer parseTierCap(final Object raw) {
        if (!(raw instanceof Number number)) return null;

        final double value = number.doubleValue();

        if (!Double.isFinite(value) || value != Math.rint(value)) return null;
        if (value < 0 || value > MAX_PRICE_DUST) return null;

        return (int) value;
    }

    /**
     * Only usable overrides are kept, so a missing key and an invalid one both
     * mean "no explicit cap" rather than "cap at zero".
     */
    private static Map<String, Integer> getTierCaps(final FileConfiguration config) {
        final Map<String, Integer> caps = new HashMap<>();

        final ConfigurationSection section = config.getConfigurationSection(TIER_CAP_PATH);
        if (section == null) return caps;

        for (final String tier : section.getKeys(false)) {
            final Integer cap = parseTierCap(section.get(tier));

            if (cap != null) caps.put(tier, cap);
        }

        return caps;
    }

    private static int getLegacyMaxDustLevelFromBook(CEBook book, FileConfiguration config, final int ceiling) {
        String path = "Tinker.Crazy-Enchantments." + book.getEnchantment().getName() + ".Book";
        if (!config.contains(path)) return 1;

        return parseLegacyDustCap(config.getString(path, "0"), book.getLevel(), ceiling);
    }

    /**
     * The old per-book formula, kept in long arithmetic. A delimiter-only or
     * otherwise malformed value, a result outside the supported range, or an
     * overflowing result falls back to the minimum instead of throwing or
     * handing the dust an unusable potency.
     */
    static int parseLegacyDustCap(final String raw, final int level, final int ceiling) {
        if (raw == null) return 1;

        String[] values = raw.replaceAll(" ", "").split(",", -1);

        if (values.length > 2) return 1;

        try {
            long baseAmount = Long.parseLong(values[0]);
            long multiplier = values.length < 2 ? 0L : Long.parseLong(values[1]);
            long result = Math.addExact(baseAmount, Math.multiplyExact(multiplier, (long) level));

            return result >= 1 && result <= ceiling ? (int) result : 1;
        } catch (final NumberFormatException | ArithmeticException e) {
            return 1;
        }
    }

    public static Map<Integer, Integer> getSlots() {
        final Map<Integer, Integer> slots = new HashMap<>(23);

        slots.put(1, 5);
        slots.put(2, 6);
        slots.put(3, 7);
        slots.put(9, 14);
        slots.put(10, 15);
        slots.put(11, 16);
        slots.put(12, 17);
        slots.put(18, 23);
        slots.put(19, 24);
        slots.put(20, 25);
        slots.put(21, 26);
        slots.put(27, 32);
        slots.put(28, 33);
        slots.put(29, 34);
        slots.put(30, 35);
        slots.put(36, 41);
        slots.put(37, 42);
        slots.put(38, 43);
        slots.put(39, 44);
        slots.put(45, 50);
        slots.put(46, 51);
        slots.put(47, 52);
        slots.put(48, 53);

        return slots;
    }
}
