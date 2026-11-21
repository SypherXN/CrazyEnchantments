package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.DemonForgedSettings;
import com.badbones69.crazyenchantments.paper.api.utils.EnderShiftSettings;
import com.badbones69.crazyenchantments.paper.api.utils.ExecuteSettings;
import com.badbones69.crazyenchantments.paper.api.utils.ImplantsSettings;
import com.badbones69.crazyenchantments.paper.api.utils.RocketEscapeSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.api.utils.ValorSettings;
import com.badbones69.crazyenchantments.paper.api.utils.VampireSettings;
import com.badbones69.crazyenchantments.paper.api.utils.WitherSettings;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eight CE enchants that now are their CosmicPvP counterparts: Valor, Implants, Rocket Escape
 * (was Rocket), Ender Shift (was Adrenaline), Vampire, Execute, Wither (sword to armor) and
 * DemonForged (axe to sword and axe). Pure math through the settings records, plus the shipped
 * registry (enum, Enchantments.yml, Enchantment-Types.yml, Tinker.yml) and the handler sources.
 */
class CosmicReworkLogicTest {

    private static final Path JAVA = Path.of("src/main/java/com/badbones69/crazyenchantments/paper");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static final Pattern ENUM_ENTRY = Pattern.compile(
            "(?m)^ {4}([A-Z][A-Z0-9_]*)\\(\"([^\"]+)\", \"([^\"]+)\"(?:, (\\d+), (\\d+))?\\)\\s*[,;]?\\s*$");

    // ---------------------------------------------------------------------------------------------
    // Valor
    // ---------------------------------------------------------------------------------------------

    @Test
    void valorFullSetAtVIsTwentyTwoPointFivePoints() {
        final ValorSettings valor = ValorSettings.DEFAULTS;

        assertEquals(5.625, valor.piecePp(5), 1.0e-9, "one V piece");
        assertEquals(22.5, 4 * valor.piecePp(5), 1.0e-9, "four V pieces: the DOC 22.5%");
        assertEquals(0.0, valor.piecePp(0), "no level, no reduction");
    }

    @Test
    void valorCountsOnlyAHeldSword() {
        assertTrue(ValorSettings.isSword("DIAMOND_SWORD"));
        assertTrue(ValorSettings.isSword("WOODEN_SWORD"));
        assertFalse(ValorSettings.isSword("DIAMOND_AXE"));
        assertFalse(ValorSettings.isSword("AIR"));
        assertFalse(ValorSettings.isSword(null));
    }

    // ---------------------------------------------------------------------------------------------
    // Implants
    // ---------------------------------------------------------------------------------------------

    @Test
    void implantsIntervalShortensWithLevelAndConvertsToTicks() {
        final ImplantsSettings implants = ImplantsSettings.DEFAULTS;

        assertEquals(120L, implants.intervalTicks(1));
        assertEquals(100L, implants.intervalTicks(2));
        assertEquals(80L, implants.intervalTicks(3));
        assertEquals(80L, implants.intervalTicks(9), "a level past the table uses the last entry");
    }

    @Test
    void implantsRestoresAreCappedAtMaxHealthAndAFullFoodBar() {
        final ImplantsSettings implants = ImplantsSettings.DEFAULTS;

        assertEquals(11.0, implants.healed(10.0, 20.0));
        assertEquals(20.0, implants.healed(19.5, 20.0), "capped at max health");
        assertEquals(20.0, implants.healed(20.0, 20.0));
        assertEquals(0.0, implants.healed(0.0, 20.0), "a dead wearer is never healed");
        assertEquals(15, implants.fed(14));
        assertEquals(20, implants.fed(20), "capped at a full bar");
    }

    @Test
    void implantsDoesNothingAtFullHealthAndFood() {
        final ImplantsSettings implants = ImplantsSettings.DEFAULTS;

        assertFalse(implants.needs(20.0, 20.0, 20));
        assertTrue(implants.needs(19.0, 20.0, 20), "health alone is enough");
        assertTrue(implants.needs(20.0, 20.0, 19), "hunger alone is enough");
    }

