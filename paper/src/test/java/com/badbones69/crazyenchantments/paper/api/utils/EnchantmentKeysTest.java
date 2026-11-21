package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A live server keeps its own Enchantments.yml across the Sniper swap, so the
 * old pair must be read under today's names in memory, both blocks keeping
 * their own settings, and an ambiguous file must not hand the old poison
 * settings to the headshot. The file itself is never rewritten.
 */
class EnchantmentKeysTest {

    private static final List<Double> MULTIPLIERS = List.of(1.5, 2.0, 2.5, 3.0, 3.5);

    @Test
    void todayPairIsReadLiterallyAndNotCopied() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.Sniper.Name", "&7Sniper");
        live.set("Enchantments.Sniper.MaxPower", 5);
        live.set("Enchantments.Sniper.Tuning.Multipliers", MULTIPLIERS);
        live.set("Enchantments.Poison Shot.Name", "&7Poison Shot");
        live.set("Enchantments.Poison Shot.Chance-System.Base", 25);

        final List<String> warnings = new ArrayList<>();

        assertFalse(EnchantmentKeys.needsSniperKeyReview(live), "repair can use the current keys");
        assertSame(live, EnchantmentKeys.normalize(live, warnings::add), "a current file needs no copy");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void theShippedFileNeedsNoNormalisation() throws IOException {
        final FileConfiguration shipped = YamlConfiguration.loadConfiguration(resource("src/main/resources/Enchantments.yml").toFile());
        final List<String> warnings = new ArrayList<>();

        assertSame(shipped, EnchantmentKeys.normalize(shipped, warnings::add));
        assertTrue(warnings.isEmpty(), "the shipped Enchantments.yml must already use today's keys: " + warnings);
    }

    @Test
    void theOldPairKeepsBothBlocksOnTheirOwnEnchant() {
        // Parsed from text, the way the plugin loads a live file: nested lists
        // and multi-level blocks must survive the in-memory rename.
        final YamlConfiguration live = parse("""
                Enchantments:
                  Sniper:
                    Enabled: true
                    Rarity: 'Ultimate'
                    Name: '&7Poison Shot'
                    MaxPower: 2
                    Chance-System:
                      Base: 25
                      Increase: 5
                    Info:
                      Name: '&e&lPoison Shot &7(&bI-&bII&7)'
                      Description:
                        - '&7Arrow hits can inflict Poison II'
                    Categories:
                      - 'Elite'
                  SniperHeadshot:
                    Enabled: true
                    Rarity: 'Legendary'
                    Tuning:
                      Multipliers: [1.5, 2.0, 2.5, 3.0, 3.5]
                      Min-Draw-Force: 0.9
                    Name: '&7Sniper'
                    MaxPower: 5
                    Categories:
                      - 'Ultimate'
                  Bleed:
                    MaxPower: 3
                """);

        final List<String> warnings = new ArrayList<>();
        assertTrue(EnchantmentKeys.needsSniperKeyReview(live), "repair must not insert a new Poison Shot into the old pair");
        final FileConfiguration normalized = EnchantmentKeys.normalize(live, warnings::add);

        // The poison enchant keeps the settings of its own old block.
        assertEquals("&7Poison Shot", normalized.getString("Enchantments.Poison Shot.Name"));
        assertEquals(2, normalized.getInt("Enchantments.Poison Shot.MaxPower"));
        assertEquals(25, normalized.getInt("Enchantments.Poison Shot.Chance-System.Base"));
        assertEquals(5, normalized.getInt("Enchantments.Poison Shot.Chance-System.Increase"));
        assertEquals(List.of("Elite"), normalized.getStringList("Enchantments.Poison Shot.Categories"));
        assertEquals(List.of("&7Arrow hits can inflict Poison II"),
                normalized.getStringList("Enchantments.Poison Shot.Info.Description"));
        assertEquals("Ultimate", normalized.getString("Enchantments.Poison Shot.Rarity"));

        // The headshot keeps the settings of its own old block, not the poison's.
        assertEquals("&7Sniper", normalized.getString("Enchantments.Sniper.Name"));
        assertEquals(5, normalized.getInt("Enchantments.Sniper.MaxPower"));
        assertEquals(MULTIPLIERS, normalized.getDoubleList("Enchantments.Sniper.Tuning.Multipliers"));
        assertEquals(0.9, normalized.getDouble("Enchantments.Sniper.Tuning.Min-Draw-Force"), 1.0e-9);
        assertEquals(List.of("Ultimate"), normalized.getStringList("Enchantments.Sniper.Categories"));
        assertNull(normalized.get("Enchantments.Sniper.Chance-System"), "the headshot never goes through the poison roll");

        assertNull(normalized.get("Enchantments.SniperHeadshot"), "the old headshot key is gone from the view");
        assertEquals(3, normalized.getInt("Enchantments.Bleed.MaxPower"), "the rest of the file comes along");

        // The live file is untouched.
        assertEquals("&7Poison Shot", live.getString("Enchantments.Sniper.Name"));
        assertEquals(2, live.getInt("Enchantments.Sniper.MaxPower"));
        assertNotNull(live.get("Enchantments.SniperHeadshot"));

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("SniperHeadshot"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Poison Shot"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("not changed"), warnings.getFirst());
    }

    @Test
    void theOldPairFeedsTuningThroughTheHeadshotBlock() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.Sniper.Name", "&7Poison Shot");
        live.set("Enchantments.Sniper.Chance-System.Base", 25);
        live.set("Enchantments.SniperHeadshot.Tuning.Multipliers", MULTIPLIERS);

        final FileConfiguration normalized = EnchantmentKeys.normalize(live, message -> {});

        final List<String> tuningWarnings = new ArrayList<>();
        final TuningConfig.Source source = new TuningConfig.Source(path -> null, path -> false,
                normalized::get, normalized::contains, tuningWarnings::add);

        // TuningConfig reads the headshot's numbers under today's key.
        assertEquals(MULTIPLIERS, source.getDoubleList("CosmicEnchants.Sniper.Multipliers"));
        assertTrue(tuningWarnings.isEmpty(), tuningWarnings.toString());
    }

