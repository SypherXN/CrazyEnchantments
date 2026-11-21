package com.badbones69.crazyenchantments.paper.api.enums.pdc;

import com.google.gson.Gson;

public class EnchantedBook {

    private static final Gson gson = new Gson();

    private final String name;
    private int success;
    private int destroy;
    private int level;

    /** See {@link Enchant#fromJson}; 0 is a book written before the Sniper rename. */
    private int version;

    public EnchantedBook(String enchantmentName, int success, int destroy, int level) {
        this.name = enchantmentName;
        this.success = success;
        this.destroy = destroy;
        this.level = level;
        this.version = EnchantRenames.CURRENT_VERSION;
    }

    /**
     * Reads a book's stored data, translating a book written before the Sniper
     * rename once. A book stores the enchant by name, so without this the old
     * headshot book would resolve to no enchantment at all and the old poison
     * book would apply the headshot enchant.
     *
     * @param json The raw {@code DataKeys.stored_enchantments} value, or null.
     * @return The book under today's names, or null when there is no data.
     */
    public static EnchantedBook fromJson(final String json) {
        final EnchantedBook data = gson.fromJson(json, EnchantedBook.class);

        if (data == null) return null;
        if (data.version >= EnchantRenames.CURRENT_VERSION) return data;

        return new EnchantedBook(EnchantRenames.current(data.name), data.success, data.destroy, data.level);
    }

    public int getDestroyChance() {
        return this.destroy;
    }
    public void setDestroyChance(int newChance) {
        this.destroy = newChance;
    }

    public int getSuccessChance() {
        return this.success;
    }
    public void setSuccessChance(int newChance) {
        this.success = newChance;
    }

    public String getName() {
        return this.name;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int newLevel) {
        this.level = newLevel;
    }
}