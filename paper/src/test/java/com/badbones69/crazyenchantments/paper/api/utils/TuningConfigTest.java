package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-enchant tuning values live in Enchantments.yml under
 * {@code <enchant>.Tuning}. These tests pin the section-to-key map, the
 * shipped data, the legacy fallback and the optional Rarity override.
 */
class TuningConfigTest {

    private static final double EPS = 1e-9;
    private static final Set<String> FORK_KEYS = Set.of(
            "Acid Rain", "Bee Keeper", "Blizzard", "Crouch", "Hulk", "Infestation", "Intimidate", "Nursery",
            "Radiant", "Sand Storm", "Tamer", "Battle Cry", "Cursed", "Rekt", "Tree Feller", "Boom", "Pull",
            "Multi Arrow", "Sticky Shot", "Vein Miner", "Green Thumb", "Charge",
            "Famished", "Fast Turn", "Revenge");

    /** The map is hand-written on purpose: a rename that breaks it must fail here, not at runtime. */
    private static Map<String, String> expected() {
        final Map<String, String> expected = new LinkedHashMap<>();

        expected.put("Bleed", "Bleed");
        expected.put("DeepWounds", "Deep Wounds");
        expected.put("Devour", "Devour");
        expected.put("BloodLust", "Blood Lust");
        expected.put("Armored", "Armored");
        expected.put("Heavy", "Heavy");
        expected.put("Tank", "Tank");
        expected.put("Marksman", "Marksman");
        expected.put("Clarity", "Clarity");
        expected.put("ArrowDeflect", "Arrow Deflect");
        expected.put("EnderWalker", "Ender Walker");
        expected.put("Assassin", "Assassin");
        expected.put("Enrage", "Enrage");
        expected.put("Block", "Block");
        expected.put("ThunderingBlow", "Thundering Blow");
        expected.put("Pummel", "Pummel");
        expected.put("Cleave", "Cleave");
        expected.put("Blacksmith", "Blacksmith");
        expected.put("AntiGank", "Anti Gank");
        expected.put("Barbarian", "Barbarian");
        expected.put("Insanity", "Insanity");
        expected.put("Rogue", "Rogue");
        expected.put("DoubleStrike", "Double Strike");
        expected.put("Inversion", "Inversion");
        expected.put("Disintegrate", "Disintegrate");
        expected.put("Greatsword", "Greatsword");
        expected.put("Unfocus", "Unfocus");
        expected.put("ArrowLifesteal", "Arrow Lifesteal");
        expected.put("Pacify", "Pacify");
        expected.put("Longbow", "Longbow");
        expected.put("EagleEye", "Eagle Eye");
        expected.put("Infernal", "Infernal");
        expected.put("Explosive", "Explosive");
        expected.put("Hellfire", "Hellfire");
        expected.put("Sniper", "SniperHeadshot");
        expected.put("Virus", "Virus");
        expected.put("Teleportation", "Teleportation");
        expected.put("Farcast", "Farcast");
        expected.put("Aegis", "Aegis");
        expected.put("CreeperArmor", "Creeper Armor");
        expected.put("Hardened", "Hardened");
        expected.put("Ragdoll", "Ragdoll");
        expected.put("Metaphysical", "Metaphysical");
        expected.put("Angelic", "Angelic");
        expected.put("Spirits", "Spirits");
        expected.put("SpiritLink", "Spirit Link");
        expected.put("Lifebloom", "Lifebloom");
        expected.put("Curse", "Curse");
        expected.put("Destruction", "Destruction");
        expected.put("PlagueCarrier", "Plague Carrier");
        expected.put("FeignDeath", "Feign Death");
        expected.put("AutoSell", "Auto Sell");
        expected.put("Skilling", "Skilling");
        expected.put("Training", "Training");
        expected.put("Valor", "Valor");
        expected.put("Implants", "Implants");
        expected.put("RocketEscape", "Rocket Escape");
        expected.put("EnderShift", "Ender Shift");
        expected.put("Vampire", "Vampire");
        expected.put("Execute", "Execute");
        expected.put("Wither", "Wither");
        expected.put("DemonForged", "Demon Forged");

        return expected;
    }

