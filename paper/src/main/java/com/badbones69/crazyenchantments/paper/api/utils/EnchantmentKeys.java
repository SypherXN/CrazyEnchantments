package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the Sniper rename out of a live Enchantments.yml without writing it.
 *
 * <p>The headshot enchant took the poison enchant's old name, so one block key
 * means opposite things in the two vocabularies: in a pre-rename file
 * {@code Sniper} is the poison enchant and {@code SniperHeadshot} the headshot;
 * today {@code Sniper} is the headshot and {@code Poison Shot} the poison. A
 * server keeps its own Enchantments.yml across the update, so the loader has to
 * decide which vocabulary a file uses before the registry or the tuning reads
 * it. {@link #normalize} answers that once, for both readers, by returning the
 * pair under today's names in memory. The file on disk is never touched.</p>
 *
 * <p>A file that already uses today's pair is returned as-is, so it costs
 * nothing. A file that cannot be resolved is not guessed at: the ambiguous
 * {@code Sniper} block is left out with one warning, which keeps the old poison
 * settings from silently becoming the headshot's.</p>
 */
public final class EnchantmentKeys {

    /** The headshot enchant's name; also the poison enchant's pre-rename label. */
    static final String HEADSHOT = "Sniper";

    /** The poison enchant's name. */
    static final String POISON = "Poison Shot";

    /** The headshot enchant's pre-rename block key. */
    static final String OLD_HEADSHOT = "SniperHeadshot";

    private EnchantmentKeys() {}

    public static boolean needsSniperKeyReview(final FileConfiguration file) {
        final ConfigurationSection section = file.getConfigurationSection("Enchantments");

        return section != null && (section.isConfigurationSection(OLD_HEADSHOT)
                || (section.isConfigurationSection(HEADSHOT) && !section.isConfigurationSection(POISON)));
    }

    /**
     * @param live The file as loaded, never modified.
     * @param warn Receives at most one message, and none for a file that
     *             already uses today's keys.
     * @return {@code live} when nothing needs renaming, otherwise a copy of its
     *         {@code Enchantments} section under today's names.
     */
    public static FileConfiguration normalize(final FileConfiguration live, final Consumer<String> warn) {
        final ConfigurationSection section = live.getConfigurationSection("Enchantments");

        if (section == null || !needsSniperKeyReview(live)) return live;

        final boolean todayPoison = section.isConfigurationSection(POISON);
        final boolean oldHeadshot = section.isConfigurationSection(OLD_HEADSHOT);
        final boolean sniper = section.isConfigurationSection(HEADSHOT);

        // Block key in the file -> the enchant it describes; null drops a block
        // that cannot be attributed to either enchant.
        final Map<String, String> renames = new LinkedHashMap<>();

        if (oldHeadshot) {
            renames.put(OLD_HEADSHOT, HEADSHOT);

            if (todayPoison && sniper) {
                // Both vocabularies at once. "Poison Shot" is the poison block
                // and "SniperHeadshot" the headshot block whatever happened, but
                // "Sniper" could be the old poison block or the renamed headshot
                // block. It is the one key that cannot be trusted, so it is not
                // read at all rather than guessed.
                renames.put(HEADSHOT, null);

                warn.accept("Enchantments.yml has 'Sniper', 'SniperHeadshot' and 'Poison Shot' at the same time, so the 'Sniper'"
                        + " block is ambiguous and was ignored. Reading 'Poison Shot' as the poison enchant and 'SniperHeadshot'"
                        + " as the Sniper (headshot) enchant. Delete the stray 'Sniper' block, then rename 'SniperHeadshot' to"
                        + " 'Sniper' to finish the rename.");
            } else if (todayPoison) {
                // The poison half is already renamed, the headshot half is not.
                warn.accept("Enchantments.yml has 'Poison Shot' and the old 'SniperHeadshot' block: reading 'Poison Shot' as the"
                        + " poison enchant and 'SniperHeadshot' as the Sniper (headshot) enchant. Rename 'SniperHeadshot' to"
                        + " 'Sniper' to match the shipped file.");
            } else if (sniper) {
                // The complete pre-rename pair: "Sniper" is the poison enchant.
                renames.put(HEADSHOT, POISON);

                warn.accept("Enchantments.yml still uses the pre-rename Sniper pair: the 'Sniper' block is being read as the"
                        + " Poison Shot enchant and the 'SniperHeadshot' block as the Sniper (headshot) enchant. Both keep their"
                        + " settings; the file itself is not changed. Rename those two block keys to 'Poison Shot' and 'Sniper'"
                        + " to match the shipped file.");
            } else {
                warn.accept("Enchantments.yml has no 'Sniper' block: the Sniper (headshot) enchant is being read from its old"
                        + " 'SniperHeadshot' block. Rename that block to 'Sniper' to match the shipped file.");
            }
        } else {
            // Only a "Sniper" block, with neither other key of the pair. It is
            // either the old poison block (headshot block removed) or today's
            // headshot block (poison block removed). Reading it as the headshot
            // would hand the old poison settings to the headshot enchant, so
            // neither enchant is loaded until the file says which it is.
            renames.put(HEADSHOT, null);

            warn.accept("Enchantments.yml has a 'Sniper' block but neither 'SniperHeadshot' nor 'Poison Shot', so it cannot be"
                    + " told whether that block is the old poison enchant or the Sniper (headshot) enchant. Neither Sniper nor"
                    + " Poison Shot was loaded and stored items are untouched. Add or restore the missing block: 'Sniper' is the"
                    + " headshot and 'Poison Shot' the poison, as in the shipped Enchantments.yml.");
        }

        // A deep copy: every destination section is created here and only leaf
        // values are copied in. Setting a live ConfigurationSection into the copy
        // would share it (and its parent/path), so a write through the copy would
        // land in the live file's data.
        final YamlConfiguration normalized = new YamlConfiguration();
        final String prefix = "Enchantments.";

        for (final Map.Entry<String, Object> entry : live.getValues(true).entrySet()) {
            String path = entry.getKey();

            if (path.equals("Enchantments")) {
                normalized.createSection(path);

                continue;
            }

            if (path.startsWith(prefix)) {
                final String rest = path.substring(prefix.length());
                final int dot = rest.indexOf('.');
                final String top = dot < 0 ? rest : rest.substring(0, dot);
                final String rename = renames.containsKey(top) ? renames.get(top) : top;

                if (rename == null) continue;

                path = prefix + rename + rest.substring(top.length());
            }

            final Object value = entry.getValue();

            if (value instanceof ConfigurationSection child) {
                // Keep an empty block; non-empty ones are rebuilt from their leaves.
                if (child.getKeys(false).isEmpty()) normalized.createSection(path);
            } else if (value instanceof List<?> list) {
                normalized.set(path, new ArrayList<>(list));
            } else {
                normalized.set(path, value);
            }
        }

        return normalized;
    }
}
