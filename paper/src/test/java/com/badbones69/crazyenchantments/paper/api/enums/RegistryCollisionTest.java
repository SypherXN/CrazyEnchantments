package com.badbones69.crazyenchantments.paper.api.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the registry against duplicate enchant keys and display names. Reads
 * the shipped sources so it stays honest without loading Bukkit.
 */
class RegistryCollisionTest {

    private static final Pattern YAML_KEY = Pattern.compile("(?m)^ {2}([A-Za-z0-9_-][A-Za-z0-9_ -]*):(?:\\s*#.*)?\\s*$");
    private static final Pattern ENUM_ENTRY = Pattern.compile("(?m)^ {4}([A-Z][A-Z0-9_]*)\\(\"([^\"]+)\", \"([^\"]+)\"(?:, \\d+, \\d+)?\\)\\s*[,;]?\\s*$");

    @Test
    void enchantKeysAndDisplayNamesStayUnique() throws IOException {
        final String yaml = read("src/main/resources/Enchantments.yml");
        final String registry = read(enumSource());
        final Map<String, Integer> yamlKeys = count(YAML_KEY, yaml, 1);
        final Map<String, Integer> enumNames = count(ENUM_ENTRY, registry, 2);

        assertEquals(1, yamlKeys.getOrDefault("bleed", 0).intValue(), "Enchantments.yml must define Bleed exactly once");
        assertEquals(1, enumNames.getOrDefault("bleed", 0).intValue(), "CEnchantments must define Bleed exactly once");

        yamlKeys.forEach((key, count) -> assertEquals(1, count.intValue(), "duplicate Enchantments.yml key: " + key));
        enumNames.forEach((name, count) -> assertEquals(1, count.intValue(), "duplicate enchant display name: " + name));
        assertEquals(ENUM_ENTRY.matcher(registry).results().map(match -> match.group(2)).collect(Collectors.toSet()),
                YAML_KEY.matcher(yaml).results().map(match -> match.group(1)).collect(Collectors.toSet()));
    }

    @Test
    void armorEquipLookupResolvesDisplayNamesWithSpaces() throws IOException {
        final String armorSource = read("src/main/java/com/badbones69/crazyenchantments/paper/enchantments/ArmorEnchantments.java");
        final String registrySource = read(enumSource());

        assertTrue(registrySource.contains("public static CEnchantments fromName("), "CEnchantments must expose a display-name lookup");
        assertTrue(armorSource.contains("CEnchantments.fromName("), "ArmorEnchantments must resolve equipped enchants by display name");
        assertTrue(!armorSource.contains("CEnchantments.valueOf("), "valueOf() cannot resolve display names such as \"Creeper Armor\"");
    }

    @Test
    void bleedIsRegisteredAsAnAxeEnchantWithTheApprovedChance() throws IOException {
        final String source = read(enumSource());

        assertTrue(source.contains("BLEED(\"Bleed\", \"Axe\", 8, 2)"), "Bleed must keep the approved 8/+2 chance");

        final Matcher matcher = ENUM_ENTRY.matcher(source);

        boolean found = false;

        while (matcher.find()) {
            if (!matcher.group(2).equals("Bleed")) continue;

            found = true;
            assertEquals("Axe", matcher.group(3));
        }

        assertTrue(found, "Bleed enum entry is missing");
    }

    @Test
    void bloodLustUsesTheApprovedChanceCurve() throws IOException {
        final String source = read(enumSource());
        final String yaml = read("src/main/resources/Enchantments.yml");

        assertTrue(source.contains("BLOODLUST(\"Blood Lust\", \"Chestplate\", 5, 3)"), "Blood Lust must keep the approved 5/+3 chance and the chestplate-only type");

        final Matcher chance = Pattern
                .compile("(?ms)^  Blood Lust:\\s*$.*?^    Chance-System:\\s*$\\s*Base: (\\d+)\\s*Increase: (\\d+)")
                .matcher(yaml);

        assertTrue(chance.find(), "Blood Lust must declare Base/Increase");

        final int base = Integer.parseInt(chance.group(1));
        final int increase = Integer.parseInt(chance.group(2));

        assertEquals(5, base);
        assertEquals(3, increase);
        assertEquals(20, base + increase * (6 - 1), "5% at level I and +3% per level must be 20% at level VI");
    }

    private static Map<String, Integer> count(final Pattern pattern, final String text, final int group) {
        final Map<String, Integer> counts = new HashMap<>();
        final Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            counts.merge(matcher.group(group).toLowerCase(Locale.ROOT), 1, Integer::sum);
        }

        return counts;
    }

    private static String enumSource() {
        return "src/main/java/com/badbones69/crazyenchantments/paper/api/enums/CEnchantments.java";
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative));
    }
}