    // ---------------------------------------------------------------------------------------------
    // Rocket Escape
    // ---------------------------------------------------------------------------------------------

    @Test
    void rocketEscapeTriggersOnlyAtOrBelowThirtyPercent() {
        final RocketEscapeSettings rocket = RocketEscapeSettings.DEFAULTS;

        assertTrue(rocket.triggers(6.0, 20.0), "exactly 30% triggers");
        assertTrue(rocket.triggers(1.0, 20.0));
        assertFalse(rocket.triggers(6.5, 20.0));
        assertFalse(rocket.triggers(0.0, 20.0), "a dead wearer never triggers");
        assertFalse(rocket.triggers(5.0, 0.0), "no max health, no trigger");
    }

    @Test
    void rocketEscapeCooldownIsPerLevelAndExactlyElapsedIsReady() {
        final RocketEscapeSettings rocket = RocketEscapeSettings.DEFAULTS;
        final long second = 1_000_000_000L;

        assertTrue(rocket.ready(null, 0L, 1), "no previous launch");
        assertFalse(rocket.ready(0L, 29L * second, 1));
        assertTrue(rocket.ready(0L, 30L * second, 1), "elapsed == cooldown is allowed again");
        assertFalse(rocket.ready(0L, 24L * second, 2));
        assertTrue(rocket.ready(0L, 25L * second, 2));
        assertFalse(rocket.ready(0L, 19L * second, 3));
        assertTrue(rocket.ready(0L, 20L * second, 3));
        assertEquals(9.0, rocket.velocity(1));
        assertEquals(10.0, rocket.velocity(2));
        assertEquals(11.0, rocket.velocity(3));
    }

    @Test
    void rocketEscapeFallImmunityIsAPerPlayerDeadlineThatAlwaysExpires() {
        final long window = RocketEscapeSettings.FALL_IMMUNITY_NANOS;
        final long launch = 1_000_000_000L;

        assertEquals(10_000_000_000L, window, "the launch covers ten seconds of landing");
        assertTrue(RocketEscapeSettings.fallImmunityLive(launch + window, launch + window - 1),
                "live until the last nanosecond of the window");
        assertFalse(RocketEscapeSettings.fallImmunityLive(launch + window, launch + window),
                "expires exactly at the window, so immunity is never permanent");
        assertFalse(RocketEscapeSettings.fallImmunityLive(null, launch), "no launch, no immunity");
    }

    @Test
    void rocketEscapeFallImmunityNeedsNoDelayedRemoval() throws IOException {
        final String armor = Files.readString(JAVA.resolve("enchantments/ArmorEnchantments.java"));

        assertTrue(armor.contains("fallImmunityUntil"), "one monotonic deadline per player");
        assertFalse(armor.contains("fallenPlayers"),
                "a shared list plus a delayed removal leaks immunity when the task is dropped");
    }

    // ---------------------------------------------------------------------------------------------
    // Ender Shift
    // ---------------------------------------------------------------------------------------------

    @Test
    void enderShiftStartsAtThirtyFiveAndEndsOnlyAboveForty() {
        final EnderShiftSettings shift = EnderShiftSettings.DEFAULTS;

        assertFalse(shift.nextActive(false, 8.0, 20.0), "40% is not low enough to start");
        assertTrue(shift.nextActive(false, 7.0, 20.0), "exactly 35% starts");
        assertTrue(shift.nextActive(true, 8.0, 20.0), "40% keeps an active shift: hysteresis");
        assertFalse(shift.nextActive(true, 8.5, 20.0), "above 40% ends it");
        assertFalse(shift.nextActive(false, 0.0, 20.0), "a dead wearer never starts");
    }

