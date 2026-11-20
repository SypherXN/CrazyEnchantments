package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.api.utils.ExplosionHelper.Blast;
import com.badbones69.crazyenchantments.paper.api.utils.PacifySettings.PacifyState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BowEnchantLogicTest {

    private static final String JAVA = "src/main/java/com/badbones69/crazyenchantments/paper/";
    private static final String RESOURCES = "src/main/resources/";
    private static final double EPS = 1.0e-9;

    @Test
    void unfocusReductionAndDurationAtEveryLevel() {
        for (int level = 1; level <= 5; level++) {
            final var effect = UnfocusSettings.DEFAULTS.refresh(null, level, 100L);
            assertEquals(10.0 * level, effect.reductionPp(), EPS);
            assertEquals(100L + 2_000L * level, effect.expiryMillis());
            assertEquals(1.0 - 0.1 * level, effect.multiplier(), EPS);
        }
    }

    @Test
    void unfocusWeakerHitMayExtendButCannotShortenOrWeaken() {
        final var settings = UnfocusSettings.DEFAULTS;
        final var strong = settings.refresh(null, 5, 0L);

        assertEquals(strong, settings.refresh(strong, 1, 1_000L));
        final var refreshed = settings.refresh(strong, 1, 9_000L);
        assertEquals(50.0, refreshed.reductionPp(), EPS);
        assertEquals(11_000L, refreshed.expiryMillis());
    }

    @Test
    void unfocusStrongerHitReplacesNeverAddsAndExpiredEffectDoesNotCarry() {
        final var settings = UnfocusSettings.DEFAULTS;
        final var weak = settings.refresh(null, 1, 0L);
        final var strong = settings.refresh(weak, 4, 100L);

        assertEquals(40.0, strong.reductionPp(), EPS);
        assertTrue(strong.active(8_099L));
        assertFalse(strong.active(8_100L));
        assertEquals(10.0, settings.refresh(strong, 1, 8_100L).reductionPp(), EPS);
    }

    @Test
    void arrowLifestealHealsTwentyPercent() {
        assertEquals(12.0, ArrowLifestealSettings.DEFAULTS.healedHealth(10.0, 20.0, 10.0), EPS);
    }

    @Test
    void arrowLifestealCapsBothPerProcAndAtMaxHealth() {
        final var settings = ArrowLifestealSettings.DEFAULTS;
        assertEquals(13.0, settings.healedHealth(10.0, 20.0, 30.0), EPS);
        assertEquals(20.0, settings.healedHealth(19.0, 20.0, 30.0), EPS);
        assertEquals(20.0, settings.healedHealth(20.0, 20.0, 30.0), EPS);
    }

    @Test
    void arrowLifestealIgnoresZeroDamageAndInvalidDamage() {
        for (final double damage : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(12.0, ArrowLifestealSettings.DEFAULTS.healedHealth(12.0, 20.0, damage), EPS);
        }
    }

    @Test
    void pacifyDurationAtEveryLevel() {
        final long[] expected = {1_000, 1_700, 2_300, 3_000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], PacifySettings.DEFAULTS.durationMillis(i + 1));
        }
    }

    @Test
    void pacifyExpiresExactlyAtDeadlineAndDoesNotAffectOtherVictims() {
        final UUID victim = UUID.randomUUID();
        PacifyState.apply(victim, PacifySettings.DEFAULTS, 2, 100L);

        assertEquals(1L, PacifyState.remainingMillis(victim, 1_799L));
        assertEquals(0L, PacifyState.remainingMillis(UUID.randomUUID(), 1_799L));
        assertEquals(0L, PacifyState.remainingMillis(victim, 1_800L));
    }

    @Test
    void pacifyDoesNotShortenAndPrunesOnlyExpiredEntries() {
        final UUID victim = UUID.randomUUID();
        PacifyState.apply(victim, PacifySettings.DEFAULTS, 4, 0L);
        PacifyState.apply(victim, PacifySettings.DEFAULTS, 1, 100L);

        PacifyState.prune(2_999L);
        assertEquals(1L, PacifyState.remainingMillis(victim, 2_999L));
        PacifyState.prune(3_000L);
        assertEquals(0L, PacifyState.remainingMillis(victim, 2_999L));
    }

    @Test
    void pacifiedRageHoldsExistingStackWithoutRolling() {
        assertEquals(PacifySettings.RageHit.HOLD,
                PacifySettings.rageHit(true, true, true, () -> fail("Pacify must not roll Rage")));
        assertEquals(PacifySettings.RageHit.NONE,
                PacifySettings.rageHit(true, true, false, () -> fail("Pacify must not roll without a Rage weapon")));
    }

    @Test
    void pacifiedWithoutRageDoesNothingWithoutRolling() {
        assertEquals(PacifySettings.RageHit.NONE,
                PacifySettings.rageHit(true, false, true, () -> fail("Pacify must not start Rage")));
        assertEquals(PacifySettings.RageHit.GAIN, PacifySettings.rageHit(false, false, true, () -> true));
    }

    @Test
    void longbowMultipliersAtEveryLevel() {
        final double[] expected = {1.0, 1.05, 1.10, 1.15, 1.20, 1.20};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], LongbowSettings.DEFAULTS.multiplier(i), EPS);
        }
    }

    @Test
    void eagleEyeDistanceBandsHonorAllThreeBoundaries() {
        final var settings = EagleEyeSettings.DEFAULTS;
        assertEquals(1, settings.wearPoints(Math.nextDown(15.0)));
        assertEquals(2, settings.wearPoints(15.0));
        assertEquals(2, settings.wearPoints(Math.nextDown(30.0)));
        assertEquals(3, settings.wearPoints(30.0));
        assertEquals(3, settings.wearPoints(Math.nextDown(45.0)));
        assertEquals(4, settings.wearPoints(45.0));
    }

    @Test
    void eagleEyeCannotBreakArmorOrRestoreAlreadyWornArmor() {
        assertEquals(12, EagleEyeSettings.wornDamage(8, 100, 4));
        assertEquals(99, EagleEyeSettings.wornDamage(98, 100, 4));
        assertEquals(99, EagleEyeSettings.wornDamage(99, 100, 4));
        assertEquals(100, EagleEyeSettings.wornDamage(100, 100, 4));
        assertEquals(0, EagleEyeSettings.wornDamage(0, 0, 4));
    }

    @Test
    void infernalHasFlatRadiusAndDamageWithLevelledFire() {
        final var settings = InfernalSettings.DEFAULTS;
        assertEquals(2.0, settings.radius(), EPS);
        assertEquals(1.0, settings.damageHp(), EPS);
        assertEquals(60, settings.fireTicks(1));
        assertEquals(80, settings.fireTicks(2));
        assertEquals(100, settings.fireTicks(3));
    }

    @Test
    void explosiveDamageAtEveryLevelAndRadius() {
        final var settings = ExplosiveSettings.DEFAULTS;
        assertEquals(3.0, settings.radius(), EPS);
        for (int level = 1; level <= 5; level++) {
            assertEquals(3.0 + 0.5 * (level - 1), settings.damageHp(level), EPS);
        }
    }

    @Test
    void hellfireDamageAtEveryLevelAndRadiusAndDirectFire() {
        final var settings = HellfireSettings.DEFAULTS;
        assertEquals(2.0, settings.radius(), EPS);
        assertEquals(60, settings.fireTicks());
        for (int level = 1; level <= 5; level++) {
            assertEquals(1.5 + 0.25 * (level - 1), settings.damageHp(level), EPS);
        }
    }

    @Test
    void sharedImpactSumsInRangeSplashIntoOneHitPerBystander() {
        final var impact = ExplosionHelper.planImpact(List.of(new Blast(2, 1, 80, 80),
                new Blast(3, 4, 0, 0), new Blast(2, 2, 0, 60)),
                null, Map.of("near", 4.0, "far", 9.0, "outside", Math.nextUp(9.0)));

        assertEquals(Map.of("near", new ExplosionHelper.Hit(7.0, 80),
                "far", new ExplosionHelper.Hit(4.0, 0)), impact.bystanders());
        assertEquals(0.0, impact.arrowDamage(0.0), EPS);
    }

    @Test
    void directTargetGetsAllSplashAndLongestFireRegardlessOfRadiusNotSyntheticDamage() {
        final var blasts = List.of(new Blast(2, 1, 100, 100), new Blast(3, 3, 0, 0),
                new Blast(2, 1.5, 0, 60));
        final var impact = ExplosionHelper.planImpact(blasts, "direct", Map.of("direct", 100.0, "near", 1.0));
        final var withinRadius = ExplosionHelper.planImpact(blasts, "direct", Map.of("direct", 1.0, "near", 1.0));
        assertEquals(impact, withinRadius);
        assertEquals(12.5, impact.arrowDamage(7.0), EPS);
        assertEquals(100, impact.direct().fireTicks());
        assertEquals(Map.of("near", new ExplosionHelper.Hit(5.5, 100)), impact.bystanders());
    }

    @Test
    void hellfireAllowsOnlyFiveExecutionsPerRollingSecond() {
        var gate = HellfireSettings.Window.EMPTY;
        for (int i = 0; i < 5; i++) {
            final var next = gate.claim(900L + 20L * i);
            assertNotSame(gate, next);
            gate = next;
        }
        assertSame(gate, gate.claim(1_000L), "not a fixed wall-clock second");
        assertSame(gate, gate.claim(1_899L));
        final var next = gate.claim(1_900L);
        assertNotSame(gate, next);
        assertSame(next, next.claim(1_919L));
        assertNotSame(next, next.claim(1_920L));
    }

    @Test
    void hellfireWindowIsIndependentByShooterAndIdleTimeReleasesSlots() {
        final var b = HellfireSettings.Window.EMPTY;
        var a = b;
        for (int i = 0; i < 5; i++) a = a.claim(0L);
        assertSame(a, a.claim(0L));
        assertNotSame(b, b.claim(0L));
        assertFalse(a.expired(999L));
        assertTrue(a.expired(1_000L));
        for (int i = 0; i < 5; i++) {
            final var next = a.claim(3_000L);
            assertNotSame(a, next);
            a = next;
        }
        assertSame(a, a.claim(3_000L));
    }

    @Test
    void malformedMissingAndOutOfRangeTuningWarnOncePerKey() {
        final List<String> warnings = new ArrayList<>();
        final Map<String, ?> raw = Map.of(
                "CosmicEnchants.ArrowLifesteal.Damage-Share", "0.8",
                "CosmicEnchants.Hellfire.Radius", 50,
                "CosmicEnchants.Unfocus.Reduction-PP", List.of(10, "20", 30, 40, 50),
                "CosmicEnchants.Longbow.Bonus-Per-Level-PP", 7);
        final var values = new TuningConfig.BowValues(raw::get, warnings::add);
        assertEquals(0.2, values.number("ArrowLifesteal.Damage-Share", 0.2, 0.0, 1.0), EPS);
        assertEquals(2.0, values.number("Hellfire.Radius", 2.0, 0.5, 16.0), EPS);
        assertEquals(UnfocusSettings.DEFAULTS.reductionPp(),
                values.table("Unfocus.Reduction-PP", UnfocusSettings.DEFAULTS.reductionPp(), 0.0, 100.0));
        assertEquals(3.0, values.number("ArrowLifesteal.Cap-HP", 3.0, 0.0, 100.0), EPS);
        assertEquals(7.0, values.number("Longbow.Bonus-Per-Level-PP", 5.0, 0.0, 100.0), EPS);
        assertEquals(4, warnings.size());
        assertTrue(warnings.getFirst().contains("CosmicEnchants.ArrowLifesteal.Damage-Share"));
        assertTrue(warnings.getLast().contains("CosmicEnchants.ArrowLifesteal.Cap-HP"));
    }

    @Test
    void settingsRejectInvalidRangesAndSnapshotDurationTables() {
        assertThrows(IllegalArgumentException.class, () -> new LongbowSettings(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new HellfireSettings(0.0, 1.0, 0.25, 3.0));
        assertThrows(IllegalArgumentException.class, () -> new PacifySettings(List.of(1.0, 2.0, 3.0)));

        final var table = new ArrayList<>(PacifySettings.DEFAULTS.durationSeconds());
        final var settings = new PacifySettings(table);
        table.set(0, 100.0);
        assertEquals(1_000L, settings.durationMillis(1));
    }

    private record Registry(String name, String block, int level, String category, Integer base, Integer increase) {}

    static Stream<Registry> registry() {
        return Stream.of(
                new Registry("Unfocus", "Unfocus", 5, "Ultimate", 8, 3),
                new Registry("Arrow Lifesteal", "ArrowLifesteal", 5, "Ultimate", 5, 3),
                new Registry("Pacify", "Pacify", 4, "Ultimate", 10, 5),
                new Registry("Longbow", "Longbow", 4, "Ultimate", null, null),
                new Registry("Eagle Eye", "EagleEye", 5, "Ultimate", 6, 3),
                new Registry("Infernal", "Infernal", 3, "Elite", 6, 3),
                new Registry("Explosive", "Explosive", 5, "Unique", 8, 3),
                new Registry("Hellfire", "Hellfire", 5, "Ultimate", null, null));
    }

    @ParameterizedTest
    @MethodSource("registry")
    void enumAndYamlAgreeAndCosmicTuningExists(final Registry expected) throws IOException {
        final Map<?, ?> enchant = section(section(yaml("Enchantments.yml"), "Enchantments"), expected.name());
        assertEquals(Boolean.TRUE, enchant.get("Enabled"));
        assertEquals("Bow", enchant.get("Enchantment-Type"));
        assertEquals(expected.level(), enchant.get("MaxPower"));
        assertEquals(List.of(expected.category()), enchant.get("Categories"));

        final String symbol = expected.name().toUpperCase(Locale.ROOT).replace(' ', '_');
        final String parameters;
        if (expected.base() == null) {
            assertFalse(enchant.containsKey("Chance-System"));
            parameters = "";
        } else {
            final Map<?, ?> chance = section(enchant, "Chance-System");
            assertEquals(expected.base(), chance.get("Base"));
            assertEquals(expected.increase(), chance.get("Increase"));
            parameters = ", " + expected.base() + ", " + expected.increase();
        }
        final String enumSource = read(JAVA + "api/enums/CEnchantments.java");
        assertTrue(enumSource.contains(symbol + "(\"" + expected.name() + "\", \"Bow\"" + parameters + "),"));

        final Map<?, ?> config = section(enchant, "Tuning");
        assertFalse(config.isEmpty());
        final String tuning = read(JAVA + "api/utils/TuningConfig.java");
        for (final Object key : config.keySet()) {
            assertTrue(tuning.contains("\"" + expected.block() + "." + key + "\""), "Unwired tuning key: " + key);
        }
    }

    @Test
    void pacifyKeepsCurrentMultiplierAndRefreshesTheSameRageTask() throws IOException {
        final String sword = read(JAVA + "enchantments/SwordEnchantments.java");
        final int rage = sword.indexOf("final var rageHit = PacifySettings.rageHit(");
        assertTrue(rage >= 0);
        final String body = sword.substring(rage, sword.indexOf("}.runDelayed(40))", rage));
        assertTrue(body.indexOf("PacifyState.remainingMillis") < body.indexOf("() -> EnchantUtils.isEventActive(CEnchantments.RAGE"));
        assertTrue(body.contains("if (rageHit != PacifySettings.RageHit.NONE)"));
        assertTrue(body.contains("if (rageHit == PacifySettings.RageHit.GAIN)"));
        assertTrue(body.contains("}\n\n                    event.setDamage(event.getDamage() * cePlayer.getRageMultiplier());"));
        assertTrue(body.contains("cePlayer.getRageTask().cancel()"));
        assertTrue(body.indexOf("if (frozenMillis > 0L)") < body.indexOf("cePlayer.setRageMultiplier(0.0)"));
        assertTrue(body.contains("cePlayer.setRageTask(damager.getScheduler().runDelayed("));
    }

    @Test
    void newExplosionsAreVisualOnlyAndDamageIsSynthetic() throws IOException {
        final String helper = read(JAVA + "api/utils/ExplosionHelper.java");
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");
        assertFalse(helper.contains("createExplosion("));
        assertFalse(bow.contains("createExplosion("));
        assertEquals(2, helper.split("SyntheticHit\\.damageBlast\\(", -1).length, "one blast damage call site");
        assertTrue(helper.contains("SyntheticHit.damageBlast(source, target, hit.damageHp())"));
        assertTrue(helper.contains("target instanceof ArmorStand"));
        assertTrue(helper.contains("Bukkit.isOwnedByCurrentRegion(target)"));
        assertTrue(helper.contains("setFireTicks("));
        assertTrue(helper.contains("support.isFriendly(source, target)"));
    }

    @Test
    void landedEffectsUseMonitorAndLifestealUsesFinalDamageAfterArmorAndFeign() throws IOException {
        final String source = read(JAVA + "enchantments/BowEnchantments.java");
        final String plugin = read(JAVA + "CrazyEnchantments.java");
        final int defensive = plugin.indexOf("new DefensiveArmorListener()");
        final int feign = plugin.indexOf("feignDeath.onDamageResolved(hit)");
        final int lifesteal = plugin.indexOf("registerEvent(EntityDamageByEntityEvent.class, bow, EventPriority.MONITOR");
        assertTrue(defensive >= 0 && feign > defensive && lifesteal > feign);
        assertTrue(plugin.contains("bow.onArrowLifestealResolved(hit);\n        }, this, false);"),
                "cancelled hits still reach the handler for cleanup");

        final int resolved = source.indexOf("public void onArrowLifestealResolved(");
        assertTrue(resolved >= 0);
        final String heal = source.substring(resolved, source.indexOf("\n    }\n", resolved));
        assertTrue(source.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onLandedBowDamage"));
        assertTrue(source.contains("event.setDamage(combinedDamage)"));
        assertFalse(source.contains("Bukkit.getRegionScheduler()"), "no delayed synthetic hit at an entity impact");
        assertTrue(heal.contains("final PendingLifesteal pending = this.pendingLifesteal.remove(event);\n"
                + "        if (pending == null || event.isCancelled() || SyntheticHit.isSynthetic()"));
        assertTrue(heal.contains("Math.min(finalDamage, target.getHealth())"));
        assertTrue(heal.contains("EntityDamageEvent.DamageModifier.BLOCKING) >= 0.0"));
        assertTrue(heal.contains("Bukkit.isOwnedByCurrentRegion(player)"));
        assertTrue(heal.contains("Bukkit.isOwnedByCurrentRegion(target)"));
    }

    @Test
    void longbowReadsTheTargetMainHandAndARemoteShooterIsAnExplicitSkip() throws IOException {
        final String source = read(JAVA + "enchantments/BowEnchantments.java");
        final int modifiers = source.indexOf("public void onBowDamageModifiers");
        assertTrue(modifiers >= 0);
        final String body = source.substring(modifiers, source.indexOf("\n    }\n", modifiers));
        assertTrue(body.contains("event.getDamager() instanceof AbstractArrow"));
        assertTrue(body.contains("arrow.getShooter()"));
        assertTrue(body.contains("equipment = target.getEquipment()"));
        assertTrue(body.contains("equipment.getItemInMainHand()"));
        assertTrue(body.contains("SyntheticHit.isBleedTick()"));

        // The cross-region rule is explicit here too, so a remote shot is a skip
        // and never a cross-region read.
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(arrow)"));
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(shooter)"));

        // Both bow bonuses spend the shared budget instead of capping the hit again.
        assertTrue(body.contains("DamageModifiers.applyOutgoingBonus("));
        assertFalse(body.contains("capBonus("));
    }

    @Test
    void bowStateExpiresLazilyAndCannotBeClearedByRelogging() throws IOException {
        final String source = read(JAVA + "enchantments/BowEnchantments.java");
        assertFalse(source.contains("PlayerQuitEvent"));
        assertTrue(source.contains("this.unfocusEffects.values().removeIf"));
        assertTrue(source.contains("PacifyState.prune(now)"));
        assertTrue(source.contains("this.hellfireWindows.values().removeIf"));
    }

    @Test
    void hellfireClaimUsesAnAtomicPerShooterCompute() throws IOException {
        final String source = read(JAVA + "enchantments/BowEnchantments.java");
        final int claim = source.indexOf("private boolean claimHellfire(");
        assertTrue(claim >= 0);
        final String body = source.substring(claim, source.indexOf("\n    }\n", claim));
        assertTrue(body.contains("this.hellfireWindows.compute(shooter,"));
        assertTrue(body.contains("current.claim(nowMillis())"));
        assertFalse(body.contains("this.hellfireWindows.put("));
    }

    private static Map<?, ?> yaml(final String file) throws IOException {
        return new Yaml().load(read(RESOURCES + file));
    }

    private static Map<?, ?> section(final Map<?, ?> data, final String key) {
        return assertInstanceOf(Map.class, data.get(key), key);
    }

    private static String read(final String path) throws IOException {
        final Path direct = Path.of(path);
        return Files.readString(Files.exists(direct) ? direct : Path.of("paper").resolve(path)).replace("\r\n", "\n");
    }
}