    @Test
    void sectionMapIsOneToOneAndMatchesTheShippedEnchantKeys() throws IOException {
        final Map<String, String> map = TuningConfig.sectionKeys();

        assertEquals(expected(), map, "the legacy section to Enchantments.yml key map");
        assertEquals(map.size(), new HashSet<>(map.values()).size(), "two sections must never share a key");

        final Map<String, Object> enchantments = enchantments();
        final Set<String> blocksWithTuning = new HashSet<>();

        for (final Object key : enchantments.keySet()) {
            if (enchantments.get(key) instanceof Map<?, ?> block && block.get("Tuning") != null) blocksWithTuning.add(String.valueOf(key));
        }

        final Set<String> expectedBlocks = new HashSet<>(map.values());
        expectedBlocks.addAll(FORK_KEYS);
        assertEquals(expectedBlocks, blocksWithTuning, "every mapped key ships exactly one Tuning block");

        for (final String key : expectedBlocks) {
            assertTrue(enchantments.containsKey(key), "Enchantments.yml is missing " + key);
            final Map<?, ?> block = assertInstanceOf(Map.class, enchantments.get(key), key);
            assertFalse(assertInstanceOf(Map.class, block.get("Tuning"), key).isEmpty(), key + ".Tuning must not be empty");
        }

        // KillAura shipped as an empty config.yml block, so it needs no Tuning block.
        assertFalse(map.containsValue("Kill Aura"));
        assertFalse(assertInstanceOf(Map.class, enchantments.get("Kill Aura"), "Kill Aura").containsKey("Tuning"));
    }

    @Test
    void forkSettingsMatchShippedValuesAndReadEveryKeyThroughSource() throws IOException {
        final Map<String, Object> moved = new HashMap<>();
        flatten("Enchantments.", enchantments(), moved);
        final Set<String> shippedKeys = new HashSet<>();
        moved.forEach((path, value) -> {
            if (FORK_KEYS.stream().anyMatch(enchant -> path.startsWith("Enchantments." + enchant + ".Tuning."))) {
                shippedKeys.add(path);
            }
        });
        final List<String> warnings = new ArrayList<>();
        final var source = new TuningConfig.Source(path -> null, path -> false, moved::get, moved::containsKey, warnings::add);
        final ForkEnchantSettings settings = ForkEnchantSettings.load(path -> {
            assertTrue(shippedKeys.remove(path), "missing or repeated fork key: " + path);
            return source.get(path);
        }, warnings::add);

        assertTrue(shippedKeys.isEmpty(), shippedKeys.toString());
        assertEquals(ForkEnchantSettings.DEFAULTS, settings);
        assertEquals(0.25, settings.crouch().reduction(3), EPS);
        assertEquals(2, settings.auras().get("Blizzard").amplifierAt(3));
        assertEquals(1, settings.auras().get("Acid Rain").amplifierAt(3));
        assertEquals(240, settings.cursed().durationTicks(3));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void forkSettingsAcceptOverridesAndWarnForInvalidValues() {
        final Map<String, Object> moved = Map.of(
                "Enchantments.Boom.Tuning.Damage-HP", 7.5,
                "Enchantments.Blizzard.Tuning.Radius", 4.0,
                "Enchantments.Vein Miner.Tuning.Max-Blocks", 14,
                "Enchantments.Hulk.Tuning.Duration-Ticks", 240,
                "Enchantments.Nursery.Tuning.Interval-Ticks", 1.5,
                "Enchantments.Famished.Tuning.Amplifier", "invalid",
                "Enchantments.Radiant.Tuning.Duration-Ticks", Double.POSITIVE_INFINITY);
        final List<String> warnings = new ArrayList<>();
        final var source = new TuningConfig.Source(path -> 99, path -> true, moved::get, moved::containsKey, warnings::add);
        final ForkEnchantSettings settings = ForkEnchantSettings.load(source::get, warnings::add);

        assertEquals(7.5, settings.boom().damageHp(), EPS);
        assertEquals(4.0, settings.maxAuraRadius(), EPS);
        assertEquals(14, settings.veinMiner().maxBlocks());
        assertEquals(240, settings.hulkDurationTicks());
        assertEquals(ForkEnchantSettings.DEFAULTS.nursery(), settings.nursery());
        assertEquals(ForkEnchantSettings.DEFAULTS.famished(), settings.famished());
        assertEquals(ForkEnchantSettings.DEFAULTS.auras().get("Radiant"), settings.auras().get("Radiant"));
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().allMatch(warning -> warning.startsWith("Invalid Enchantments.")), warnings.toString());
    }

