package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeEnchantLogicTest {

    private static final double EPS = 1.0e-9;

    private static final String ENUM_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/api/enums/CEnchantments.java";

    private static final String ENCHANTMENTS_YML = "src/main/resources/Enchantments.yml";

    // ---- Assassin: always-on, +5% per level within 2.0 blocks, flat x0.90 beyond ----

    @Test
    void assassinBonusIsFivePercentPerLevelUpToOnePointTwoFive() {
        final AssassinSettings settings = AssassinSettings.DEFAULTS;

        assertEquals(1.05, settings.multiplier(1, 1.0), EPS);
        assertEquals(1.10, settings.multiplier(2, 1.0), EPS);
        assertEquals(1.15, settings.multiplier(3, 1.0), EPS);
        assertEquals(1.20, settings.multiplier(4, 1.0), EPS);
        assertEquals(1.25, settings.multiplier(5, 1.0), EPS);
    }

    @Test
    void assassinExactlyTwoBlocksStillGetsTheBonus() {
        final AssassinSettings settings = AssassinSettings.DEFAULTS;

        assertEquals(1.25, settings.multiplier(5, 2.0), EPS);
        assertEquals(1.05, settings.multiplier(1, 2.0), EPS);
        assertEquals(1.25, settings.multiplier(5, 0.0), EPS);
    }

    @Test
    void assassinLosesTheBonusAndGetsTheFlatPenaltyStrictlyBeyondTwoBlocks() {
        final AssassinSettings settings = AssassinSettings.DEFAULTS;

        for (int level = 1; level <= 5; level++) {
            assertEquals(0.9, settings.multiplier(level, Math.nextUp(2.0)), EPS);
            assertEquals(0.9, settings.multiplier(level, 2.5), EPS);
            assertEquals(0.9, settings.multiplier(level, 4.0), EPS);
            assertEquals(0.9, settings.multiplier(level, 50.0), EPS);
        }
    }

    @Test
    void assassinUnusableInputsLeaveTheHitAlone() {
        final AssassinSettings settings = AssassinSettings.DEFAULTS;

        assertEquals(1.0, settings.multiplier(0, 1.0), EPS);
        assertEquals(1.0, settings.multiplier(-3, 5.0), EPS);
        assertEquals(1.0, settings.multiplier(3, Double.NaN), EPS);
        // Levels past V use the V bonus.
        assertEquals(1.25, settings.multiplier(9, 1.0), EPS);
    }

    @Test
    void assassinValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(AssassinSettings.DEFAULTS, AssassinSettings.validated(5.0, 2.0, 0.9, warnings::add));
        assertTrue(warnings.isEmpty());

        final AssassinSettings bad = AssassinSettings.validated(Double.NaN, -1.0, 5.0, warnings::add);

        assertEquals(AssassinSettings.DEFAULTS, bad);
        assertEquals(3, warnings.size());
    }

    // ---- Enrage: always-on, 10 x level x missingFraction percent ----

    @Test
    void enrageRampsWithMissingHealthAtLevelThree() {
        final EnrageSettings settings = EnrageSettings.DEFAULTS;

        assertEquals(30.0, settings.bonusPp(3, 0.0, 20.0), EPS, "100% missing is +30% at III");
        assertEquals(15.0, settings.bonusPp(3, 10.0, 20.0), EPS, "50% missing is +15% at III");
        assertEquals(7.5, settings.bonusPp(3, 15.0, 20.0), EPS, "25% missing is +7.5% at III");
        assertEquals(1.30, settings.multiplier(3, 0.0, 20.0), EPS);
        assertEquals(1.15, settings.multiplier(3, 10.0, 20.0), EPS);
    }

    @Test
    void enrageScalesTenPointsPerLevelAndHasNoThreshold() {
        final EnrageSettings settings = EnrageSettings.DEFAULTS;

        assertEquals(10.0, settings.bonusPp(1, 0.0, 20.0), EPS);
        assertEquals(20.0, settings.bonusPp(2, 0.0, 20.0), EPS);
        assertEquals(30.0, settings.bonusPp(3, 0.0, 20.0), EPS);
        // Above 50% health still earns a (smaller) bonus: it is a ramp, not a threshold.
        assertEquals(3.0, settings.bonusPp(3, 18.0, 20.0), EPS);
        assertEquals(1.03, settings.multiplier(3, 18.0, 20.0), EPS);
    }

    @Test
    void enrageGivesNothingAtFullHealthAndIgnoresUnusableInputs() {
        final EnrageSettings settings = EnrageSettings.DEFAULTS;

        assertEquals(0.0, settings.bonusPp(3, 20.0, 20.0), EPS);
        assertEquals(1.0, settings.multiplier(3, 20.0, 20.0), EPS);
        // Health above max (for example an inflated reading) never goes negative or past the cap.
        assertEquals(0.0, settings.bonusPp(3, 24.0, 20.0), EPS);
        assertEquals(30.0, settings.bonusPp(3, -4.0, 20.0), EPS);
        assertEquals(0.0, settings.bonusPp(0, 1.0, 20.0), EPS);
        assertEquals(0.0, settings.bonusPp(3, 1.0, 0.0), EPS);
        assertEquals(0.0, settings.bonusPp(3, Double.NaN, 20.0), EPS);
        assertEquals(0.0, EnrageSettings.missingFraction(Double.NaN, 20.0), EPS);
    }

    @Test
    void enrageMissingFractionUsesMaxHealth() {
        assertEquals(0.5, EnrageSettings.missingFraction(20.0, 40.0), EPS);
        assertEquals(0.75, EnrageSettings.missingFraction(5.0, 20.0), EPS);
        assertEquals(15.0, EnrageSettings.DEFAULTS.bonusPp(3, 20.0, 40.0), EPS);
    }

    @Test
    void enrageValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(EnrageSettings.DEFAULTS, EnrageSettings.validated(-1.0, warnings::add));
        assertEquals(EnrageSettings.DEFAULTS, EnrageSettings.validated(Double.NaN, warnings::add));
        assertEquals(2, warnings.size());
        assertEquals(12.0, EnrageSettings.validated(12.0, warnings::add).ppPerLevel(), EPS);
    }

    @Test
    void assassinAndEnrageComposeByMultiplyingWithoutASharedBudget() {
        final double far = AssassinSettings.DEFAULTS.multiplier(3, 10.0);
        final double near = AssassinSettings.DEFAULTS.multiplier(5, 1.0);
        final double enrage = EnrageSettings.DEFAULTS.multiplier(3, 0.0, 20.0);

        assertEquals(0.9 * 1.30, DamageModifiers.multiply(far, enrage), EPS);
        assertEquals(1.25 * 1.30, DamageModifiers.multiply(near, enrage), EPS);
    }

    // ---- Block: chance 6 / +3, reflect 25/35/45% ----

    @Test
    void blockReflectsTwentyFiveThirtyFiveFortyFivePercentPerLevel() {
        final BlockSettings settings = BlockSettings.DEFAULTS;

        assertEquals(2.5, settings.reflectAmount(1, 10.0), EPS);
        assertEquals(3.5, settings.reflectAmount(2, 10.0), EPS);
        assertEquals(4.5, settings.reflectAmount(3, 10.0), EPS);
        assertEquals(25.0, settings.reflectPercent(1), EPS);
        assertEquals(35.0, settings.reflectPercent(2), EPS);
        assertEquals(45.0, settings.reflectPercent(3), EPS);
    }

    @Test
    void blockReflectsNothingForUnusableHitsOrLevels() {
        final BlockSettings settings = BlockSettings.DEFAULTS;

        assertEquals(0.0, settings.reflectAmount(0, 10.0), EPS);
        assertEquals(0.0, settings.reflectAmount(3, 0.0), EPS);
        assertEquals(0.0, settings.reflectAmount(3, -4.0), EPS);
        assertEquals(0.0, settings.reflectAmount(3, Double.NaN), EPS);
        assertEquals(0.0, settings.reflectAmount(3, Double.POSITIVE_INFINITY), EPS);
        // Past level III uses the III share.
        assertEquals(4.5, settings.reflectAmount(7, 10.0), EPS);
    }

    @Test
    void blockNeverReEntersTheReflectPathOrProcsOnACancelledHit() {
        assertTrue(BlockSettings.mayProc(false, false));
        assertFalse(BlockSettings.mayProc(true, false), "a reflection or Bleed tick must not roll Block");
        assertFalse(BlockSettings.mayProc(false, true), "a hit another plugin cancelled must not proc");
        assertFalse(BlockSettings.mayProc(true, true));
    }

    @Test
    void blockReflectionDoesNotCompoundWhenBothSidesWieldBlock() {
        // Model of the listener: the first hit may proc; the reflected hit is synthetic, so it never may.
        final BlockSettings settings = BlockSettings.DEFAULTS;

        int reflections = 0;
        double damage = 10.0;
        boolean synthetic = false;

        for (int hop = 0; hop < 5; hop++) {
            if (!BlockSettings.mayProc(synthetic, false)) break;

            damage = settings.reflectAmount(3, damage);
            reflections++;
            synthetic = true;
        }

        assertEquals(1, reflections);
        assertEquals(4.5, damage, EPS);
    }

    @Test
    void blockValidationRejectsOutOfRangeShares() {
        final List<String> warnings = new ArrayList<>();

        final BlockSettings bad = BlockSettings.validated(-1.0, 101.0, Double.NaN, warnings::add);

        assertEquals(BlockSettings.DEFAULTS, bad);
        assertEquals(3, warnings.size());
    }

    // ---- Thundering Blow: chance 5 / +3, flat +1/+2/+3 HP ----

    @Test
    void thunderingBlowAddsOneTwoThreeFlatHp() {
        final ThunderingBlowSettings settings = ThunderingBlowSettings.DEFAULTS;

        assertEquals(1.0, settings.bonusHp(1), EPS);
        assertEquals(2.0, settings.bonusHp(2), EPS);
        assertEquals(3.0, settings.bonusHp(3), EPS);
    }

    @Test
    void thunderingBlowBonusHasNoUnexpectedLevelsOrNegatives() {
        final ThunderingBlowSettings settings = ThunderingBlowSettings.DEFAULTS;

        assertEquals(0.0, settings.bonusHp(0), EPS);
        assertEquals(0.0, settings.bonusHp(-2), EPS);
        assertEquals(3.0, settings.bonusHp(9), EPS);

        final List<String> warnings = new ArrayList<>();

        assertEquals(ThunderingBlowSettings.DEFAULTS, ThunderingBlowSettings.validated(-1.0, warnings::add));
        assertEquals(1, warnings.size());
        assertEquals(1.5, ThunderingBlowSettings.validated(1.5, warnings::add).hpPerLevel(), EPS);
    }

    // ---- Registry: chance systems agree between the enum and Enchantments.yml ----

    @Test
    void assassinAndEnrageAreAlwaysOnInTheEnumAndTheYaml() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertTrue(source.contains("ASSASSIN(\"Assassin\", \"Sword\"),"), "Assassin must have no chance values");
        assertTrue(source.contains("ENRAGE(\"Enrage\", \"Sword\"),"), "Enrage must have no chance values");

        assertFalse(entry(yaml, "Assassin").contains("Chance-System"), "Assassin must not declare a Chance-System");
        assertFalse(entry(yaml, "Enrage").contains("Chance-System"), "Enrage must not declare a Chance-System");
    }

    @Test
    void blockAndThunderingBlowUseTheApprovedOddsInTheEnumAndTheYaml() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertTrue(source.contains("BLOCK(\"Block\", \"Sword\", 6, 3)"), "Block must be 6 / +3");
        assertTrue(source.contains("THUNDERING_BLOW(\"Thundering Blow\", \"Sword\", 5, 3)"), "Thundering Blow must be 5 / +3");

        assertChance(yaml, "Block", 6, 3);
        assertChance(yaml, "Thundering Blow", 5, 3);

        assertEquals(12, 6 + 3 * (3 - 1), "Block is 12% at III");
        assertEquals(11, 5 + 3 * (3 - 1), "Thundering Blow is 11% at III");
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

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative));
    }
}
