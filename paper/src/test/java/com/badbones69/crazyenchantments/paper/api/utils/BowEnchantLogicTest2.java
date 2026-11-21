package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The four new bow enchants: Sniper (headshot), Virus, Teleportation and
 * Farcast. Pure maths are tested directly; the listener behaviour is guarded by
 * reading its source as text, in the style of the first bow test. No Bukkit
 * server is loaded, and the existing BowEnchantLogicTest is left untouched.
 */
class BowEnchantLogicTest2 {

    private static final String JAVA = "src/main/java/com/badbones69/crazyenchantments/paper/";
    private static final String RESOURCES = "src/main/resources/";
    private static final double EPS = 1.0e-9;

    // ---- Sniper: always-on, 1.5x-3.5x on a fully drawn headshot ----

    @Test
    void sniperMultipliersAtEveryLevel() {
        final SniperSettings settings = SniperSettings.DEFAULTS;
        final double[] expected = {1.5, 2.0, 2.5, 3.0, 3.5};

        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], settings.multiplier(level), EPS);
        }

        assertEquals(1.0, settings.multiplier(0), EPS, "an unusable level is neutral");
        assertEquals(3.5, settings.multiplier(9), EPS, "a level past the table uses the last entry");
    }

    @Test
    void sniperHeadshotIsTheTopQuarterOfTheBox() {
        final SniperSettings settings = SniperSettings.DEFAULTS;

        assertFalse(settings.headshot(Math.nextDown(1.5), 0.0, 2.0), "just below the quarter line");
        assertTrue(settings.headshot(1.5, 0.0, 2.0), "the quarter line itself is a headshot");
        assertTrue(settings.headshot(2.0, 0.0, 2.0), "the top of the box");
        assertFalse(settings.headshot(2.49, 1.0, 2.0), "the box offset moves the line to 2.5");
        assertTrue(settings.headshot(2.5, 1.0, 2.0));
        assertFalse(settings.headshot(3.01, 1.0, 2.0), "above the box is not a headshot");
        assertFalse(settings.headshot(0.99, 1.0, 2.0), "below the box is not a headshot");
        assertFalse(settings.headshot(5.0, 0.0, 0.0), "a degenerate box is never a headshot");
    }

    @Test
    void sniperDrawForceBoundary() {
        final SniperSettings settings = SniperSettings.DEFAULTS;

        assertFalse(settings.fullyDrawn(Math.nextDown(0.9)));
        assertTrue(settings.fullyDrawn(0.9), "exactly 0.9 is fully drawn");
        assertTrue(settings.fullyDrawn(1.0));
        assertFalse(settings.fullyDrawn(Double.NaN), "a missing force is never enough");
    }

    @Test
    void sniperDrawForceExpiresAtTheTtl() {
        final SniperSettings settings = SniperSettings.DEFAULTS;
        final SniperSettings.Shot shot = SniperSettings.Shot.inFlight(1.0f, 1_000L);

        assertFalse(shot.expired(1_000L + settings.forceTtlMillis() - 1L, settings.forceTtlMillis()));
        assertTrue(shot.expired(1_000L + settings.forceTtlMillis(), settings.forceTtlMillis()));
    }

    @Test
    void sniperImpactPointArrivesAfterTheShotAndIsRequiredForAHeadshot() {
        final SniperSettings.Shot shot = SniperSettings.Shot.inFlight(1.0f, 1_000L);

        assertFalse(shot.impactKnown(), "no impact point before the projectile reports a hit");
        assertTrue(shot.withImpact(65.5).impactKnown());
        assertEquals(65.5, shot.withImpact(65.5).impactY(), EPS);
        assertEquals(1.0f, shot.withImpact(65.5).force());
        assertEquals(1_000L, shot.withImpact(65.5).firedAtMillis());
    }

    // ---- Virus: +25/50/75/100% of the remaining duration, 20 s cap ----

    @Test
    void virusExtendsByShareOfTheRemainingDuration() {
        final VirusSettings settings = VirusSettings.DEFAULTS;
        final int[] expected = {125, 150, 175, 200};

        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], settings.extendedTicks(100, level), "level " + level);
        }
    }

    @Test
    void virusCapsTheTotalRemainingAtTwentySeconds() {
        final VirusSettings settings = VirusSettings.DEFAULTS;

        assertEquals(375, settings.extendedTicks(300, 1));
        assertEquals(400, settings.extendedTicks(300, 4), "15 s doubled would be 30 s, capped at 20 s");
        assertEquals(400, settings.maxTicks());
    }

    @Test
    void virusNeverShortensAnEffectOrInventsOne() {
        final VirusSettings settings = VirusSettings.DEFAULTS;

        assertEquals(500, settings.extendedTicks(500, 4), "an effect past the cap is left alone");
        assertEquals(0, settings.extendedTicks(0, 4), "a missing effect stays missing");
        assertEquals(0, settings.extendedTicks(-20, 4));
    }

    @Test
    void virusTreatsNegativeDurationsAsNotRunning() {
        assertFalse(VirusSettings.present(-1), "the vanilla infinite marker is not a running timer");
        assertFalse(VirusSettings.present(-20));
        assertTrue(VirusSettings.present(0), "a zero-tick effect is present and expiring");
        assertTrue(VirusSettings.present(100));
    }

    // ---- Teleportation: 30 blocks, 10 s, safe spot within 2 blocks ----

    @Test
    void teleportationRangeIsInclusiveAtThirtyBlocks() {
        final TeleportationSettings settings = TeleportationSettings.DEFAULTS;

        assertTrue(settings.withinRange(0.0));
        assertTrue(settings.withinRange(30.0));
        assertFalse(settings.withinRange(Math.nextUp(30.0)));
        assertFalse(settings.withinRange(-0.1));
    }

    @Test
    void teleportationCooldownIsSpentOnlyByAClaim() {
        final TeleportationSettings settings = TeleportationSettings.DEFAULTS;
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
        final UUID shooter = UUID.randomUUID();

        assertTrue(settings.available(cooldowns, shooter, 1_000L));
        assertTrue(settings.available(cooldowns, UUID.randomUUID(), 1_000L), "windows are per shooter");

        settings.claim(cooldowns, shooter, 1_000L);
        assertFalse(settings.available(cooldowns, shooter, 10_999L));
        assertTrue(settings.available(cooldowns, shooter, 11_000L));
    }

    @Test
    void teleportationSearchStaysWithinTwoBlocksAndSkipsTheAllyBlock() {
        final List<TeleportationSettings.Offset> offsets = TeleportationSettings.DEFAULTS.offsets();

        assertEquals(12, offsets.size());
        assertTrue(offsets.stream().noneMatch(offset -> offset.dx() == 0 && offset.dz() == 0),
                "the ally's own block is never a landing");
        assertTrue(offsets.stream().allMatch(offset -> offset.dx() * offset.dx() + offset.dz() * offset.dz() <= 4),
                "every offset is within two blocks");
        assertEquals(new TeleportationSettings.Offset(-1, 0), offsets.getFirst(), "nearest first, fixed order");
        assertTrue(offsets.contains(new TeleportationSettings.Offset(2, 0)));
        assertFalse(offsets.contains(new TeleportationSettings.Offset(2, 2)), "the diagonal is past two blocks");
    }

    @Test
    void teleportationSafeLandingNeedsAirSpaceFloorAndNoHazard() {
        assertTrue(TeleportationSettings.safeLanding(false, false, true, false));
        assertFalse(TeleportationSettings.safeLanding(true, false, true, false), "feet block is solid");
        assertFalse(TeleportationSettings.safeLanding(false, true, true, false), "head block is solid");
        assertFalse(TeleportationSettings.safeLanding(false, false, false, false), "no floor under the spot");
        assertFalse(TeleportationSettings.safeLanding(false, false, true, true), "lava, fire, cactus or powder snow");
    }

    // ---- Farcast: 1.5x chance at or below 40% HP, 2.0 push, 3 s ----

    @Test
    void farcastChanceMultiplierAtTheFortyPercentCut() {
        final FarcastSettings settings = FarcastSettings.DEFAULTS;

        assertEquals(1.5, settings.chanceMultiplier(8.0, 20.0), EPS, "exactly 40% is low health");
        assertEquals(1.5, settings.chanceMultiplier(1.0, 20.0), EPS);
        assertEquals(1.0, settings.chanceMultiplier(Math.nextUp(8.0), 20.0), EPS);
        assertEquals(1.0, settings.chanceMultiplier(1.0, 0.0), EPS, "no max health means no low-health bonus");
    }

    @Test
    void farcastPushIsHorizontalAndNeverNaN() {
        final FarcastSettings settings = FarcastSettings.DEFAULTS;

        final FarcastSettings.Push south = settings.push(0.0, -5.0);
        assertEquals(0.0, south.x(), EPS);
        assertEquals(-2.0, south.z(), EPS);

        final FarcastSettings.Push diagonal = settings.push(3.0, 4.0);
        assertEquals(1.2, diagonal.x(), EPS);
        assertEquals(1.6, diagonal.z(), EPS);

        final FarcastSettings.Push overlapping = settings.push(0.0, 0.0);
        assertEquals(0.0, overlapping.x(), EPS);
        assertEquals(0.0, overlapping.z(), EPS);
    }

    @Test
    void farcastCooldownIsThreeSeconds() {
        final FarcastSettings settings = FarcastSettings.DEFAULTS;
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
        final UUID holder = UUID.randomUUID();

        assertEquals(3_000L, settings.cooldownMillis());
        settings.claim(cooldowns, holder, 0L);
        assertFalse(settings.available(cooldowns, holder, 2_999L));
        assertTrue(settings.available(cooldowns, holder, 3_000L));
    }

    // ---- Registry: enum, YAML and config agree ----

    private record Registry(String yamlKey, String tuningBlock, String displayName, int maxPower, String category,
                            Integer base, Integer increase, String enumLine) {}

    private static List<Registry> registry() {
        return List.of(
                new Registry("SniperHeadshot", "Sniper", "&7Sniper", 5, "Ultimate", null, null,
                        "SNIPER_HEADSHOT(\"SniperHeadshot\", \"Bow\"),"),
                new Registry("Virus", "Virus", "&7Virus", 4, "Unique", 8, 4,
                        "VIRUS(\"Virus\", \"Bow\", 8, 4),"),
                new Registry("Teleportation", "Teleportation", "&7Teleportation", 5, "Elite", 8, 4,
                        "TELEPORTATION(\"Teleportation\", \"Bow\", 8, 4),"),
                new Registry("Farcast", "Farcast", "&7Farcast", 5, "Elite", 8, 4,
                        "FARCAST(\"Farcast\", \"Bow\", 8, 4),"));
    }

    @Test
    void newBowEnchantsMatchTheirYamlAndEnum() throws IOException {
        final Map<?, ?> enchantments = section(yaml("Enchantments.yml"), "Enchantments");
        final String enumSource = read(JAVA + "api/enums/CEnchantments.java");
        final String tuning = read(JAVA + "api/utils/TuningConfig.java");

        for (final Registry expected : registry()) {
            final Map<?, ?> enchant = section(enchantments, expected.yamlKey());

            assertEquals(Boolean.TRUE, enchant.get("Enabled"));
            assertEquals("Bow", enchant.get("Enchantment-Type"));
            assertEquals(expected.maxPower(), enchant.get("MaxPower"));
            assertEquals(expected.displayName(), enchant.get("Name"));
            assertEquals(List.of(expected.category()), enchant.get("Categories"));
            assertTrue(enumSource.contains(expected.enumLine()), "missing enum entry: " + expected.enumLine());

            if (expected.base() == null) {
                assertFalse(enchant.containsKey("Chance-System"), "always-on enchants declare no chance system");
            } else {
                final Map<?, ?> chance = section(enchant, "Chance-System");
                assertEquals(expected.base(), chance.get("Base"));
                assertEquals(expected.increase(), chance.get("Increase"));
            }

            final Map<?, ?> config = section(enchant, "Tuning");

            assertFalse(config.isEmpty());
            for (final Object key : config.keySet()) {
                assertTrue(tuning.contains("\"" + expected.tuningBlock() + "." + key + "\""), "unwired tuning key: " + key);
            }
        }
    }

    @Test
    void sniperNeverRollsTheOldPoisonShotEnchant() throws IOException {
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");

        assertTrue(bow.contains("CEnchantments.SNIPER_HEADSHOT"));
        assertFalse(Pattern.compile("CEnchantments\\.SNIPER\\b").matcher(bow).find(),
                "the old Sniper (Poison Shot) enchant must stay untouched");

        assertEquals("&7Poison Shot", section(section(yaml("Enchantments.yml"), "Enchantments"), "Sniper").get("Name"));
    }

    @Test
    void sniperAndLongbowBothLandOnTheSameHitBecauseNoCapShips() throws IOException {
        // Behaviour: the helper the bow handlers call no longer truncates, so a
        // 3.5x Sniper and a Longbow bonus both apply to one event.
        final EntityDamageEvent event = new EntityDamageEvent(null, DamageCause.CUSTOM, null, 12.0);

        final double sniperBonus = 12.0 * (3.5 - 1.0);
        assertEquals(sniperBonus, DamageModifiers.applyOutgoingBonus(event, 12.0, sniperBonus), EPS);
        assertEquals(42.0, event.getDamage(), EPS);
        final double longbowBonus = 42.0 * 0.2;
        assertEquals(longbowBonus, DamageModifiers.applyOutgoingBonus(event, 42.0, longbowBonus), EPS);
        assertEquals(50.4, event.getDamage(), EPS, "bonuses stack because the shipped cap is disabled");

        // Alone, Longbow may still spend the same budget.
        final EntityDamageEvent longbowOnly = new EntityDamageEvent(null, DamageCause.CUSTOM, null, 12.0);
        assertEquals(2.4, DamageModifiers.applyOutgoingBonus(longbowOnly, 12.0, 12.0 * 0.2), EPS);
        assertEquals(14.4, longbowOnly.getDamage(), EPS);

        // Wiring: both handlers ask the shared helper and never cap the hit on their own.
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");
        assertTrue(method(bow, "private void applyHeadshot(").contains("DamageModifiers.applyOutgoingBonus("));
        assertTrue(method(bow, "public void onBowDamageModifiers(").contains("DamageModifiers.applyOutgoingBonus("));
        assertFalse(bow.contains("DamageModifiers.capBonus("), "a per-handler cap is what let the bonuses stack");
    }

    @Test
    void sniperCapturesTheDrawForceOnShootAndPrunesItLazily() throws IOException {
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");
        final int shoot = bow.indexOf("public void onBowShoot(");
        final String body = bow.substring(shoot, bow.indexOf("\n    }\n", shoot));

        assertTrue(body.contains("SniperSettings.Shot.inFlight(event.getForce()"));
        assertTrue(body.contains("this.drawnShots.put(arrow.getUniqueId()"));
        assertTrue(body.contains("pruneBowState(nowMillis())"), "the shoot path prunes stale state");
        assertTrue(bow.contains("this.drawnShots.values().removeIf"));
        assertFalse(bow.contains("PlayerQuitEvent"), "stale shots expire instead of a second listener");
    }

    @Test
    void sniperDrawIsKeptForALivingVictimAndDroppedForAnyOtherImpact() {
        final Map<UUID, SniperSettings.Shot> shots = new HashMap<>();
        final UUID arrow = UUID.randomUUID();

        shots.put(arrow, SniperSettings.Shot.inFlight(1.0f, 1_000L));

        // A block or a non-living entity never produces the damage event the
        // headshot needs, so the entry goes instead of waiting out the TTL.
        SniperSettings.recordImpact(shots, arrow, false, 65.5);
        assertFalse(shots.containsKey(arrow));

        // A living victim keeps it and gains the impact point.
        shots.put(arrow, SniperSettings.Shot.inFlight(1.0f, 1_000L));
        SniperSettings.recordImpact(shots, arrow, true, 65.5);

        final SniperSettings.Shot captured = shots.get(arrow);
        assertNotNull(captured);
        assertEquals(65.5, captured.impactY(), EPS);
        assertEquals(1.0f, captured.force());
        assertEquals(1_000L, captured.firedAtMillis());

        // Missing entries stay missing, and a consumed entry has nothing left.
        SniperSettings.recordImpact(shots, UUID.randomUUID(), true, 65.5);
        assertEquals(1, shots.size());
        assertNotNull(shots.remove(arrow));
        assertFalse(shots.containsKey(arrow));
    }

    @Test
    void sniperDrawTtlShipsAtSixtySeconds() {
        assertEquals(60_000L, SniperSettings.DEFAULTS.forceTtlMillis());

        final SniperSettings.Shot shot = SniperSettings.Shot.inFlight(1.0f, 0L);
        assertFalse(shot.expired(60_000L - 1L, SniperSettings.DEFAULTS.forceTtlMillis()));
        assertTrue(shot.expired(60_000L, SniperSettings.DEFAULTS.forceTtlMillis()));
    }

    @Test
    void bowBlastComponentOutlivesTheFoldForItsOwnEvent() {
        final EntityDamageEvent folded = new EntityDamageEvent(null, DamageCause.CUSTOM, null, 10.0);
        final EntityDamageEvent other = new EntityDamageEvent(null, DamageCause.CUSTOM, null, 10.0);

        // An event that was never folded reads zero and must not throw.
        assertEquals(0.0, BowBlastContext.blastComponent(folded), EPS, "no fold, no amount");

        BowBlastContext.fold(folded, 3.5, () -> {
            assertEquals(3.5, BowBlastContext.blastComponent(folded), EPS);
            assertEquals(0.0, BowBlastContext.blastComponent(other), EPS, "another event never inherits the amount");
        });

        // A later MONITOR listener in the same dispatch still reads it.
        assertEquals(3.5, BowBlastContext.blastComponent(folded), EPS, "the amount is retained after the fold returns");
        assertEquals(0.0, BowBlastContext.blastComponent(other), EPS);

        // The next fold on this thread overwrites the retained value.
        BowBlastContext.fold(other, 1.25, () -> assertEquals(1.25, BowBlastContext.blastComponent(other), EPS));
        assertEquals(1.25, BowBlastContext.blastComponent(other), EPS);
        assertEquals(0.0, BowBlastContext.blastComponent(folded), EPS, "the identity check stops a stale read");
    }

    @Test
    void virusChecksTheDebuffsBeforeStrippingRegeneration() throws IOException {
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");
        final String body = method(bow, "private void applyVirus(");

        assertTrue(body.contains("PotionEffectType.WITHER"));
        assertTrue(body.contains("PotionEffectType.POISON"));
        assertTrue(body.contains("runningEffect(victim.getPotionEffect(PotionEffectType.WITHER))"),
                "an infinite Wither must not count as present");
        assertTrue(body.indexOf("if (wither == null && poison == null) return;")
                < body.indexOf("removePotionEffect(PotionEffectType.REGENERATION)"),
                "no debuff present means Regeneration is left alone too");

        final String running = method(bow, "private static PotionEffect runningEffect(");
        assertTrue(running.contains("VirusSettings.present(effect.getDuration())"));
    }

    @Test
    void teleportationRunsTheTeleportOnTheShootersEntityScheduler() throws IOException {
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");
        final String body = method(bow, "private void tryTeleportation(");

        assertTrue(body.contains("shooter.getScheduler().run(this.plugin"));
        assertTrue(body.contains("shooter.teleportAsync(landing)"));
        assertTrue(body.indexOf("getScheduler().run") < body.indexOf("shooter.teleportAsync(landing)"));
        assertFalse(body.contains("shooter.teleport(landing)"), "a blocking teleport can throw on Folia");

        assertFalse(bow.contains("Bukkit.getScheduler()"));
        assertFalse(bow.contains("Bukkit.getRegionScheduler()"));
    }

    @Test
    void teleportationRechecksTheWindowInsideTheCallbackAndClaimsOnlyOnSuccess() throws IOException {
        final String body = method(read(JAVA + "enchantments/BowEnchantments.java"), "private void tryTeleportation(");
        final int callback = body.indexOf("getScheduler().run");

        assertTrue(body.indexOf("settings.available(", callback) < body.indexOf("teleportAsync(", callback),
                "the window is re-read on the shooter's own thread");
        assertTrue(body.indexOf("pendingTeleports.add(", callback) < body.indexOf("teleportAsync(", callback),
                "several arrows in one tick must yield one teleport");
        assertTrue(body.indexOf("teleportAsync(landing).whenComplete(") < body.indexOf("settings.claim("),
                "the window is spent only by a completed teleport");
        // whenComplete, not thenAccept, so a failed teleport still releases the in-flight flag.
        assertTrue(body.contains("Boolean.TRUE.equals(success)) settings.claim("),
                "only a successful teleport spends the window");
        assertTrue(body.contains("pendingTeleports.remove(shooterId)"));
    }

    @Test
    void teleportationSearchesBlocksOnTheCurrentRegionOnly() throws IOException {
        final String bow = read(JAVA + "enchantments/BowEnchantments.java");
        final String search = method(bow, "private static Location safeLandingNear(");
        final String safe = method(bow, "private static boolean landingIsSafe(");

        assertTrue(search.contains("Bukkit.isOwnedByCurrentRegion(candidate)"));
        assertTrue(search.contains("landingIsSafe(candidate)"));
        assertTrue(safe.contains("BlockFace.UP"));
        assertTrue(safe.contains("BlockFace.DOWN"));
        assertTrue(safe.contains("TeleportationSettings.safeLanding("));
    }

    @Test
    void farcastIgnoresBleedTicksProjectilesAndAllies() throws IOException {
        final String body = method(read(JAVA + "enchantments/BowEnchantments.java"), "public void onFarcast(");

        assertTrue(body.contains("SyntheticHit.isBleedTick()"));
        assertTrue(body.contains("DamageCause.ENTITY_ATTACK"));
        assertTrue(body.contains("DamageCause.ENTITY_SWEEP_ATTACK"));
        assertTrue(body.contains("this.support.isFriendly(attacker, shooter)"));
        assertTrue(body.contains("isBow(weapon.getType())"));
        assertTrue(body.contains("settings.push("));
        assertFalse(body.contains("setDamage("), "Farcast never damages the attacker");
    }

    private static String method(final String source, final String signature) {
        final int start = source.indexOf(signature);

        assertTrue(start >= 0, "missing method: " + signature);

        return source.substring(start, source.indexOf("\n    }\n", start));
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