    @Test
    void normalisingThePairLeavesOtherLegacySectionsAlone() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.Sniper.Name", "&7Poison Shot");
        live.set("Enchantments.SniperHeadshot.Name", "&7Sniper");
        live.set("Enchantments.Rocket Escape.Tuning.Velocity", List.of(4.5, 5.0, 5.5));

        final FileConfiguration normalized = EnchantmentKeys.normalize(live, message -> {});

        final List<String> warnings = new ArrayList<>();
        final TuningConfig.Source source = new TuningConfig.Source(path -> null, path -> false,
                normalized::get, normalized::contains, warnings::add);

        // Rocket Escape still adopts its CosmicEnchants section through the copy.
        assertEquals(List.of(4.5, 5.0, 5.5), source.getDoubleList("CosmicEnchants.RocketEscape.Velocity"));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void anUnpairedSniperBlockIsNotReadAsTheHeadshot() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.Sniper.Name", "&7Poison Shot");
        live.set("Enchantments.Sniper.MaxPower", 2);
        live.set("Enchantments.Sniper.Chance-System.Base", 25);
        live.set("Enchantments.Bleed.MaxPower", 3);

        final List<String> warnings = new ArrayList<>();
        final FileConfiguration normalized = EnchantmentKeys.normalize(live, warnings::add);

        assertNull(normalized.get("Enchantments.Sniper"), "the old poison settings must not become the headshot");
        assertNull(normalized.get("Enchantments.Poison Shot"), "and they must not be guessed as poison either");
        assertEquals(3, normalized.getInt("Enchantments.Bleed.MaxPower"), "the rest of the file still loads");

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("neither"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Poison Shot"), warnings.getFirst());
    }

    @Test
    void aMixedFileKeepsTheTwoUnambiguousBlocksAndDropsSniper() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.Sniper.Name", "&7Poison Shot"); // stray copy of the old poison block
        live.set("Enchantments.Sniper.MaxPower", 9);
        live.set("Enchantments.SniperHeadshot.Name", "&7Sniper");
        live.set("Enchantments.SniperHeadshot.MaxPower", 5);
        live.set("Enchantments.Poison Shot.Name", "&7Poison Shot");
        live.set("Enchantments.Poison Shot.MaxPower", 2);
        live.set("Enchantments.Poison Shot.Chance-System.Base", 25);

        final List<String> warnings = new ArrayList<>();
        final FileConfiguration normalized = EnchantmentKeys.normalize(live, warnings::add);

