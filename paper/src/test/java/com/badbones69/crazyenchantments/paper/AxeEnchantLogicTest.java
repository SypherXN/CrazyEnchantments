package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.AntiGankSettings;
import com.badbones69.crazyenchantments.paper.api.utils.BarbarianSettings;
import com.badbones69.crazyenchantments.paper.api.utils.BlacksmithSettings;
import com.badbones69.crazyenchantments.paper.api.utils.CleaveSettings;
import com.badbones69.crazyenchantments.paper.api.utils.InsanitySettings;
import com.badbones69.crazyenchantments.paper.api.utils.PummelSettings;
import com.badbones69.crazyenchantments.paper.api.utils.RogueSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eight axe enchants Pummel, Cleave, Arrow Break, Blacksmith, Anti Gank,
 * Barbarian, Insanity and Rogue. Pure-math rules are tested directly; behaviour
 * that lives inside the listener is guarded by reading its source as text, in
 * the style of MeleeEnchantLogicTest. No Bukkit server is loaded.
 */
class AxeEnchantLogicTest {

    private static final double EPS = 1.0e-9;

    private static final String ENUM_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/api/enums/CEnchantments.java";

    private static final String AXE_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/enchantments/AxeEnchantments.java";

    private static final String ENCHANTMENTS_YML = "src/main/resources/Enchantments.yml";

    // ---- Pummel: 8 / +4, Slowness II for 3/4/5 s within 3.0 blocks ----

    @Test
    void pummelSlowsForThreeFourFiveSecondsAtSlownessTwoWithinThreeBlocks() {
        final PummelSettings settings = PummelSettings.DEFAULTS;

        assertEquals(60, settings.durationTicks(1));
        assertEquals(80, settings.durationTicks(2));
        assertEquals(100, settings.durationTicks(3));
        assertEquals(1, settings.amplifier(), "amplifier 1 is Slowness II");
        assertEquals(3.0, settings.radius(), EPS);
    }

    @Test
    void pummelUnusableLevelsAreHandled() {
        final PummelSettings settings = PummelSettings.DEFAULTS;

        assertEquals(0, settings.durationTicks(0));
        assertEquals(0, settings.durationTicks(-2));
        assertEquals(100, settings.durationTicks(9), "a level past the list uses the last entry");
    }

    @Test
    void pummelValidationWarnsOncePerBadKeyAndFallsBack() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(PummelSettings.DEFAULTS, PummelSettings.validated(3.0, 1, List.of(3.0, 4.0, 5.0), warnings::add));
        assertTrue(warnings.isEmpty());

        final PummelSettings bad = PummelSettings.validated(-1.0, 99, List.of(), warnings::add);

