package com.badbones69.crazyenchantments.paper.commands.types.admin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomGearSelectionTest {

    @Test
    void rollsDistinctEnchantmentsAtValidLevels() {
        final List<RandomGearCommand.Candidate> pool = List.of(
                candidate("A", 3),
                candidate("B", 5),
                candidate("C", 2),
                candidate("D", 4));

        final RandomGearCommand.Selection selection = RandomGearCommand.select(pool, List.of(), 3, 3, new Random(7));

        assertNull(selection.failure);
        assertEquals(3, selection.rolls.size());
        assertEquals(3, selection.rolls.stream().map(RandomGearCommand.Roll::key).distinct().count());

        for (final RandomGearCommand.Roll roll : selection.rolls) {
            final int max = pool.stream()
                    .filter(candidate -> candidate.key().equals(roll.key()))
                    .findFirst()
                    .orElseThrow()
                    .maxLevel();

            assertTrue(roll.level() >= 1 && roll.level() <= max,
                    roll.key() + " rolled outside 1.." + max + ": " + roll.level());
        }
    }

    @Test
    void levelsStayWithinTheConfiguredMaxAcrossSeeds() {
        final List<RandomGearCommand.Candidate> pool = List.of(candidate("A", 4));

        for (int seed = 0; seed < 50; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.select(pool, List.of(), 1, 1, new Random(seed));

            assertNull(selection.failure);
            assertTrue(selection.rolls.getFirst().level() >= 1);
            assertTrue(selection.rolls.getFirst().level() <= 4);
        }
    }

    @Test
    void refusesAConflictingPairInEitherDirection() {
        final RandomGearCommand.Candidate a = candidate("A", 1);
        final RandomGearCommand.Candidate b = candidate("B", 1, "A");

        for (int seed = 0; seed < 20; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.select(
                    List.of(a, b), List.of(), 2, 2, new Random(seed));

            assertNotNull(selection.failure, "A and B must never be rolled together");
            assertTrue(selection.rolls.isEmpty());
        }
    }

    @Test
    void failsBeforeAnyChangeWhenSlotsAreShort() {
        final List<RandomGearCommand.Candidate> pool = List.of(
                candidate("A", 1), candidate("B", 1), candidate("C", 1));

        final RandomGearCommand.Selection selection = RandomGearCommand.select(pool, List.of(), 3, 2, new Random(2));

        assertNotNull(selection.failure);
        assertTrue(selection.rolls.isEmpty());
    }

    @Test
    void zeroSlotsExplainTheDefaultBaseLimit() {
        final RandomGearCommand.Selection selection = RandomGearCommand.select(
                List.of(candidate("A", 1)), List.of(), 1, 0, new Random(1));

        assertNotNull(selection.failure);
        assertTrue(selection.failure.contains("Base-Limit"), selection.failure);
    }

    @Test
    void neverRerollsAnEnchantAlreadyOnTheItem() {
        final RandomGearCommand.Candidate a = candidate("A", 1);

        final RandomGearCommand.Selection selection = RandomGearCommand.select(
                List.of(a), List.of(a), 1, 1, new Random(3));

        assertNotNull(selection.failure);
        assertTrue(selection.rolls.isEmpty());
    }

    @Test
    void skipsCandidatesThatConflictWithExistingEnchantments() {
        final RandomGearCommand.Candidate existing = candidate("A", 1);
        final RandomGearCommand.Candidate blocked = candidate("B", 1, "A");
        final RandomGearCommand.Candidate allowed = candidate("C", 1);

        for (int seed = 0; seed < 20; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.select(
                    List.of(blocked, allowed), List.of(existing), 1, 1, new Random(seed));

            assertNull(selection.failure);
            assertEquals("C", selection.rolls.getFirst().key());
        }
    }

    @Test
    void parsesFixedAndInclusiveRangeCounts() {
        assertEquals(new RandomGearCommand.CountRange(5, 5), RandomGearCommand.parseCount("5"));
        assertEquals(new RandomGearCommand.CountRange(2, 5), RandomGearCommand.parseCount("2-5"));
        assertEquals(new RandomGearCommand.CountRange(2, 5), RandomGearCommand.parseCount(" 2-5 "));
    }

    @Test
    void rejectsMalformedReversedZeroAndOverflowingCounts() {
        for (final String value : List.of("0", "-1", "5-2", "1-0", "0-1", "2-0", "1-2-3", "2-", "abc",
                "2147483648", "1-2147483648", "2 5")) {
            assertThrows(IllegalArgumentException.class, () -> RandomGearCommand.parseCount(value), value);
        }
    }

    @Test
    void commonAliasResolvesToTheSimpleTier() {
        assertEquals("Simple", RandomGearCommand.canonicalRarity("Common"));
        assertEquals("Simple", RandomGearCommand.canonicalRarity("common"));
        assertEquals("Unique", RandomGearCommand.canonicalRarity("Unique"));
        assertEquals("any", RandomGearCommand.canonicalRarity("any"));
    }

    @Test
    void mixedPrefersOneOfEachViableTierBeforeRepeating() {
        final List<RandomGearCommand.TierCandidate> pool = List.of(
                tier("Simple", "A", 1),
                tier("Unique", "B", 1),
                tier("Elite", "C", 1));

        for (int seed = 0; seed < 20; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.selectMixed(
                    pool, List.of(), 3, new Random(seed));

            assertNull(selection.failure);
            assertEquals(3, selection.rolls.size());
            assertEquals(3, selection.rolls.stream().map(RandomGearCommand.Roll::tier).distinct().count());
        }
    }

    @Test
    void mixedRepeatsATierOnlyWhenNoUnusedTierRemainsViable() {
        final List<RandomGearCommand.TierCandidate> pool = List.of(
                tier("Simple", "A", 1),
                tier("Simple", "D", 1),
                tier("Unique", "B", 1));

        for (int seed = 0; seed < 20; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.selectMixed(
                    pool, List.of(), 3, new Random(seed));

            assertNull(selection.failure);
            assertEquals(3, selection.rolls.size());
            assertEquals(2, selection.rolls.stream().map(RandomGearCommand.Roll::tier).distinct().count());
            assertEquals(2, selection.rolls.stream()
                    .filter(roll -> roll.tier().equals("Simple")).count());
        }
    }

    @Test
    void mixedRefusesAConflictingPairAcrossTiers() {
        final List<RandomGearCommand.TierCandidate> pool = List.of(
                tier("Simple", "A", 1),
                tier("Unique", "B", 1, "A"));

        for (int seed = 0; seed < 20; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.selectMixed(
                    pool, List.of(), 2, new Random(seed));

            assertNotNull(selection.failure);
            assertTrue(selection.rolls.isEmpty());
        }
    }

    @Test
    void mixedSkipsTiersThatConflictWithExistingEnchantments() {
        final RandomGearCommand.Candidate existing = candidate("A", 1);
        final List<RandomGearCommand.TierCandidate> pool = List.of(
                tier("Simple", "B", 1, "A"),
                tier("Unique", "C", 1));

        for (int seed = 0; seed < 20; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.selectMixed(
                    pool, List.of(existing), 1, new Random(seed));

            assertNull(selection.failure);
            assertEquals("C", selection.rolls.getFirst().key());
            assertEquals("Unique", selection.rolls.getFirst().tier());
        }
    }

    @Test
    void mixedRangeStaysBetweenTheRequestedBoundsAndReportsTiers() {
        final List<RandomGearCommand.TierCandidate> pool = new java.util.ArrayList<>();

        for (final String tier : List.of("Simple", "Unique", "Elite")) {
            for (final String key : List.of("A", "B", "C")) {
                pool.add(tier(tier, tier + key, 2));
            }
        }

        for (int seed = 0; seed < 40; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.rollMixed(
                    pool, List.of(), new RandomGearCommand.CountRange(2, 5), 10, new Random(seed));

            assertNull(selection.failure);
            assertTrue(selection.rolls.size() >= 2 && selection.rolls.size() <= 5,
                    "actual count " + selection.rolls.size());
            assertTrue(selection.rolls.stream().allMatch(roll -> roll.tier() != null));
        }
    }

    @Test
    void mixedRangeNeverGoesBelowTheMinimum() {
        final List<RandomGearCommand.TierCandidate> pool = List.of(
                tier("Simple", "A", 1),
                tier("Unique", "B", 1));

        final RandomGearCommand.Selection selection = RandomGearCommand.rollMixed(
                pool, List.of(), new RandomGearCommand.CountRange(3, 5), 10, new Random(1));

        assertNotNull(selection.failure);
        assertTrue(selection.rolls.isEmpty());
    }

    @Test
    void mixedZeroSlotsExplainsTheDefaultBaseLimit() {
        final RandomGearCommand.Selection selection = RandomGearCommand.rollMixed(
                List.of(tier("Simple", "A", 1)),
                List.of(), new RandomGearCommand.CountRange(1, 2), 0, new Random(1));

        assertNotNull(selection.failure);
        assertTrue(selection.failure.contains("Base-Limit"), selection.failure);
    }

    @Test
    void standardRangeRandomizesWithinFeasibleBounds() {
        final List<RandomGearCommand.Candidate> pool = List.of(
                candidate("A", 2), candidate("B", 2), candidate("C", 2));

        for (int seed = 0; seed < 40; seed++) {
            final RandomGearCommand.Selection selection = RandomGearCommand.rollStandard(
                    pool, List.of(), new RandomGearCommand.CountRange(2, 3), 3, new Random(seed));

            assertNull(selection.failure);
            assertTrue(selection.rolls.size() >= 2 && selection.rolls.size() <= 3,
                    "actual count " + selection.rolls.size());
            assertTrue(selection.rolls.stream().allMatch(roll -> roll.tier() == null));
        }
    }

    private static RandomGearCommand.Candidate candidate(final String key, final int maxLevel, final String... conflicts) {
        return new RandomGearCommand.Candidate(key, maxLevel, Set.of(conflicts));
    }

    private static RandomGearCommand.TierCandidate tier(final String tier, final String key, final int maxLevel,
                                                        final String... conflicts) {
        return new RandomGearCommand.TierCandidate(tier, candidate(key, maxLevel, conflicts));
    }
}
