package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G7 rules: additive incoming percentage points, one application per hit, an
 * 80 pp cap, exclusive reduction classes, exact Deathbringer odds, multiplying
 * outgoing boosts, a fixed non-compounding Deathbringer, and 0.01 rounding.
 */
class DamageModifiersTest {

    private static final DamageModifierSettings SETTINGS = DamageModifierSettings.DEFAULTS;

    @Test
    void perLevelValuesMatchTheSpec() {
        assertEquals(1.85, DamageModifiers.piecePP(SETTINGS.armoredReductionPerLevelPp(), 1), 1e-9);
        assertEquals(7.40, DamageModifiers.piecePP(SETTINGS.armoredReductionPerLevelPp(), 4), 1e-9);
        assertEquals(2.00, DamageModifiers.piecePP(SETTINGS.heavyReductionPerLevelPp(), 1), 1e-9);
        assertEquals(10.00, DamageModifiers.piecePP(SETTINGS.heavyReductionPerLevelPp(), 5), 1e-9);
        assertEquals(7.40, DamageModifiers.piecePP(SETTINGS.tankReductionPerLevelPp(), 4), 1e-9);
        assertEquals(6.00, DamageModifiers.piecePP(SETTINGS.marksmanBonusPerLevelPp(), 4), 1e-9);
    }

    @Test
    void piecesSumBeforeOneApplication() {
        final double perPiece = DamageModifiers.piecePP(SETTINGS.armoredReductionPerLevelPp(), 4);

        final double fourPieces = DamageModifiers.sumPP(
                SETTINGS.incomingReductionCapPp(),
                perPiece, perPiece, perPiece, perPiece
        );

        assertEquals(29.6, fourPieces, 1e-9);
        assertEquals(14.08, DamageModifiers.reduceOnce(20.0, fourPieces), 1e-9);

        // Compounding each piece would be a different, wrong number.
        assertNotEquals(20.0 * Math.pow(1.0 - perPiece / 100.0, 4), DamageModifiers.reduceOnce(20.0, fourPieces), 1e-9);
    }

    @Test
    void reductionIsCappedAtEightyPoints() {
        assertEquals(80.0, DamageModifiers.sumPP(80.0, 20.0, 20.0, 20.0, 20.0, 20.0), 1e-9);
        assertEquals(4.0, DamageModifiers.reduceOnce(20.0, 80.0), 1e-9);
    }

    @Test
    void zeroAndNegativeInputsAreInert() {
        assertEquals(0.0, DamageModifiers.piecePP(1.85, 0), 1e-9);
        assertEquals(0.0, DamageModifiers.piecePP(-1.85, 4), 1e-9);
        assertEquals(0.0, DamageModifiers.sumPP(80.0, -5.0, 0.0), 1e-9);
        assertEquals(0.0, DamageModifiers.reduceOnce(0.0, 50.0), 1e-9);
        assertEquals(10.0, DamageModifiers.reduceOnce(10.0, -5.0), 1e-9);
        assertEquals(10.0, DamageModifiers.capBonus(10.0, 1.0, 20.0), 1e-9);
    }

    @Test
    void arrowHitsNeverReadTheHandEvenAfterASwap() {
        final var hand = new AtomicReference<>("BOW");
        hand.set("DIAMOND_AXE");
        assertEquals(DamageModifiers.Reduction.HEAVY, DamageModifiers.reductionFor(true, false, () -> {
            throw new AssertionError("Arrow read the shooter's swapped hand: " + hand.get());
        }));
    }

    @Test
    void reductionClassesAreMutuallyExclusive() {
        assertEquals(DamageModifiers.Reduction.HEAVY, DamageModifiers.reductionFor(true, true, () -> "IRON_SWORD"));
        assertEquals(DamageModifiers.Reduction.ARMORED, DamageModifiers.reductionFor(false, true, () -> "IRON_SWORD"));
        assertEquals(DamageModifiers.Reduction.TANK, DamageModifiers.reductionFor(false, true, () -> "IRON_AXE"));
        assertEquals(DamageModifiers.Reduction.NONE, DamageModifiers.reductionFor(false, true, () -> "IRON_PICKAXE"));
        assertEquals(DamageModifiers.Reduction.NONE, DamageModifiers.reductionFor(false, false, () -> "IRON_AXE"));
    }

