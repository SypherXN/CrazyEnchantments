package com.badbones69.crazyenchantments.paper.api.utils;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transmog scroll is the only thing that sorts an item, and it sorts by the
 * category rarity the config declares. Ordinary enchant updates and lines that
 * merely mention an enchant's name must not move.
 */
class TransmogLoreTest {

    private static final String SUFFIX = " &7[&6&n%amount%&7]";

    @Test
    void onlyAnEnchantsOwnLineMatches() {
        assertTrue(LoreLines.isEnchantLine("Heavy III", "Heavy"));
        assertTrue(LoreLines.isEnchantLine("  Frost XXI ", "Frost"));
        assertTrue(LoreLines.isEnchantLine("Cleave 12", "Cleave"));

        // Prose that mentions the name, or the bare name, is not the enchant's line.
        assertFalse(LoreLines.isEnchantLine("Heavy", "Heavy"));
        assertFalse(LoreLines.isEnchantLine("A true heavy hitter", "Heavy"));
        assertFalse(LoreLines.isEnchantLine("Heavy Protection", "Heavy"));
        assertFalse(LoreLines.isEnchantLine("HeavyIII", "Heavy"));
        assertFalse(LoreLines.isEnchantLine("Heavily", "Heavy"));
    }

    @Test
    void anUpgradeStaysWhereItWas() {
        final List<Component> lore = lines("Normal one", "Heavy II", "Frost III", "Normal two");

        final boolean replaced = LoreLines.replaceFirst(lore, match("Heavy"), Component.text("Heavy III"));

        assertTrue(replaced);
        assertEquals(List.of("Normal one", "Heavy III", "Frost III", "Normal two"), plain(lore));
    }

    @Test
    void aNewEnchantJoinsTheEnchantBlock() {
        final List<Component> lore = lines("Normal one", "Heavy II", "Frost III", "Normal two");

        LoreLines.insertAfterLastEnchant(lore, match("Heavy", "Frost"), List.of(Component.text("Poison I")));

        assertEquals(List.of("Normal one", "Heavy II", "Frost III", "Poison I", "Normal two"), plain(lore));
    }

    @Test
    void aNewEnchantGoesLastWithoutAnEnchantBlock() {
        final List<Component> lore = lines("Normal one", "Normal two");

        LoreLines.insertAfterLastEnchant(lore, match("Heavy"), List.of(Component.text("Heavy I")));

        assertEquals(List.of("Normal one", "Normal two", "Heavy I"), plain(lore));
    }

    @Test
    void missingLinesAreReportedSoTheyCanBeAppended() {
        assertFalse(LoreLines.replaceFirst(lines("Normal one"), match("Heavy"), Component.text("Heavy I")));
    }

    @Test
    void shippedRaritiesPutLegendaryAboveTheRest() throws IOException {
        final Map<String, Integer> rarity = shippedRarities();

        final List<String> names = new ArrayList<>(rarity.keySet());
        Collections.reverse(names);

        assertEquals(List.of("Legendary", "Ultimate", "Elite", "Unique", "Simple"),
                LoreLines.highestRarityFirst(names, rarity));
    }

    @Test
    void equalRaritiesKeepTheirOrder() {
        final Map<String, Integer> rarity = Map.of("Alpha", 2, "Beta", 2, "Gamma", 1);

        assertEquals(List.of("Alpha", "Beta", "Gamma"),
                LoreLines.highestRarityFirst(List.of("Alpha", "Beta", "Gamma"), rarity));
    }

    @Test
    void everySectionSurvivesATypoInTheConfig() {
        assertEquals(List.of("Normal_Lore", "CE_Enchantments", "Protection"),
                LoreLines.sectionOrder(List.of("Normal_Lore", "Bogus", "Normal_Lore")));

        assertEquals(LoreLines.SECTIONS, LoreLines.sectionOrder(List.of()));
    }

    @Test
    void shippedLoreOrderIsTheDefault() throws IOException {
        assertEquals(LoreLines.SECTIONS, readConfig("Settings", "TransmogScroll", "Lore-Order"));
    }

