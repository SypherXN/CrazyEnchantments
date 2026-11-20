package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.AngelicSettings;
import com.badbones69.crazyenchantments.paper.api.utils.LifebloomSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SpiritLinkSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SpiritsSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ArmorEnchantLogicTest3 {

    private static final UUID WEARER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static final long SECOND = 1_000_000_000L;
    private static final String JAVA = "src/main/java/com/badbones69/crazyenchantments/paper/";
    private static final String ARMOR = JAVA + "enchantments/ArmorEnchantments.java";

    @Test
    void angelicHealsHalfHpEachSecond() {
        assertEquals(0.5, AngelicSettings.DEFAULTS.healPerSecond());
        assertEquals(20L, AngelicSettings.PULSE_TICKS);
    }

    @Test
    void angelicLastsFivePulses() {
        assertEquals(5.0, AngelicSettings.DEFAULTS.durationSeconds());
        assertEquals(100L, AngelicSettings.DEFAULTS.durationTicks());
        assertEquals(5L, AngelicSettings.DEFAULTS.durationTicks() / AngelicSettings.PULSE_TICKS);
    }

    @Test
    void angelicNeverReplacesAnActiveRun() {
        final var active = new ConcurrentHashMap<UUID, Object>();
        final Object first = new Object();
        assertTrue(AngelicSettings.claim(active, WEARER, first));
        assertFalse(AngelicSettings.claim(active, WEARER, new Object()));
        assertSame(first, active.get(WEARER));
        assertTrue(AngelicSettings.claim(active, OTHER, new Object()));
        assertTrue(active.remove(WEARER, first));
        assertTrue(AngelicSettings.claim(active, WEARER, new Object()));
    }

    @Test
    void angelicRejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new AngelicSettings(Double.NaN, 5.0));
        assertThrows(IllegalArgumentException.class, () -> new AngelicSettings(0.5, 0.0));
    }

    @Test
    void spiritsCountFollowsLevelTable() {
        final int[] expected = {1, 1, 1, 2, 2, 2, 3, 3, 3, 4};
        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], SpiritsSettings.DEFAULTS.blazeCount(level), "level " + level);
        }
    }

    @Test
    void spiritsNeverSummonsMoreThanFourOrAtLevelZero() {
        assertEquals(0, SpiritsSettings.DEFAULTS.blazeCount(0));
        assertEquals(0, SpiritsSettings.DEFAULTS.blazeCount(Integer.MIN_VALUE));
        assertEquals(4, SpiritsSettings.DEFAULTS.blazeCount(Integer.MAX_VALUE));
    }

    @Test
    void spiritsHealsOneHpWithinFourBlocksPerBlaze() {
        assertEquals(1.0, SpiritsSettings.DEFAULTS.heal());
        assertEquals(4.0, SpiritsSettings.DEFAULTS.radius());
    }

    @Test
    void spiritsCadenceIsTwoSecondsWithinATenSecondLifetime() {
        final SpiritsSettings settings = SpiritsSettings.DEFAULTS;
        assertEquals(2.0, settings.cadenceSeconds());
        assertEquals(40L, settings.cadenceTicks());
        assertEquals(10.0, settings.lifetimeSeconds());
        assertEquals(200L, settings.lifetimeTicks());
    }

    @Test
    void spiritsCooldownBoundaryIsThirtySecondsPerWearer() {
        final SpiritsSettings settings = SpiritsSettings.DEFAULTS;
        final var cooldowns = new ConcurrentHashMap<UUID, Long>();
        assertEquals(30.0, settings.cooldownSeconds());
        assertTrue(settings.claim(cooldowns, WEARER, 0));
        assertFalse(settings.claim(cooldowns, WEARER, 30 * SECOND - 1));
        assertTrue(settings.claim(cooldowns, OTHER, 30 * SECOND - 1));
        assertTrue(settings.claim(cooldowns, WEARER, 30 * SECOND));
        assertFalse(settings.claim(cooldowns, WEARER, 30 * SECOND + 1));
    }

    @Test
    void spiritsRejectsInvalidCadenceAndLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new SpiritsSettings(4, 1, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SpiritsSettings(4, 1, 2, Double.POSITIVE_INFINITY, 30));
    }

    @Test
    void spiritLinkHealFollowsLevelTable() {
        final double[] expected = {1.0, 1.25, 1.5, 1.75, 2.0};
        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], SpiritLinkSettings.DEFAULTS.heal(level), "level " + level);
        }
        assertEquals(0.0, SpiritLinkSettings.DEFAULTS.heal(0));
        assertEquals(2.0, SpiritLinkSettings.DEFAULTS.heal(99));
    }

    @Test
    void spiritLinkRadiusIsSixBlocks() {
        assertEquals(6.0, SpiritLinkSettings.DEFAULTS.radius());
        assertThrows(IllegalArgumentException.class, () -> new SpiritLinkSettings(-1, 1, 0.25, 2));
    }

    @Test
    void spiritLinkCooldownBoundaryIsTwoSecondsAndFailureDoesNotRefresh() {
        final SpiritLinkSettings settings = SpiritLinkSettings.DEFAULTS;
        final var cooldowns = new ConcurrentHashMap<UUID, Long>();
        assertEquals(2.0, settings.cooldownSeconds());
        assertTrue(settings.claim(cooldowns, WEARER, -50));
        assertFalse(settings.claim(cooldowns, WEARER, 2 * SECOND - 51));
        assertTrue(settings.claim(cooldowns, WEARER, 2 * SECOND - 50));
        assertFalse(settings.claim(cooldowns, WEARER, 2 * SECOND - 49));
    }

    @Test
    void lifebloomRadiusIsEightBlocks() {
        assertEquals(8.0, LifebloomSettings.DEFAULTS.radius());
        assertThrows(IllegalArgumentException.class, () -> new LifebloomSettings(Double.NaN));
    }

    @Test
    void supportOnlyHealsAlliesAndNeverTheTriggeringAttacker() {
        assertTrue(LifebloomSettings.eligible(false, false, false, false, true));
        assertFalse(LifebloomSettings.eligible(false, false, false, false, false));
        assertFalse(LifebloomSettings.eligible(false, true, false, false, true));
        assertFalse(LifebloomSettings.eligible(false, true, false, true, true));
    }

    @Test
    void onlySpiritsCanIncludeTheWearer() {
        assertTrue(LifebloomSettings.eligible(false, false, true, true, false));
        assertFalse(LifebloomSettings.eligible(false, false, true, false, true));
        assertFalse(LifebloomSettings.eligible(true, false, true, true, true));
    }

    @Test
    void deadOrZeroHealthEntitiesAreNotHealed() {
        assertFalse(LifebloomSettings.eligible(true, false, false, true, true));
        assertEquals(7.0, LifebloomSettings.healedHealth(true, 7.0, 20.0, 100.0));
        assertEquals(0.0, LifebloomSettings.healedHealth(false, 0.0, 20.0, 100.0));
    }

    @Test
    void healingClampsAndLifebloomFillsTheActualMaxHealth() {
        assertEquals(10.5, LifebloomSettings.healedHealth(false, 10.0, 20.0, 0.5));
        assertEquals(20.0, LifebloomSettings.healedHealth(false, 19.75, 20.0, 0.5));
        assertEquals(20.0, LifebloomSettings.healedHealth(false, 20.0, 20.0, 1.0));
        assertEquals(41.0, LifebloomSettings.healedHealth(false, 1.0, 41.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void allFourChanceBlocksMatchTheEnumAndExpectedMetadata() throws IOException {
        final String source = read(JAVA + "api/enums/CEnchantments.java");
        final String yaml = read("src/main/resources/Enchantments.yml");
        checkEnchant(source, yaml, "ANGELIC", "Angelic", "Armor", "Ultimate", 5, 6, 3);
        checkEnchant(source, yaml, "SPIRITS", "Spirits", "Armor", "Ultimate", 10, 4, 1);
        checkEnchant(source, yaml, "SPIRIT_LINK", "Spirit Link", "Chestplate", "Elite", 5, 10, 5);
        checkEnchant(source, yaml, "LIFEBLOOM", "Lifebloom", "Leggings", "Unique", 5, 10, 5);
    }

    @Test
    void hitAndDeathHandlersUseCorrectSlotsAndShareThePerPieceRoll() throws IOException {
        final String source = read(ARMOR);
        final String hit = method(source, "onSupportDamage");
        assertTrue(hit.contains("combinedPieceAttempt(wearer, CEnchantments.ANGELIC, armor)"),
                "Angelic is one combined attempt over every armor piece");
        assertTrue(hit.contains("combinedPieceAttempt(wearer, CEnchantments.SPIRITS, armor)"),
                "Spirits is one combined attempt for the selected proc, not one event per piece");
        assertTrue(hit.contains("CEnchantments.SPIRIT_LINK, wearer.getEquipment().getChestplate()"));
        assertTrue(method(source, "onSupportDeath").contains("CEnchantments.LIFEBLOOM, wearer.getEquipment().getLeggings()"));
        assertTrue(method(source, "pieceLevels").contains("EnchantUtils.isEventActive(enchant, wearer, item, enchants)"));
    }

    @Test
    void angelicCombinesThePiecesIntoSingleAttempt() {
        assertEquals(0.06, AngelicSettings.combinedChance(6.0), 1.0e-9, "one piece keeps its own chance");
        assertEquals(0.1164, AngelicSettings.combinedChance(6.0, 6.0), 1.0e-9, "two pieces combine, they do not add");
        assertEquals(1.0, AngelicSettings.combinedChance(6.0, 100.0), 1.0e-9, "a certain piece dominates");
        assertEquals(0.0, AngelicSettings.combinedChance(), 1.0e-9);

        assertEquals(6.0, AngelicSettings.procChancePp(6, 3, 1), 1.0e-9);
        assertEquals(12.0, AngelicSettings.procChancePp(6, 3, 3), 1.0e-9, "base plus the per-level increase");
        assertEquals(100.0, AngelicSettings.procChancePp(95, 10, 4), 1.0e-9, "clamped at a certain proc");
    }

    @Test
    void angelicFiresOneUseEventAndOnlyOneHealingRun() throws IOException {
        final String source = read(ARMOR);
        final String attempt = method(source, "combinedPieceAttempt");

        assertEquals(attempt.indexOf("normalEnchantEvent("), attempt.lastIndexOf("normalEnchantEvent("),
                "one use event decides the whole combined attempt");
        assertTrue(attempt.contains("AngelicSettings.combinedChance("), "the roll uses the combined odds");
        assertTrue(attempt.indexOf("combinedChance(") < attempt.indexOf("normalEnchantEvent("),
                "the roll happens before the event is fired");

        final String hit = method(source, "onSupportDamage");

        assertTrue(hit.contains("!this.angelicRuns.containsKey(id)"), "no new run starts while one is healing");
    }

    @Test
    void spiritLinkChecksTheCooldownBeforeItRollsOrEmits() throws IOException {
        final String hit = method(read(ARMOR), "onSupportDamage");

        assertTrue(hit.contains("settings.ready(this.spiritLinkCooldowns, id, now)"),
                "an enchant on cooldown neither rolls nor fires its use event");
        assertTrue(hit.indexOf("settings.ready(") < hit.indexOf("pieceLevels(wearer, CEnchantments.SPIRIT_LINK"),
                "eligibility comes first");
        assertTrue(hit.indexOf("settings.ready(") < hit.indexOf("settings.claim("),
                "the claim only follows an accepted proc");

        final SpiritLinkSettings settings = SpiritLinkSettings.DEFAULTS;
        final var cooldowns = new ConcurrentHashMap<UUID, Long>();

        assertTrue(settings.ready(cooldowns, WEARER, 0L), "nothing claimed yet");
        assertTrue(settings.claim(cooldowns, WEARER, 0L));
        assertFalse(settings.ready(cooldowns, WEARER, 2 * SECOND - 1), "still cooling down");
        assertTrue(settings.ready(cooldowns, WEARER, 2 * SECOND), "the boundary is inclusive");
    }

    @Test
    void scheduledEffectsUseWearerOwnershipAndStopOnReload() throws IOException {
        final String source = read(ARMOR);
        assertFalse(source.contains("Bukkit.getScheduler()"));
        final String schedule = method(source, "scheduleSupportRun");
        assertTrue(schedule.contains("wearer.getScheduler().runAtFixedRate(this.plugin"));
        assertTrue(schedule.contains("!current.getAsBoolean()"));
        assertTrue(method(source, "startSpirits").contains("settings == TuningConfig.spirits()"));
        assertTrue(method(source, "startAngelic").contains("settings == TuningConfig.angelic()"));
    }

    @Test
    void supportBlazesArePassiveAndCannotDealDamageOrDropLoot() throws IOException {
        final String source = read(ARMOR);
        final String configure = method(source, "configureSpirit");
        assertTrue(configure.contains("setAI(false)") && configure.contains("setTarget(null)"));
        assertTrue(configure.contains("setInvulnerable(true)") && configure.contains("setPersistent(false)"));
        assertTrue(method(source, "onSpiritTarget").contains("isSpirit(event.getTarget())"));
        assertTrue(method(source, "onSpiritDamage").contains("isSpirit(attacker(event))"));
        final String death = method(source, "onSpiritDeath");
        assertTrue(death.contains("getDrops().clear()") && death.contains("setDroppedExp(0)"));
    }

    @Test
    void wearersDeathQuitAndDisableCancelTasksAndRemoveSpirits() throws IOException {
        final String source = read(ARMOR);
        assertTrue(method(source, "onSupportDeath").contains("stopSupportRuns("));
        assertTrue(method(source, "onArmorQuit").contains("stopSupportRuns("));
        assertTrue(method(source, "stopSupportRuns").contains("finishSupportRun(this.spiritsRuns"));
        assertTrue(method(source, "stop").contains("finishSupportRun(this.spiritsRuns"));
        final String finish = method(source, "finishSupportRun");
        assertTrue(finish.contains("run.task.cancel()") && finish.contains("removeSpirit(blaze,"));
        assertTrue(method(source, "removeSpirit").contains("Bukkit.isOwnedByCurrentRegion(blaze)"));
    }

    @Test
    void spawnChecksRegionAndTwoAirBlocksBeforeAddingEntity() throws IOException {
        final String spawn = method(read(ARMOR), "spawnSpirits");
        final int ownership = spawn.indexOf("Bukkit.isOwnedByCurrentRegion(at)");
        assertTrue(ownership >= 0 && ownership < spawn.indexOf("world.spawn("));
        assertTrue(spawn.contains("at.getBlock().getType().isAir()"));
        assertTrue(spawn.contains("getRelative(0, 1, 0).getType().isAir()"));
    }

    @Test
    void everyShippedTuningKeyIsValidatedAndMatchesTheRecordDefault() throws IOException {
        final String yaml = read("src/main/resources/Enchantments.yml");
        final String tuning = read(JAVA + "api/utils/TuningConfig.java");
        final var angelic = AngelicSettings.DEFAULTS;
        final var spirits = SpiritsSettings.DEFAULTS;
        final var link = SpiritLinkSettings.DEFAULTS;
        Map.of(
                "Angelic", Map.of("Heal-Per-Second", angelic.healPerSecond(), "Duration-Seconds", angelic.durationSeconds()),
                "Spirits", Map.of("Radius", spirits.radius(), "Heal", spirits.heal(),
                        "Cadence-Seconds", spirits.cadenceSeconds(), "Lifetime-Seconds", spirits.lifetimeSeconds(),
                        "Cooldown-Seconds", spirits.cooldownSeconds()),
                "SpiritLink", Map.of("Radius", link.radius(), "Base-Heal", link.baseHeal(),
                        "Heal-Per-Level", link.healPerLevel(), "Cooldown-Seconds", link.cooldownSeconds()),
                "Lifebloom", Map.of("Radius", LifebloomSettings.DEFAULTS.radius())
        ).forEach((name, keys) -> {
            final String enchant = TuningConfig.sectionKeys().get(name);
            assertNotNull(enchant, "no enchant key for tuning block " + name);
            final var block = Pattern.compile("(?ms)^  " + Pattern.quote(enchant) + ":\\n(.*?)(?=^  \\S|\\z)").matcher(yaml);
            assertTrue(block.find(), name);
            keys.forEach((key, value) -> {
                assertTrue(block.group(1).contains("      " + key + ": " + value + "\n"), name + "." + key);
                assertTrue(tuning.contains("values.number(\"" + name + "." + key + "\""), name + "." + key);
            });
        });
    }

    private static void checkEnchant(final String source, final String yaml, final String constant, final String name,
                                     final String type, final String category, final int max, final int base, final int increase) {
        assertTrue(source.contains("%s(\"%s\", \"%s\", %d, %d)".formatted(constant, name, type, base, increase)));
        final var entry = Pattern.compile("(?ms)^  " + Pattern.quote(name) + ":\\n(.*?)(?=^  \\S|\\z)").matcher(yaml);
        assertTrue(entry.find(), name);
        final String body = entry.group(1);
        assertTrue(body.contains("MaxPower: " + max + "\n"), name);
        assertTrue(body.contains("Enchantment-Type: '" + type + "'"), name);
        assertTrue(body.contains("Categories:\n      - '" + category + "'"), name);
        assertTrue(body.contains("Chance-System:\n      Base: " + base + "\n      Increase: " + increase + "\n"), name);
    }

    private static String method(final String source, final String name) {
        final var start = Pattern.compile("(?m)^    (?:public |private )(?:static )?\\S+ " + name + "\\(").matcher(source);
        assertTrue(start.find(), name);
        final int end = source.indexOf("\n    }", start.end());
        assertTrue(end > start.end(), name);
        return source.substring(start.start(), end);
    }

    private static String read(final String relative) throws IOException {
        final Path path = Path.of(relative);
        return Files.readString(Files.exists(path) ? path : Path.of("paper").resolve(path)).replace("\r\n", "\n");
    }
}
