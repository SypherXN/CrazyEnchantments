package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McMMOXpLogicTest {

    private static final McMMOXpSettings DEFAULTS = McMMOXpSettings.DEFAULTS;
    private static final Path MODULE = Files.isDirectory(Path.of("src/main/java")) ? Path.of("") : Path.of("paper");
    private static final Path JAVA = MODULE.resolve("src/main/java");
    private static final String PACKAGE = "com/badbones69/crazyenchantments/paper/";
    private static final Path LISTENER = JAVA.resolve(PACKAGE + "listeners/McMMOExperienceListener.java");
    private static final String SKILLING_RATE = "CosmicEnchants.Skilling.Bonus-Per-Level-PP";
    private static final String TRAINING_RATE = "CosmicEnchants.Training.Bonus-Per-Level-PP";

    @ParameterizedTest
    @CsvSource({"1,3", "2,6", "3,9", "4,12", "5,15", "6,18", "7,21", "8,24", "9,27", "10,30",
            "0,0", "-1,0", "11,30", "2147483647,30"})
    void defaultBonusTableForBothEnchants(int level, double expected) {
        assertEquals(expected, McMMOXpLogic.bonusPercent(true, level, DEFAULTS.skillingPerLevelPp()));
        assertEquals(expected, McMMOXpLogic.bonusPercent(true, level, DEFAULTS.trainingPerLevelPp()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MINING", "WOODCUTTING", "HERBALISM", "EXCAVATION", "FISHING"})
    void gatheringSkillsOnlyUseSkilling(String skill) {
        assertEquals(3.0, McMMOXpLogic.bonusPercent(McMMOXpLogic.isSkillingSkill(skill), 1, DEFAULTS.skillingPerLevelPp()));
        assertEquals(0.0, McMMOXpLogic.bonusPercent(McMMOXpLogic.isTrainingSkill(skill), 1, DEFAULTS.trainingPerLevelPp()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SWORDS", "AXES", "ARCHERY", "UNARMED", "TAMING"})
    void combatSkillsOnlyUseTraining(String skill) {
        assertEquals(0.0, McMMOXpLogic.bonusPercent(McMMOXpLogic.isSkillingSkill(skill), 1, DEFAULTS.skillingPerLevelPp()));
        assertEquals(3.0, McMMOXpLogic.bonusPercent(McMMOXpLogic.isTrainingSkill(skill), 1, DEFAULTS.trainingPerLevelPp()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACROBATICS", "REPAIR", "CROSSBOWS", "MACES", "TRIDENTS"})
    void otherSkillsQualifyForNeitherEnchant(String skill) {
        assertFalse(McMMOXpLogic.isSkillingSkill(skill));
        assertFalse(McMMOXpLogic.isTrainingSkill(skill));
    }

    @ParameterizedTest
    @CsvSource({"6,0,10,6", "0,6,10,6", "0,0,10,0", "-2,0,10,0", "4,7,10,7", "7,4,10,7",
            "9,0,4,4", "0,9,4,4", "9,11,0,0", "9,11,-1,0"})
    void highestHeldLevelCannotExceedTheConfiguredMax(int mainHand, int offHand, int max, int expected) {
        assertEquals(expected, McMMOXpLogic.heldLevel(mainHand, offHand, max));
    }

    @Test
    void emptyAndWrongTypeItemsCannotContributeEvenIfTheyHaveEnchantData() throws IOException {
        final String listener = Files.readString(LISTENER);
        assertTrue(listener.contains("heldLevel(levelInHand(key, mainHand), levelInHand(key, offHand), key.getMaxLevel())"));
        assertTrue(Pattern.compile("if \\(item == null \\|\\| item\\.isEmpty\\(\\) \\|\\| !key\\.canEnchantItem\\(item\\)\\) return 0;\\s*(?://[^\\r\\n]*\\s*)?"
                + "return this\\.books\\.getEnchantments\\(item\\)\\.getOrDefault\\(key, 0\\);").matcher(listener).find());
    }

    @Test
    void combinedBonusIsAdditiveButCannotExceedThirtyPercent() {
        assertEquals(21.0, McMMOXpLogic.combinedBonusPercent(9.0, 12.0));
        assertEquals(30.0, McMMOXpLogic.combinedBonusPercent(21.0, 18.0));
        assertEquals(30.0, McMMOXpLogic.combinedBonusPercent(30.0, 30.0));
        assertEquals(30.0, McMMOXpLogic.bonusPercent(true, 10, 10.0));
        assertEquals(20.0, McMMOXpLogic.bonusPercent(true, 100, 2.0));
    }

    @Test
    void bonusPreservesFractionalXpAndDoesNotChangeNonPositiveOrOverflowingAwards() {
        assertEquals(13.65f, McMMOXpLogic.applyBonus(10.5f, 30.0));
        assertEquals(10.5f, McMMOXpLogic.applyBonus(10.5f, 0.0));
        assertEquals(0.0f, McMMOXpLogic.applyBonus(0.0f, 30.0));
        assertEquals(-4.0f, McMMOXpLogic.applyBonus(-4.0f, 30.0));
        assertEquals(Float.MAX_VALUE, McMMOXpLogic.applyBonus(Float.MAX_VALUE, 30.0));
    }

    @Test
    void ratesCanBeTunedIndependentlyButMustBeFiniteAndWithinTheCap() {
        assertEquals(0.0, new McMMOXpSettings(0.0, 5.0).skillingPerLevelPp());
        assertEquals(5.0, new McMMOXpSettings(0.0, 5.0).trainingPerLevelPp());
        assertThrows(IllegalArgumentException.class, () -> new McMMOXpSettings(-1.0, 3.0));
        assertThrows(IllegalArgumentException.class, () -> new McMMOXpSettings(3.0, 31.0));
        assertThrows(IllegalArgumentException.class, () -> new McMMOXpSettings(Double.NaN, 3.0));
    }

    @Test
    void configRatesValidatePerKeyAndReloadPublishesTheNewSnapshot() {
        final Map<String, Object> raw = new HashMap<>();
        final List<String> warnings = new ArrayList<>();
        try {
            raw.put(TRAINING_RATE, 4.5);
            TuningConfig.loadMcMMOXp(raw::get, warnings::add);
            final McMMOXpSettings initial = TuningConfig.mcMMOXp();
            assertEquals(new McMMOXpSettings(3.0, 4.5), initial);
            assertTrue(warnings.isEmpty(), "a missing key defaults without warning");

            raw.put(SKILLING_RATE, "7.0");
            raw.put(TRAINING_RATE, 31);
            TuningConfig.loadMcMMOXp(raw::get, warnings::add);
            assertEquals(DEFAULTS, TuningConfig.mcMMOXp());
            assertEquals(List.of(
                    "Invalid " + SKILLING_RATE + " value '7.0'; using 3.0.",
                    "Invalid " + TRAINING_RATE + " value '31'; using 3.0."), warnings);

            warnings.clear();
            raw.put(SKILLING_RATE, 7);
            raw.put(TRAINING_RATE, 2.5);
            TuningConfig.loadMcMMOXp(raw::get, warnings::add);
            assertEquals(new McMMOXpSettings(7.0, 2.5), TuningConfig.mcMMOXp());
            assertEquals(new McMMOXpSettings(3.0, 4.5), initial);
            assertTrue(warnings.isEmpty());
        } finally {
            TuningConfig.loadMcMMOXp(key -> null, warnings::add);
        }
    }

    @Test
    void productionReloadAndListenerRouteEachRateToItsOwnEnchant() throws IOException {
        final String config = Files.readString(JAVA.resolve(PACKAGE + "api/utils/TuningConfig.java"));
        final String listener = Files.readString(LISTENER);

        final int loadStart = config.indexOf("public static void load(");
        final int xpLoad = config.indexOf("loadMcMMOXp(config::get, warn);", loadStart);
        assertTrue(loadStart >= 0 && xpLoad > loadStart && xpLoad < config.indexOf("\n    }", loadStart),
                "the production reload must publish mcMMO XP tuning");
        assertTrue(listener.contains("final McMMOXpSettings tuning = TuningConfig.mcMMOXp();"));
        assertTrue(listener.contains("bonusPercent(skilling, skillingLevel, tuning.skillingPerLevelPp())"));
        assertTrue(listener.contains("bonusPercent(training, trainingLevel, tuning.trainingPerLevelPp())"));
    }

    @Test
    void fishingRodCanCarrySkillingAndFishingIsAQualifyingSkill() throws IOException {
        final Path typesFile = MODULE.resolve("src/main/resources/Enchantment-Types.yml");
        final Map<?, ?> yaml = new Yaml().load(Files.readString(typesFile));
        final Map<?, ?> type = (Map<?, ?>) ((Map<?, ?>) yaml.get("Types")).get("Weapons-and-Tools");

        assertTrue(((List<?>) type.get("Enchantable-Items")).contains("FISHING_ROD"));
        assertTrue(McMMOXpLogic.isSkillingSkill("FISHING"));
    }

    @ParameterizedTest
    @CsvSource({"Skilling,Weapons-and-Tools", "Training,Weapons"})
    void registryAndConfigAgreeOnAlwaysOnRareLevelTenEnchants(String name, String type) throws IOException {
        final Path resources = MODULE.resolve("src/main/resources");
        final Map<?, ?> yaml = new Yaml().load(Files.readString(resources.resolve("Enchantments.yml")));
        final Map<?, ?> enchant = (Map<?, ?>) ((Map<?, ?>) yaml.get("Enchantments")).get(name);
        final Map<?, ?> tuning = (Map<?, ?>) enchant.get("Tuning");

        assertEquals(true, enchant.get("Enabled"));
        assertEquals(type, enchant.get("Enchantment-Type"));
        assertEquals(10, enchant.get("MaxPower"));
        assertEquals(List.of("Unique"), enchant.get("Categories"));
        assertFalse(enchant.containsKey("Chance-System"));
        assertEquals(3.0, tuning.get("Bonus-Per-Level-PP"));
        assertTrue(Files.readString(JAVA.resolve(PACKAGE + "api/enums/CEnchantments.java"))
                .contains("%s(\"%s\", \"%s\"),".formatted(name.toUpperCase(Locale.ROOT), name, type)));
    }

    @Test
    void noNewMcMMOImportsEscapeTheOptionalListener() throws IOException {
        final Pattern imports = Pattern.compile("\\bimport\\s+(?:static\\s+)?com\\s*\\.\\s*gmail\\s*\\.\\s*nossr50\\s*\\.[^;]+;");
        final Path legacySupport = JAVA.resolve(PACKAGE + "support/parties/mcmmo/McMMOSupport.java");
        try (var paths = Files.walk(JAVA)) {
            for (final Path path : paths.filter(file -> file.toString().endsWith(".java") && !file.equals(LISTENER)).toList()) {
                final List<String> found = imports.matcher(Files.readString(path)).results()
                        .map(match -> match.group().replaceAll("\\s+", "")).toList();
                // Only this existing PartyAPI import is allowed in the separately gated legacy integration.
                if (path.equals(legacySupport) && found.equals(List.of("importcom.gmail.nossr50.api.PartyAPI;"))) continue;
                assertTrue(found.isEmpty(), () -> path + " has unexpected mcMMO imports: " + found);
            }
        }
    }

    @Test
    void registersOnlyWhenMcMMOIsEnabled() throws IOException {
        final String plugin = Files.readString(JAVA.resolve(PACKAGE + "CrazyEnchantments.java"));
        final String gate = "if (Bukkit.getPluginManager().isPluginEnabled(\"mcMMO\")) {";
        final String registration = "this.pluginManager.registerEvents(new McMMOExperienceListener(this), this);";

        assertTrue(Pattern.compile(Pattern.quote(gate) + "\\s*" + Pattern.quote(registration) + "\\s*}").matcher(plugin).find());
        assertEquals(1L, Pattern.compile("new\\s+McMMOExperienceListener\\s*\\(").matcher(plugin).results().count());
    }

    @Test
    void inventoryIsRegionOwnedAndTheExistingAwardIsUpdatedOnlyOnce() throws IOException {
        final String listener = Files.readString(LISTENER);
        assertTrue(listener.contains("ignoreCancelled = true"));
        assertTrue(Pattern.compile("if \\(event\\.isAsynchronous\\(\\) \\|\\| !Bukkit\\.isOwnedByCurrentRegion\\(player\\)\\) return;\\s*"
                + "final PlayerInventory inventory = player\\.getInventory\\(\\);\\s*"
                + "final ItemStack mainHand = inventory\\.getItemInMainHand\\(\\);\\s*"
                + "final ItemStack offHand = inventory\\.getItemInOffHand\\(\\);").matcher(listener).find());
        assertEquals(1L, Pattern.compile("\\.getRawXpGained\\s*\\(").matcher(listener).results().count());
        assertEquals(1L, Pattern.compile("\\.setRawXpGained\\s*\\(").matcher(listener).results().count());
        assertFalse(Pattern.compile("\\bExperienceAPI\\b|\\bgetScheduler\\s*\\(|\\bcallEvent\\s*\\(|\\bsetCancelled\\s*\\(")
                .matcher(listener).find());
    }
}