    @Test
    void theSuffixRendersTheSameWhateverCodeStyleTheConfigUses() {
        // A '§' or upper-case hex config must match a name stored with '&' codes.
        assertEquals(LoreLines.renderedSuffix(SUFFIX, "3"), LoreLines.renderedSuffix(" §7[§6§n%Amount%§7]", "3"));
        assertEquals(LoreLines.renderedSuffix(" &#ff0000+%amount%", "2"), LoreLines.renderedSuffix(" &#FF0000+%Amount%", "2"));
    }

    @Test
    void theCountIsReplacedNotStacked() {
        final String base = "&bDiamond Sword";
        final String once = base + LoreLines.renderedSuffix(SUFFIX, "3");

        assertEquals(base, LoreLines.withoutSuffix(once, SUFFIX));

        final String twice = LoreLines.withoutSuffix(once, SUFFIX) + LoreLines.renderedSuffix(SUFFIX, "4");

        assertEquals(base + LoreLines.renderedSuffix(SUFFIX, "4"), twice);
        assertEquals(base, LoreLines.withoutSuffix(twice, SUFFIX));
    }

    @Test
    void aNameAlreadyShowingTheCountNeedsNoChange() {
        final String base = "&bDiamond Sword";
        final String name = base + LoreLines.renderedSuffix(SUFFIX, "5");

        // This is the no-op the scroll relies on: rebuild yields the same name.
        assertEquals(name, LoreLines.withoutSuffix(name, SUFFIX) + LoreLines.renderedSuffix(SUFFIX, "5"));

        assertNotEquals(name, LoreLines.withoutSuffix(name, SUFFIX) + LoreLines.renderedSuffix(SUFFIX, "6"));
    }

    @Test
    void aConfiguredStyleStillRecognisesAStoredSuffix() {
        final String stored = "&bDiamond Sword" + LoreLines.renderedSuffix(SUFFIX, "7");

        assertEquals("&bDiamond Sword", LoreLines.withoutSuffix(stored, " §7[§6§n%amount%§7]"));
    }

    @Test
    void aTemplateEndingInTheAmountIsStillStrippedOnce() {
        // "10" must not be read as the "0" suffix, so the count is replaced, not stacked.
        final String template = " &7(%amount%)";
        final String base = "&bDiamond Sword";
        final String name = base + LoreLines.renderedSuffix(template, "10");

        assertEquals(base, LoreLines.withoutSuffix(name, template));
        assertEquals(name, LoreLines.withoutSuffix(name, template) + LoreLines.renderedSuffix(template, "10"));
    }

    @Test
    void protectionMarkersMatchOnlyTheWholeLine() {
        assertTrue(LoreLines.isMarkerLine("PROTECTED", "PROTECTED"));
        assertTrue(LoreLines.isMarkerLine("  Ancient Protection ", "Ancient Protection"));

        assertFalse(LoreLines.isMarkerLine("Ancient Protection keeps this item safe", "Ancient Protection"));
        assertFalse(LoreLines.isMarkerLine("PROTECTED-ish", "PROTECTED"));
    }

    private static Predicate<Component> match(final String... names) {
        return line -> {
            final String plain = ColorUtils.toPlainText(line);

            for (final String name : names) {
                if (LoreLines.isEnchantLine(plain, name)) return true;
            }

            return false;
        };
    }

    private static List<Component> lines(final String... text) {
        final List<Component> lore = new ArrayList<>();

        for (final String line : text) lore.add(Component.text(line));

        return lore;
    }

    private static List<String> plain(final List<Component> lore) {
        return lore.stream().map(ColorUtils::toPlainText).toList();
    }

    private static Map<String, Integer> shippedRarities() throws IOException {
        final Map<String, Integer> rarity = new LinkedHashMap<>();
        final Map<?, ?> categories = (Map<?, ?>) readConfig("Categories");

        categories.forEach((name, section) ->
                rarity.put(String.valueOf(name), (Integer) ((Map<?, ?>) section).get("Rarity")));

        return rarity;
    }

    private static Object readConfig(final String... path) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        final Path relative = Path.of("src/main/resources/config.yml");

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        Object node = new Yaml().load(Files.readString(root.resolve(relative)));

        for (final String key : path) node = ((Map<?, ?>) node).get(key);

        return node;
    }
}
