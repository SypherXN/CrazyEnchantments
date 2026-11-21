package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 100% proc switch is session state: it toggles per player, never leaks a
 * null or cleared id, and an even number of concurrent toggles leaves the same
 * state it started in.
 */
class ProcTestTest {

    private static final UUID PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void clearSessions() {
        ProcTest.clear();
    }

    @Test
    void togglesOnAndOff() {
        assertFalse(ProcTest.isEnabled(PLAYER));

        assertTrue(ProcTest.toggle(PLAYER));
        assertTrue(ProcTest.isEnabled(PLAYER));

        assertFalse(ProcTest.toggle(PLAYER));
        assertFalse(ProcTest.isEnabled(PLAYER));
    }

    @Test
    void quitAndClearDropOnlyThatPlayer() {
        final UUID other = UUID.randomUUID();

        ProcTest.toggle(PLAYER);
        ProcTest.toggle(other);

        ProcTest.clear(PLAYER);

        assertFalse(ProcTest.isEnabled(PLAYER));
        assertTrue(ProcTest.isEnabled(other));
    }

    @Test
    void nullIdIsNeverEnabled() {
        assertFalse(ProcTest.isEnabled(null));
        assertFalse(ProcTest.toggle(null));

        ProcTest.clear((UUID) null);

        assertFalse(ProcTest.isEnabled(null));
    }

    @Test
    void concurrentTogglesKeepEveryPlayer() throws Exception {
        final int count = 16;
        final List<UUID> players = new ArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(count);
        final List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int index = 0; index < count; index++) {
                final UUID player = UUID.randomUUID();
                players.add(player);
                results.add(pool.submit(() -> ProcTest.toggle(player)));
            }
        } finally {
            pool.shutdown();
        }

        for (final Future<Boolean> result : results) assertTrue(result.get(), "a fresh player's first toggle is ON");

        assertEquals(count, players.stream().filter(ProcTest::isEnabled).count());
    }

    @Test
    void concurrentTogglesOfOnePlayerKeepEvenParity() throws Exception {
        final int count = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(count);
        final List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int index = 0; index < count; index++) results.add(pool.submit(() -> ProcTest.toggle(PLAYER)));
        } finally {
            pool.shutdown();
        }

        int on = 0;

        for (final Future<Boolean> result : results) {
            if (result.get()) on++;
        }

        assertEquals(count / 2, on, "an even number of flips must report ON exactly half the time");
        assertFalse(ProcTest.isEnabled(PLAYER), "the parity also holds in the final state");
    }
}