        assertEquals(PummelSettings.DEFAULTS, bad);
        assertEquals(3, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new PummelSettings(Double.NaN, 1, List.of(3.0)));
        assertThrows(IllegalArgumentException.class, () -> new PummelSettings(10.1, 1, List.of(3.0)));
        assertThrows(IllegalArgumentException.class, () -> new PummelSettings(3.0, 1, List.of(0.0)));
    }

    // ---- Cleave: 12 / +2, 35% splash to up to 4 others within 1.0 + 0.5 x level ----

    @Test
    void cleaveRadiusGrowsHalfABlockPerLevelFromOnePointFiveToFourPointFive() {
        final CleaveSettings settings = CleaveSettings.DEFAULTS;

        assertEquals(1.5, settings.radius(1), EPS);
        assertEquals(2.0, settings.radius(2), EPS);
        assertEquals(3.0, settings.radius(4), EPS);
        assertEquals(4.5, settings.radius(7), EPS);
        assertEquals(1.5, settings.radius(0), EPS, "a level below I counts as I");
        assertEquals(4.5, settings.radius(12), EPS, "a level past VII counts as VII");
    }

    @Test
    void cleaveSplashIsThirtyFivePercentOfTheFinalDamageToFourTargets() {
        final CleaveSettings settings = CleaveSettings.DEFAULTS;

        assertEquals(3.5, settings.splashDamage(10.0), EPS);
        assertEquals(0.35, settings.splashDamage(1.0), EPS);
        assertEquals(4, settings.maxTargets());
        assertEquals(0.0, settings.splashDamage(0.0), EPS);
        assertEquals(0.0, settings.splashDamage(-4.0), EPS);
        assertEquals(0.0, settings.splashDamage(Double.NaN), EPS);
        assertEquals(0.0, settings.splashDamage(Double.POSITIVE_INFINITY), EPS);
    }

    @Test
    void cleaveValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(CleaveSettings.DEFAULTS, CleaveSettings.validated(0.35, 4, 1.0, 0.5, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(CleaveSettings.DEFAULTS, CleaveSettings.validated(0.0, 0, -1.0, Double.NaN, warnings::add));
        assertEquals(4, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new CleaveSettings(1.5, 4, 1.0, 0.5));
    }

    // ---- Blacksmith: 6 / +3, half damage vs players, 1/1/1/2/2 repair points ----

    @Test
    void blacksmithHalvesTheHitAndRepairsOneOneOneTwoTwoPoints() {
        final BlacksmithSettings settings = BlacksmithSettings.DEFAULTS;

        assertEquals(5.0, settings.reduce(10.0), EPS);
        assertEquals(1, settings.repairPoints(1));
        assertEquals(1, settings.repairPoints(2));
        assertEquals(1, settings.repairPoints(3));
        assertEquals(2, settings.repairPoints(4));
        assertEquals(2, settings.repairPoints(5));
        assertEquals(0, settings.repairPoints(0));
        assertEquals(2, settings.repairPoints(9));
    }

    @Test
    void blacksmithPicksTheLargestMissingDurabilityAndIgnoresAirAndUndamagedPieces() {
        // Slots: helmet, chestplate, leggings, boots. A 0 maximum is air or a non-damageable item.
        assertEquals(1, BlacksmithSettings.mostDamaged(new int[]{165, 240, 225, 195}, new int[]{10, 100, 30, 0}));
        assertEquals(3, BlacksmithSettings.mostDamaged(new int[]{165, 240, 225, 195}, new int[]{10, 20, 30, 190}));
        // The largest absolute missing wins, not the largest fraction.
        assertEquals(1, BlacksmithSettings.mostDamaged(new int[]{55, 400, 0, 0}, new int[]{50, 60, 999, 999}));
        // Ties go to the earliest slot.
        assertEquals(0, BlacksmithSettings.mostDamaged(new int[]{100, 100, 100, 100}, new int[]{40, 40, 40, 40}));
    }

    @Test
    void blacksmithWithNoDamagedPieceSkipsTheRepairButKeepsTheReduction() {
        assertEquals(-1, BlacksmithSettings.mostDamaged(new int[]{165, 240, 225, 195}, new int[]{0, 0, 0, 0}));
        assertEquals(-1, BlacksmithSettings.mostDamaged(new int[]{0, 0, 0, 0}, new int[]{5, 5, 5, 5}));
        assertEquals(-1, BlacksmithSettings.mostDamaged(new int[0], new int[0]));

        assertEquals(5.0, BlacksmithSettings.DEFAULTS.reduce(10.0), EPS);
    }

    @Test
    void blacksmithRepairNeverGoesBelowZeroDamage() {
        assertEquals(8, BlacksmithSettings.repairedDamage(10, 2));
        assertEquals(9, BlacksmithSettings.repairedDamage(10, 1));
        assertEquals(0, BlacksmithSettings.repairedDamage(1, 2));
        assertEquals(0, BlacksmithSettings.repairedDamage(0, 2));
        assertEquals(10, BlacksmithSettings.repairedDamage(10, 0));
    }

    @Test
    void blacksmithValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(BlacksmithSettings.DEFAULTS, BlacksmithSettings.validated(0.5, List.of(1, 1, 1, 2, 2), warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(BlacksmithSettings.DEFAULTS, BlacksmithSettings.validated(2.0, List.of(-1), warnings::add));
        assertEquals(2, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new BlacksmithSettings(0.0, List.of(1)));
    }

    // ---- Anti Gank: always-on, window 5.0 s, +10% per attacker over 6 - level, cap 1.5 ----

    @Test
    void antiGankThresholdIsSixMinusLevelAndTheBonusNeedsMoreAttackers() {
        final AntiGankSettings settings = AntiGankSettings.DEFAULTS;

        assertEquals(5, settings.threshold(1));
        assertEquals(4, settings.threshold(2));
        assertEquals(3, settings.threshold(3));
        assertEquals(2, settings.threshold(4));

        // Level IV: two attackers is the threshold and earns nothing; three is the first bonus.
        assertEquals(1.0, settings.multiplier(4, 0), EPS);
        assertEquals(1.0, settings.multiplier(4, 2), EPS);
        assertEquals(1.10, settings.multiplier(4, 3), EPS);
        assertEquals(1.20, settings.multiplier(4, 4), EPS);
        assertEquals(1.30, settings.multiplier(4, 5), EPS);
        assertEquals(1.40, settings.multiplier(4, 6), EPS);

        assertEquals(1.0, settings.multiplier(1, 5), EPS);
        assertEquals(1.10, settings.multiplier(1, 6), EPS);
    }

    @Test
    void antiGankIsHardCappedAtOnePointFive() {
        final AntiGankSettings settings = AntiGankSettings.DEFAULTS;

        assertEquals(1.5, settings.multiplier(4, 7), EPS);
        assertEquals(1.5, settings.multiplier(4, 30), EPS);
        assertEquals(1.0, settings.multiplier(0, 30), EPS, "no level means no bonus");
        assertEquals(1.0, settings.multiplier(-1, 30), EPS);
    }

    @Test
    void antiGankCountsDistinctAttackersIncludingTheCurrentTarget() {
        final AntiGankSettings settings = AntiGankSettings.DEFAULTS;
        final Map<UUID, Long> hits = new ConcurrentHashMap<>();
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final UUID c = UUID.randomUUID();

        settings.record(hits, a, 0L);
        settings.record(hits, a, 100L);
        settings.record(hits, b, 200L);
        settings.record(hits, c, 300L);

        assertEquals(3, settings.count(hits, 400L), "a repeat attacker is one entry, and the current target still counts");
    }

    @Test
    void antiGankWindowIsFiveSecondsAndExpiredEntriesAreDroppedOnRead() {
        final AntiGankSettings settings = AntiGankSettings.DEFAULTS;
        final Map<UUID, Long> hits = new ConcurrentHashMap<>();
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final long fiveSeconds = 5_000_000_000L;

        settings.record(hits, a, 0L);
        settings.record(hits, b, 1_000_000_000L);

        assertEquals(5_000_000_000L, settings.windowNanos());
        assertEquals(2, settings.count(hits, fiveSeconds - 1L));
        // A hit exactly five seconds old is already expired; b is still inside its window.
        assertEquals(1, settings.count(hits, fiveSeconds));
        assertEquals(1, hits.size(), "the expired entry was pruned by the read");
        assertEquals(0, settings.count(hits, 6_000_000_000L));
        assertTrue(hits.isEmpty());
    }

    @Test
    void antiGankRefreshingAnAttackerExtendsItsWindow() {
        final AntiGankSettings settings = AntiGankSettings.DEFAULTS;
        final Map<UUID, Long> hits = new ConcurrentHashMap<>();
        final UUID a = UUID.randomUUID();

        settings.record(hits, a, 0L);
        settings.record(hits, a, 4_000_000_000L);

        assertEquals(1, settings.count(hits, 8_000_000_000L));
        assertEquals(0, settings.count(hits, 9_000_000_000L));
    }

    @Test
    void antiGankValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(AntiGankSettings.DEFAULTS, AntiGankSettings.validated(5.0, 0.10, 1.5, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(AntiGankSettings.DEFAULTS, AntiGankSettings.validated(0.0, -0.1, 0.5, warnings::add));
        assertEquals(3, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new AntiGankSettings(5.0, 0.1, 0.9));
        assertThrows(IllegalArgumentException.class, () -> new AntiGankSettings(5.0, 0.1, 1.6));
    }

    @Test
    void antiGankEvictsTheOldestLiveAttackerAtTheHistoryBound() {
        final AntiGankSettings settings = AntiGankSettings.DEFAULTS;
        final Map<UUID, Long> hits = new HashMap<>();
        final int limit = TuningConfig.MAX_ANTI_GANK_HISTORY;
        for (int i = 0; i < limit; i++) settings.record(hits, new UUID(0, i), i);

        settings.record(hits, new UUID(0, 0), limit); // refresh the oldest
        settings.record(hits, new UUID(0, limit), limit + 1L);

        assertEquals(limit, hits.size());
        assertFalse(hits.containsKey(new UUID(0, 1)));
        assertTrue(hits.containsKey(new UUID(0, 0)));
        assertTrue(hits.containsKey(new UUID(0, limit)));
    }

    // ---- Barbarian: always-on, 5% x level vs an axe in the target's main hand ----

    @Test
    void barbarianIsFivePercentPerLevelOnlyAgainstAnAxe() {
        final BarbarianSettings settings = BarbarianSettings.DEFAULTS;

        assertEquals(1.05, settings.multiplier(1, true), EPS);
        assertEquals(1.10, settings.multiplier(2, true), EPS);
        assertEquals(1.15, settings.multiplier(3, true), EPS);
        assertEquals(1.20, settings.multiplier(4, true), EPS);

        for (int level = 1; level <= 4; level++) {
            assertEquals(1.0, settings.multiplier(level, false), EPS, "no axe, no bonus");
        }

        assertEquals(1.0, settings.multiplier(0, true), EPS);
        assertEquals(1.20, settings.multiplier(9, true), EPS);
    }

    @Test
    void barbarianAxeDetectionDoesNotMistakePickaxesOrSwordsForAxes() {
        assertTrue(BarbarianSettings.isAxe("DIAMOND_AXE"));
        assertTrue(BarbarianSettings.isAxe("NETHERITE_AXE"));
        assertTrue(BarbarianSettings.isAxe("WOODEN_AXE"));
        assertFalse(BarbarianSettings.isAxe("DIAMOND_PICKAXE"));
        assertFalse(BarbarianSettings.isAxe("DIAMOND_SWORD"));
        assertFalse(BarbarianSettings.isAxe("AIR"));
        assertFalse(BarbarianSettings.isAxe(""));
        assertFalse(BarbarianSettings.isAxe(null));
    }

    // ---- Insanity: always-on, 2.5% x level vs a sword in the target's main hand ----

    @Test
    void insanityIsTwoPointFivePercentPerLevelUpToTwentyOnlyAgainstASword() {
        final InsanitySettings settings = InsanitySettings.DEFAULTS;

        assertEquals(1.025, settings.multiplier(1, true), EPS);
        assertEquals(1.05, settings.multiplier(2, true), EPS);
        assertEquals(1.10, settings.multiplier(4, true), EPS);
        assertEquals(1.20, settings.multiplier(8, true), EPS);
        assertEquals(1.20, settings.multiplier(12, true), EPS);

        for (int level = 1; level <= 8; level++) {
            assertEquals(1.0, settings.multiplier(level, false), EPS, "no sword, no bonus");
        }

        assertEquals(1.0, settings.multiplier(0, true), EPS);
        assertEquals(2.5, settings.bonusPp(1), EPS);
        assertEquals(20.0, settings.bonusPp(8), EPS);
    }

    @Test
    void insanitySwordDetectionIsExact() {
        assertTrue(InsanitySettings.isSword("DIAMOND_SWORD"));
        assertTrue(InsanitySettings.isSword("WOODEN_SWORD"));
        assertFalse(InsanitySettings.isSword("DIAMOND_AXE"));
        assertFalse(InsanitySettings.isSword("BOW"));
        assertFalse(InsanitySettings.isSword(null));
    }

    @Test
    void barbarianAndInsanityValidationFallBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(BarbarianSettings.DEFAULTS, BarbarianSettings.validated(-5.0, warnings::add));
        assertEquals(InsanitySettings.DEFAULTS, InsanitySettings.validated(Double.NaN, warnings::add));
        assertEquals(2, warnings.size());
        assertEquals(7.0, BarbarianSettings.validated(7.0, warnings::add).bonusPerLevelPp(), EPS);
        assertEquals(2, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new BarbarianSettings(101.0));
        assertThrows(IllegalArgumentException.class, () -> new InsanitySettings(-0.1));
    }

    // ---- Rogue: always-on, 1.10/1.15/1.20 from behind, 135 degrees, 2.0 s cooldown ----

    @Test
    void rogueMultipliersAreOneTenOneFifteenOneTwenty() {
        final RogueSettings settings = RogueSettings.DEFAULTS;

        assertEquals(1.10, settings.multiplier(1), EPS);
        assertEquals(1.15, settings.multiplier(2), EPS);
        assertEquals(1.20, settings.multiplier(3), EPS);
        assertEquals(1.0, settings.multiplier(0), EPS);
        assertEquals(1.20, settings.multiplier(9), EPS);
    }

    @Test
    void rogueBehindIsTheRearNinetyDegreeConeInTheHorizontalPlane() {
        final RogueSettings settings = RogueSettings.DEFAULTS;

        // Victim faces +Z. An attacker on -Z is directly behind.
        assertTrue(settings.isBehind(0.0, 1.0, 0.0, -3.0));
        assertTrue(settings.isBehind(0.0, 1.0, 0.5, -3.0), "slightly off the exact rear is still behind");

        // In front and at the sides are not behind.
        assertFalse(settings.isBehind(0.0, 1.0, 0.0, 3.0));
        assertFalse(settings.isBehind(0.0, 1.0, 3.0, 0.0), "90 degrees to the side");
        assertFalse(settings.isBehind(0.0, 1.0, -3.0, 0.0));
        assertFalse(settings.isBehind(0.0, 1.0, 3.0, -2.9), "just inside 135 degrees of the front is not behind");
    }

    @Test
    void rogueExactlyOneThirtyFiveDegreesCountsAsBehindAndJustUnderDoesNot() {
        final RogueSettings settings = RogueSettings.DEFAULTS;

        // Facing +Z; the attacker at (1, -1) is exactly 135 degrees from the facing.
        assertTrue(settings.isBehind(0.0, 1.0, 1.0, -1.0));
        assertTrue(settings.isBehind(0.0, 1.0, -1.0, -1.0));
        // Rotate the attacker one degree toward the front: 134 degrees.
        final double rad = Math.toRadians(134.0);

        assertFalse(settings.isBehind(0.0, 1.0, Math.sin(rad), Math.cos(rad)));

        final double behindRad = Math.toRadians(136.0);

        assertTrue(settings.isBehind(0.0, 1.0, Math.sin(behindRad), Math.cos(behindRad)));
    }

    @Test
    void rogueZeroLengthAndUnusableVectorsAreNeverBehind() {
        final RogueSettings settings = RogueSettings.DEFAULTS;

        // Looking straight up or down leaves no horizontal facing.
        assertFalse(settings.isBehind(0.0, 0.0, 0.0, -3.0));
        // Standing exactly on the victim leaves no horizontal offset (only a vertical one).
        assertFalse(settings.isBehind(0.0, 1.0, 0.0, 0.0));
        assertFalse(settings.isBehind(0.0, 0.0, 0.0, 0.0));
        assertFalse(settings.isBehind(Double.NaN, 1.0, 0.0, -3.0));
        assertFalse(settings.isBehind(0.0, 1.0, Double.NaN, -3.0));
        assertFalse(settings.isBehind(0.0, Double.POSITIVE_INFINITY, 0.0, -3.0));
    }

    @Test
    void rogueCooldownIsTwoSecondsPerAttackerAndIndependentBetweenAttackers() {
        final RogueSettings settings = RogueSettings.DEFAULTS;
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        assertEquals(2_000L, settings.cooldownMillis());
        assertFalse(settings.onCooldown(cooldowns, first, 0L));

        assertTrue(settings.claim(cooldowns, first, 1_000L));
        assertTrue(settings.onCooldown(cooldowns, first, 1_000L));
        assertFalse(settings.claim(cooldowns, first, 2_999L), "still cooling down one millisecond early");
        assertTrue(settings.onCooldown(cooldowns, first, 2_999L));

        assertFalse(settings.onCooldown(cooldowns, first, 3_000L), "exactly two seconds later it is ready");
        assertTrue(settings.claim(cooldowns, first, 3_000L));

        assertTrue(settings.claim(cooldowns, second, 1_500L), "another attacker has its own cooldown");

        cooldowns.remove(first);

        assertFalse(settings.onCooldown(cooldowns, first, 3_001L), "quit clears the cooldown");
    }

    @Test
    void rogueValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(RogueSettings.DEFAULTS, RogueSettings.validated(List.of(1.10, 1.15, 1.20), 135.0, 2.0, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(RogueSettings.DEFAULTS, RogueSettings.validated(List.of(0.5), 0.0, -1.0, warnings::add));
        assertEquals(3, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new RogueSettings(List.of(1.1), 200.0, 2.0));
    }

    // ---- Registry: enum, Enchantments.yml and the chance formula agree ----

    @Test
    void chanceEnchantsUseTheApprovedOddsInTheEnumAndTheYaml() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertTrue(source.contains("PUMMEL(\"Pummel\", \"Axe\", 8, 4),"), "Pummel must be 8 / +4");
        assertTrue(source.contains("CLEAVE(\"Cleave\", \"Axe\", 12, 2),"), "Cleave must be 12 / +2");
        assertTrue(source.contains("ARROW_BREAK(\"Arrow Break\", \"Axe\", 10, 5),"), "Arrow Break must be 10 / +5");
        assertTrue(source.contains("BLACKSMITH(\"Blacksmith\", \"Axe\", 6, 3),"), "Blacksmith must be 6 / +3");

        assertChance(yaml, "Pummel", 8, 4);
        assertChance(yaml, "Cleave", 12, 2);
        assertChance(yaml, "Arrow Break", 10, 5);
        assertChance(yaml, "Blacksmith", 6, 3);

        // chance + increase x (level - 1) at the maximum level.
        assertEquals(16, 8 + 4 * (3 - 1), "Pummel is 16% at III");
        assertEquals(24, 12 + 2 * (7 - 1), "Cleave is 24% at VII");
        assertEquals(35, 10 + 5 * (6 - 1), "Arrow Break is 35% at VI");
        assertEquals(18, 6 + 3 * (5 - 1), "Blacksmith is 18% at V");
    }

    @Test
    void alwaysOnEnchantsCarryNoChanceValuesAndNoChanceSystem() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertTrue(source.contains("ANTI_GANK(\"Anti Gank\", \"Axe\"),"), "Anti Gank must have no chance values");
        assertTrue(source.contains("BARBARIAN(\"Barbarian\", \"Axe\"),"), "Barbarian must have no chance values");
        assertTrue(source.contains("INSANITY(\"Insanity\", \"Axe\"),"), "Insanity must have no chance values");
        assertTrue(source.contains("ROGUE(\"Rogue\", \"Axe\"),"), "Rogue must have no chance values");

        for (final String name : List.of("Anti Gank", "Barbarian", "Insanity", "Rogue")) {
            assertFalse(entry(yaml, name).contains("Chance-System"), name + " must not declare a Chance-System");
        }

        // The code treats a chance of 0 as always succeeding, so a zero chance must never be declared.
        assertFalse(source.contains("ANTI_GANK(\"Anti Gank\", \"Axe\", 0"));
        assertFalse(source.contains("ROGUE(\"Rogue\", \"Axe\", 0"));
    }

    @Test
    void yamlEntriesDeclareTheApprovedMaxPowerTypeAndCategory() throws IOException {
        final String yaml = read(ENCHANTMENTS_YML);

        final Map<String, String[]> expected = new HashMap<>();

        expected.put("Pummel", new String[]{"3", "Elite"});
        expected.put("Cleave", new String[]{"7", "Ultimate"});
        expected.put("Arrow Break", new String[]{"6", "Ultimate"});
        expected.put("Blacksmith", new String[]{"5", "Elite"});
        expected.put("Anti Gank", new String[]{"4", "Elite"});
        expected.put("Barbarian", new String[]{"4", "Elite"});
        expected.put("Insanity", new String[]{"8", "Ultimate"});
        expected.put("Rogue", new String[]{"3", "Legendary"});

        expected.forEach((name, values) -> {
            final String entry = entry(yaml, name);

            assertTrue(entry.contains("Enabled: true"), name + " must be enabled");
            assertTrue(entry.contains("MaxPower: " + values[0] + "\n"), name + " MaxPower");
            assertTrue(entry.contains("Enchantment-Type: 'Axe'"), name + " must be an axe enchant");
            assertTrue(entry.contains("Name: '&7" + name + "'"), name + " needs its &7 name");
            assertTrue(entry.contains("Categories:\n      - '" + values[1] + "'\n"), name + " must be only in " + values[1]);
            assertTrue(entry.contains("Info:") && entry.contains("Description:"), name + " needs Info and a Description");
        });
    }

    @Test
    void configShipsEveryTunableAtItsRecordDefault() throws IOException {
        final String yaml = read(ENCHANTMENTS_YML);

        assertEquals("3.0", value(yaml, "Pummel", "Radius"));
        assertEquals("1", value(yaml, "Pummel", "Slowness-Amplifier"));
        assertEquals("[3.0, 4.0, 5.0]", value(yaml, "Pummel", "Duration-Seconds"));

        assertEquals("0.35", value(yaml, "Cleave", "Splash-Share"));
        assertEquals("4", value(yaml, "Cleave", "Max-Targets"));
        assertEquals("1.0", value(yaml, "Cleave", "Base-Radius"));
        assertEquals("0.5", value(yaml, "Cleave", "Radius-Per-Level"));

        assertEquals("0.5", value(yaml, "Blacksmith", "Damage-Multiplier"));
        assertEquals("[1, 1, 1, 2, 2]", value(yaml, "Blacksmith", "Repair-Points"));

        assertEquals("5.0", value(yaml, "AntiGank", "Window-Seconds"));
        assertEquals("0.10", value(yaml, "AntiGank", "Bonus-Per-Extra-Attacker"));
        assertEquals("1.5", value(yaml, "AntiGank", "Cap"));

        assertEquals("5.0", value(yaml, "Barbarian", "Bonus-Per-Level-PP"));
        assertEquals("2.5", value(yaml, "Insanity", "Bonus-Per-Level-PP"));

        assertEquals("[1.10, 1.15, 1.20]", value(yaml, "Rogue", "Rear-Multipliers"));
        assertEquals("135.0", value(yaml, "Rogue", "Rear-Angle-Degrees"));
        assertEquals("2.0", value(yaml, "Rogue", "Cooldown-Seconds"));

        // The shipped numbers are the record defaults.
        assertEquals(PummelSettings.DEFAULTS.radius(), Double.parseDouble(value(yaml, "Pummel", "Radius")), EPS);
        assertEquals(CleaveSettings.DEFAULTS.splashShare(), Double.parseDouble(value(yaml, "Cleave", "Splash-Share")), EPS);
        assertEquals(AntiGankSettings.DEFAULTS.cap(), Double.parseDouble(value(yaml, "AntiGank", "Cap")), EPS);
        assertEquals(RogueSettings.DEFAULTS.cooldownSeconds(), Double.parseDouble(value(yaml, "Rogue", "Cooldown-Seconds")), EPS);
    }

    @Test
    void tuningConfigReadsEveryNewKeyThroughItsValidatedSettings() throws IOException {
        final String tuning = read("src/main/java/com/badbones69/crazyenchantments/paper/api/utils/TuningConfig.java");

        for (final String key : List.of(
                "CosmicEnchants.Pummel.Radius", "CosmicEnchants.Pummel.Slowness-Amplifier", "CosmicEnchants.Pummel.Duration-Seconds",
                "CosmicEnchants.Cleave.Splash-Share", "CosmicEnchants.Cleave.Max-Targets", "CosmicEnchants.Cleave.Base-Radius",
                "CosmicEnchants.Cleave.Radius-Per-Level", "CosmicEnchants.Blacksmith.Damage-Multiplier",
                "CosmicEnchants.Blacksmith.Repair-Points", "CosmicEnchants.AntiGank.Window-Seconds",
                "CosmicEnchants.AntiGank.Bonus-Per-Extra-Attacker", "CosmicEnchants.AntiGank.Cap",
                "CosmicEnchants.Barbarian.Bonus-Per-Level-PP", "CosmicEnchants.Insanity.Bonus-Per-Level-PP",
                "CosmicEnchants.Rogue.Rear-Multipliers", "CosmicEnchants.Rogue.Rear-Angle-Degrees",
                "CosmicEnchants.Rogue.Cooldown-Seconds")) {
            assertTrue(tuning.contains("\"" + key + "\"")
                    || tuning.contains("values.number(\"" + key.substring("CosmicEnchants.".length()) + "\""),
                    "TuningConfig must read " + key);
        }

        for (final String settings : List.of("PummelSettings", "CleaveSettings", "BlacksmithSettings", "AntiGankSettings",
                "BarbarianSettings", "InsanitySettings", "RogueSettings")) {
            assertTrue(tuning.contains(settings + ".validated("), "TuningConfig must validate " + settings);
        }
    }

    // ---- Listener behaviour, guarded by reading the source ----

    @Test
    void pummelAndCleaveAreOwnedByTheEventRegionAndSplashIsSynthetic() throws IOException {
        final String body = method(read(AXE_SOURCE), "onPummelCleave");

        assertTrue(body.contains("SyntheticHit.isBleedTick()"), "a splash hit must never re-enter Cleave or Pummel");
        assertTrue(body.contains("EventUtils.isIgnoredEvent(event)"));
        assertTrue(body.contains("EventUtils.isIgnoredUUID(damager.getUniqueId())"));
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(damager)") && body.contains("Bukkit.isOwnedByCurrentRegion(victim)"));
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(living)"), "every splash target must be owned before it is read");
        assertTrue(body.contains("SyntheticHit.damage(damager, living, splash)"), "splash goes through the attributed SyntheticHit helper");
        assertFalse(body.contains("living.damage("), "no raw damage call: it would skip the synthetic marker");
        assertTrue(body.contains("event.getFinalDamage()"), "splash is based on the primary hit's final damage");
        assertTrue(body.contains("cleaveSettings.maxTargets()"));
        assertTrue(body.contains("this.support.isFriendly(damager, living)"), "allies are skipped");
        assertTrue(body.contains("living.getUniqueId().equals(damager.getUniqueId())"), "the wielder is skipped by UUID");
        assertTrue(body.contains("living.getUniqueId().equals(victim.getUniqueId())"), "the primary victim is skipped by UUID");
        assertFalse(body.contains("== damager") || body.contains("== victim") || body.contains("!= damager") || body.contains("!= victim"),
                "no reference-equality entity checks");
        assertTrue(body.contains("candidate instanceof ArmorStand"));
        assertTrue(body.contains("PotionEffectType.SLOWNESS") && body.contains("pummelSettings.amplifier()"));
        assertTrue(body.contains("victim.addPotionEffect(slow)"), "Pummel includes the primary victim");
        assertTrue(body.contains("distanceSquared"), "targets are filtered by radius");
    }

    @Test
    void cleaveAndPummelEachUseTheirOwnRadiusExpression() throws IOException {
        final String axe = read(AXE_SOURCE);
        final String body = method(axe, "onPummelCleave");

        // Each radius comes from its own settings object, and each loop filters by its own radius.
        assertTrue(body.contains("final double pummelRadius = pummel ? pummelSettings.radius() : 0.0;"));
        assertTrue(body.contains("final double cleaveRadius = cleave ? cleaveSettings.radius(cleaveLevel) : 0.0;"));
        assertTrue(body.contains("if (pummel && distanceSquared <= pummelSquared)"),
                "Pummel slows by the Pummel radius");
        assertTrue(body.contains("if (cleave && distanceSquared <= cleaveSquared)"), "Cleave splashes by the Cleave radius");
        assertFalse(body.contains("cleaveSettings.radius()") || body.contains("pummelSettings.radius(pummelLevel)"),
                "neither enchant may borrow the other's radius shape");
        assertEquals(1, count(body, "location.distanceSquared(center)"));
        assertTrue(body.contains("PummelSettings.MAX_TARGETS - 1"), "the Pummel cap includes the primary target");

        // Cleave's radius is 1.0 + 0.5 x level, scaled by the level; Pummel's is a flat 3.0.
        final String cleave = read("src/main/java/com/badbones69/crazyenchantments/paper/api/utils/CleaveSettings.java");
        final String pummel = read("src/main/java/com/badbones69/crazyenchantments/paper/api/utils/PummelSettings.java");

        assertTrue(cleave.contains("new CleaveSettings(0.35, 4, 1.0, 0.5)"));
        assertTrue(cleave.contains("this.baseRadius + this.radiusPerLevel * Math.clamp(level, 1, MAX_LEVEL)"));
        assertTrue(pummel.contains("new PummelSettings(3.0, 1, List.of(3.0, 4.0, 5.0))"));
        assertFalse(pummel.contains("level") && pummel.contains("radiusPerLevel"), "Pummel's radius does not scale with level");

        for (int level = 1; level <= 7; level++) {
            assertEquals(1.0 + 0.5 * level, CleaveSettings.DEFAULTS.radius(level), EPS, "Cleave radius at level " + level);
        }

        assertEquals(3.0, PummelSettings.DEFAULTS.radius(), EPS);
        // Distinct from Pummel's 3.0 at every level but IV, where the two formulas happen to meet.
        for (final int level : new int[]{1, 2, 3, 5, 6, 7}) {
            assertTrue(Math.abs(CleaveSettings.DEFAULTS.radius(level) - PummelSettings.DEFAULTS.radius()) > EPS, "level " + level);
        }
    }

    @Test
    void everyCeDamageHandlerIgnoresAHitWhileTheSyntheticMarkerIsSet() throws IOException {
        // The marker is what keeps Cleave splash at depth 1: each melee-reacting handler returns on it.
        final String base = "src/main/java/com/badbones69/crazyenchantments/paper/";

        assertTrue(read(base + "enchantments/AxeEnchantments.java").contains("public void onPlayerDamage(EntityDamageByEntityEvent event) {\n        if (SyntheticHit.isBleedTick()"));
        assertTrue(read(base + "enchantments/SwordEnchantments.java").contains("public void onPlayerDamage(EntityDamageByEntityEvent event) {\n        if (SyntheticHit.isBleedTick()"));
        assertTrue(read(base + "enchantments/ArmorEnchantments.java").contains("public void onPlayerDamage(EntityDamageByEntityEvent event) {\n        if (SyntheticHit.isBleedTick()"));
        assertTrue(read(base + "enchantments/AllyEnchantments.java").contains("public void onAllySpawn(EntityDamageByEntityEvent event) {\n        if (SyntheticHit.isBleedTick()"));
        assertTrue(read(base + "listeners/DamageModifierListener.java").contains("public void onDamage(final EntityDamageByEntityEvent event) {\n        if (SyntheticHit.isBleedTick()"));
        assertTrue(read(base + "listeners/BlockListener.java").contains("SyntheticHit.isBleedTick()"));

        final String axe = read(AXE_SOURCE);

        for (final String handler : List.of("onPummelCleave", "onAntiGankTrack")) {
            assertTrue(method(axe, handler).contains("SyntheticHit.isBleedTick()"), handler + " must ignore synthetic hits");
        }
    }

    @Test
    void arrowBreakCancelsAndRemovesOnlyALiveUnconsumedArrowOnTheOwningRegion() throws IOException {
        final String body = method(read(AXE_SOURCE), "onArrowBreak");

        assertTrue(read(AXE_SOURCE).contains("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n    public void onArrowBreak"),
                "an already cancelled hit (Arrow Deflect's window) must not be consumed again");
        assertTrue(body.contains("public void onArrowBreak(final EntityDamageByEntityEvent event) {\n"
                        + "        if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;\n"),
                "the same first-line guards as the sibling handlers");
        assertTrue(body.contains("event.isCancelled()"));
        assertTrue(body.contains("arrow instanceof Arrow") && body.contains("arrow instanceof SpectralArrow"),
                "normal, tipped (Arrow) and spectral arrows");
        assertFalse(body.contains("Trident"), "a trident is not an arrow");
        assertTrue(body.contains("arrow.isDead()") && body.contains("!arrow.isValid()"), "an already removed arrow is never consumed again");
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(victim)") && body.contains("Bukkit.isOwnedByCurrentRegion(arrow)"));
        assertTrue(body.contains("this.methods.getItemInHand(victim)"), "the axe must be in the victim's main hand");
        assertTrue(body.contains("EnchantUtils.isEventActive(CEnchantments.ARROW_BREAK, victim, item, enchantments)"), "chance roll");
        assertTrue(body.contains("event.setCancelled(true)") && body.contains("arrow.remove()"));
    }

    @Test
    void blacksmithHalvesTheHitOnlyForPlayersAndRepairsOnlyAfterTheHitLands() throws IOException {
        final String axe = read(AXE_SOURCE);
        final String bonuses = method(axe, "applyAxeBonuses");
        final String repair = method(axe, "onBlacksmithRepair");

        assertTrue(bonuses.contains("victim instanceof Player && EnchantUtils.isEventActive(CEnchantments.BLACKSMITH"));
        assertTrue(bonuses.contains("TuningConfig.blacksmith().damageMultiplier()"));
        assertTrue(bonuses.contains("state.pendingBlacksmith.put(event, blacksmithLevel)"));

        assertTrue(repair.contains("this.combatState.pendingBlacksmith.remove(event)"));
        assertTrue(repair.contains("event.isCancelled()") && repair.contains("event.getFinalDamage() > 0.0"));
        assertTrue(repair.contains("Bukkit.isOwnedByCurrentRegion(damager)"));
        assertTrue(repair.contains("TuningConfig.blacksmith().repairPoints(level)"));
        assertTrue(axe.contains("@EventHandler(priority = EventPriority.MONITOR)\n    public void onBlacksmithRepair"),
                "the pending entry is removed even for a cancelled hit");

        // The repair target is the ATTACKER: the player taken from event.getDamager(), never event.getEntity().
        assertTrue(repair.contains("if (!(event.getDamager() instanceof Player damager) || !Bukkit.isOwnedByCurrentRegion(damager)) return;"));
        assertTrue(repair.contains("repairMostDamagedArmor(damager, TuningConfig.blacksmith().repairPoints(level));"));
        assertFalse(repair.contains("event.getEntity()"), "the repair path must never look at the victim");
        assertFalse(repair.contains("repairMostDamagedArmor((") || repair.contains("repairMostDamagedArmor(victim"),
                "the repair is never aimed at the victim");

        final String piece = method(axe, "repairMostDamagedArmor");

        assertTrue(piece.contains("private void repairMostDamagedArmor(final Player player, final int points) {"));
        assertTrue(piece.contains("final EntityEquipment equipment = player.getEquipment();"), "the worn armor of the player passed in");
        assertTrue(piece.contains("equipment.getItem(slots[i])") && piece.contains("equipment.setItem(slots[index], piece)"),
                "read and write go through that same worn-armor view");
        assertFalse(piece.contains("event") || piece.contains("victim") || piece.contains("getEntity()"),
                "the repair helper has no route to the victim");
        assertEquals(1, count(piece, "getEquipment()"));

        assertTrue(piece.contains("BlacksmithSettings.mostDamaged("));
        assertTrue(piece.contains("BlacksmithSettings.repairedDamage("));
        assertTrue(piece.contains("EquipmentSlot.HEAD") && piece.contains("EquipmentSlot.FEET"));
        assertTrue(piece.contains("if (index < 0) return;"), "no damaged piece: skip the repair");
    }

    @Test
    void alwaysOnBonusesComposeByMultiplyingAndNeverRollAChance() throws IOException {
        final String axe = read(AXE_SOURCE);
        final String bonuses = method(axe, "applyAxeBonuses");

        assertTrue(bonuses.contains("if (!isDirectMelee(event)) return;"), "melee only");
        assertTrue(method(axe, "onPlayerDamage").contains("Bukkit.isOwnedByCurrentRegion(damager) || !Bukkit.isOwnedByCurrentRegion(entity)"),
                "skip, never fall back, when a side is not owned");
        assertTrue(bonuses.contains("DamageModifiers.multiply(multiplier"), "bonuses multiply with each other");
        assertTrue(bonuses.contains("BarbarianSettings.isAxe(held)") && bonuses.contains("InsanitySettings.isSword(held)"));
        // Barbarian and Insanity read the TARGET's main hand: the equipment of `victim`, never of the attacker.
        assertTrue(bonuses.contains("final EntityEquipment equipment = victim.getEquipment();\n"
                        + "            final String held = equipment == null ? \"\" : equipment.getItemInMainHand().getType().name();"),
                "the held material comes from the victim's equipment");
        assertEquals(1, count(bonuses, "getEquipment()"), "only the victim's equipment is read");
        assertEquals(1, count(bonuses, "getItemInMainHand()"));
        assertFalse(bonuses.contains("damager.getEquipment()") || bonuses.contains("damager.getInventory()")
                || bonuses.contains("getItemInHand(damager)"), "the attacker's hand must not feed Barbarian or Insanity");
        assertTrue(bonuses.contains("barbarianLevel > 0 && BarbarianSettings.isAxe(held)"));
        assertTrue(bonuses.contains("insanityLevel > 0 && InsanitySettings.isSword(held)"));
        assertTrue(bonuses.contains("TuningConfig.barbarian().multiplier(barbarianLevel, true)"));
        assertTrue(bonuses.contains("TuningConfig.insanity().multiplier(insanityLevel, true)"));
        assertTrue(bonuses.contains("antiGank.count(hits, System.nanoTime())"), "the current target is not excluded");
        assertTrue(bonuses.contains("DamageModifiers.applyOutgoingBonus(event, baseDamage, event.getDamage() * (matchup - 1.0))"));
        assertTrue(bonuses.contains("rogue.onCooldown(") && bonuses.contains("rogue.claim("));
        assertTrue(bonuses.contains("facing.getX(), facing.getZ(), at.getX() - from.getX(), at.getZ() - from.getZ()"),
                "Rogue works in the horizontal plane");
        assertFalse(bonuses.contains("getY()"), "no vertical component may reach the angle");
        assertFalse(bonuses.contains("percentPick"), "always-on bonuses do not roll their own chance");
        assertFalse(bonuses.contains("Random"), "always-on bonuses do not roll their own chance");
    }

    @Test
    void antiGankTrackingIsPerWielderRecordsOnlyPlayerAttackersAndClearsWithRogue() throws IOException {
        final String axe = read(AXE_SOURCE);
        final String track = method(axe, "onAntiGankTrack");
        final String clear = method(axe, "clearPlayerState");

        assertTrue(track.contains("Bukkit.isOwnedByCurrentRegion(wielder)"));
        assertTrue(track.contains("source instanceof Player attacker"));
        assertTrue(track.contains("attacker.getUniqueId().equals(wielder.getUniqueId())"), "self damage is not an attacker");
        assertTrue(track.contains("TuningConfig.antiGank().record(hits, attacker.getUniqueId()"));
        assertTrue(track.contains("event.getFinalDamage()"), "a hit that did no damage is not recorded");
        assertTrue(axe.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onAntiGankTrack"),
                "a cancelled hit is not an attack");

        assertTrue(clear.contains("state.recentAttackers.remove(id)"));
        assertTrue(clear.contains("state.rogueCooldowns.remove(id)"));
        for (final String handler : List.of("onQuit", "onWorldChange", "onCombatDeath")) {
            assertTrue(method(axe, handler).contains("clearPlayerState("), handler + " must clear both maps");
        }
        assertTrue(method(axe, "clearCombatState").contains("this.combatState = new CombatState()"));
    }

    // ---- helpers ----

    private static int count(final String text, final String needle) {
        int count = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) count++;

        return count;
    }

    private static void assertChance(final String yaml, final String name, final int base, final int increase) {
        final Matcher chance = Pattern.compile("(?s)Chance-System:\\s*Base: (\\d+)\\s*Increase: (\\d+)").matcher(entry(yaml, name));

        assertTrue(chance.find(), name + " must declare Base/Increase");
        assertEquals(base, Integer.parseInt(chance.group(1)), name + " Base");
        assertEquals(increase, Integer.parseInt(chance.group(2)), name + " Increase");
    }

    /**
     * The text of one top-level Enchantments.yml entry, up to the next
     * "#Do not change this line." marker (or the end of the file).
     */
    private static String entry(final String yaml, final String name) {
        final Matcher start = Pattern.compile("(?m)^  " + Pattern.quote(name) + ":\\s*$").matcher(yaml);

        assertTrue(start.find(), "Enchantments.yml must define " + name);

        final int from = start.end();
        final int to = yaml.indexOf("#Do not change this line.", from);

        return to < 0 ? yaml.substring(from) : yaml.substring(from, to);
    }

    /**
     * The value of one key inside one enchant's Tuning block in Enchantments.yml.
     */
    private static String value(final String yaml, final String block, final String key) {
        final String enchant = TuningConfig.sectionKeys().get(block);

        assertNotNull(enchant, "no enchant key for tuning block " + block);

        final Matcher start = Pattern.compile("(?m)^  " + Pattern.quote(enchant) + ":[ \\t]*$").matcher(yaml);

        assertTrue(start.find(), "Enchantments.yml must define " + enchant);

        final Matcher next = Pattern.compile("(?m)^  [^ #]").matcher(yaml);
        final int from = start.end();
        final int to = next.find(from) ? next.start() : yaml.length();

        final Matcher line = Pattern.compile("(?m)^      " + Pattern.quote(key) + ": (.+?)\\s*$").matcher(yaml.substring(from, to));

        assertTrue(line.find(), "Enchantments.yml must define " + enchant + ".Tuning." + key);

        return line.group(1);
    }

    /**
     * The source of one method, from its declaration to its closing brace at four-space indent.
     */
    private static String method(final String source, final String name) {
        final Matcher start = Pattern.compile("(?m)^    (?:public |private )?(?:static )?\\w+ " + Pattern.quote(name) + "\\(").matcher(source);

        assertTrue(start.find(), "AxeEnchantments must declare " + name);

        final int end = source.indexOf("\n    }\n", start.start());

        assertTrue(end > 0, "could not find the end of " + name);

        return source.substring(start.start(), end + 6);
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative)).replace("\r\n", "\n");
    }
}