        assertEquals(2, normalized.getInt("Enchantments.Poison Shot.MaxPower"), "the new poison block wins");
        assertEquals(25, normalized.getInt("Enchantments.Poison Shot.Chance-System.Base"));
        assertEquals(5, normalized.getInt("Enchantments.Sniper.MaxPower"), "the headshot comes from its old block");
        assertEquals("&7Sniper", normalized.getString("Enchantments.Sniper.Name"));
        assertNull(normalized.get("Enchantments.SniperHeadshot"));

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("ambiguous"), warnings.getFirst());
    }

    @Test
    void theCopySharesNothingWithTheLiveFileAndKeepsTheRootKeys() {
        final YamlConfiguration live = parse("""
                Rarity-Format: 2
                Enchantments:
                  Sniper:
                    MaxPower: 2
                    Chance-System:
                      Base: 25
                    Categories:
                      - 'Elite'
                  SniperHeadshot:
                    MaxPower: 5
                    Tuning: {}
                """);

        final FileConfiguration normalized = EnchantmentKeys.normalize(live, message -> {});

        // Root keys outside Enchantments survive.
        assertEquals(2, normalized.get("Rarity-Format"));

        // The renamed section is a real section of the copy, at its own path.
        final ConfigurationSection poison = normalized.getConfigurationSection("Enchantments.Poison Shot");
        assertNotNull(poison);
        assertEquals("Enchantments.Poison Shot", poison.getCurrentPath());
        assertSame(normalized, poison.getRoot(), "the section belongs to the copy, not the live file");
        assertEquals("Enchantments.Sniper", normalized.getConfigurationSection("Enchantments.Sniper").getCurrentPath());
        assertNotNull(normalized.getConfigurationSection("Enchantments.Sniper.Tuning"), "an empty block is kept");

        // Writing through the copy never reaches the live data.
        poison.set("MaxPower", 99);
        poison.set("Chance-System.Base", 1);
        normalized.set("Rarity-Format", 1);

        assertEquals(2, live.getInt("Enchantments.Sniper.MaxPower"));
        assertEquals(25, live.getInt("Enchantments.Sniper.Chance-System.Base"));
        assertEquals(List.of("Elite"), live.getStringList("Enchantments.Sniper.Categories"));
        assertEquals(2, live.get("Rarity-Format"));
        assertNull(live.get("Enchantments.Poison Shot"), "the live file gains no renamed key");
    }

    @Test
    void aRenamedPoisonWithTheOldHeadshotKeyIsRead() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.Poison Shot.Name", "&7Poison Shot");
        live.set("Enchantments.Poison Shot.MaxPower", 2);
        live.set("Enchantments.SniperHeadshot.Name", "&7Sniper");
        live.set("Enchantments.SniperHeadshot.MaxPower", 5);

        final List<String> warnings = new ArrayList<>();
        final FileConfiguration normalized = EnchantmentKeys.normalize(live, warnings::add);

        assertEquals(2, normalized.getInt("Enchantments.Poison Shot.MaxPower"));
        assertEquals(5, normalized.getInt("Enchantments.Sniper.MaxPower"));
        assertNull(normalized.get("Enchantments.SniperHeadshot"));

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("SniperHeadshot"), warnings.getFirst());
    }

    @Test
    void aHeadshotThatOnlyHasItsOldKeyIsStillRead() {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("Enchantments.SniperHeadshot.Name", "&7Sniper");
        live.set("Enchantments.SniperHeadshot.MaxPower", 5);

        final List<String> warnings = new ArrayList<>();
        final FileConfiguration normalized = EnchantmentKeys.normalize(live, warnings::add);

        assertEquals("&7Sniper", normalized.getString("Enchantments.Sniper.Name"));
        assertEquals(5, normalized.getInt("Enchantments.Sniper.MaxPower"));
        assertNull(normalized.get("Enchantments.SniperHeadshot"));
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.getFirst().contains("Rename"), warnings.getFirst());
    }

    /** The resource as a path, from the repo root or from paper/. */
    private static Path resource(final String relative) {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return root.resolve(relative);
    }

    private static YamlConfiguration parse(final String yaml) {
        final YamlConfiguration config = new YamlConfiguration();

        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException exception) {
            throw new AssertionError(exception);
        }

        return config;
    }
}
