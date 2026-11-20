package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodLustLogicTest {

    private static final BloodLustSettings SETTINGS = BloodLustSettings.DEFAULTS;

    @Test
    void footprintIsSevenBySevenAndIncludesTheExactThreeBlockEdge() {
        assertTrue(SETTINGS.withinFootprint(0, 0, 0));
        assertTrue(SETTINGS.withinFootprint(3, 0, 3));
        assertTrue(SETTINGS.withinFootprint(-3, 3, -3));

        assertFalse(SETTINGS.withinFootprint(4, 0, 0));
        assertFalse(SETTINGS.withinFootprint(0, 4, 0));
        assertFalse(SETTINGS.withinFootprint(0, 0, -4));
    }

    @Test
    void cooldownAllowsOneHealPerWindow() {
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
        final UUID wearer = UUID.randomUUID();

        assertTrue(SETTINGS.claim(cooldowns, wearer, 1_000L));
        assertFalse(SETTINGS.claim(cooldowns, wearer, 1_999L));
        assertTrue(SETTINGS.claim(cooldowns, wearer, 2_000L));
        assertFalse(SETTINGS.claim(cooldowns, wearer, 2_000L));
    }

    @Test
    void cooldownIsPerWearer() {
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

        assertTrue(SETTINGS.claim(cooldowns, UUID.randomUUID(), 1_000L));
        assertTrue(SETTINGS.claim(cooldowns, UUID.randomUUID(), 1_000L));
    }

    @Test
    void invalidTuningFallsBackAndWarnsOncePerKey() {
        final List<String> warnings = new ArrayList<>();

        final BloodLustSettings validated = BloodLustSettings.validated(
                -1.0, Double.NaN, 17, 11.0, warnings::add
        );

        assertEquals(BloodLustSettings.DEFAULTS, validated);
        assertEquals(4, warnings.size());
    }
}
