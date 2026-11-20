package com.badbones69.crazyenchantments.paper.api.builders.types.tinkerer;

import com.badbones69.crazyenchantments.paper.api.enums.pdc.DustData;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recycled book's Mystery Dust cap is chosen as: an explicit
 * {@code Settings.Tier-Potency-Cap} override (0..50), otherwise the rarity's
 * XP-point shop price ({@code round(50 * price / highest price)}), otherwise
 * the legacy per-book formula clamped to 50. Invalid overrides are ignored, so
 * they fall through to price scaling instead of becoming an implicit zero. The
 * opening roll is 0-based only for tinkered rewards.
 */
class TinkerDustCapTest {

    private static final int CEILING = 100;

    private static final Map<String, Integer> SHIPPED_PRICES = new LinkedHashMap<>();

    static {
        SHIPPED_PRICES.put("Simple", 400);
        SHIPPED_PRICES.put("Unique", 800);
        SHIPPED_PRICES.put("Elite", 2500);
        SHIPPED_PRICES.put("Ultimate", 5000);
        SHIPPED_PRICES.put("Legendary", 25000);
    }

    private static final Map<String, Integer> SHIPPED_OVERRIDES = Map.of(
            "Simple", 1,
            "Unique", 2,
            "Elite", 5,
            "Ultimate", 10,
            "Legendary", 50);

    @Test
    void shippedPricesMapToTheRequestedCaps() {
        final Map<String, Integer> caps = TinkererManager.capsFromPrices(SHIPPED_PRICES);

        for (Map.Entry<String, Integer> tier : SHIPPED_OVERRIDES.entrySet()) {
            assertEquals(tier.getValue(), caps.get(tier.getKey()), tier.getKey());
        }
    }

    @Test
    void capsFollowLivePriceChanges() {
        final Map<String, Integer> doubled = new LinkedHashMap<>(SHIPPED_PRICES);
        doubled.put("Simple", 800);

        assertEquals(2, TinkererManager.capsFromPrices(doubled).get("Simple"), "a changed price changes the cap");
        assertEquals(50, TinkererManager.capsFromPrices(Map.of("Simple", 2000, "Legendary", 2000)).get("Simple"), "the scale is relative, not absolute");
        assertEquals(50, TinkererManager.capsFromPrices(Map.of("Simple", 7)).get("Simple"), "a sole priced tier is its own maximum");
    }

    @Test
    void theZeroAndFiftyEndpointsAreReachable() {
        final Map<String, Integer> caps = TinkererManager.capsFromPrices(Map.of("Simple", 1, "Legendary", 25000));

        assertEquals(0, caps.get("Simple"), "a price too small to round up caps at zero");
        assertEquals(50, caps.get("Legendary"), "the highest price caps at fifty");
    }

    @Test
    void everyCapStaysWithinTheTinkerRange() {
        for (Map.Entry<String, Integer> tier : TinkererManager.capsFromPrices(SHIPPED_PRICES).entrySet()) {
            final int cap = tier.getValue();

            assertTrue(cap >= 0 && cap <= 50, tier.getKey() + '=' + cap);
        }
    }

    @Test
    void noPositivePriceMeansNoPriceCap() {
        assertTrue(TinkererManager.capsFromPrices(Map.of()).isEmpty());
        assertTrue(TinkererManager.capsFromPrices(null).isEmpty());
        assertTrue(TinkererManager.capsFromPrices(Map.of("Simple", 0, "Legendary", -5)).isEmpty());
    }

    @Test
    void explicitOverrideWinsOverPriceIncludingZero() {
        // The supplier throws if the price or legacy path is touched, proving
        // the explicit override wins outright.
        assertEquals(50, TinkererManager.chooseTinkerDustCap("Legendary", Map.of("Legendary", 50), Map.of("Legendary", 10), () -> {
            throw new AssertionError("price or legacy parsed despite an explicit override");
        }));
        assertEquals(0, TinkererManager.chooseTinkerDustCap("Simple", Map.of("Simple", 0), Map.of("Simple", 50), () -> {
            throw new AssertionError("price or legacy parsed despite an explicit zero override");
        }), "an explicit zero is a real override, not a missing entry");
    }

    @Test
    void missingOverrideScalesByPrice() {
        assertEquals(2, TinkererManager.chooseTinkerDustCap("Unique", Map.of(), Map.of("Unique", 2), () -> {
            throw new AssertionError("legacy parsed despite a price cap");
        }));
        assertEquals(50, TinkererManager.chooseTinkerDustCap("Legendary", Map.of("Simple", 1), Map.of("Legendary", 50), () -> {
            throw new AssertionError("legacy parsed despite a price cap");
        }), "another tier's override must not leak");
    }

