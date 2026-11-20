package com.badbones69.crazyenchantments.paper.api.builders.types.tinkerer;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recycled book's Mystery Dust cap comes from Tinker.yml's
 * {@code Settings.Tier-Potency-Cap} when the book's enchant resolves to one of
 * the tabulated rarities; everything else keeps the legacy per-book formula.
 * Anything outside the server's supported 1..ceiling potency range becomes 1,
 * so the dust can always be opened.
 */
class TinkerDustCapTest {

    private static final int CEILING = 100;

    private static final Map<String, Integer> SHIPPED = Map.of(
            "Simple", 5,
            "Unique", 10,
            "Elite", 15,
            "Ultimate", 20,
            "Legendary", 25);

    @Test
    void everyShippedTierOverridesTheLegacyValue() {
        for (Map.Entry<String, Integer> tier : SHIPPED.entrySet()) {
            assertEquals(tier.getValue().intValue(), TinkererManager.getMaxDustLevel(tier.getKey(), SHIPPED, CEILING, () -> 6), tier.getKey());
        }
    }

    @Test
    void unrecognizedRarityHasNoTierCap() {
        assertNull(TinkererManager.getTierDustCap(null, SHIPPED), "no resolved rarity");
        assertNull(TinkererManager.getTierDustCap("Mythical", SHIPPED), "custom rarity");
        assertNull(TinkererManager.getTierDustCap("Simple", Map.of()), "no table at all");
    }

    @Test
    void configuredTierWinsWithoutReadingTheLegacyValue() {
        // The supplier throws if the legacy value is touched, proving the tier
        // is resolved before the legacy Book entry is parsed.
        assertEquals(25, TinkererManager.getMaxDustLevel("Legendary", SHIPPED, CEILING, () -> {
            throw new AssertionError("legacy value parsed despite a configured tier cap");
        }));
    }

    @Test
    void missingTierFallsBackToTheLegacyValue() {
        assertEquals(6, TinkererManager.getMaxDustLevel(null, SHIPPED, CEILING, () -> 6));
        assertEquals(6, TinkererManager.getMaxDustLevel("Mythical", SHIPPED, CEILING, () -> 6));
        assertEquals(6, TinkererManager.getMaxDustLevel("Simple", Map.of(), CEILING, () -> 6));
    }

    @Test
    void invalidTierCapsFallBackToTheMinimum() {
        for (Integer cap : List.of(0, -1, CEILING + 1, Integer.MAX_VALUE)) {
            assertEquals(1, TinkererManager.getMaxDustLevel("Simple", Map.of("Simple", cap), CEILING, () -> {
                throw new AssertionError("legacy value parsed despite a configured tier entry");
            }), String.valueOf(cap));
        }
    }

    @Test
    void malformedLegacyValueFailsSafe() {
        assertEquals(1, TinkererManager.parseLegacyDustCap(null, 1, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap("oops", 1, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap("", 1, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap(",", 1, CEILING), "delimiter only");
        assertEquals(1, TinkererManager.parseLegacyDustCap(" , ", 1, CEILING), "delimiter only with spaces");
        assertEquals(1, TinkererManager.parseLegacyDustCap("5,,1", 1, CEILING), "three fields");
        assertEquals(1, TinkererManager.parseLegacyDustCap("5,", 1, CEILING), "empty multiplier");
        assertEquals(1, TinkererManager.parseLegacyDustCap("5,1,", 1, CEILING), "empty third field");
        assertEquals(1, TinkererManager.parseLegacyDustCap("5, oops", 1, CEILING));
    }

    @Test
    void subMinimumLegacyValueFailsSafe() {
        assertEquals(1, TinkererManager.parseLegacyDustCap("0", 1, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap("-1", 1, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap("2, -1", 5, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap("101", 1, CEILING), "above the ceiling");
    }

    @Test
    void overflowLegacyValueFailsSafe() {
        assertEquals(1, TinkererManager.parseLegacyDustCap(String.valueOf(Long.MAX_VALUE) + ", 1", 2, CEILING));
        assertEquals(1, TinkererManager.parseLegacyDustCap("999999999999999999999", 1, CEILING));
    }

    @Test
    void validLegacyValueKeepsTheOldFormula() {
        assertEquals(6, TinkererManager.parseLegacyDustCap("5, 1", 1, CEILING));
        assertEquals(7, TinkererManager.parseLegacyDustCap("5, 1", 2, CEILING));
        assertEquals(5, TinkererManager.parseLegacyDustCap("5", 3, CEILING), "a missing multiplier is zero");
        assertEquals(5, TinkererManager.parseLegacyDustCap(" 5 , 1 ", 0, CEILING), "spaces are stripped");
        assertEquals(100, TinkererManager.parseLegacyDustCap("100", 1, CEILING), "the ceiling itself is allowed");
    }

    @Test
    void onlyAnUnambiguousSingleRarityCounts() {
        assertEquals("Legendary", TinkererManager.resolvedRarity(List.of("Legendary")));
        assertNull(TinkererManager.resolvedRarity(List.of("Simple", "Legendary")),
                "a multi-category book must not borrow the highest category");
        assertNull(TinkererManager.resolvedRarity(List.of()));
        assertNull(TinkererManager.resolvedRarity(null));
    }

    @Test
    void shippedTinkerYmlCarriesTheFiveTierTable() throws IOException {
        final Map<String, Object> root = new Yaml().load(Files.readString(Path.of("src/main/resources/Tinker.yml")));
        final Map<String, Object> settings = asMap(root.get("Settings"));
        final Map<String, Object> caps = asMap(settings.get("Tier-Potency-Cap"));

        assertEquals(SHIPPED.size(), caps.size());

        for (Map.Entry<String, Integer> tier : SHIPPED.entrySet()) {
            assertEquals(tier.getValue(), ((Number) caps.get(tier.getKey())).intValue(), tier.getKey());
        }
    }

    @Test
    void productionMethodResolvesRarityInsteadOfTheHighestCategory() throws IOException {
        final String source = Files.readString(Path.of(
                "src/main/java/com/badbones69/crazyenchantments/paper/api/builders/types/tinkerer/TinkererManager.java"));

        assertTrue(source.contains("resolvedRarity("), "the book's rarity is resolved before the cap lookup");
        assertTrue(source.contains("() -> getLegacyMaxDustLevelFromBook"), "the legacy read is deferred");
        assertTrue(source.contains("number.longValue()"), "tier caps are read without int truncation");
        assertFalse(source.contains("getHighestEnchantmentCategory"), "the highest category is not the rarity");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return (Map<String, Object>) value;
    }
}
