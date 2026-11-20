package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSellLogicTest {

    /** A drop is its name; prices come from the map, amounts from the second map. */
    private static final class Shop implements AutoSellLogic.Appraiser<String> {

        final Map<String, Double> prices = new HashMap<>();
        final List<String> settled = new ArrayList<>();
        final List<String> quoted = new ArrayList<>();
        boolean refuseSettle;

        @Override
        public OptionalDouble quote(final String drop) {
            this.quoted.add(drop);
            final Double price = this.prices.get(drop);
            return price == null ? OptionalDouble.empty() : OptionalDouble.of(price);
        }

        @Override
        public boolean settle(final String drop, final double money) {
            if (this.refuseSettle) return false;

            this.settled.add(drop);
            return true;
        }
    }

    private static final Map<String, Integer> AMOUNTS = Map.of("stone", 64, "coal", 3, "dirt", 10, "gem", 1);

    private static AutoSellLogic.Outcome<String> run(final List<String> drops, final Shop shop) {
        return AutoSellLogic.sellAll(drops, AMOUNTS::get, shop);
    }

    @Test
    void aggregatesEveryPricedDropOfOneBreak() {
        final Shop shop = new Shop();
        shop.prices.put("stone", 12.5);
        shop.prices.put("coal", 7.25);

        final var outcome = run(List.of("stone", "coal"), shop);

        assertEquals(List.of("stone", "coal"), outcome.sold());
        assertEquals(67, outcome.items());
        assertEquals(19.75, outcome.money(), 1e-9);
        assertEquals(List.of("stone", "coal"), shop.settled);
    }

    @Test
    void unsellableDropsAreNeverSettledAndStayOut() {
        final Shop shop = new Shop();
        shop.prices.put("coal", 7.0);

        final var outcome = run(List.of("dirt", "coal", "gem"), shop);

        assertEquals(List.of("coal"), outcome.sold());
        assertEquals(List.of("coal"), shop.settled);
        assertEquals(3, outcome.items());
    }

    @Test
    void onlyAFinitePositiveQuoteIsPaid() {
        final Shop shop = new Shop();
        shop.prices.put("stone", 0.0);
        shop.prices.put("coal", -4.0);
        shop.prices.put("dirt", Double.NaN);
        shop.prices.put("gem", Double.POSITIVE_INFINITY);

        final var outcome = run(List.of("stone", "coal", "dirt", "gem"), shop);

        assertTrue(outcome.isEmpty());
        assertEquals(0.0, outcome.money());
        assertTrue(shop.settled.isEmpty(), "money must never be credited for an unpriced item");
        assertFalse(AutoSellLogic.isPayable(Double.NaN));
        assertTrue(AutoSellLogic.isPayable(0.01));
    }

    @Test
    void aDropThatCouldNotBeSettledIsKeptAndNotCounted() {
        final Shop shop = new Shop();
        shop.prices.put("coal", 7.0);
        shop.refuseSettle = true;

        final var outcome = run(List.of("coal"), shop);

        assertTrue(outcome.isEmpty());
        assertEquals(0, outcome.items());
        assertEquals(0.0, outcome.money());
    }

    @Test
    void onlyTheGivenDropsAreAppraised() {
        final Shop shop = new Shop();
        shop.prices.put("coal", 7.0);
        shop.prices.put("gem", 99.0); // priced, but belongs to another event

        final var outcome = run(List.of("coal"), shop);

        assertEquals(List.of("coal"), shop.quoted);
        assertEquals(7.0, outcome.money(), 1e-9);
        assertFalse(outcome.sold().contains("gem"));
    }

    @Test
    void removeSoldKeepsTheOrderOfWhatRemainsAndMatchesByIdentity() {
        final String first = new String("coal");
        final String twin = new String("coal");
        final List<String> drops = new ArrayList<>(List.of("dirt", first, "gem", twin));

        AutoSellLogic.removeSold(drops, List.of(first));

        assertEquals(3, drops.size());
        assertEquals("dirt", drops.get(0));
        assertEquals("gem", drops.get(1));
        assertSame(twin, drops.get(2));
    }

    @Test
    void summaryIsOneFormattedMessageForTheWholeBreak() {
        final Shop shop = new Shop();
        shop.prices.put("stone", 12.5);
        shop.prices.put("coal", 7.256);
        shop.prices.put("gem", 1.0);

        final var outcome = run(List.of("stone", "coal", "gem"), shop);
        final String message = AutoSellLogic.summary(AutoSellSettings.DEFAULT_MESSAGE, outcome.items(), outcome.money());

        // Three drops sold, still exactly one message built for the break.
        assertEquals("&aAuto Sell: &e68 &aitems for &6$20.76&a.", message);
        assertEquals(3, outcome.sold().size());
    }

    @Test
    void summaryIsAbsentWhenNothingSoldOrTheTemplateIsBlank() {
        assertNull(AutoSellLogic.summary(AutoSellSettings.DEFAULT_MESSAGE, 0, 0.0));
        assertNull(AutoSellLogic.summary(AutoSellSettings.DEFAULT_MESSAGE, 5, Double.NaN));
        assertNull(AutoSellLogic.summary("", 5, 10.0));
        assertNull(AutoSellLogic.summary("  ", 5, 10.0));
        assertNull(AutoSellLogic.summary(null, 5, 10.0));
        assertEquals("5 for 10.00", AutoSellLogic.summary("%items% for %price%", 5, 10.0));
    }

    @Test
    void priceFormattingIsLocaleIndependentAndTwoDecimals() {
        assertEquals("1234567.50", AutoSellLogic.formatPrice(1_234_567.5));
        assertEquals("0.01", AutoSellLogic.formatPrice(0.01));
        assertEquals("3.00", AutoSellLogic.formatPrice(3));
    }

    @Test
    void onlyAQualifyingPickaxeAtALevelPasses() {
        assertTrue(AutoSellLogic.qualifies(true, 1));
        assertFalse(AutoSellLogic.qualifies(true, 0));
        assertFalse(AutoSellLogic.qualifies(true, -1));
        assertFalse(AutoSellLogic.qualifies(false, 1));
        assertEquals(1, AutoSellLogic.MAX_LEVEL);
    }

    @Test
    void settingsDefaultAndValidateTheMessage() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(AutoSellSettings.DEFAULTS, AutoSellSettings.validated(null, warnings::add));
        assertEquals("hi", AutoSellSettings.validated("hi", warnings::add).message());
        assertEquals("", AutoSellSettings.validated("", warnings::add).message());
        assertTrue(warnings.isEmpty());

        assertEquals(AutoSellSettings.DEFAULTS, AutoSellSettings.validated(5, warnings::add));
        assertEquals(AutoSellSettings.DEFAULTS, AutoSellSettings.validated("x".repeat(257), warnings::add));
        assertEquals(2, warnings.size());
    }

    @Test
    void shippedConfigAndEnchantEntryMatchTheSpec() throws IOException {
        final Map<?, ?> entry = (Map<?, ?>) ((Map<?, ?>) yaml("Enchantments.yml").get("Enchantments")).get("Auto Sell");
        final Map<?, ?> autoSell = (Map<?, ?>) entry.get("Tuning");
        assertEquals(AutoSellSettings.DEFAULT_MESSAGE, autoSell.get("Message"));

        assertEquals(1, entry.get("MaxPower"));
        assertEquals("Pickaxe", entry.get("Enchantment-Type"));
        assertEquals(List.of("Legendary"), entry.get("Categories"));
        assertFalse(entry.containsKey("Chance-System"));

        assertTrue(source("api/enums/CEnchantments.java").contains("AUTO_SELL(\"Auto Sell\", \"Pickaxe\"),"));
    }

    @Test
    void softdependListsBothShopEditionsExactlyOnce() throws IOException {
        final String plugin = Files.readString(resources("plugin.yml"));

        assertEquals(1L, Pattern.compile("(?m)^\\s*- EconomyShopGUI\\s*$").matcher(plugin).results().count());
        assertEquals(1L, Pattern.compile("(?m)^\\s*- EconomyShopGUI-Premium\\s*$").matcher(plugin).results().count());
    }

    @Test
    void noEconomyShopGuiTypeLeaksOutsideTheGatedClass() throws IOException {
        final Path root = javaRoot();
        final List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            for (final Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                final String text = Files.readString(file);
                if (text.contains("me.gypopo") || text.contains("EconomyShopGUIHook")) offenders.add(root.relativize(file).toString().replace('\\', '/'));
            }
        }

        assertEquals(List.of("api/utils/AutoSellSupport.java"), offenders);

        // Nor in any test: they would not compile against a compileOnly dependency.
        try (Stream<Path> files = Files.walk(testRoot())) {
            for (final Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                if (file.getFileName().toString().equals("AutoSellLogicTest.java")) continue;
                assertFalse(Files.readString(file).contains("me.gypopo"), file.toString());
            }
        }
    }

    @Test
    void registrationSitsInsideBothPluginEnabledChecks() throws IOException {
        final String plugin = source("CrazyEnchantments.java");
        final String check = "if (Bukkit.getPluginManager().isPluginEnabled(\"EconomyShopGUI\") || Bukkit.getPluginManager().isPluginEnabled(\"EconomyShopGUI-Premium\")) {";
        final String registration = "pickaxeEnchantments.setAutoSeller(new AutoSellSupport(this));";

        assertTrue(Pattern.compile(Pattern.quote(check) + "\\s*try\\s*\\{\\s*" + Pattern.quote(registration)
                + "\\s*}\\s*catch\\s*\\(LinkageError").matcher(plugin).find());
        assertEquals(1L, Pattern.compile("new\\s+AutoSellSupport\\s*\\(").matcher(plugin).results().count());
    }

    @Test
    void pickaxeSellsOnceAfterTransformsAndNamesNoShopType() throws IOException {
        final String picks = source("enchantments/PickaxeEnchantments.java");
        final int start = picks.indexOf("public void onDropAlter(");
        final String drops = picks.substring(start, picks.indexOf("public void onExperience(", start));

        final int sell = drops.indexOf("seller.sell(event)");
        assertTrue(Pattern.compile("EventPriority\\.NORMAL, ignoreCancelled = true\\)\\s+public void onDropAlter").matcher(picks).find());
        assertTrue(Pattern.compile("EventPriority\\.MONITOR, ignoreCancelled = true\\)\\s+public void onAutoSell").matcher(picks).find());
        assertTrue(drops.indexOf("transformDrops(event);") >= 0 && drops.indexOf("public void onAutoSell(") < sell);
        assertTrue(drops.contains("if (seller != null && !event.isCancelled()) seller.sell(event);"));
        assertEquals(1L, Pattern.compile(Pattern.quote("seller.sell(event)")).matcher(drops).results().count());
        assertTrue(sell < drops.indexOf("private void transformDrops("));
        final String tools = source("enchantments/ToolEnchantments.java");
        final String plugin = source("CrazyEnchantments.java");
        assertTrue(Pattern.compile("EventPriority\\.MONITOR, ignoreCancelled = true\\)\\s+public void onTelepathy").matcher(tools).find());
        final int pickaxeRegistration = plugin.indexOf("registerEvents(pickaxeEnchantments, this)");
        assertTrue(pickaxeRegistration >= 0 && pickaxeRegistration < plugin.indexOf("registerEvents(new ToolEnchantments(), this)"));
        assertTrue(drops.contains("if (!isOreBlock(event.getBlockState().getType())) return;"));
        assertFalse(picks.contains("me.gypopo"));
        assertFalse(picks.contains("AutoSellSupport"));

        final int fuse = drops.indexOf("blast.matches(event, event.getBlock())");
        final int smelt = drops.indexOf("entity.setItemStack(getOreDrop(drop, drop.getAmount()))", fuse);
        final int skipAutoSmelt = drops.indexOf("return; // Fuse", smelt);
        assertTrue(fuse >= 0 && smelt > fuse && skipAutoSmelt > smelt
                        && skipAutoSmelt < drops.indexOf("CEnchantments.AUTOSMELT")
                        && picks.contains("case RAW_IRON -> Material.IRON_INGOT;"),
                "with Fuse and Auto Smelt present, the seller sees the ingot already written by Fuse, not raw iron");
    }

    @Test
    void supportUsesShopPricingAndRequiresVaultConfirmationBeforeUpdatingLimits() throws IOException {
        final String support = source("api/utils/AutoSellSupport.java");

        assertTrue(support.contains("EconomyShopGUIHook.getSellPrice(player, stack)"));
        assertTrue(support.contains("EconomyShopGUIHook.isSellAble(shopItem)"));
        assertTrue(support.contains("vault.depositPlayer(player, money)"));
        assertTrue(support.contains(".updateLimits()"));
        assertTrue(support.contains("entry.getKey().getType() != EconomyType.VAULT"));
        // Credit first, then the limit bookkeeping (the javadoc also names updateLimits, so match the calls).
        final int confirmation = support.indexOf("if (response == null || !response.transactionSuccess())");
        assertTrue(confirmation > support.indexOf("vault.depositPlayer(") && confirmation < support.indexOf("priced.sellPrice().updateLimits()"));
        assertTrue(support.contains("Bukkit.isOwnedByCurrentRegion(player)"));
        assertTrue(support.indexOf("Bukkit.isOwnedByCurrentRegion(player)") < support.indexOf("this.methods.getItemInHand(player)"));

        // Do not run the shop's inventory-bound transaction or deposit twice.
        assertFalse(Pattern.compile("EconomyShopGUIHook\\.sellItem\\s*\\(").matcher(support).find());
        assertFalse(support.contains("depositBalance"));
        assertFalse(support.contains("getInventory"));
        assertTrue(support.contains("catch (final LinkageError error)"));
    }

    private static Map<?, ?> yaml(final String name) throws IOException {
        return new Yaml().load(Files.readString(resources(name)));
    }

    private static Path resources(final String name) {
        final Path local = Path.of("src/main/resources", name);
        return Files.exists(local) ? local : Path.of("paper").resolve(local);
    }

    private static Path javaRoot() {
        final Path local = Path.of("src/main/java/com/badbones69/crazyenchantments/paper");
        return Files.exists(local) ? local : Path.of("paper").resolve(local);
    }

    private static Path testRoot() {
        final Path local = Path.of("src/test/java");
        return Files.exists(local) ? local : Path.of("paper").resolve(local);
    }

    private static String source(final String relative) throws IOException {
        return Files.readString(javaRoot().resolve(relative));
    }
}
