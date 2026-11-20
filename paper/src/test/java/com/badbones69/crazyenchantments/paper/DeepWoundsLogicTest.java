package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.DeepWoundsSettings;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepWoundsLogicTest {

    private static final DeepWoundsSettings SETTINGS = DeepWoundsSettings.DEFAULTS;

    private static final Path JAVA = Path.of("src/main/java/com/badbones69/crazyenchantments/paper");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void bleedChanceIsBoostedToTenTwelveFourteen() {
        assertEquals(10, SETTINGS.boostedChance(8, 1));
        assertEquals(12, SETTINGS.boostedChance(8, 2));
        assertEquals(14, SETTINGS.boostedChance(8, 3));

        assertEquals(10, (int) (8 * SETTINGS.chanceMultiplier(8, 1)));
        assertEquals(12, (int) (8 * SETTINGS.chanceMultiplier(8, 2)));
        assertEquals(14, (int) (8 * SETTINGS.chanceMultiplier(8, 3)));
    }

    @Test
    void boostedChanceIsCappedAtOneHundred() {
        assertEquals(100, SETTINGS.boostedChance(90, 3));
        assertEquals(100, SETTINGS.boostedChance(100, 1));
        assertEquals(100, (int) (90 * SETTINGS.chanceMultiplier(90, 3)));
        assertEquals(100, (int) (100 * SETTINGS.chanceMultiplier(100, 3)));
    }

    @Test
    void levelZeroKeepsTheBaseChance() {
        assertEquals(1.0, SETTINGS.factor(0), 1.0e-9);
        assertEquals(8, SETTINGS.boostedChance(8, 0));
        assertEquals(1.0, SETTINGS.chanceMultiplier(8, 0));
    }

    @Test
    void anAbsentEnchantKeepsTheBaseChance() {
        assertEquals(SETTINGS.boostedChance(8, 0), SETTINGS.boostedChance(8, -1));
        assertEquals(8, SETTINGS.boostedChance(8, -1));
    }

    @Test
    void aChanceOfZeroStaysAlwaysOn() {
        assertEquals(0, SETTINGS.boostedChance(0, 1));
        assertEquals(0, SETTINGS.boostedChance(0, 3));
        assertEquals(1.0, SETTINGS.chanceMultiplier(0, 3));
    }

    @Test
    void invalidFactorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DeepWoundsSettings(List.of(1.25)));
        assertThrows(IllegalArgumentException.class,
                () -> new DeepWoundsSettings(List.of(1.25, 1.50, Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> new DeepWoundsSettings(List.of(1.25, 1.50, 0.5)));
    }

    @Test
    void deepWoundsHasNoChanceSystemAndMatchesTheRegistry() throws IOException {
        var entry = enchant("Deep Wounds");

        assertFalse(entry.containsKey("Chance-System"), "Deep Wounds is always-on");
        assertEquals(true, entry.get("Enabled"));
        assertEquals("&7Deep Wounds", entry.get("Name"));
        assertEquals(3, entry.get("MaxPower"));
        assertEquals("Axe", entry.get("Enchantment-Type"));
        assertEquals(List.of("Unique"), entry.get("Categories"));

        assertTrue(source("api/enums/CEnchantments.java").contains("DEEP_WOUNDS(\"Deep Wounds\", \"Axe\"),"),
                "the enum entry carries no chance values");
    }

    @Test
    void enumAndYamlAgree() throws IOException {
        var entry = enchant("Deep Wounds");
        var enumEntry = "DEEP_WOUNDS(\"Deep Wounds\", \"" + entry.get("Enchantment-Type") + "\")";

        assertTrue(source("api/enums/CEnchantments.java").contains(enumEntry));
        assertFalse(entry.containsKey("Chance-System"));
        assertEquals(List.of("Unique"), entry.get("Categories"));
    }

    @Test
    void blessedCleansesPotionSlownessWithoutTouchingTheBleedAttribute() throws IOException {
        var cleanse = between(source("enchantments/AxeEnchantments.java"),
                "private void removeBadPotions(", "\n    }\n");

        assertInOrder(cleanse, "PotionEffectType.SLOWNESS", "bad.forEach(player::removePotionEffect)");
        assertFalse(cleanse.contains("BleedSystem.") || cleanse.contains("getAttribute("));
    }

    @Test
    void bleedProcPathUsesOneBoostedNormalRoll() throws IOException {
        var proc = between(source("enchantments/AxeEnchantments.java"),
                "private void onBleedDamage(", "public void onPlayerDeath(");

        assertInOrder(proc, "CEnchantments.DEEP_WOUNDS", "EnchantUtils.isEventActive(CEnchantments.BLEED",
                "settings.chanceMultiplier(baseChance, deepWoundsLevel)", "BleedSystem.apply(");
        assertEquals(1, proc.split("BleedSystem\\.apply\\(", -1).length - 1, "no fallback may bypass the permission or veto");
    }

    private static Map<?, ?> enchant(String name) throws IOException {
        return section(section(yaml("Enchantments.yml"), "Enchantments"), name);
    }

    private static Map<?, ?> yaml(String name) throws IOException {
        return new Yaml().load(Files.readString(RESOURCES.resolve(name)));
    }

    private static Map<?, ?> section(Map<?, ?> map, Object key) {
        return assertInstanceOf(Map.class, map.get(key));
    }

    private static String source(String file) throws IOException {
        return Files.readString(JAVA.resolve(file)).replace("\r\n", "\n");
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to >= 0, end);
        return source.substring(from, to);
    }

    private static void assertInOrder(String source, String... snippets) {
        int offset = 0;
        for (String snippet : snippets) {
            int found = source.indexOf(snippet, offset);
            assertTrue(found >= 0, snippet);
            offset = found + snippet.length();
        }
    }
}
