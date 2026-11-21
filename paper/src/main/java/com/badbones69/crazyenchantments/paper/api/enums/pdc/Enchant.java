package com.badbones69.crazyenchantments.paper.api.enums.pdc;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Enchant {

    private static final Gson gson = new Gson();

    private final HashMap<String, Integer> enchants;

    /**
     * The enchant-name vocabulary of this map. Gson fills it from the payload
     * and leaves it 0 when the payload has none, which is data written before
     * the Sniper rename. It is deliberately not initialised at the field: this
     * class must never gain a no-args constructor, or Gson would hand old data
     * the current version and read the old poison "Sniper" as the headshot.
     */
    private int version;

    public Enchant(HashMap<String, Integer> enchants) {
        this.enchants = enchants == null ? new HashMap<>() : enchants;
        this.version = EnchantRenames.CURRENT_VERSION;
    }

    /**
     * Reads stored enchant data, translating a payload written before the
     * Sniper rename once. See {@link EnchantRenames}: a payload without a
     * version holds the old names, and a translated map only becomes permanent
     * when it is written back, because writing it also writes the version.
     *
     * @param json The raw {@code DataKeys.enchantments} value, or null.
     * @return The map under today's names, or null when there is no data.
     */
    public static Enchant fromJson(final String json) {
        final Enchant data = gson.fromJson(json, Enchant.class);

        if (data == null) return null;
        if (data.enchants == null) return new Enchant(new HashMap<>());
        if (data.version >= EnchantRenames.CURRENT_VERSION) return data;

        final Map<String, Integer> migrated = new HashMap<>();

        // One pass over the stored keys: no rename may feed another. Merging by
        // max keeps a level instead of silently dropping it if a hand-edited
        // payload already holds both spellings.
        data.enchants.forEach((name, level) -> migrated.merge(EnchantRenames.current(name), level, Math::max));

        data.enchants.clear();
        data.enchants.putAll(migrated);
        data.version = EnchantRenames.CURRENT_VERSION;

        return data;
    }

    /**
     *
     * @param enchantment The enchantment you want to check for.
     * @return true if the item has the specified enchantment.
     */
    public boolean hasEnchantment(String enchantment) {
        return this.enchants.containsKey(enchantment);
    }

    /**
     *
     * @return Hashmap of all enchantments and their corresponding levels.
     */
    public HashMap<String, Integer> getFullEnchantments() {
        return this.enchants;
    }

    /**
     *
     * @return Set of enchantments that are on the item.
     */
    public Set<String> getEnchantments() {
        return this.enchants.keySet();
    }

    /**
     *
     * @param enchantment The enchantment want the level for.
     * @return The level or null if the enchantment is not on the item.
     */
    public Integer getLevel(String enchantment) {
        return this.enchants.get(enchantment);
    }

    /**
     *
     * @param enchantment The enchantment you want to add.
     */
    public void addEnchantments(Map<String, Integer> enchantment) {
        this.enchants.putAll(enchantment);
    }

    /**
     *
     * @param enchantment The enchantment you want to add.
     * @param level The level of the enchantment.
     */
    public void addEnchantment(String enchantment, Integer level) {
        this.enchants.put(enchantment, level);
    }

    /**
     *
     * @param enchantment The enchantment you want to remove.
     */
    public void removeEnchantment(String enchantment) {
        this.enchants.remove(enchantment);
    }

    /**
     *
     * @return true if there are no enchantments on the item.
     */
    public boolean isEmpty() {
        return this.enchants.isEmpty();
    }
}