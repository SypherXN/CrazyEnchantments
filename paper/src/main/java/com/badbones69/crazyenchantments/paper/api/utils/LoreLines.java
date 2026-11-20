package com.badbones69.crazyenchantments.paper.api.utils;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Server-free helpers shared by the lore writers: recognising an enchant's own
 * line, keeping the order an item already carries and applying the transmog
 * scroll's configured sections.
 */
public final class LoreLines {

    /** A level as {@link NumberUtils#convertLevelString(int)} writes it. */
    private static final Pattern LEVEL = Pattern.compile("[IVXLCDM]+|\\d+");

    /** The transmog lore sections, in their shipped order. */
    public static final List<String> SECTIONS = List.of("CE_Enchantments", "Protection", "Normal_Lore");

    private LoreLines() {}

    /**
     * True when the plain lore line is the enchant's own line: its name followed
     * by a level. A line of prose that merely mentions the name (e.g. "a heavy
     * hitter") is not an enchant line, so it is never rewritten.
     *
     * @param plain The line's text with colour codes removed.
     * @param strippedName The enchant's custom name with colour codes removed.
     * @return True when the line belongs to the enchant.
     */
    public static boolean isEnchantLine(final String plain, final String strippedName) {
        final String line = plain.trim();
        final String name = strippedName.trim();

        if (!line.startsWith(name + " ")) return false;

        return LEVEL.matcher(line.substring(name.length() + 1).trim()).matches();
    }

    /**
     * The configured lore order, with any known section the config left out
     * appended, so a typo in {@code Lore-Order} cannot delete that lore. Unknown
     * names are dropped and duplicates keep their first position.
     *
     * @param configured The list read from the scroll's config.
     * @return Every section, configured order first.
     */
    public static List<String> sectionOrder(final List<String> configured) {
        final List<String> order = new ArrayList<>();

        for (final String section : configured) {
            if (SECTIONS.contains(section) && !order.contains(section)) order.add(section);
        }

        for (final String section : SECTIONS) {
            if (!order.contains(section)) order.add(section);
        }

        return order;
    }

    /**
     * Orders the enchant names highest rarity first. Names of equal rarity keep
     * their existing order, so the scroll never shuffles two enchants of a tier.
     *
     * @param names The names in their current order.
     * @param rarity The rarity of each name; unknown names rank last.
     * @return A new list, highest first.
     */
    public static List<String> highestRarityFirst(final List<String> names, final Map<String, Integer> rarity) {
        final List<String> ordered = new ArrayList<>(names);

        ordered.sort((first, second) -> Integer.compare(
                rarity.getOrDefault(second, Integer.MIN_VALUE),
                rarity.getOrDefault(first, Integer.MIN_VALUE)));

        return ordered;
    }

    /**
     * The scroll's count suffix, filled in and normalised through the same
     * serializer an item name uses. A config written with {@code §} codes or
     * upper-case hex therefore matches a name stored with {@code &} and
     * lower-case hex, so a repeat use can recognise its own suffix.
     *
     * @param template The configured suffix, e.g. {@code " &7[&6&n%amount%&7]"}.
     * @param amount The count to show, already formatted.
     * @return The suffix as it renders.
     */
    public static String renderedSuffix(final String template, final String amount) {
        return ColorUtils.toLegacy(ColorUtils.legacyTranslateColourCodes(
                template.replace("%Amount%", amount).replace("%amount%", amount)));
    }

    /**
     * The name without a count this scroll added before, so a repeat use replaces
     * the count instead of stacking a second suffix on the name.
     *
     * @param name The current name, in legacy form.
     * @param template The configured suffix template.
     * @return The name with any previous count removed.
     */
    public static String withoutSuffix(final String name, final String template) {
        // Highest first, so a count of 10 is not mistaken for a count of 0.
        for (int amount = 100; amount >= 0; amount--) {
            final String suffix = renderedSuffix(template, String.valueOf(amount));

            if (!name.endsWith(suffix)) continue;

            return name.substring(0, name.length() - suffix.length());
        }

        return name;
    }

    /**
     * True when the plain line is exactly the expected text, surrounding spaces
     * aside. Used for marker lines such as the protection lore, so a line of
     * prose that merely mentions the marker is kept.
     *
     * @param plain The line's text with colour codes removed.
     * @param expected The marker's text with colour codes removed.
     * @return True when the line is the marker.
     */
    public static boolean isMarkerLine(final String plain, final String expected) {
        return plain.trim().equals(expected.trim());
    }

    /**
     * Puts a line where the enchant's existing line was, so upgrading an enchant
     * does not move it down the lore.
     *
     * @param lore The lines to edit in place.
     * @param match Finds the enchant's line.
     * @param replacement The freshly rendered line.
     * @return True when a line was replaced, false when the enchant had none.
     */
    public static boolean replaceFirst(final List<Component> lore, final Predicate<Component> match, final Component replacement) {
        for (int index = 0; index < lore.size(); index++) {
            if (!match.test(lore.get(index))) continue;

            lore.set(index, replacement);

            return true;
        }

        return false;
    }

    /**
     * Inserts lines directly after the last enchant line, so a new enchant joins
     * the enchant block instead of landing below unrelated lore. With no enchant
     * line present the lines go at the end.
     *
     * @param lore The lines to edit in place.
     * @param isEnchantLine Tells an enchant's line from normal lore.
     * @param lines The lines to insert, in the order they should show.
     */
    public static void insertAfterLastEnchant(final List<Component> lore, final Predicate<Component> isEnchantLine, final List<Component> lines) {
        if (lines.isEmpty()) return;

        int index = lore.size();

        for (int position = lore.size() - 1; position >= 0; position--) {
            if (!isEnchantLine.test(lore.get(position))) continue;

            index = position + 1;

            break;
        }

        lore.addAll(index, lines);
    }
}