    @Test
    void deathbringerUsesAnExactPercentRoll() {
        assertTrue(DamageModifiers.exactChance(3, 0));
        assertTrue(DamageModifiers.exactChance(3, 2));
        assertFalse(DamageModifiers.exactChance(3, 3));
        assertFalse(DamageModifiers.exactChance(3, 99));

        assertTrue(DamageModifiers.exactChance(7, 6));
        assertFalse(DamageModifiers.exactChance(7, 7));

        for (final int chance : new int[]{3, 5, 7}) {
            assertEquals(chance, IntStream.range(0, 100).filter(roll -> DamageModifiers.exactChance(chance, roll)).count());
        }
    }

    @Test
    void marksmanBonusIsCappedAndMultipliesWithOtherEnchants() {
        assertEquals(24.0, DamageModifiers.sumPP(25.0, 6.0, 6.0, 6.0, 6.0), 1e-9);
        assertEquals(25.0, DamageModifiers.sumPP(25.0, 6.0, 6.0, 6.0, 6.0, 6.0), 1e-9);

        assertEquals(1.24, DamageModifiers.outgoingMultiplier(24.0), 1e-9);
        assertEquals(1.86, DamageModifiers.multiply(DamageModifiers.outgoingMultiplier(24.0), DamageModifiers.deathbringerMultiplier(1)), 1e-9);
    }

    @Test
    void deathbringerProcsNeverCompound() {
        assertEquals(1.0, DamageModifiers.deathbringerMultiplier(0), 1e-9);
        assertEquals(1.5, DamageModifiers.deathbringerMultiplier(1), 1e-9);
        assertEquals(1.5, DamageModifiers.deathbringerMultiplier(4), 1e-9);
    }

    @Test
    void singleHitIsModifiedOnceAndBonusIsCapped() {
        final double multiplier = DamageModifiers.multiply(
                DamageModifiers.outgoingMultiplier(25.0),
                DamageModifiers.deathbringerMultiplier(3)
        );

        assertEquals(1.875, multiplier, 1e-9);
        assertEquals(18.75, DamageModifiers.capBonus(10.0, multiplier, 20.0), 1e-9);

        // The +20 cap still bites on a big enough hit.
        assertEquals(60.0, DamageModifiers.capBonus(40.0, multiplier, 20.0), 1e-9);

        // Reduction takes 40 to 20 before the multiplier applies.
        final double reduced = DamageModifiers.reduceOnce(40.0, 50.0);
        assertEquals(37.5, DamageModifiers.capBonus(reduced, multiplier, 20.0), 1e-9);
    }

    @Test
    void modifiedHitsRoundToTwoDecimals() {
        assertEquals(14.08, DamageModifiers.roundDamage(20.0, 14.0832), 1e-9);
        assertEquals(14.09, DamageModifiers.roundDamage(20.0, 14.085), 1e-9);
        assertEquals(14.0832, DamageModifiers.roundDamage(14.0832, 14.0832), 1e-9);
    }

    @Test
    void outgoingBonusesStackBecauseTheShippedCapIsDisabled() {
        final var event = new EntityDamageEvent(null, DamageCause.CUSTOM, null, 12.0);
        assertEquals(15.0, DamageModifiers.applyOutgoingBonus(event, 12.0, 15.0));
        assertEquals(27.0, event.getDamage());
        event.setDamage(20.0);
        assertEquals(20.0, DamageModifiers.applyOutgoingBonus(event, 20.0, 20.0));
        assertEquals(40.0, event.getDamage());

        final var next = new EntityDamageEvent(null, DamageCause.CUSTOM, null, 12.0);
        DamageModifiers.applyOutgoingBonus(next, 12.0, 0.0);
        next.setDamage(22.0);
        assertEquals(20.0, DamageModifiers.applyOutgoingBonus(next, 22.0, 20.0));
        assertEquals(42.0, next.getDamage());
    }
}