    @Test
    void everyLegacyTuningPathResolvesFromTheEnchantmentsFile() throws IOException {
        final Map<String, Object> legacy = new HashMap<>();
        final Map<String, Object> moved = new HashMap<>();
        final List<String> warnings = new ArrayList<>();
        flatten("Enchantments.", enchantments(), moved);

        final TuningConfig.Source source = new TuningConfig.Source(
                legacy::get, legacy::containsKey, moved::get, moved::containsKey, warnings::add);

        for (final Map.Entry<String, String> entry : expected().entrySet()) {
            final Map<?, ?> block = assertInstanceOf(Map.class, enchantments().get(entry.getValue()), entry.getValue());
            final Map<?, ?> tuning = assertInstanceOf(Map.class, block.get("Tuning"), entry.getValue());

            for (final Object sub : tuning.keySet()) {
                final String legacyPath = "CosmicEnchants." + entry.getKey() + "." + sub;
                final String movedPath = "Enchantments." + entry.getValue() + ".Tuning." + sub;

                assertTrue(moved.containsKey(movedPath), movedPath + " must be shipped");
                assertTrue(source.contains(legacyPath), legacyPath + " must resolve");
                assertEquals(moved.get(movedPath), source.get(legacyPath), legacyPath + " must read the Enchantments.yml value");
            }
        }

        assertTrue(warnings.isEmpty(), "a fully migrated server must not warn");
    }

    @Test
    void configYmlKeepsOnlyTheGlobalDamageModifiersAndNoMovedValue() throws IOException {
        final Map<?, ?> cosmic = assertInstanceOf(Map.class, yaml("config.yml").get("CosmicEnchants"), "CosmicEnchants");

        assertEquals(Set.of("DamageModifiers"), cosmic.keySet(), "only the global block stays in config.yml");

        final Map<?, ?> damageModifiers = assertInstanceOf(Map.class, cosmic.get("DamageModifiers"), "DamageModifiers");
        assertTrue(damageModifiers.containsKey("Incoming-Reduction-Cap-PP"));
        assertTrue(damageModifiers.containsKey("Bonus-Damage-Cap-HP"));

        for (final Map.Entry<String, String> entry : expected().entrySet()) {
            final Map<?, ?> tuning = assertInstanceOf(Map.class,
                    assertInstanceOf(Map.class, enchantments().get(entry.getValue())).get("Tuning"), entry.getValue());
            final Object legacyBlock = cosmic.get(entry.getKey());

            assertFalse(cosmic.containsKey(entry.getKey()), "CosmicEnchants." + entry.getKey() + " must be gone");

            for (final Object sub : tuning.keySet()) {
                assertFalse(legacyBlock instanceof Map<?, ?> movedDown && movedDown.containsKey(sub),
                        "CosmicEnchants." + entry.getKey() + "." + sub + " must be gone");
            }
        }
    }

