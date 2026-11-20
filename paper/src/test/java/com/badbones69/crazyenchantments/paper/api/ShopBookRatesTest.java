package com.badbones69.crazyenchantments.paper.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Books bought straight from the enchanter shop roll their destroy and success rates
 * separately over 0-100 inclusive, so every chance is equally likely on every category.
 */
class ShopBookRatesTest {

    /** Hands out the queued values and records the bound of every draw. */
    private static final class ScriptedRandom extends Random {

        private final Deque<Integer> values = new ArrayDeque<>();
        private final List<Integer> bounds = new ArrayList<>();

        private ScriptedRandom(final Integer... values) {
            this.values.addAll(List.of(values));
        }

        @Override
        public int nextInt(final int bound) {
            this.bounds.add(bound);

            return this.values.removeFirst();
        }
    }

    @Test
    void eachRateIsItsOwnInclusiveRoll() {
        final ScriptedRandom random = new ScriptedRandom(0, 100);

        // Both ends are reachable, so the roll is 0-100 inclusive rather than 1-100.
        assertEquals(0, CrazyManager.rollBookRate(random));
        assertEquals(100, CrazyManager.rollBookRate(random));
        // One draw per rate: the second roll must not reuse the first value.
        assertEquals(List.of(101, 101), random.bounds);
    }
}
