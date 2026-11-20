package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The planner decides whether a tag stack can be parked before a rename starts
 * and how much of an admin grant actually fits, so it is the guard against a
 * lost cursor stack and a false grant message.
 */
class NameTagStorageTest {

    private static NameTagStorage.Slot empty(final int index) {
        return new NameTagStorage.Slot(index, 0, false);
    }

    private static NameTagStorage.Slot tag(final int index, final int amount) {
        return new NameTagStorage.Slot(index, amount, true);
    }

    private static NameTagStorage.Slot occupied(final int index) {
        return new NameTagStorage.Slot(index, 1, false);
    }

    @Test
    void aWholeStackGoesIntoAnEmptySlot() {
        final NameTagStorage.Plan plan = NameTagStorage.plan(5, 64, List.of(empty(0)));

        assertTrue(plan.fits());
        assertEquals(List.of(new NameTagStorage.Move(0, 5, false)), plan.moves());
        assertEquals(0, plan.remainder());
    }

    @Test
    void existingTagsAreToppedUpBeforeANewSlot() {
        final NameTagStorage.Plan plan = NameTagStorage.plan(5, 64, List.of(tag(0, 60), empty(1)));

        assertTrue(plan.fits());
        assertEquals(List.of(
                new NameTagStorage.Move(0, 4, true),
                new NameTagStorage.Move(1, 1, false)
        ), plan.moves());
    }

    @Test
    void aFullInventoryRefusesTheWholeStack() {
        final NameTagStorage.Plan plan = NameTagStorage.plan(1, 64, List.of(tag(0, 64), occupied(1)));

        assertFalse(plan.fits());
        assertTrue(plan.moves().isEmpty());
        assertEquals(1, plan.remainder());
    }

    @Test
    void aPartialGrantReportsTheRest() {
        final NameTagStorage.Plan plan = NameTagStorage.plan(130, 64, List.of(empty(0), empty(1)));

        assertFalse(plan.fits());
        assertEquals(List.of(
                new NameTagStorage.Move(0, 64, false),
                new NameTagStorage.Move(1, 64, false)
        ), plan.moves());
        assertEquals(2, plan.remainder());
    }

    @Test
    void occupiedSlotsAreNeverUsed() {
        final NameTagStorage.Plan plan = NameTagStorage.plan(1, 64, List.of(occupied(0)));

        assertFalse(plan.fits());
        assertTrue(plan.moves().isEmpty());
    }
}
