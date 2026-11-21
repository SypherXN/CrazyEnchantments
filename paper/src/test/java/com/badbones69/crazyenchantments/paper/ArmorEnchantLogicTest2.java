package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.AegisSettings;
import com.badbones69.crazyenchantments.paper.api.utils.CreeperArmorSettings;
import com.badbones69.crazyenchantments.paper.api.utils.HardenedSettings;
import com.badbones69.crazyenchantments.paper.api.utils.MetaphysicalSettings;
import com.badbones69.crazyenchantments.paper.api.utils.RagdollSettings;
import com.badbones69.crazyenchantments.paper.api.utils.StickySettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
 * Sticky, Aegis, Creeper Armor, Hardened, Ragdoll and Metaphysical. Pure-math
 * rules are tested directly; listener behaviour is guarded by reading the
 * source as text, in the style of DefensiveArmorLogicTest and
 * AxeEnchantLogicTest. No Bukkit server is loaded.
 */
class ArmorEnchantLogicTest2 {

    private static final double EPS = 1.0e-9;

    private static final long SECOND = 1_000_000_000L;

    private static final String ENUM_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/api/enums/CEnchantments.java";

    private static final String ARMOR_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/enchantments/ArmorEnchantments.java";

    private static final String ENCHANTMENTS_YML = "src/main/resources/Enchantments.yml";