    @Test
    void aLegacyValueIsStillReadWithExactlyOneWarning() {
        final Map<String, Object> legacy = new HashMap<>();
        legacy.put("CosmicEnchants.Bleed.Cadence-Seconds", 9.0);
        final Map<String, Object> moved = new HashMap<>();
        final List<String> warnings = new ArrayList<>();
        final TuningConfig.Source source = new TuningConfig.Source(
                legacy::get, legacy::containsKey, moved::get, moved::containsKey, warnings::add);

        assertEquals(9.0, source.getDouble("CosmicEnchants.Bleed.Cadence-Seconds", 1.0), EPS);
        assertEquals(9.0, source.getDouble("CosmicEnchants.Bleed.Cadence-Seconds", 1.0), EPS);

        assertEquals(1, warnings.size(), "one warning per deprecated key, however often it is read");
        assertTrue(warnings.getFirst().contains("CosmicEnchants.Bleed.Cadence-Seconds"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Enchantments.Bleed.Tuning.Cadence-Seconds"), warnings.getFirst());
    }

    @Test
    void theMovedValueWinsAndTheGlobalBlockStaysInConfigYml() {
        final Map<String, Object> legacy = new HashMap<>();
        legacy.put("CosmicEnchants.Bleed.Cadence-Seconds", 9.0);
        legacy.put("CosmicEnchants.DamageModifiers.Bonus-Damage-Cap-HP", 7.0);
        final Map<String, Object> moved = new HashMap<>();
        moved.put("Enchantments.Bleed.Tuning.Cadence-Seconds", 2.5);
        moved.put("Enchantments.SniperHeadshot.Tuning.Force-TTL-Seconds", 60.0);
        moved.put("Enchantments.Pummel.Tuning.Duration-Seconds", List.of(1.0, 2.0));
        moved.put("Enchantments.Blacksmith.Tuning.Repair-Points", List.of(1, 2));
        final List<String> warnings = new ArrayList<>();
        final TuningConfig.Source source = new TuningConfig.Source(
                legacy::get, legacy::containsKey, moved::get, moved::containsKey, warnings::add);

        assertEquals(2.5, source.getDouble("CosmicEnchants.Bleed.Cadence-Seconds", 1.0), EPS);
        assertEquals(7.0, source.getDouble("CosmicEnchants.DamageModifiers.Bonus-Damage-Cap-HP", 20.0), EPS);
        assertEquals(60.0, source.getDouble("CosmicEnchants.Sniper.Force-TTL-Seconds", 3.0), EPS);
        assertEquals(List.of(1.0, 2.0), source.getDoubleList("CosmicEnchants.Pummel.Duration-Seconds"));
        assertEquals(List.of(1, 2), source.getIntegerList("CosmicEnchants.Blacksmith.Repair-Points"));
        assertTrue(warnings.isEmpty(), "DamageModifiers is global and must never warn");
    }

    @Test
    void everyTrapSectionMapsToItsRenamedEnchant() {
        assertEquals("SniperHeadshot", TuningConfig.sectionKeys().get("Sniper"));
        assertEquals("Deep Wounds", TuningConfig.sectionKeys().get("DeepWounds"));
        assertEquals("Blood Lust", TuningConfig.sectionKeys().get("BloodLust"));
        assertEquals("Creeper Armor", TuningConfig.sectionKeys().get("CreeperArmor"));
        assertEquals("Anti Gank", TuningConfig.sectionKeys().get("AntiGank"));
    }

    @Test
    void legacyFormatMapsEveryOldNameToItsCosmicTier() {
        for (final Object absent : new Object[]{null, 1, "1"}) {
            final List<String> notice = new ArrayList<>();
            final TuningConfig.Rarities rarities = new TuningConfig.Rarities(absent, notice::add);

            assertEquals(TuningConfig.Rarities.LEGACY_FORMAT, rarities.format());
            assertEquals("Simple", rarities.rarity("Common", "Glowing"));
            assertEquals("Unique", rarities.rarity("rare", "Glowing"));
            assertEquals("Elite", rarities.rarity("Legendary", "Sticky"));
            assertEquals("Ultimate", rarities.rarity(" Ultimate ", "Aegis"));
            assertEquals("Legendary", rarities.rarity("Mythical", "Bleed"));
            assertNull(rarities.rarity(null, "Bleed"));

            assertEquals(1, notice.size(), "one legacy notice per load, whatever the spelling: " + notice);
            assertTrue(notice.getFirst().contains("Rarity-Format"), notice.getFirst());
        }

        // Format 1 is the old vocabulary only: a name invented by format 2 is
        // refused instead of guessed at, and Legendary keeps its old meaning.
        final List<String> strict = new ArrayList<>();
        final TuningConfig.Rarities rarities = new TuningConfig.Rarities(null, strict::add);

        assertNull(rarities.rarity("Simple", "Glowing"));
        assertNull(rarities.category("Elite", "config.yml Categories"));
        assertEquals("Godlike", rarities.category("Godlike", "config.yml Categories"),
                "a custom tier is not part of either vocabulary and keeps working");
        assertEquals(2, strict.size(), strict.toString());
        assertTrue(strict.getFirst().contains("Simple"), strict.getFirst());
    }

    @Test
    void currentFormatReadsLegendaryAsTheTopTierAndRefusesOldOnlyNames() {
        final List<String> warnings = new ArrayList<>();
        final TuningConfig.Rarities rarities = new TuningConfig.Rarities(2, warnings::add);

        assertEquals(TuningConfig.Rarities.CURRENT_FORMAT, rarities.format());
        assertEquals("Legendary", rarities.rarity("legendary", "SniperHeadshot"));
        assertEquals("Ultimate", rarities.rarity("Ultimate", "Sniper"));
        assertEquals("Elite", rarities.category("Elite", "config.yml Categories"));
        assertEquals("Godlike", rarities.category("Godlike", "config.yml Categories"),
                "a custom tier is not part of either vocabulary and keeps working");
        assertTrue(warnings.isEmpty(), warnings.toString());

        // An old-only name is unknown, never silently remapped.
        assertNull(rarities.rarity("Common", "Glowing"));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("Common"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Enchantments.Glowing"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Rarity-Format 2"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Categories"), warnings.getFirst());

        assertNull(rarities.rarity(7, "Glowing"));
        assertNull(rarities.category("Mythical", "config.yml Categories"));
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    void oneRarityValueWarnsOncePerLoadEvenWhenBothLoadersAsk() {
        final List<String> warnings = new ArrayList<>();
        final TuningConfig.Rarities rarities = new TuningConfig.Rarities(2, warnings::add);

        assertNull(rarities.rarity("Common", "Glowing"));
        assertNull(rarities.rarity("Common", "Glowing"));

        final Map<String, Object> raw = new HashMap<>();
        raw.put("Enchantments.Glowing.Rarity", "Common");

        TuningConfig.validate(List.of("Glowing"), Set.of("Glowing"), raw::get, rarities, warnings::add);

        assertEquals(1, warnings.size(), "validate() and the loader must not both warn: " + warnings);
    }

    @Test
    void loadTimeValidationWarnsOnceForUnknownKeysAndUnknownRarityAndNeverThrows() {
        final Map<String, Object> raw = new HashMap<>();
        raw.put("Enchantments.Bleeed.Tuning", Map.of("Cadence-Seconds", 1.0));
        raw.put("Enchantments.Bleed.Rarity", "Epic");
        final List<String> warnings = new ArrayList<>();
        final TuningConfig.Rarities rarities = new TuningConfig.Rarities(2, warnings::add);

        TuningConfig.validate(List.of("Bleeed", "Bleed"), Set.of("Bleed"), raw::get, rarities, warnings::add);

        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("Bleeed"), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("Tuning"), "the warning must name the orphaned block");
        assertTrue(warnings.getLast().contains("Epic"), warnings.getLast());
    }

    @Test
    void everyShippedEnchantPinsACosmicRarity() throws IOException {
        final Map<?, ?> root = yaml("Enchantments.yml");

        // Root-level, column 0, outside the Enchantments section.
        assertTrue(Pattern.compile("(?m)^Rarity-Format: 2$").matcher(read("src/main/resources/Enchantments.yml")).find(),
                "the shipped file must select the Cosmic vocabulary at column 0");
        assertEquals(2, root.get("Rarity-Format"), "the discriminator is a root key");
        assertFalse(enchantments().containsKey("Rarity-Format"), "the discriminator must not be an enchant key");

        final Map<String, Integer> pinned = new HashMap<>();

        for (final Map.Entry<String, Object> entry : enchantments().entrySet()) {
            final Map<?, ?> block = assertInstanceOf(Map.class, entry.getValue(), entry.getKey());
            assertInstanceOf(Boolean.class, block.get("Enabled"), entry.getKey());
            assertInstanceOf(String.class, block.get("Name"), entry.getKey());
            assertInstanceOf(Number.class, block.get("MaxPower"), entry.getKey());

            final Object rarity = block.get("Rarity");
            assertTrue(rarity instanceof String, entry.getKey() + " has no Rarity");
            assertTrue(TuningConfig.RARITIES.contains(rarity), entry.getKey() + " has an unknown Rarity: " + rarity);

            for (final Object category : assertInstanceOf(List.class, block.get("Categories"), entry.getKey())) {
                assertTrue(TuningConfig.RARITIES.contains(category),
                        entry.getKey() + " lists an unknown category: " + category);
            }

            pinned.merge((String) rarity, 1, Integer::sum);
        }

        assertEquals(160, pinned.values().stream().mapToInt(Integer::intValue).sum(), "every block is pinned");
        assertEquals(Map.of("Simple", 18, "Unique", 28, "Elite", 42, "Ultimate", 38, "Legendary", 34), pinned);

        // Exactly one Rarity line per block: SnakeYAML would keep the last of a
        // duplicated key and hide a mis-inserted pin.
        final String raw = read("src/main/resources/Enchantments.yml");
        assertEquals(160, Pattern.compile("(?m)^    Rarity: '").matcher(raw).results().count(),
                "one Rarity line per block");
        assertFalse(raw.contains("@KEEP@"), "the migration must leave no marker behind");

        // The trap, pinned deliberately: old Legendary is now Elite under the
        // migration format, and the two Sniper blocks are pinned by hand rather
        // than mapped. Under the shipped Cosmic tiers SniperHeadshot (Cosmic
        // "Sniper") is Legendary and the poison block has no counterpart.
        final Map<?, ?> sniperHeadshot = assertInstanceOf(Map.class, enchantments().get("SniperHeadshot"), "SniperHeadshot");
        assertEquals("Legendary", sniperHeadshot.get("Rarity"));
        final Map<?, ?> sniper = assertInstanceOf(Map.class, enchantments().get("Sniper"), "Sniper");
        assertEquals("poison shot", displayWord(sniper.get("Name")), "the Sniper key keeps its Poison Shot display name");
        assertEquals("Ultimate", sniper.get("Rarity"));

        final Map<?, ?> bleed = assertInstanceOf(Map.class, enchantments().get("Bleed"), "Bleed");
        assertEquals(List.of("Elite", "Legendary"), bleed.get("Categories"));
        assertEquals("Ultimate", bleed.get("Rarity"));
    }

    @Test
    void shippedConfigUsesTheCosmicCategoryIdentities() throws IOException {
        final Map<?, ?> config = yaml("config.yml");
        final Map<?, ?> categories = assertInstanceOf(Map.class, config.get("Categories"), "Categories");

        assertEquals(new HashSet<>(TuningConfig.RARITIES), new HashSet<>(categories.keySet()),
                "config.yml Categories sections");

        final Map<String, Integer> rarities = new HashMap<>();

        for (final Object key : categories.keySet()) {
            final Map<?, ?> category = assertInstanceOf(Map.class, categories.get(key), String.valueOf(key));

            final String label = "Simple".equals(key) ? "common" : String.valueOf(key).toLowerCase(Locale.ROOT);
            assertEquals(label, displayWord(category.get("Name")), "the shop label for " + key);

            rarities.put(String.valueOf(key), assertInstanceOf(Number.class, category.get("Rarity")).intValue());
        }

        // The tiers keep today's order, so an upgraded server keeps its pools.
        assertTrue(rarities.get("Simple") < rarities.get("Unique"));
        assertTrue(rarities.get("Unique") < rarities.get("Elite"));
        assertTrue(rarities.get("Elite") < rarities.get("Ultimate"));
        assertTrue(rarities.get("Ultimate") < rarities.get("Legendary"));

        final Map<?, ?> damageModifiers = assertInstanceOf(Map.class,
                assertInstanceOf(Map.class, config.get("CosmicEnchants"), "CosmicEnchants").get("DamageModifiers"), "DamageModifiers");
        assertEquals(0.0, damageModifiers.get("Bonus-Damage-Cap-HP"), "the shipped cap stays 0.0");
    }

    @Test
    void bothLoadersResolveThroughOneIdentitySet() throws IOException {
        final String manager = read("src/main/java/com/badbones69/crazyenchantments/paper/api/CrazyManager.java");
        final String books = read("src/main/java/com/badbones69/crazyenchantments/paper/controllers/settings/EnchantmentBookSettings.java");

        assertTrue(books.contains("TuningConfig.RARITY_FORMAT"), "the category loader must read the discriminator");
        assertTrue(books.contains("new TuningConfig.Rarities("), "one vocabulary per load");

        // The enchant loader takes the category loader's instance, so the two
        // resolve the same spellings to the same identities and warn once.
        assertTrue(manager.contains("this.enchantmentBookSettings.rarities()"), "both loaders share one vocabulary");
        assertTrue(manager.contains("buildCategories(rarities)"), "the categories use that vocabulary");
        assertTrue(manager.contains("loadEnchantments(enchants, nextCategories, rarities)"),
                "the enchant loader uses the same vocabulary");

        assertTrue(manager.contains("category.getIdentity().equals(identity)"), "Categories match on identity");
        assertTrue(manager.contains("category.getIdentity().equals(rarity)"), "Rarity matches on identity");
        assertTrue(books.contains("rarities.category(category, path)"), "config.yml sections resolve on identity");

        assertTrue(manager.contains("rarities.rarity(enchants.get(path + \".Rarity\"), name)"),
                "load must resolve the optional Rarity");
        assertTrue(manager.contains("enchantment.setRarity(category)"), "load must apply the override");
        assertTrue(manager.indexOf("getStringList(path + \".Categories\")") < manager.indexOf("enchantment.setRarity(category)"),
                "the override replaces the Categories it read first");
        assertTrue(manager.contains("TuningConfig.validate(section.getKeys(false), known, enchants::get, rarities, this::warn)"),
                "load must warn about unknown keys, Rarity values and orphaned Tuning blocks");
    }

    private static Map<String, Object> enchantments() throws IOException {
        return assertInstanceOf(Map.class, yaml("Enchantments.yml").get("Enchantments"), "Enchantments");
    }

    /** The visible word of a display name, without the colour codes. */
    private static String displayWord(final Object name) {
        return String.valueOf(name).replaceAll("&[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> yaml(final String file) throws IOException {
        final Object loaded = new Yaml().load(read("src/main/resources/" + file));

        return assertInstanceOf(Map.class, loaded, file);
    }

    private static void flatten(final String prefix, final Map<?, ?> source, final Map<String, Object> out) {
        source.forEach((key, value) -> {
            final String path = prefix + key;
            out.put(path, value);

            if (value instanceof Map<?, ?> child) flatten(path + ".", child, out);
        });
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative));
    }
}