    @Test
    void enderShiftSpeedAmplifierRisesAtIII() {
        final EnderShiftSettings shift = EnderShiftSettings.DEFAULTS;

        assertEquals(0, shift.speedAmplifier(1));
        assertEquals(0, shift.speedAmplifier(2));
        assertEquals(1, shift.speedAmplifier(3));
        assertEquals(100, shift.effectTicks());
    }

    @Test
    void enderShiftRejectsAClearCutBelowTheTriggerCut() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnderShiftSettings(40.0, 35.0, List.of(0.0, 0.0, 1.0), 0, 5.0));
    }

    // ---------------------------------------------------------------------------------------------
    // Vampire
    // ---------------------------------------------------------------------------------------------

    @Test
    void vampireHealsOneTwoThreeAndNeverMoreThanThree() {
        final VampireSettings vampire = VampireSettings.DEFAULTS;

        assertEquals(1.0, vampire.heal(1));
        assertEquals(2.0, vampire.heal(2));
        assertEquals(3.0, vampire.heal(3));
        assertEquals(60L, vampire.delayTicks());
        assertThrows(IllegalArgumentException.class, () -> new VampireSettings(List.of(1.0, 2.0, 3.5), 3.0),
                "the DOC ceiling of 3 HP cannot be tuned past");
    }

    @Test
    void vampireHealIsCappedAtMaxHealthAndSkipsTheDead() {
        assertEquals(20.0, VampireSettings.healed(19.0, 20.0, 3.0));
        assertEquals(13.0, VampireSettings.healed(10.0, 20.0, 3.0));
        assertEquals(0.0, VampireSettings.healed(0.0, 20.0, 3.0), "a wielder who died gets nothing");
    }

    // ---------------------------------------------------------------------------------------------
    // Execute
    // ---------------------------------------------------------------------------------------------

    @Test
    void executeUsesItsOwnThirtyPercentThresholdNotTheHeroicSixHp() {
        final ExecuteSettings execute = ExecuteSettings.DEFAULTS;

        // A 100 HP boss at 30 HP is low: a flat "under 6 HP" rule would not fire here.
        assertTrue(execute.belowThreshold(30.0, 100.0));
        assertFalse(execute.belowThreshold(31.0, 100.0));
        // A 20 HP player at 7 HP is not low: a flat "under 6 HP" rule would not fire, and neither does this.
        assertFalse(execute.belowThreshold(7.0, 20.0));
        assertTrue(execute.belowThreshold(6.0, 20.0));
    }

    @Test
    void executeBonusIsThreePointsPerLevelAndZeroAboveTheThreshold() {
        final ExecuteSettings execute = ExecuteSettings.DEFAULTS;

        assertEquals(0.03, execute.bonusFraction(1, 5.0, 20.0), 1.0e-9);
        assertEquals(0.21, execute.bonusFraction(7, 5.0, 20.0), 1.0e-9, "+21% at VII");
        assertEquals(0.21, execute.bonusFraction(9, 5.0, 20.0), 1.0e-9, "a level past VII is VII");
        assertEquals(0.0, execute.bonusFraction(7, 10.0, 20.0), "above the threshold nothing is added");
        assertEquals(0.0, execute.bonusFraction(0, 5.0, 20.0));
    }

    // ---------------------------------------------------------------------------------------------
    // Wither, DemonForged
    // ---------------------------------------------------------------------------------------------

    @Test
    void witherKeepsTheSwordEffectWitherThreeForTwoSeconds() {
        assertEquals(2, WitherSettings.DEFAULTS.amplifier());
        assertEquals(40, WitherSettings.DEFAULTS.durationTicks());
    }

    @Test
    void demonForgedWearsOnePointByDefault() {
        assertEquals(1, DemonForgedSettings.DEFAULTS.durabilityPoints());
        assertThrows(IllegalArgumentException.class, () -> new DemonForgedSettings(0));
    }

    // ---------------------------------------------------------------------------------------------
    // Registry: names, types, counts
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyEnumEntryHasExactlyOneYamlBlockAndNoKeyIsOrphaned() throws IOException {
        final Map<String, String> enumTypes = enumEntries().values().stream()
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.displayName(), entry.type()), Map::putAll);
        final Set<String> yamlKeys = enchantments().keySet().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        // The loader builds "Enchantments." + displayName, so the match is exact, case included.
        assertEquals(enumTypes.keySet(), yamlKeys, "enum display names and Enchantments.yml keys must be 1:1");
        assertEquals(enumEntries().size(), enumTypes.size(), "no duplicate display name in the enum");
        assertEquals(enumTypes.size(), yamlKeys.size());
    }

    @Test
    void theEnumTypeOfEveryEnchantEqualsItsYamlType() throws IOException {
        for (final EnumEntry entry : enumEntries().values()) {
            final Map<?, ?> block = enchant(entry.displayName());

            // The loader resolves the type name case-insensitively (Methods#getFromName).
            assertTrue(entry.type().equalsIgnoreCase(String.valueOf(block.get("Enchantment-Type"))), entry.displayName());
        }
    }

    @Test
    void renamesCarryTheCosmicNameInKeyNameAndInfo() throws IOException {
        final Map<String, EnumEntry> byConstant = enumEntries();

        assertEquals("Rocket Escape", byConstant.get("ROCKET").displayName());
        assertEquals("Ender Shift", byConstant.get("ADRENALINE").displayName());

        assertFalse(enchantments().containsKey("Rocket"), "the old Rocket key is gone");
        assertFalse(enchantments().containsKey("Adrenaline"), "the old Adrenaline key is gone");

        assertEquals("&7Rocket Escape", enchant("Rocket Escape").get("Name"));
        assertEquals("&7Ender Shift", enchant("Ender Shift").get("Name"));
        assertTrue(String.valueOf(info("Rocket Escape").get("Name")).contains("Rocket Escape"));
        assertTrue(String.valueOf(info("Ender Shift").get("Name")).contains("Ender Shift"));

        final Map<?, ?> tinker = section(section(yaml("Tinker.yml"), "Tinker"), "Crazy-Enchantments");

        assertTrue(tinker.containsKey("Rocket Escape") && tinker.containsKey("Ender Shift"));
        assertFalse(tinker.containsKey("Rocket") || tinker.containsKey("Adrenaline"));
    }

    @Test
    void maxLevelsMatchCosmic() throws IOException {
        final Map<String, Integer> expected = Map.of(
                "Valor", 5, "Implants", 3, "Rocket Escape", 3, "Ender Shift", 3,
                "Vampire", 3, "Execute", 7, "Wither", 5, "Demon Forged", 4);

        expected.forEach((name, max) -> assertEquals(max, assertDoesNotThrow(() -> enchant(name)).get("MaxPower"), name));
    }

    @Test
    void witherMovesToArmorAndDemonForgedCoversSwordAndAxe() throws IOException {
        final Map<String, EnumEntry> byConstant = enumEntries();

        assertEquals("Armor", byConstant.get("WITHER").type());
        assertEquals("Armor", enchant("Wither").get("Enchantment-Type"));
        assertEquals("Sword-and-Axe", byConstant.get("DEMONFORGED").type());
        assertEquals("Sword-and-Axe", enchant("Demon Forged").get("Enchantment-Type"));
        assertEquals("Helmet-and-Boots", byConstant.get("ADRENALINE").type());
        assertEquals("Helmet-and-Boots", enchant("Ender Shift").get("Enchantment-Type"));
    }

    @Test
    void theNewTypesCoverExactlyTheRightItemsAndUseFreeSlots() throws IOException {
        final Map<?, ?> types = section(yaml("Enchantment-Types.yml"), "Types");

        final Set<Object> swordAndAxe = new HashSet<>(materials(types, "Sword"));
        swordAndAxe.addAll(materials(types, "Axe"));

        assertEquals(swordAndAxe, new HashSet<>(materials(types, "Sword-and-Axe")));
        assertEquals(swordAndAxe.size(), materials(types, "Sword-and-Axe").size(), "no duplicate material");

        final Set<Object> helmetAndBoots = new HashSet<>(materials(types, "Helmet"));
        helmetAndBoots.addAll(materials(types, "Boots"));

        assertEquals(helmetAndBoots, new HashSet<>(materials(types, "Helmet-and-Boots")));
        assertEquals(helmetAndBoots.size(), materials(types, "Helmet-and-Boots").size(), "no duplicate material");

        final Set<Integer> slots = new HashSet<>();
        final Set<String> hidden = new HashSet<>();

        for (final Object type : types.keySet()) {
            final Map<?, ?> config = section(types, type);

            // Combo types keep their old slot and are never drawn, so only the
            // per-piece pages the menu shows must be unique.
            if (Boolean.FALSE.equals(config.get("Menu"))) {
                hidden.add(String.valueOf(type));
                continue;
            }

            final int slot = (int) section(config, "Display-Item").get("Slot");

            assertTrue(slots.add(slot), "slot collision: " + slot);
        }

        assertEquals(Set.of("Armor", "Tool", "Weapons", "Weapons-and-Tools", "Damaged-Items",
                "Helmet-and-Boots", "Sword-and-Axe"), hidden);
        assertEquals(9, slots.size(), "one page per piece");

        // The info menu has 18 slots (index = Slot - 1).
        assertTrue(slots.stream().allMatch(slot -> slot >= 1 && slot <= 18));
    }

    @Test
    void chanceKeysAreKeptWhereTheBalanceSheetKeepsThemAndGoneWhereTheEnchantIsAlwaysOn() throws IOException {
        final Map<String, EnumEntry> byConstant = enumEntries();

        // Kept: Vampire 5/5, Wither 10/3, DemonForged 10/5.
        assertChance(byConstant.get("VAMPIRE"), "Vampire", 5, 5);
        assertChance(byConstant.get("WITHER"), "Wither", 10, 3);
        assertChance(byConstant.get("DEMONFORGED"), "Demon Forged", 10, 5);

        // Always-on: no chance in the enum (hasChanceSystem false) and no Chance-System block.
        for (final String constant : List.of("VALOR", "IMPLANTS", "ROCKET", "ADRENALINE", "EXECUTE")) {
            final EnumEntry entry = byConstant.get(constant);

            assertNotNull(entry, constant);
            assertFalse(entry.hasChance(), constant + " must not declare a chance in the enum");
            assertFalse(enchant(entry.displayName()).containsKey("Chance-System"), constant + " must not declare a Chance-System");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tuning
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyReworkedEnchantShipsItsTuningKeysEqualToTheRecordDefaults() throws IOException {
        final String tuning = Files.readString(JAVA.resolve("api/utils/TuningConfig.java"));
        final ImplantsSettings implants = ImplantsSettings.DEFAULTS;
        final RocketEscapeSettings rocket = RocketEscapeSettings.DEFAULTS;
        final EnderShiftSettings shift = EnderShiftSettings.DEFAULTS;
        final VampireSettings vampire = VampireSettings.DEFAULTS;
        final ExecuteSettings execute = ExecuteSettings.DEFAULTS;

        final Map<String, Map<String, Object>> expected = new LinkedHashMap<>();

        expected.put("Valor", Map.of("Reduction-Per-Level-PP", ValorSettings.DEFAULTS.reductionPerLevelPp()));
        expected.put("Implants", Map.of(
                "Interval-Seconds", implants.intervalSeconds(),
                "Heal", implants.healHp(),
                "Hunger", implants.hunger()));
        expected.put("RocketEscape", Map.of(
                "Trigger-PP", rocket.triggerPp(),
                "Velocity", rocket.velocity(),
                "Cooldown-Seconds", rocket.cooldownSeconds()));
        expected.put("EnderShift", Map.of(
                "Trigger-PP", shift.triggerPp(),
                "Clear-PP", shift.clearPp(),
                "Speed-Amplifier", shift.speedAmplifier(),
                "Health-Boost-Amplifier", shift.healthBoostAmplifier(),
                "Effect-Seconds", shift.effectSeconds()));
        expected.put("Vampire", Map.of("Heal", vampire.healHp(), "Delay-Seconds", vampire.delaySeconds()));
        expected.put("Execute", Map.of(
                "Threshold-PP", execute.thresholdPp(),
                "Bonus-Per-Level-PP", execute.bonusPerLevelPp()));
        expected.put("Wither", Map.of(
                "Amplifier", WitherSettings.DEFAULTS.amplifier(),
                "Duration-Seconds", WitherSettings.DEFAULTS.durationSeconds()));
        expected.put("DemonForged", Map.of("Durability-Points", DemonForgedSettings.DEFAULTS.durabilityPoints()));

        assertEquals(8, expected.size());

        expected.forEach((block, keys) -> {
            final String enchant = TuningConfig.sectionKeys().get(block);

            assertNotNull(enchant, "no enchant key for tuning block " + block);

            final Map<?, ?> shipped = assertDoesNotThrow(() -> section(enchant(enchant), "Tuning"));

            assertEquals(keys.keySet(), new HashSet<>(shipped.keySet().stream().map(String::valueOf).toList()),
                    block + " ships exactly its tuning keys");

            keys.forEach((key, value) -> {
                assertTrue(sameNumbers(value, shipped.get(key)), block + "." + key + ": " + shipped.get(key) + " vs " + value);
                assertTrue(tuning.contains("\"" + block + "." + key + "\""), "TuningConfig must read " + block + "." + key);
            });
        });
    }

    @Test
    void everyHandlerReadsItsNumbersThroughTuningConfig() throws IOException {
        final String damage = Files.readString(JAVA.resolve("listeners/DamageModifierListener.java"));
        final String armor = Files.readString(JAVA.resolve("enchantments/ArmorEnchantments.java"));
        final String sword = Files.readString(JAVA.resolve("enchantments/SwordEnchantments.java"));
        final String axe = Files.readString(JAVA.resolve("enchantments/AxeEnchantments.java"));

        assertTrue(damage.contains("TuningConfig.valor().reductionPerLevelPp()"));
        assertTrue(armor.contains("TuningConfig.implants()"));
        assertTrue(armor.contains("TuningConfig.rocketEscape()"));
        assertTrue(armor.contains("TuningConfig.enderShift()"));
        assertTrue(armor.contains("TuningConfig.wither()"));
        assertTrue(sword.contains("TuningConfig.vampire()"));
        assertTrue(sword.contains("TuningConfig.execute().bonusFraction("));
        assertTrue(axe.contains("TuningConfig.demonForged().durabilityPoints()"));
    }

    // ---------------------------------------------------------------------------------------------
    // Handlers and stale copy
    // ---------------------------------------------------------------------------------------------

    @Test
    void theOldBehavioursAreGone() throws IOException {
        final String sword = Files.readString(JAVA.resolve("enchantments/SwordEnchantments.java"));
        final String processor = Files.readString(JAVA.resolve("tasks/processors/ArmorProcessor.java"));
        final String manager = Files.readString(JAVA.resolve("api/CrazyManager.java"));
        final String armor = Files.readString(JAVA.resolve("enchantments/ArmorEnchantments.java"));

        assertFalse(sword.contains("CEnchantments.WITHER"), "Wither is no longer a sword hit effect");
        assertFalse(sword.contains("CEnchantments.EXECUTE, damager, item, enchantments)) {\n            damager.addPotionEffect"),
                "Execute no longer grants Strength");
        assertFalse(sword.contains("damager.setHealth(entityHealth + event.getDamage() / 2"), "Vampire no longer heals instantly");
        assertFalse(processor.contains("IMPLANTS"), "Implants no longer rides the movement processor");
        assertFalse(manager.contains("CEnchantments.VALOR"), "Valor is no longer a Resistance potion");
        assertFalse(armor.contains("CEnchantments.ADRENALINE, player, armor, enchants)) {\n                player.addPotionEffect"),
                "Ender Shift no longer fires once per hit");
    }

    @Test
    void sniperHeadshotLoreNoLongerClaimsTheDisabledCap() throws IOException {
        final String lore = String.valueOf(section(enchant("SniperHeadshot"), "Info").get("Description"));

        assertFalse(lore.contains("capped"), lore);
        assertFalse(lore.contains("20 HP"), lore);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private record EnumEntry(String constant, String displayName, String type, boolean hasChance, int base, int increase) {}

    private static Map<String, EnumEntry> enumEntries() throws IOException {
        final Map<String, EnumEntry> entries = new LinkedHashMap<>();
        final Matcher matcher = ENUM_ENTRY.matcher(Files.readString(JAVA.resolve("api/enums/CEnchantments.java")));

        while (matcher.find()) {
            final boolean chance = matcher.group(4) != null;

            assertEquals(null, entries.put(matcher.group(1), new EnumEntry(matcher.group(1), matcher.group(2), matcher.group(3), chance,
                    chance ? Integer.parseInt(matcher.group(4)) : 0, chance ? Integer.parseInt(matcher.group(5)) : 0)),
                    "duplicate enum constant " + matcher.group(1));
        }

        return entries;
    }

    private static void assertChance(final EnumEntry entry, final String yamlKey, final int base, final int increase) throws IOException {
        assertTrue(entry.hasChance(), yamlKey);
        assertEquals(base, entry.base(), yamlKey);
        assertEquals(increase, entry.increase(), yamlKey);

        final Map<?, ?> chance = section(enchant(yamlKey), "Chance-System");

        assertEquals(base, chance.get("Base"), yamlKey);
        assertEquals(increase, chance.get("Increase"), yamlKey);
    }

    /** Compares a record default (Number or List of Numbers) with the parsed YAML value. */
    private static boolean sameNumbers(final Object expected, final Object shipped) {
        if (expected instanceof List<?> list) {
            if (!(shipped instanceof List<?> other) || other.size() != list.size()) return false;

            final List<Object> left = new ArrayList<>(list);
            final List<Object> right = new ArrayList<>(other);

            for (int index = 0; index < left.size(); index++) {
                if (!sameNumbers(left.get(index), right.get(index))) return false;
            }

            return true;
        }

        return expected instanceof Number a && shipped instanceof Number b && Double.compare(a.doubleValue(), b.doubleValue()) == 0;
    }

    private static Map<?, ?> enchantments() throws IOException {
        return section(yaml("Enchantments.yml"), "Enchantments");
    }

    private static Map<?, ?> enchant(final String name) throws IOException {
        return section(enchantments(), name);
    }

    private static Map<?, ?> info(final String name) throws IOException {
        return section(enchant(name), "Info");
    }

    private static List<?> materials(final Map<?, ?> types, final String name) {
        return (List<?>) section(types, name).get("Enchantable-Items");
    }

    private static Map<?, ?> yaml(final String name) throws IOException {
        return new Yaml().load(Files.readString(RESOURCES.resolve(name)));
    }

    private static Map<?, ?> section(final Map<?, ?> map, final Object key) {
        return assertInstanceOf(Map.class, map.get(key), String.valueOf(key));
    }

    private static <T> T assertDoesNotThrow(final org.junit.jupiter.api.function.ThrowingSupplier<T> supplier) {
        return org.junit.jupiter.api.Assertions.assertDoesNotThrow(supplier);
    }
}