    private static final String UTILS = "src/main/java/com/badbones69/crazyenchantments/paper/api/utils/";

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);

    // ---- Sticky: Ultimate, VIII, binary armor-removal gate (any worn piece blocks) ----

    @Test
    void anySingleWornStickyPieceBlocksArmorRemoval() {
        for (int level = 1; level <= 8; level++) {
            assertEquals(0.0, StickySettings.armorRemovalMultiplier(level), EPS, "Sticky " + level + " must block");
        }

        assertEquals(0.0, StickySettings.armorRemovalMultiplier(1), EPS, "level I is enough on its own");
        assertEquals(0.0, StickySettings.armorRemovalMultiplier(1, 0, 0, 0), EPS, "one worn piece among empty slots");
    }

    @Test
    void theGateDoesNotDependOnHowManyPiecesAreWorn() {
        assertEquals(0.0, StickySettings.armorRemovalMultiplier(1), EPS);
        assertEquals(0.0, StickySettings.armorRemovalMultiplier(1, 1), EPS);
        assertEquals(0.0, StickySettings.armorRemovalMultiplier(1, 2, 3, 8), EPS);
    }

    @Test
    void withoutStickyTheGateAllowsRemoval() {
        assertEquals(1.0, StickySettings.armorRemovalMultiplier(), EPS, "no pieces");
        assertEquals(1.0, StickySettings.armorRemovalMultiplier(0, 0, 0, 0), EPS, "unenchanted pieces");
        assertEquals(1.0, StickySettings.armorRemovalMultiplier(-3), EPS, "a negative level is not worn Sticky");
    }

    @Test
    void disarmerIsGoneAndStickyCarriesNoCurve() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String armor = read(ARMOR_SOURCE);
        final String sticky = entry(read(ENCHANTMENTS_YML), "Sticky");

        assertFalse(source.contains("DISARMER("), "the Disarmor enum entry was removed");
        assertFalse(source.contains("\"Disarmor\""), "its display name is gone too");
        assertFalse(armor.contains("onDisarmerUse"), "the Disarmer hook went with the enchant");
        assertFalse(sticky.contains("Reduction-Per-Level"), "Sticky no longer ships a per-level value");
        assertFalse(sticky.contains("Disarmor"), "Sticky no longer names the deleted enchant");
        assertTrue(sticky.contains("Currently has no effect"), "the shipped text does not promise an unwired effect");
    }

    // ---- Aegis: Elite, VI, window 5.0 s, threshold 8 - L (strict), x0.5 ----

    @Test
    void aegisThresholdIsEightMinusLevelAndStrict() {
        final AegisSettings settings = AegisSettings.DEFAULTS;
        final int[] thresholds = {7, 6, 5, 4, 3, 2};

        for (int level = 1; level <= 6; level++) {
            assertEquals(thresholds[level - 1], settings.threshold(level));
            // Exactly the threshold in attackers: nobody is halved, whatever the rank.
            for (int rank = 1; rank <= settings.threshold(level); rank++) {
                assertFalse(settings.affects(level, settings.threshold(level), rank), "level " + level + " count == threshold");
            }
            // One more attacker: only the newest rank is beyond the threshold.
            assertFalse(settings.affects(level, settings.threshold(level) + 1, settings.threshold(level)));
            assertTrue(settings.affects(level, settings.threshold(level) + 1, settings.threshold(level) + 1));
        }

        assertFalse(settings.affects(0, 50, 50), "no Aegis, no halving");
    }

    @Test
    void aegisAtLevelSixHalvesTheThirdAttackerAndBeyondButNotTheFirstTwo() {
        final AegisSettings settings = AegisSettings.DEFAULTS;
        final Map<UUID, Deque<Long>> hits = new HashMap<>();

        settings.record(hits, A, 0L);
        settings.record(hits, B, SECOND);

        // Two attackers: more than 2 is needed, so the second attacker's next hit is untouched.
        assertEquals(2, settings.count(hits, B, 2 * SECOND));
        assertEquals(1.0, settings.multiplier(6, settings.count(hits, B, 2 * SECOND), settings.rank(hits, B, 2 * SECOND)), EPS);

        // A third attacker makes three: it ranks third and its hit is halved.
        final int count = settings.count(hits, C, 2 * SECOND);
        final int rank = settings.rank(hits, C, 2 * SECOND);

        assertEquals(3, count);
        assertEquals(3, rank);
        assertEquals(0.5, settings.multiplier(6, count, rank), EPS);

        // The first two attackers are still not halved once the third is recorded.
        settings.record(hits, C, 2 * SECOND);

        assertEquals(1.0, settings.multiplier(6, 3, settings.rank(hits, A, 3 * SECOND)), EPS);
        assertEquals(1.0, settings.multiplier(6, 3, settings.rank(hits, B, 3 * SECOND)), EPS);
        assertEquals(0.5, settings.multiplier(6, 3, settings.rank(hits, C, 3 * SECOND)), EPS);
    }

    @Test
    void aegisRanksByFirstHitTimeAndAnAttackerKeepsItsRankWhileHittingAgain() {
        final AegisSettings settings = AegisSettings.DEFAULTS;
        final Map<UUID, Deque<Long>> hits = new HashMap<>();

        settings.record(hits, B, 1 * SECOND);
        settings.record(hits, A, 2 * SECOND);
        settings.record(hits, C, 3 * SECOND);
        // B hits again later: its FIRST hit is still the earliest, so it stays first.
        settings.record(hits, B, 4 * SECOND);

        assertEquals(List.of(B, A, C), settings.ranking(hits, 4 * SECOND));
        assertEquals(1, settings.rank(hits, B, 4 * SECOND));
        assertEquals(4, settings.rank(hits, D, 4 * SECOND), "a new attacker ranks last");
        assertEquals(0.5, settings.multiplier(6, settings.count(hits, D, 4 * SECOND), settings.rank(hits, D, 4 * SECOND)), EPS);
    }

    @Test
    void aegisUsesTheFirstHitStillInsideTheWindowAndTheBoundaryIsExpired() {
        final AegisSettings settings = AegisSettings.DEFAULTS;
        final Map<UUID, Deque<Long>> hits = new HashMap<>();

        settings.record(hits, A, 0L);
        settings.record(hits, B, 1 * SECOND);
        settings.record(hits, A, 4 * SECOND);

        // 4.999999999 s: A's first hit is 0.
        assertEquals(List.of(A, B), settings.ranking(hits, 5 * SECOND - 1L));

        // Exactly 5.0 s: A's hit at 0 is already gone, so A's first hit is the one at 4 s and it ranks after B.
        assertEquals(List.of(B, A), settings.ranking(hits, 5 * SECOND));
        assertEquals(1, settings.rank(hits, B, 5 * SECOND));
        assertEquals(2, settings.rank(hits, A, 5 * SECOND));

        // Everything past the window is dropped on read.
        assertEquals(1, settings.count(hits, D, 10 * SECOND));
        assertTrue(hits.isEmpty());
    }

    @Test
    void aegisHalvingAppliesOnceAndLowerLevelsNeedMoreAttackers() {
        final AegisSettings settings = AegisSettings.DEFAULTS;

        // Never 0.25: the multiplier is one fixed value however far past the threshold the count goes.
        assertEquals(0.5, settings.multiplier(6, 12, 12), EPS);
        assertEquals(0.5, settings.multiplier(6, 3, 3), EPS);

        // Level I needs more than seven attackers; level III more than five.
        assertEquals(1.0, settings.multiplier(1, 7, 7), EPS);
        assertEquals(0.5, settings.multiplier(1, 8, 8), EPS);
        assertEquals(1.0, settings.multiplier(3, 5, 5), EPS);
        assertEquals(0.5, settings.multiplier(3, 6, 6), EPS);
        assertEquals(1.0, settings.multiplier(0, 20, 20), EPS);
    }

    @Test
    void aegisKeepsItsOwnTrackerAndDoesNotTouchOrDuplicateAntiGanks() throws IOException {
        final String armor = read(ARMOR_SOURCE);
        final String settings = read(UTILS + "AegisSettings.java");

        assertFalse(armor.contains("recentAttackers"), "Anti Gank's private map is never read here");
        assertFalse(armor.contains("AntiGankSettings"));
        assertTrue(armor.contains("aegisHits"), "Aegis owns one tracker map");
        assertTrue(settings.contains("Deque<Long>"), "the tracker keeps first-hit times, which Anti Gank's latest-hit map cannot");

        final String hit = method(armor, "onAegisHit");

        assertTrue(hit.contains("isDirectMelee(event.getCause())"), "melee only");
        assertTrue(hit.contains("aegis.reach("), "count and rank come from one pass over the history");
        assertTrue(hit.contains("event.setDamage(event.getDamage() * multiplier)"), "halved once, on the base damage");
        assertEquals(1, count(hit, "event.setDamage("), "one edit per hit, never stacked");
        assertTrue(armor.contains("@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)\n    public void onAegisHit"),
                "before the HIGHEST handlers so lethality checks see the halved hit");
        assertTrue(method(armor, "onAegisRecord").contains("getFinalDamage() > 0.0"), "only landed hits are recorded");
    }

    @Test
    void aegisCountAndRankComeFromOnePassAndMatchTheSeparateReads() {
        final AegisSettings settings = AegisSettings.DEFAULTS;
        final Map<UUID, Deque<Long>> hits = new HashMap<>();

        settings.record(hits, A, 0L);
        settings.record(hits, B, SECOND);

        final AegisSettings.Reach recorded = settings.reach(hits, B, 2 * SECOND);

        assertEquals(2, recorded.count());
        assertEquals(2, recorded.rank());
        assertEquals(settings.count(hits, B, 2 * SECOND), recorded.count(), "one pass agrees with the separate reads");
        assertEquals(settings.rank(hits, B, 2 * SECOND), recorded.rank());

        final AegisSettings.Reach fresh = settings.reach(hits, D, 2 * SECOND);

        assertEquals(3, fresh.count(), "an attacker that has not hit yet is still counted");
        assertEquals(3, fresh.rank(), "and ranks last");
    }

    @Test
    void aegisHistoryIsBoundedToThirtyTwoAttackersAndEvictsTheOldest() {
        final AegisSettings settings = AegisSettings.DEFAULTS;
        final Map<UUID, Deque<Long>> hits = new HashMap<>();

        assertEquals(32, AegisSettings.MAX_ATTACKERS);

        for (int i = 0; i < 40; i++) settings.record(hits, new UUID(0, i), i * 1_000L);

        assertEquals(AegisSettings.MAX_ATTACKERS, hits.size(), "the history cannot grow with the fight");
        assertFalse(hits.containsKey(new UUID(0, 0)), "the earliest attacker made room");
        assertTrue(hits.containsKey(new UUID(0, 39)), "the newest attacker is tracked");
    }

    @Test
    void aegisOnlyTracksWearersWhoWearIt() throws IOException {
        final String record = method(read(ARMOR_SOURCE), "onAegisRecord");

        assertTrue(record.contains("wornHighest(wearer, CEnchantments.AEGIS, wearer.getEquipment().getArmorContents()) < 1"),
                "a victim without Aegis builds no history");
        assertTrue(record.indexOf("wornHighest(") < record.indexOf("computeIfAbsent("),
                "and the check happens before any history is created");
        assertFalse(record.contains("pieceLevels("), "bookkeeping must not roll or fire a use event");
    }

    // ---- Creeper Armor: Ultimate, III, [33, 67, 100] pp of the final explosion damage ----

    @Test
    void creeperArmorReductionTableIsThirtyThreeSixtySevenHundred() {
        final CreeperArmorSettings settings = CreeperArmorSettings.DEFAULTS;

        assertEquals(33.0, settings.piecePp(1), EPS);
        assertEquals(67.0, settings.piecePp(2), EPS);
        assertEquals(100.0, settings.piecePp(3), EPS);
        assertEquals(0.0, settings.piecePp(0), EPS);

        assertEquals(6.7, CreeperArmorSettings.reducedFinal(10.0, 33.0), 1.0e-9);
        assertEquals(3.3, CreeperArmorSettings.reducedFinal(10.0, 67.0), 1.0e-9);
        assertEquals(0.0, CreeperArmorSettings.reducedFinal(10.0, 100.0), EPS);
        assertEquals(10.0, CreeperArmorSettings.reducedFinal(10.0, 0.0), EPS);
        assertEquals(0.0, CreeperArmorSettings.reducedFinal(-4.0, 33.0), EPS);
    }

    @Test
    void creeperArmorPiecesAddAndAreCappedAtOneHundred() {
        final CreeperArmorSettings settings = CreeperArmorSettings.DEFAULTS;

        assertEquals(66.0, settings.totalPp(1, 1), EPS);
        assertEquals(99.0, settings.totalPp(1, 1, 1), EPS);
        assertEquals(100.0, settings.totalPp(2, 2), EPS, "67 + 67 caps at 100");
        assertEquals(100.0, settings.totalPp(3, 3, 3, 3), EPS);
        assertEquals(0.0, settings.totalPp(), EPS);
    }

    @Test
    void creeperArmorKnockbackIsCancelledOnlyAtLevelThree() {
        final CreeperArmorSettings settings = CreeperArmorSettings.DEFAULTS;

        assertFalse(settings.cancelsKnockback(0));
        assertFalse(settings.cancelsKnockback(1));
        assertFalse(settings.cancelsKnockback(2));
        assertTrue(settings.cancelsKnockback(3));
    }

    @Test
    void creeperArmorHealsOneHpOnceEveryThreeSecondsAtLevelThree() {
        final CreeperArmorSettings settings = CreeperArmorSettings.DEFAULTS;
        final long last = 50L;
        final long ready = last + 3 * SECOND;

        assertEquals(1.0, settings.healAmount(3, null, 0L, 10.0, 20.0), EPS);
        assertEquals(0.0, settings.healAmount(2, null, 0L, 10.0, 20.0), EPS, "level II does not heal");
        assertEquals(0.0, settings.healAmount(3, last, ready - 1L, 10.0, 20.0), EPS, "one nanosecond early");
        assertEquals(1.0, settings.healAmount(3, last, ready, 10.0, 20.0), EPS, "exactly 3.0 s later");
        assertEquals(0.5, settings.healAmount(3, null, 0L, 19.5, 20.0), EPS, "never above max health");
        assertEquals(0.0, settings.healAmount(3, null, 0L, 20.0, 20.0), EPS);
        assertEquals(0.0, settings.healAmount(3, null, 0L, 0.0, 20.0), EPS, "no heal for a dead wearer");
    }

    @Test
    void creeperArmorFindsTheBaseDamageWhoseFinalDamageIsReducedByTheWantedShare() {
        // Armor takes 40% and absorption eats a flat 2 HP: not proportional to the base.
        final java.util.function.DoubleUnaryOperator finalOf = base -> Math.max(0.0, base * 0.6 - 2.0);
        final double base = 20.0;
        final double finalDamage = finalOf.applyAsDouble(base);
        final double target = CreeperArmorSettings.reducedFinal(finalDamage, 50.0);
        final double solved = CreeperArmorSettings.baseForFinal(base, target, finalOf);

        assertEquals(10.0, finalDamage, EPS);
        assertEquals(5.0, target, EPS);
        assertEquals(5.0, finalOf.applyAsDouble(solved), 1.0e-6);
        assertTrue(finalOf.applyAsDouble(solved) <= target, "never removes less than asked");
        // Naively halving the base would have left 4.0 here, removing 60% instead of 50%.
        assertEquals(4.0, finalOf.applyAsDouble(base * 0.5), EPS);

        assertEquals(base, CreeperArmorSettings.baseForFinal(base, finalDamage, finalOf), EPS, "nothing to remove");
        assertEquals(0.0, CreeperArmorSettings.baseForFinal(base, 0.0, finalOf), EPS);
    }

    // ---- Hardened: Elite, III, [10, 20, 30]% per wear point ----

    @Test
    void hardenedSkipTableIsTenTwentyThirtyPercent() {
        final HardenedSettings settings = HardenedSettings.DEFAULTS;

        assertEquals(10.0, settings.skipChancePp(1), EPS);
        assertEquals(20.0, settings.skipChancePp(2), EPS);
        assertEquals(30.0, settings.skipChancePp(3), EPS);
        assertEquals(0.0, settings.skipChancePp(0), EPS);
    }

    @Test
    void hardenedRollsEveryWearPointOnItsOwnAndTheBoundaryIsExclusive() {
        final HardenedSettings settings = HardenedSettings.DEFAULTS;
        final double[] rolls = {0.05, 0.15, 0.25, 0.35, 0.5};

        // Level II skips below 0.20: the first two of five points.
        assertEquals(3, settings.survivingPoints(5, 2, sequence(rolls)));
        // Level III skips below 0.30: the first three.
        assertEquals(2, settings.survivingPoints(5, 3, sequence(rolls)));
        // Level I skips below 0.10: only the first.
        assertEquals(4, settings.survivingPoints(5, 1, sequence(rolls)));
        // A roll of exactly the chance survives.
        assertEquals(1, settings.survivingPoints(1, 1, sequence(0.10)));
        assertEquals(0, settings.survivingPoints(1, 1, sequence(0.0999999)));

        assertEquals(7, settings.survivingPoints(7, 0, sequence(0.0)), "no level skips nothing");
        assertEquals(0, settings.survivingPoints(0, 3, sequence(0.0)));
    }

    @Test
    void hardenedSkipFrequencyMatchesTheTable() {
        final HardenedSettings settings = HardenedSettings.DEFAULTS;
        final int trials = 300_000;

        for (int level = 1; level <= 3; level++) {
            final Random random = new Random(100 + level);
            int survived = 0;

            for (int i = 0; i < trials; i++) survived += settings.survivingPoints(1, level, random::nextDouble);

            assertEquals(1.0 - level * 0.10, survived / (double) trials, 0.005, "level " + level);
        }
    }

    @Test
    void hardenedOnlyTouchesWornArmorAndNeverHeldItems() throws IOException {
        final String handler = method(read(ARMOR_SOURCE), "onArmorWear");

        assertTrue(handler.contains("slot.isArmor()"), "the damaged item must be armor");
        assertTrue(handler.contains("getEquipment().getItem(slot)"), "and it must be what the player wears in that slot");
        assertTrue(handler.contains("worn != item && !worn.isSimilar(item)"),
                "a getter that copies the slot stack must not let wearing it bypass the roll");

        for (final String held : List.of("MainHand", "OffHand", "getItemInHand", "isHand", "HAND")) {
            assertFalse(handler.contains(held), "Reforged owns held items, Hardened must not read: " + held);
        }

        assertTrue(handler.contains("setCancelled(true)") && handler.contains("setDamage(surviving)"));
    }

    @Test
    void creeperArmorReducesOnlyTheFoldedSplashComponent() throws IOException {
        final String armor = read(ARMOR_SOURCE);
        final String folded = method(armor, "reduceFoldedBlast");

        assertTrue(folded.contains("BowBlastContext.blastComponent(event)"),
                "the folded splash is read from the bow hand-off for this exact event");
        assertTrue(folded.contains("CreeperArmorSettings.reducedFinal(blastHp, TuningConfig.creeperArmor().totalPp(levels))"),
                "the share is the same summed, capped Creeper Armor share");
        assertTrue(folded.contains("event.setDamage(event.getDamage() - removed)"),
                "only the component's share is removed from the hit");
        assertFalse(folded.contains("event.setDamage(removed)"), "the arrow damage is never replaced");
        assertTrue(folded.contains("levels.length == 0"), "a wearer without the enchant is left alone");

        // The standalone blast path is unchanged: provenance first, then the vanilla cause.
        final String standalone = method(armor, "onExplosionDamage");

        assertTrue(standalone.contains("final boolean blast = SyntheticHit.isBlast();"));
        assertTrue(standalone.indexOf("SyntheticHit.isBlast()") < standalone.indexOf("DamageCause.ENTITY_EXPLOSION"));
    }

    @Test
    void theFoldedBlastHandlerRunsAfterTheBowFoldAtMonitor() throws IOException {
        final String plugin = read("src/main/java/com/badbones69/crazyenchantments/paper/CrazyEnchantments.java");
        final String listener = read("src/main/java/com/badbones69/crazyenchantments/paper/listeners/DefensiveArmorListener.java");

        final int bow = plugin.indexOf("registerEvents(bow, this)");
        final int defensive = plugin.indexOf("new DefensiveArmorListener()");

        assertTrue(bow >= 0 && defensive > bow,
                "the handler must be registered after the bow listener, or it runs before the fold");
        assertTrue(listener.contains("armor.reduceFoldedBlast(event)"),
                "the reduction is owned by the armor listener, only its trigger lives here");
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onFoldedBlastDamage("),
                "MONITOR, because HIGHEST cannot see a fold that has not run yet");
    }

    // ---- Ragdoll: 3x knockback, IV, [1.05, 1.35, 1.65, 1.95] + 0.1 vertical, 1.0 s cooldown ----

    @Test
    void ragdollVelocityTableAndVerticalPart() {
        final RagdollSettings settings = RagdollSettings.DEFAULTS;
        final double[] expected = {1.05, 1.35, 1.65, 1.95};

        for (int level = 1; level <= 4; level++) {
            assertEquals(expected[level - 1], settings.horizontalVelocity(level), EPS);

            final RagdollSettings.Push push = settings.push(level, 3.0, 4.0);

            assertEquals(expected[level - 1], Math.hypot(push.x(), push.z()), 1.0e-9, "horizontal magnitude");
            assertEquals(0.1, push.y(), EPS, "vertical part");
            assertEquals(0.6, push.x() / Math.hypot(push.x(), push.z()), 1.0e-9, "away from the damager, along dx");
            assertEquals(0.8, push.z() / Math.hypot(push.x(), push.z()), 1.0e-9, "away from the damager, along dz");
        }

        assertEquals(0.0, settings.horizontalVelocity(0), EPS);
        assertTrue(settings.push(0, 1.0, 0.0).isNone(), "no level, no push");
        assertTrue(settings.push(2, 0.0, 0.0).isNone(), "overlapping entities get no NaN push");
        assertEquals(-1.95, settings.push(4, -2.0, 0.0).x(), 1.0e-9, "pushed the other way when the damager is on the other side");
    }

    @Test
    void ragdollCooldownIsOneSecondPerWearerAndInclusiveAtTheBoundary() {
        final RagdollSettings settings = RagdollSettings.DEFAULTS;
        final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

        assertTrue(settings.claim(cooldowns, A, 100L));
        assertFalse(settings.claim(cooldowns, A, 100L + SECOND - 1L), "one nanosecond early");
        assertTrue(settings.claim(cooldowns, B, 100L + 5L), "another wearer is independent");
        assertTrue(settings.claim(cooldowns, A, 100L + SECOND), "exactly 1.0 s later");
        assertFalse(settings.claim(cooldowns, A, 100L + SECOND + 1L), "the new window started at the last claim");
    }

    @Test
    void ragdollIgnoresProjectilesThornsExplosionsAlliesAndSelf() throws IOException {
        final String armor = read(ARMOR_SOURCE);
        final String handler = method(armor, "onRagdollHit");

        assertTrue(handler.contains("isDirectMelee(event.getCause())"), "entity attack and sweep only");
        assertTrue(handler.contains("event.getDamager() instanceof LivingEntity damager"), "a projectile is not a living damager");
        assertTrue(handler.contains("getCausingEntity()"), "the damager must be the one that caused the hit");
        assertTrue(handler.contains("damager.getUniqueId().equals(wearer.getUniqueId())"), "self");
        assertTrue(handler.contains("this.support.isFriendly(damager, wearer)"), "allies");
        assertFalse(handler.contains("THORNS") || handler.contains("EXPLOSION") || handler.contains("PROJECTILE"));
        assertTrue(handler.contains("Bukkit.isOwnedByCurrentRegion(damager)"), "Folia: the damager is only read when owned");
        assertTrue(armor.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onRagdollHit"),
                "only a hit that landed pushes");
        assertTrue(handler.contains("getScheduler().runDelayed(this.plugin"), "delayed work uses the entity scheduler");
        assertTrue(handler.contains("getVelocity().add("), "added after vanilla knockback, not replacing it");
    }

    // ---- Metaphysical: Ultimate, IV, boots only, [30, 50, 70, 90]% ----

    @Test
    void metaphysicalResistTableIsThirtyFiftySeventyNinety() {
        final MetaphysicalSettings settings = MetaphysicalSettings.DEFAULTS;
        final double[] expected = {30.0, 50.0, 70.0, 90.0};

        for (int level = 1; level <= 4; level++) {
            final double chance = expected[level - 1];

            assertEquals(chance, settings.resistChancePp(level), EPS);
            assertTrue(settings.resists(true, "ADDED", "PLUGIN", false, level, chance / 100.0 - 1.0e-9), "just under the chance resists");
            assertFalse(settings.resists(true, "ADDED", "PLUGIN", false, level, chance / 100.0), "the roll is exclusive at the chance");
            assertFalse(settings.resists(true, "ADDED", "PLUGIN", false, level, 0.999999) && chance < 100.0);
        }

        assertEquals(0.0, settings.resistChancePp(0), EPS);
        assertFalse(settings.resists(true, "ADDED", "PLUGIN", false, 0, 0.0), "no boots enchant, nothing resisted");
    }

    @Test
    void metaphysicalLeavesEveryOtherEffectAndAnUnresistedSlowUntouched() throws IOException {
        final MetaphysicalSettings settings = MetaphysicalSettings.DEFAULTS;

        // Mining Fatigue, Weakness and the rest are not slowness: never resisted, whatever the roll.
        for (final double roll : new double[]{0.0, 0.1, 0.5, 0.99}) {
            assertFalse(settings.resists(false, "ADDED", "PLUGIN", false, 4, roll), "other effects pass through");
        }

        // The decision is only ever cancel or not: an unresisted slow keeps its duration and amplifier because
        // the handler never builds, changes or re-applies an effect.
        final String handler = method(read(ARMOR_SOURCE), "onSlowApplied");

        assertFalse(handler.contains("new PotionEffect") || handler.contains("setOverride") || handler.contains("addPotionEffect"));
        assertTrue(handler.contains("PotionEffectType.SLOWNESS.equals(effect.getType())"));
        assertTrue(handler.contains("event.setCancelled(true)"));
    }

    @Test
    void metaphysicalResistsCesOwnSlowsAndOtherSourcesButNotTheWearersOwnActions() {
        final MetaphysicalSettings settings = MetaphysicalSettings.DEFAULTS;

        // CE's Snare, Pummel, Freeze and auras all apply through addPotionEffect: cause PLUGIN. Tipped
        // arrows, splash potions and effect clouds are other sources too.
        for (final String cause : List.of("PLUGIN", "ARROW", "POTION_SPLASH", "AREA_EFFECT_CLOUD", "ATTACK")) {
            assertFalse(MetaphysicalSettings.isExcludedCause(cause), cause + " is another source");
            assertTrue(settings.resists(true, "ADDED", cause, false, 4, 0.0), cause + " is resisted");
            assertTrue(settings.resists(true, "CHANGED", cause, false, 4, 0.0), cause + " upgrading a slow is resisted");
        }

        // The wearer's own potion, milk, commands and expiry are not another source.
        for (final String cause : List.of("POTION_DRINK", "MILK", "COMMAND", "EXPIRATION")) {
            assertTrue(MetaphysicalSettings.isExcludedCause(cause), cause);
            assertFalse(settings.resists(true, "ADDED", cause, false, 4, 0.0), cause + " is never resisted");
        }

        // Removing a slow is never resisted: milk and commands clear, they do not add.
        assertFalse(settings.resists(true, "CLEARED", "MILK", false, 4, 0.0));
        assertFalse(settings.resists(true, "REMOVED", "PLUGIN", false, 4, 0.0));
        assertFalse(settings.resists(true, "CLEARED", "COMMAND", false, 4, 0.0));

        // Self-inflicted CE armor effects (Hulk, Drunk, Insomnia) are exempt even though they arrive as PLUGIN.
        assertFalse(settings.resists(true, "ADDED", "PLUGIN", true, 4, 0.0));
    }

    @Test
    void procTestForceOnlySkipsTheRollNeverTheEligibility() throws IOException {
        final MetaphysicalSettings settings = MetaphysicalSettings.DEFAULTS;

        // Forced while eligible: a roll that would never pass is overridden.
        assertTrue(settings.resists(true, "ADDED", "PLUGIN", false, 4, 0.999999, true));
        assertFalse(settings.resists(true, "ADDED", "PLUGIN", false, 4, 0.999999), "the normal overload still rolls");

        // Forced is still not eligible: another effect, no boots level, CE's own effect.
        assertFalse(settings.resists(false, "ADDED", "PLUGIN", false, 4, 0.0, true), "not Slowness");
        assertFalse(settings.resists(true, "ADDED", "PLUGIN", false, 0, 0.0, true), "no boots enchant");
        assertFalse(settings.resists(true, "ADDED", "PLUGIN", true, 4, 0.0, true), "self-inflicted effect");

        // Forced is still not eligible: a removal, or the wearer's own potion, milk or command.
        assertFalse(settings.resists(true, "REMOVED", "PLUGIN", false, 4, 0.0, true));
        assertFalse(settings.resists(true, "CLEARED", "MILK", false, 4, 0.0, true));

        for (final String cause : List.of("POTION_DRINK", "MILK", "COMMAND", "EXPIRATION")) {
            assertFalse(settings.resists(true, "ADDED", cause, false, 4, 0.0, true), cause + " is never forced");
        }

        // The handler passes the switch only as resists()' last argument, after every gate.
        final String slow = method(read(ARMOR_SOURCE), "onSlowApplied");

        assertTrue(slow.contains("nextDouble(), ProcTest.isEnabled(wearer.getUniqueId())"),
                "the force flag is resists()' final argument, not a bypass around it");

        // The two self-rolled armor procs consult the switch after their own checks.
        final String armor = read(ARMOR_SOURCE);

        assertTrue(method(armor, "combinedPieceAttempt").contains("ProcTest.isEnabled(wearer.getUniqueId())"),
                "Angelic/Spirits/Feign Death skip only the combined roll");
        assertTrue(method(armor, "onArmorWear").contains("ProcTest.isEnabled(wearer.getUniqueId())"),
                "Hardened skips only its per-point roll");
    }

    @Test
    void metaphysicalSelfInflictedMarkerNestsAndAlwaysClears() {
        assertFalse(MetaphysicalSettings.isSelfInflicted());

        MetaphysicalSettings.runSelfInflicted(() -> {
            assertTrue(MetaphysicalSettings.isSelfInflicted());

            MetaphysicalSettings.runSelfInflicted(() -> assertTrue(MetaphysicalSettings.isSelfInflicted()));

            assertTrue(MetaphysicalSettings.isSelfInflicted(), "the inner block must not unmark the outer one");
        });

        assertFalse(MetaphysicalSettings.isSelfInflicted());

        assertThrows(IllegalStateException.class, () -> MetaphysicalSettings.runSelfInflicted(() -> {
            throw new IllegalStateException("boom");
        }));

        assertFalse(MetaphysicalSettings.isSelfInflicted(), "an exception still unmarks");
    }

    @Test
    void metaphysicalReadsOnlyTheBootsSlotAndCesOwnArmorEffectsAreMarked() throws IOException {
        final String armor = read(ARMOR_SOURCE);
        final String handler = method(armor, "onSlowApplied");

        assertTrue(handler.contains("getEquipment().getBoots()"));

        for (final String other : List.of("getArmorContents", "getHelmet", "getChestplate", "getLeggings", "getItemInMainHand", "getItemInHand")) {
            assertFalse(handler.contains(other), "Metaphysical must read only the boots, not " + other);
        }

        assertTrue(method(armor, "updateEffects").contains("MetaphysicalSettings.runSelfInflicted(() -> player.addPotionEffect(new PotionEffect(effect.getKey(), duration, amplifier)))")
                , "the permanent armor effects CE adds to the wearer are marked as self-inflicted");
    }

    // ---- Registration, YAML and config ----

    @Test
    void alwaysOnEnchantsCarryNoChanceValuesAndNoChanceSystem() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertTrue(source.contains("STICKY(\"Sticky\", \"Armor\"),"));
        assertTrue(source.contains("AEGIS(\"Aegis\", \"Armor\"),"));
        assertTrue(source.contains("CREEPER_ARMOR(\"Creeper Armor\", \"Armor\"),"));
        assertTrue(source.contains("HARDENED(\"Hardened\", \"Armor\"),"));
        assertTrue(source.contains("RAGDOLL(\"Ragdoll\", \"Armor\"),"));
        assertTrue(source.contains("METAPHYSICAL(\"Metaphysical\", \"Boots\"),"), "Metaphysical is a boots enchant");

        for (final String name : List.of("Sticky", "Aegis", "Creeper Armor", "Hardened", "Ragdoll", "Metaphysical")) {
            assertFalse(entry(yaml, name).contains("Chance-System"), name + " must not declare a Chance-System");
        }

        // The shared roll treats a chance of 0 as "always succeeds", so a zero chance must never be declared.
        assertFalse(Pattern.compile("(STICKY|AEGIS|CREEPER_ARMOR|HARDENED|RAGDOLL|METAPHYSICAL)\\(\"[^\"]+\", \"[^\"]+\", ").matcher(source).find());
    }

    @Test
    void yamlEntriesDeclareTheApprovedMaxPowerTypeAndCategory() throws IOException {
        final String yaml = read(ENCHANTMENTS_YML);
        final Map<String, String[]> expected = new HashMap<>();

        expected.put("Sticky", new String[]{"8", "Armor", "Ultimate"});
        expected.put("Aegis", new String[]{"6", "Armor", "Elite"});
        expected.put("Creeper Armor", new String[]{"3", "Armor", "Ultimate"});
        expected.put("Hardened", new String[]{"3", "Armor", "Elite"});
        expected.put("Ragdoll", new String[]{"4", "Armor", "Unique"});
        expected.put("Metaphysical", new String[]{"4", "Boots", "Ultimate"});

        expected.forEach((name, values) -> {
            final String entry = entry(yaml, name);

            assertTrue(entry.contains("Enabled: true"), name + " must be enabled");
            assertTrue(entry.contains("MaxPower: " + values[0] + "\n"), name + " MaxPower");
            assertTrue(entry.contains("Enchantment-Type: '" + values[1] + "'"), name + " type");
            assertTrue(entry.contains("Name: '&7" + name + "'"), name + " needs its &7 name");
            assertTrue(entry.contains("Categories:\n      - '" + values[2] + "'\n"), name + " must be only in " + values[2]);
            assertTrue(entry.contains("Info:") && entry.contains("Description:"), name + " needs Info and a Description");
        });
    }

    @Test
    void configShipsEveryTunableAtItsRecordDefault() throws IOException {
        final String yaml = read(ENCHANTMENTS_YML);

        assertEquals("5.0", value(yaml, "Aegis", "Window-Seconds"));
        assertEquals("0.5", value(yaml, "Aegis", "Damage-Multiplier"));
        assertEquals("[33.0, 67.0, 100.0]", value(yaml, "CreeperArmor", "Reduction-PP"));
        assertEquals("1.0", value(yaml, "CreeperArmor", "Heal"));
        assertEquals("3.0", value(yaml, "CreeperArmor", "Heal-Cooldown-Seconds"));
        assertEquals("[10.0, 20.0, 30.0]", value(yaml, "Hardened", "Skip-PP"));
        assertEquals("[1.05, 1.35, 1.65, 1.95]", value(yaml, "Ragdoll", "Horizontal-Velocity"));
        assertEquals("0.1", value(yaml, "Ragdoll", "Vertical-Velocity"));
        assertEquals("1.0", value(yaml, "Ragdoll", "Cooldown-Seconds"));
        assertEquals("[30.0, 50.0, 70.0, 90.0]", value(yaml, "Metaphysical", "Resist-PP"));

        assertEquals(AegisSettings.DEFAULTS.windowSeconds(), Double.parseDouble(value(yaml, "Aegis", "Window-Seconds")), EPS);
        assertEquals(CreeperArmorSettings.DEFAULTS.healCooldownSeconds(), Double.parseDouble(value(yaml, "CreeperArmor", "Heal-Cooldown-Seconds")), EPS);
        assertEquals(RagdollSettings.DEFAULTS.cooldownSeconds(), Double.parseDouble(value(yaml, "Ragdoll", "Cooldown-Seconds")), EPS);
    }

    @Test
    void invalidTablesAreRejectedAndTuningConfigReadsEveryKey() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new HardenedSettings(List.of(10.0, 20.0)));
        assertThrows(IllegalArgumentException.class, () -> new HardenedSettings(List.of(10.0, 20.0, 130.0)));
        assertThrows(IllegalArgumentException.class, () -> new CreeperArmorSettings(List.of(33.0, 67.0, 100.0), -1.0, 3.0));
        assertThrows(IllegalArgumentException.class, () -> new RagdollSettings(List.of(0.35, 0.45, 0.55), 0.1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new MetaphysicalSettings(List.of(30.0, 50.0, 70.0, Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> new AegisSettings(0.0, 0.5));

        final String tuning = read(UTILS + "TuningConfig.java");

        for (final String key : List.of(
                "Aegis.Window-Seconds", "Aegis.Damage-Multiplier",
                "CreeperArmor.Reduction-PP", "CreeperArmor.Heal", "CreeperArmor.Heal-Cooldown-Seconds",
                "Hardened.Skip-PP", "Ragdoll.Horizontal-Velocity", "Ragdoll.Vertical-Velocity",
                "Ragdoll.Cooldown-Seconds", "Metaphysical.Resist-PP")) {
            assertTrue(tuning.contains("\"" + key + "\""), "TuningConfig must read " + key);
        }
    }

    @Test
    void noArmorEnchantUsesTheBukkitSchedulerAndEveryHandlerSkipsSyntheticHits() throws IOException {
        final String armor = read(ARMOR_SOURCE);

        assertFalse(armor.contains("Bukkit.getScheduler()"), "Folia: no global scheduler");

        for (final String file : List.of("StickySettings", "AegisSettings", "CreeperArmorSettings", "HardenedSettings",
                "RagdollSettings", "MetaphysicalSettings")) {
            final String source = read(UTILS + file + ".java");

            assertFalse(source.contains("import org.bukkit"), file + " must stay free of Bukkit types");
            assertFalse(source.contains("getScheduler"), file);
        }

        for (final String name : List.of("onAegisHit", "onAegisRecord", "onExplosionDamage", "onArmorWear", "onRagdollHit")) {
            final String handler = method(armor, name);

            assertTrue(handler.contains("SyntheticHit.isBleedTick()") && handler.contains("EventUtils.isIgnoredEvent(event)"),
                    name + " must skip Bleed ticks and ignored events");
        }

        for (final String name : List.of("onExplosionDamage", "onExplosionKnockback", "onArmorWear", "onRagdollHit", "onSlowApplied", "onAegisHit")) {
            assertTrue(method(armor, name).contains("Bukkit.isOwnedByCurrentRegion(wearer)"), name + " must check Folia ownership");
        }
    }

    @Test
    void explosionDamageIsHandledOncePerHitOnTheFinalDamage() throws IOException {
        final String armor = read(ARMOR_SOURCE);
        final String handler = method(armor, "onExplosionDamage");

        assertTrue(handler.contains("DamageCause.ENTITY_EXPLOSION") && handler.contains("DamageCause.BLOCK_EXPLOSION"));
        assertTrue(handler.contains("event.getFinalDamage()"), "the reduction is a share of the final damage");
        assertTrue(handler.contains("CreeperArmorSettings.baseForFinal("));
        assertTrue(handler.contains("settings.totalPp(levels)"), "pieces are summed before a single application");
        assertTrue(handler.indexOf("SyntheticHit.isBlast()") >= 0
                        && handler.indexOf("SyntheticHit.isBlast()") < handler.indexOf("DamageCause.ENTITY_EXPLOSION"),
                "blast provenance is read before the vanilla explosion causes");
        assertTrue(handler.contains("if (!blast && SyntheticHit.isBleedTick()) return;"),
                "a synthetic blast is let through; every other synthetic hit is skipped");
        assertTrue(armor.contains("@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)\n    public void onExplosionDamage(final EntityDamageEvent"),
                "EntityDamageEvent covers block explosions; HIGH avoids sharing HIGHEST with System Reboot");

        final String knockback = method(armor, "onExplosionKnockback");

        assertTrue(knockback.contains("EntityKnockbackEvent.Cause.EXPLOSION") && knockback.contains("cancelsKnockback("));
        assertTrue(armor.contains("import io.papermc.paper.event.entity.EntityKnockbackEvent;"),
                "the Paper event, not the Bukkit one that is deprecated for removal");
    }

    // ---- helpers ----

    private static java.util.function.DoubleSupplier sequence(final double... values) {
        final int[] next = {0};

        return () -> values[next[0]++];
    }

    private static int count(final String text, final String needle) {
        int found = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) found++;

        return found;
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

        assertTrue(start.find(), "ArmorEnchantments must declare " + name);

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
