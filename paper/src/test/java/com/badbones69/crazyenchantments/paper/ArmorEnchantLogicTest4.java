package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.CurseSettings;
import com.badbones69.crazyenchantments.paper.api.utils.DestructionSettings;
import com.badbones69.crazyenchantments.paper.api.utils.FeignDeathSettings;
import com.badbones69.crazyenchantments.paper.api.utils.PlagueCarrierSettings;
import com.badbones69.crazyenchantments.paper.api.utils.SpiritsSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Curse, Destruction, Plague Carrier and Feign Death: pure tables, predicates
 * and source guards. No Bukkit is loaded.
 */
class ArmorEnchantLogicTest4 {

    private static final UUID WEARER = new UUID(0, 3);
    private static final UUID OTHER = new UUID(0, 4);
    private static final long SECOND = 1_000_000_000L;
    private static final String JAVA = "src/main/java/com/badbones69/crazyenchantments/paper/";
    private static final String ARMOR = JAVA + "enchantments/ArmorEnchantments.java";

    // ---------------------------------------------------------------------------------------------
    // Curse
    // ---------------------------------------------------------------------------------------------

    @Test
    void curseThresholdsFollowTheLevelTable() {
        final CurseSettings settings = CurseSettings.DEFAULTS;
        final double[] expected = {20.0, 25.0, 30.0, 35.0, 40.0};

        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], settings.triggerPp(level), "level " + level);
        }

        assertEquals(0.0, settings.triggerPp(0));
        assertEquals(40.0, settings.triggerPp(99));
    }

    @Test
    void curseIsActiveAtOrBelowTheCut() {
        final CurseSettings settings = CurseSettings.DEFAULTS;

        assertTrue(settings.below(5, 8.0, 20.0), "exactly 40% must count");
        assertTrue(settings.below(5, 7.99, 20.0));
        assertFalse(settings.below(5, 8.01, 20.0));
        assertTrue(settings.below(1, 4.0, 20.0), "exactly 20% must count");
        assertFalse(settings.below(1, 4.01, 20.0));
        assertFalse(settings.below(0, 1.0, 20.0));
        assertFalse(settings.below(5, 0.0, 0.0), "no max health means no cut to compare against");
    }

    @Test
    void curseRearmsOnlyFivePointsAboveTheCut() {
        final CurseSettings settings = CurseSettings.DEFAULTS;

        assertFalse(settings.rearmed(5, 8.99, 20.0), "44.95% is still inside the hysteresis band");
        assertTrue(settings.rearmed(5, 9.0, 20.0), "45% re-arms at V");
        assertFalse(settings.rearmed(1, 4.99, 20.0));
        assertTrue(settings.rearmed(1, 5.0, 20.0), "25% re-arms at I");

        assertEquals(5.0, settings.hysteresisPp());
        assertEquals(20, CurseSettings.REFRESH_TICKS);
        assertEquals(40, CurseSettings.EFFECT_TICKS, "an effect must outlast one refresh");
    }

    @Test
    void curseResistanceIsTwoAtLevelFiveOnly() {
        assertEquals(0, CurseSettings.resistanceAmplifier(1));
        assertEquals(0, CurseSettings.resistanceAmplifier(4));
        assertEquals(1, CurseSettings.resistanceAmplifier(5));
        assertEquals(1, CurseSettings.resistanceAmplifier(9));
    }

    @Test
    void curseOnlyEverFillsAGap() {
        assertTrue(CurseSettings.mayApply(false), "an absent effect is applied");
        assertFalse(CurseSettings.mayApply(true),
                "a present effect of the same type is never applied over, refreshed or removed");
        assertTrue(CurseSettings.REFRESH_TICKS > 0 && CurseSettings.EFFECT_TICKS > CurseSettings.REFRESH_TICKS,
                "the window is longer than the refresh so a late tick never drops the buff");
    }

    @Test
    void curseRejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new CurseSettings(List.of(20.0), 5.0));
        assertThrows(IllegalArgumentException.class,
                () -> new CurseSettings(List.of(20.0, 25.0, 30.0, 35.0, 40.0), -1.0));
    }

    // ---------------------------------------------------------------------------------------------
    // Destruction
    // ---------------------------------------------------------------------------------------------

    @Test
    void destructionRollsEveryThreeSecondsForOneAndAHalfHp() {
        final DestructionSettings settings = DestructionSettings.DEFAULTS;

        assertEquals(3.0, settings.cadenceSeconds());
        assertEquals(60L, settings.cadenceTicks());
        assertEquals(4.0, settings.radiusBlocks());
        assertEquals(1.5, settings.damageHp());
    }

    @Test
    void destructionSlownessShipsOffAtTwoSeconds() {
        final DestructionSettings settings = DestructionSettings.DEFAULTS;

        assertFalse(settings.applySlowness());
        assertEquals(2.0, settings.slownessSeconds());
        assertEquals(40, settings.slownessTicks());
        assertTrue(new DestructionSettings(3.0, 4.0, 1.5, true, 2.0).applySlowness());
    }

    @Test
    void destructionNeverTargetsTheWearerAlliesDeadForeignOrIgnoredEntities() {
        final DestructionSettings settings = DestructionSettings.DEFAULTS;

        assertTrue(settings.mayHit(false, false, false, true, false, 16.0), "exactly 4 blocks is inside");
        assertFalse(settings.mayHit(false, false, false, true, false, 16.01));
        assertFalse(settings.mayHit(true, false, false, true, false, 1.0), "never the wearer");
        assertFalse(settings.mayHit(false, true, false, true, false, 1.0), "never an ally");
        assertFalse(settings.mayHit(false, false, true, true, false, 1.0), "never a dead entity");
        assertFalse(settings.mayHit(false, false, false, false, false, 1.0), "never another region's entity");
        assertFalse(settings.mayHit(false, false, false, true, true, 1.0), "never an ignored entity");
    }

    @Test
    void destructionChanceMatchesTheEnumAndTheYaml() throws IOException {
        checkChanceEnchant("DESTRUCTION", "Destruction", "Helmet", "Elite", 5, 10, 5);
    }

    // ---------------------------------------------------------------------------------------------
    // Plague Carrier
    // ---------------------------------------------------------------------------------------------

    @Test
    void plagueCreeperCountFollowsTheLevelTable() {
        final int[] expected = {1, 1, 1, 2, 2, 2, 3, 3};

        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], PlagueCarrierSettings.DEFAULTS.creepers(level), "level " + level);
        }

        assertEquals(0, PlagueCarrierSettings.DEFAULTS.creepers(0));
        assertEquals(0, PlagueCarrierSettings.DEFAULTS.creepers(Integer.MIN_VALUE));
        assertEquals(3, PlagueCarrierSettings.DEFAULTS.creepers(99));
    }

    @Test
    void plagueTriggersAtThirtyAndOnlyRearmsAtFifty() {
        final PlagueCarrierSettings settings = PlagueCarrierSettings.DEFAULTS;

        assertTrue(settings.triggers(6.0, 20.0), "exactly 30% must fire");
        assertFalse(settings.triggers(6.01, 20.0));
        assertTrue(settings.triggers(0.0, 20.0));
        assertFalse(settings.triggers(19.0, 0.0));

        assertFalse(settings.rearmed(9.99, 20.0), "49.95% is not above 50%");
        assertFalse(settings.rearmed(10.0, 20.0), "exactly 50% must not re-arm");
        assertTrue(settings.rearmed(10.01, 20.0), "just above 50% re-arms");

        assertEquals(30.0, settings.triggerPp());
        assertEquals(50.0, settings.rearmPp());
        assertTrue(settings.rearmPp() > settings.triggerPp(), "the re-arm cut must sit above the trigger");
    }

    @Test
    void plagueCooldownBoundaryIsSixtySecondsPerWearer() {
        final PlagueCarrierSettings settings = PlagueCarrierSettings.DEFAULTS;
        final var cooldowns = new ConcurrentHashMap<UUID, Long>();

        assertEquals(60.0, settings.cooldownSeconds());
        assertTrue(settings.claim(cooldowns, WEARER, 0));
        assertFalse(settings.claim(cooldowns, WEARER, 60 * SECOND - 1));
        assertTrue(settings.claim(cooldowns, OTHER, 60 * SECOND - 1));
        assertTrue(settings.claim(cooldowns, WEARER, 60 * SECOND));
        assertFalse(settings.claim(cooldowns, WEARER, 60 * SECOND + 1));
    }

    @Test
    void plagueBurstIsAVisualOnlyBlastWithAPoisonRing() {
        final PlagueCarrierSettings settings = PlagueCarrierSettings.DEFAULTS;

        assertEquals(4.0, settings.fuseSeconds());
        assertEquals(80L, settings.fuseTicks());
        assertEquals(3.0, settings.explosionRadius());
        assertEquals(4.0, settings.explosionDamageHp());
        assertEquals(4.0, settings.poisonRadius());
        assertEquals(80, settings.poisonTicks());

        assertTrue(settings.withinPoison(16.0), "exactly 4 blocks is poisoned");
        assertFalse(settings.withinPoison(16.01));
        assertFalse(settings.withinPoison(-1.0), "a foreign entity has no distance here");
    }

    // ---------------------------------------------------------------------------------------------
    // Feign Death
    // ---------------------------------------------------------------------------------------------

    @Test
    void feignTriggersAtOrBelowTwentyFivePercent() {
        final FeignDeathSettings settings = FeignDeathSettings.DEFAULTS;

        assertTrue(settings.triggers(5.0, 20.0), "exactly 25% must trigger");
        assertFalse(settings.triggers(5.01, 20.0), "above 25% must not trigger");
        assertTrue(settings.triggers(0.0, 20.0));
        assertTrue(settings.triggers(-3.0, 20.0));
        assertFalse(settings.triggers(1.0, 0.0));
    }

    @Test
    void feignHitCounterFollowsTheLevelTable() {
        final FeignDeathSettings settings = FeignDeathSettings.DEFAULTS;
        final int[] expected = {1, 2, 3, 4};

        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], settings.hitsToEnd(level), "level " + level);
        }

        assertEquals(0, settings.hitsToEnd(0));
        assertEquals(4, settings.hitsToEnd(9));
        assertFalse(settings.endsAfter(3, 2));
        assertTrue(settings.endsAfter(3, 3));
        assertTrue(settings.endsAfter(3, 4));
        assertFalse(settings.endsAfter(0, 100), "no level never ends a vanish");
    }

    @Test
    void feignLastsSixSecondsOnATwoMinuteCooldown() {
        final FeignDeathSettings settings = FeignDeathSettings.DEFAULTS;
        final var cooldowns = new ConcurrentHashMap<UUID, Long>();

        assertEquals(6.0, settings.durationSeconds());
        assertEquals(120L, settings.durationTicks());
        assertEquals(120.0, settings.cooldownSeconds());

        assertTrue(settings.claim(cooldowns, WEARER, 0));
        assertFalse(settings.claim(cooldowns, WEARER, 120 * SECOND - 1));
        assertTrue(settings.claim(cooldowns, OTHER, 120 * SECOND - 1));
        assertTrue(settings.claim(cooldowns, WEARER, 120 * SECOND));
    }

    @Test
    void feignChanceMatchesTheEnumAndTheYaml() throws IOException {
        checkChanceEnchant("FEIGN_DEATH", "Feign Death", "Armor", "Legendary", 4, 5, 5);
    }

    @Test
    void curseAndPlagueCarrierShipWithoutAChanceSystem() throws IOException {
        final String source = read(JAVA + "api/enums/CEnchantments.java");
        final String yaml = read("src/main/resources/Enchantments.yml");

        assertTrue(source.contains("CURSE(\"Curse\", \"Chestplate\")"));
        assertTrue(source.contains("PLAGUE_CARRIER(\"Plague Carrier\", \"Leggings\")"));

        for (final String name : List.of("Curse", "Plague Carrier")) {
            final var entry = Pattern.compile("(?ms)^  " + Pattern.quote(name) + ":\\n(.*?)(?=^  \\S|\\z)").matcher(yaml);

            assertTrue(entry.find(), name);
            assertFalse(entry.group(1).contains("Chance-System:"), name);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tuning keys
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyShippedNewTuningKeyIsValidatedAndMatchesTheRecordDefault() throws IOException {
        final String yaml = read("src/main/resources/Enchantments.yml");
        final String tuning = read(JAVA + "api/utils/TuningConfig.java");
        final var curse = CurseSettings.DEFAULTS;
        final var destruction = DestructionSettings.DEFAULTS;
        final var plague = PlagueCarrierSettings.DEFAULTS;
        final var feign = FeignDeathSettings.DEFAULTS;

        Map.of(
                "Curse", Map.of(
                        "Hysteresis-PP", String.valueOf(curse.hysteresisPp())),
                "Destruction", Map.of(
                        "Cadence-Seconds", String.valueOf(destruction.cadenceSeconds()),
                        "Radius", String.valueOf(destruction.radiusBlocks()),
                        "Damage-HP", String.valueOf(destruction.damageHp()),
                        "Apply-Slowness", String.valueOf(destruction.applySlowness()),
                        "Slowness-Seconds", String.valueOf(destruction.slownessSeconds())),
                "PlagueCarrier", Map.of(
                        "Trigger-PP", String.valueOf(plague.triggerPp()),
                        "Rearm-PP", String.valueOf(plague.rearmPp()),
                        "Cooldown-Seconds", String.valueOf(plague.cooldownSeconds()),
                        "Fuse-Seconds", String.valueOf(plague.fuseSeconds()),
                        "Explosion-Radius", String.valueOf(plague.explosionRadius()),
                        "Explosion-Damage-HP", String.valueOf(plague.explosionDamageHp()),
                        "Poison-Radius", String.valueOf(plague.poisonRadius()),
                        "Poison-Seconds", String.valueOf(plague.poisonSeconds())),
                "FeignDeath", Map.of(
                        "Health-PP", String.valueOf(feign.healthPp()),
                        "Duration-Seconds", String.valueOf(feign.durationSeconds()),
                        "Cooldown-Seconds", String.valueOf(feign.cooldownSeconds()))
        ).forEach((name, keys) -> {
            final String enchant = TuningConfig.sectionKeys().get(name);
            assertNotNull(enchant, "no enchant key for tuning block " + name);
            final var block = Pattern.compile("(?ms)^  " + Pattern.quote(enchant) + ":\\n(.*?)(?=^  \\S|\\z)").matcher(yaml);

            assertTrue(block.find(), name);

            keys.forEach((key, value) -> {
                assertTrue(block.group(1).contains("      " + key + ": " + value + "\n"), name + "." + key);
                assertTrue(tuning.contains("\"" + name + "." + key + "\""), name + "." + key);
            });
        });

        assertTrue(yaml.contains("      Trigger-PP: [20.0, 25.0, 30.0, 35.0, 40.0]\n"), "Curse.Trigger-PP list");
        assertTrue(tuning.contains("values.table(\"Curse.Trigger-PP\""), "Curse.Trigger-PP is validated as a table");
        assertTrue(tuning.contains("values.bool(\"Destruction.Apply-Slowness\""), "Apply-Slowness is validated as a boolean");
    }

    // ---------------------------------------------------------------------------------------------
    // Source guards
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyRunUsesAnEntitySchedulerAndNeverBukkitScheduler() throws IOException {
        final String source = read(ARMOR);

        assertFalse(source.contains("Bukkit.getScheduler()"), "Folia: never the global scheduler");
        assertTrue(method(source, "scheduleRun").contains("wearer.getScheduler().runAtFixedRate(this.plugin"));
        assertTrue(method(source, "scheduleFuse").contains("creeper.getScheduler().runDelayed(this.plugin"));
        assertTrue(method(source, "startFeign").contains("wearer.getScheduler().runDelayed(this.plugin"));
        assertTrue(method(source, "onArmorJoin").contains("Bukkit.isOwnedByCurrentRegion(player)"));
    }

    @Test
    void plagueBurstsNeverBreakBlocksOrStartFires() throws IOException {
        final String source = read(ARMOR);
        final String burst = method(source, "explodePlagueCreeper");

        assertFalse(source.contains("createExplosion"), "no world explosion may be created");
        assertTrue(burst.contains("ExplosionHelper.explode("), "the visual blast comes from ExplosionHelper");
        assertTrue(burst.contains("new ExplosionHelper.Blast(settings.explosionRadius(), settings.explosionDamageHp(), 0, 0)"),
                "the blast carries no fire ticks");
        assertTrue(burst.contains("Bukkit.isOwnedByCurrentRegion(creeper)"), "only the owning region may blast");
    }

    @Test
    void summonedCreepersAreTaggedPassiveAndCannotLootOrHit() throws IOException {
        final String source = read(ARMOR);
        final String configure = method(source, "configurePlagueCreeper");

        assertTrue(configure.contains("plagueCreeperKey"));
        assertTrue(configure.contains("setAI(false)") && configure.contains("setTarget(null)"));
        assertTrue(configure.contains("setInvulnerable(true)") && configure.contains("setPersistent(false)"));
        assertTrue(configure.contains("setLootTable(null)"));
        assertTrue(configure.contains("setExplosionRadius(0)"));

        assertTrue(method(source, "onPlagueTarget").contains("isPlagueCreeper(event.getTarget())"));
        assertTrue(method(source, "onPlagueDamage").contains("isPlagueCreeper(attacker(event))"));

        final String death = method(source, "onPlagueDeath");

        assertTrue(death.contains("getDrops().clear()") && death.contains("setDroppedExp(0)"));
    }

    @Test
    void creeperCleanupCoversExpiryDeathQuitDisableAndReload() throws IOException {
        final String source = read(ARMOR);

        assertTrue(method(source, "scheduleFuse").contains("settings == TuningConfig.plagueCarrier()"), "reload");
        assertTrue(method(source, "explodePlagueCreeper").contains("retirePlagueCreeper("), "expiry");
        assertTrue(method(source, "retirePlagueRun").contains("run.fuses"), "fuses are cancelled");
        assertTrue(method(source, "retirePlagueRun").contains("removeSummon(creeper,"), "summons are removed");
        assertTrue(method(source, "onRunDeath").contains("stopRuns("), "death ends every worn run immediately");
        assertTrue(method(source, "onArmorQuit").contains("stopRuns("), "quit");
        assertTrue(method(source, "stopRuns").contains("stopPlague("));
        assertTrue(method(source, "stop").contains("this.plagueRuns.keySet()"), "disable");
    }

    @Test
    void feignEndRestoresVisibilityWithoutHealingOrTeleporting() throws IOException {
        final String source = read(ARMOR);
        final String reveal = method(source, "revealToAll");

        assertTrue(source.contains("viewer.hideEntity(ArmorEnchantments.this.plugin, target)"),
                "the production seam hides the target for that viewer");
        assertTrue(source.contains("viewer.showEntity(ArmorEnchantments.this.plugin, target)"),
                "and only undoes a hide it recorded");
        assertFalse(source.contains("setInvisible"), "Feign never owns the wearer's invisibility flag");
        assertFalse(reveal.contains("teleport"));
        assertFalse(reveal.contains("setHealth"));

        final String trigger = method(source, "tryFeignDeath");

        assertTrue(trigger.contains("this.feignRuns.containsKey(id)"), "never re-triggers while active");
        assertTrue(trigger.contains("TuningConfig.feignDeath()"));
        assertTrue(method(source, "onFeignHit").contains("endsAfter(run.level, ++run.hits)"), "hits end the vanish");
        assertTrue(method(source, "onFeignHit").contains("damager instanceof Player"));
    }

    @Test
    void feignDeathNegatesTheTriggeringHit() throws IOException {
        final String source = read(JAVA + "enchantments/ArmorEnchantments.java");
        final String trigger = method(source, "tryFeignDeath");

        assertTrue(trigger.contains("event.setCancelled(true)"), "feign death must negate the killing blow");
        assertTrue(trigger.contains("event.setDamage(0.0)"), "and clear its damage");
        assertTrue(trigger.indexOf("settings.claim(") < trigger.indexOf("event.setCancelled(true)"),
                "a hit is only negated once the cooldown was claimed");
        assertFalse(trigger.contains("setHealth("), "feign death must not heal");
        assertFalse(trigger.contains(".teleport("), "feign death must not move the wearer");
    }

    @Test
    void spiritsOnlyAnswerDamageFromAnotherEntity() throws IOException {
        final String source = read(JAVA + "enchantments/ArmorEnchantments.java");
        final String worn = method(source, "onSupportDamage");

        // The shared hostile-source rule: a living attacker that is not the wearer, is owned by
        // this region and is not an ally. It is defined once and the Spirits branch reads it, so
        // deleting Spirits' own guard fails here instead of matching Angelic's earlier one.
        assertTrue(worn.contains("attacker instanceof LivingEntity && SpiritsSettings.hostileSource("),
                "spirits need a living, non-self, owned, non-ally source");
        assertTrue(worn.contains("() -> isSupportAlly(wearer, attacker)"),
                "the ally provider is a lazy argument, so an off-region attacker never reaches it");

        final int start = worn.indexOf("startSpirits(");
        final int guard = worn.lastIndexOf("if (hostile", start);

        assertTrue(guard >= 0 && guard < start, "the hostile guard must be the branch that summons");
        assertTrue(worn.substring(guard, start).contains("!this.spiritsRuns.containsKey(id)"),
                "and the summon is inside Spirits' own guard");

        final int declaration = worn.indexOf("final boolean hostile");
        final int body = worn.indexOf("if (hostile) {");
        final int angelic = worn.indexOf("startAngelic(");
        final int spiritLink = worn.indexOf("healAllies(");

        assertTrue(declaration >= 0 && body > declaration, "the shared rule is declared once");
        assertTrue(angelic > body && spiritLink > angelic, "Angelic and Spirit Link share the same rule");
    }

    @Test
    void theHostilePredicateNeverAsksForAnAllyItCannotReach() {
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        assertFalse(SpiritsSettings.hostileSource(false, false, () -> { calls.incrementAndGet(); return false; }),
                "an attacker owned by another region is never hostile");
        assertEquals(0, calls.get(), "an off-region attacker must not reach the ally provider");

        assertFalse(SpiritsSettings.hostileSource(true, true, () -> { calls.incrementAndGet(); return false; }),
                "a self-inflicted hit is never hostile");
        assertEquals(0, calls.get(), "a self-inflicted hit must not reach the ally provider");

        assertTrue(SpiritsSettings.hostileSource(false, true, () -> { calls.incrementAndGet(); return false; }),
                "an in-region non-ally is hostile");
        assertEquals(1, calls.get(), "the provider is reached once both earlier checks pass");

        assertFalse(SpiritsSettings.hostileSource(false, true, () -> { calls.incrementAndGet(); return true; }),
                "an in-region ally is not hostile");
        assertEquals(2, calls.get());
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static void checkChanceEnchant(final String constant, final String name, final String type,
                                           final String category, final int max, final int base, final int increase) throws IOException {
        final String source = read(JAVA + "api/enums/CEnchantments.java");
        final String yaml = read("src/main/resources/Enchantments.yml");

        assertTrue(source.contains("%s(\"%s\", \"%s\", %d, %d)".formatted(constant, name, type, base, increase)), constant);

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