    @Test
    void priceThenLegacyAndTheLegacyIsClampedToFifty() {
        assertEquals(6, TinkererManager.chooseTinkerDustCap("Legendary", Map.of(), Map.of(), () -> 6));
        assertEquals(6, TinkererManager.chooseTinkerDustCap(null, Map.of("Legendary", 50), Map.of("Legendary", 50), () -> 6));
        assertEquals(6, TinkererManager.chooseTinkerDustCap("Mythical", Map.of(), Map.of(), () -> 6));
        assertEquals(50, TinkererManager.chooseTinkerDustCap("Legendary", Map.of(), Map.of(), () -> 80));
        assertEquals(0, TinkererManager.chooseTinkerDustCap("Legendary", Map.of(), Map.of(), () -> -5), "never negative");
    }

    @Test
    void parseTierCapAcceptsWholeNumbersInRange() {
        assertEquals(0, TinkererManager.parseTierCap(0), "an explicit zero is valid");
        assertEquals(Integer.valueOf(0), TinkererManager.parseTierCap(0.0));
        assertEquals(1, TinkererManager.parseTierCap(1));
        assertEquals(50, TinkererManager.parseTierCap(50));
        assertEquals(25, TinkererManager.parseTierCap(25L));
    }

    @Test
    void parseTierCapIgnoresInvalidValuesInsteadOfCoercingZero() {
        for (Object invalid : List.<Object>of(-1, 51, 0.5, 5.5, "5", Long.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(TinkererManager.parseTierCap(invalid), String.valueOf(invalid));
        }

        assertNull(TinkererManager.parseTierCap(null), "no value");
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
    void tinkerRewardsRollFromZeroWhileAdminDustKeepsOne() throws IOException {
        assertEquals(1, new DustData("Mystery-Dust", 30, 30, 5).getRollMinimum(), "old and admin dust stay 1-based");
        assertEquals(0, new DustData("Mystery-Dust", 30, 30, 5, true).getRollMinimum());

        final String listener = Files.readString(Path.of(
                "src/main/java/com/badbones69/crazyenchantments/paper/listeners/DustControlListener.java"));

        assertTrue(listener.contains("getRollMinimum()"), "the open roll honours the per-dust minimum");
    }

    @Test
    void shippedTinkerYmlCarriesTheNewDefaultOverrides() throws IOException {
        final Map<String, Object> root = new Yaml().load(Files.readString(Path.of("src/main/resources/Tinker.yml")));
        final Map<String, Object> settings = asMap(root.get("Settings"));
        final Map<String, Object> caps = asMap(settings.get("Tier-Potency-Cap"));

        assertEquals(5, caps.size());
        assertEquals(1, ((Number) caps.get("Simple")).intValue());
        assertEquals(2, ((Number) caps.get("Unique")).intValue());
        assertEquals(5, ((Number) caps.get("Elite")).intValue());
        assertEquals(10, ((Number) caps.get("Ultimate")).intValue());
        assertEquals(50, ((Number) caps.get("Legendary")).intValue());
    }

    @Test
    void productionMethodUsesOverrideThenPriceThenLegacy() throws IOException {
        final String source = Files.readString(Path.of(
                "src/main/java/com/badbones69/crazyenchantments/paper/api/builders/types/tinkerer/TinkererManager.java"));

        assertTrue(source.contains("resolvedRarity("), "the book's rarity is resolved before the cap lookup");
        assertTrue(source.contains("parseTierCap("), "configured overrides are validated");
        assertTrue(source.contains("getPriceDustCaps("), "the cap scales by shop price when no override exists");
        assertTrue(source.contains("Currency.XP_TOTAL"), "only XP-point prices set the scale");
        assertTrue(source.contains("TuningConfig.RARITIES"), "only the five canonical tiers set the scale");
        assertTrue(source.contains("getLegacyMaxDustLevelFromBook"), "the legacy fallback is kept");

        final int tierLookup = source.indexOf("tierCaps.get(rarity)");
        final int priceLookup = source.indexOf("priceCaps.get(rarity)");

        assertTrue(tierLookup >= 0 && priceLookup > tierLookup, "the explicit override is consulted before the price");
        assertFalse(source.contains("getHighestEnchantmentCategory"), "the highest category is not the rarity");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return (Map<String, Object>) value;
    }
}
