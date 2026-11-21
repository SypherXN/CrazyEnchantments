package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.enchantments.ArmorEnchantments.WornBoost;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overload and Ninja both grant Health Boost from worn armor, so two pieces can disagree
 * about its amplifier. The highest applicable level must be the one applied, no matter
 * which piece the collection loop reaches first, and a worn downgrade must really land
 * instead of staying at the level another piece granted earlier. A finite Health Boost (a
 * potion or another plugin's) is never hidden under the infinite worn one: the worn level
 * is handed over when that effect expires or when Ender Shift's own finite one ends early.
 * Replacing a level never takes away health the new max still allows, and never heals
 * damage. The decision is tested directly; the wiring is read as source text, in the style
 * of AntiGravityTest. No Bukkit server is loaded.
 */
class HealthBoostWornTest {

    private static final String MANAGER = "src/main/java/com/badbones69/crazyenchantments/paper/api/CrazyManager.java";

    private static final String ARMOR = "src/main/java/com/badbones69/crazyenchantments/paper/enchantments/ArmorEnchantments.java";

    @Test
    void overloadAndNinjaMapALevelToTheSameHealthBoostAmplifier() throws IOException {
        final String manager = read(MANAGER);
        final int overload = healthBoostOffset(manager, "OVERLOAD");
        final int ninja = healthBoostOffset(manager, "NINJA");

        // Same offset, so the highest worn level is the strongest Health Boost.
        assertEquals(-1, overload, "Overload I is Health Boost I");
        assertEquals(-1, ninja, "Ninja I is Health Boost I");

        assertEquals(0, ArmorEnchantments.jumpAmplifier(1, overload), "Overload I is amplifier 0");
        assertEquals(2, ArmorEnchantments.jumpAmplifier(3, overload), "Overload III is amplifier 2");
        assertEquals(1, ArmorEnchantments.jumpAmplifier(2, ninja), "Ninja II is amplifier 1");
    }

    @Test
    void theHighestHealthBoostWinsInsteadOfTheFirstPiece() throws IOException {
        final String armor = read(ARMOR);

        assertTrue(armor.contains("type.equals(PotionEffectType.HEALTH_BOOST)"),
                "Health Boost must use the strongest-source branch");
        assertTrue(armor.contains("current.level() - 1 < amplifier"),
                "a stronger effective amplifier replaces the weaker one, in either collection order");
        assertTrue(armor.contains("new WornPotion(amplifier + 1, false)"),
                "the stored level is the winning amplifier plus one");
    }

    @Test
    void aWornLevelChangeReplacesTheInfiniteWornEffect() {
        assertEquals(WornBoost.REPLACE, ArmorEnchantments.wornBoostAction(true, 2, true, 0),
                "Overload III dropped to Overload I really goes down to I, not staying III");
        assertEquals(WornBoost.REPLACE, ArmorEnchantments.wornBoostAction(true, 0, true, 2),
                "a weaker infinite level is raised to the worn one");
        assertEquals(WornBoost.KEEP, ArmorEnchantments.wornBoostAction(true, 2, true, 2),
                "an equal infinite level is never removed and re-added, so health is not clamped");
        assertEquals(WornBoost.ADD, ArmorEnchantments.wornBoostAction(false, 0, false, 0),
                "nothing present: the worn level is added");
    }

    @Test
    void aFiniteBuffIsOnlyReplacedWhenTheWornLevelBeatsIt() {
        assertEquals(WornBoost.KEEP, ArmorEnchantments.wornBoostAction(true, 3, false, 0),
                "a stronger potion is preserved");
        assertEquals(WornBoost.KEEP, ArmorEnchantments.wornBoostAction(true, 1, false, 1),
                "an equal finite buff is left to expire instead of becoming infinite");
        assertEquals(WornBoost.REPLACE, ArmorEnchantments.wornBoostAction(true, 1, false, 2),
                "a weaker finite buff is raised to the worn level");
    }

    @Test
    void aWornDowngradeKeepsTheHeartsTheNewMaximumAllows() {
        assertEquals(22.0, ArmorEnchantments.settledHealth(24.0, 22.0), "a full boosted bar keeps the new maximum");
        assertEquals(18.0, ArmorEnchantments.settledHealth(18.0, 22.0), "damage below the new maximum is untouched");
        assertEquals(12.0, ArmorEnchantments.settledHealth(18.0, 12.0), "a shorter bar caps at its own maximum");
        assertEquals(24.0, ArmorEnchantments.settledHealth(24.0, 24.0), "an unchanged maximum is not touched");
    }

    @Test
    void theSettledHealthIsWrittenBackAfterAWornChange() throws IOException {
        final String armor = read(ARMOR);

        assertTrue(armor.contains("settledHealth(health, player.getMaxHealth())"), "the replacement settles health");
        assertTrue(armor.contains("player.getHealth() != kept) player.setHealth(kept)"), "damage never becomes healing");
    }

    @Test
    void aFiniteHealthBoostHandsOverToTheWornLevelOnExpiry() throws IOException {
        final String armor = read(ARMOR);

        assertTrue(armor.contains("if (event.isCancelled()) return;"), "a cancelled expiry leaves the effect in place");
        assertTrue(armor.contains("event.getAction() != EntityPotionEffectEvent.Action.REMOVED"), "only removals are watched");
        assertTrue(armor.contains("event.getCause() != EntityPotionEffectEvent.Cause.EXPIRATION"),
                "only natural expiry is watched, so CE's own removals cannot retrigger it");
        assertTrue(armor.contains("handBackWornHealthBoost(player)"), "the expiry queues the hand-over");
        assertTrue(armor.contains("player.getScheduler().run(this.plugin, task -> reconcileWornHealthBoost(player), null)"),
                "the hand-over runs on the wearer's next tick");
        assertTrue(armor.contains("getTopPotionEffects(getUpperEnchants(player))"), "it re-reads what is worn right now");
        assertTrue(armor.contains("applyWornHealthBoost(player, effect.getValue().level() - 1)"),
                "equipping goes through the same rule");
    }

    @Test
    void anEarlyEndOfEnderShiftsBoostHandsTheWornLevelBack() throws IOException {
        final String armor = read(ARMOR);
        final int start = armor.indexOf("private void endEnderShift");

        assertTrue(start > 0, "endEnderShift must exist");

        final String shift = armor.substring(start, armor.indexOf("\n    }", start));

        assertTrue(shift.contains("handBackWornHealthBoost(wearer)"),
                "Ending a shift early (health recovered, piece removed) hands the worn level back next tick");
    }

    @Test
    void theWornRefreshAppliesHealthBoostLevelOne() throws IOException {
        final String manager = read(MANAGER);

        assertTrue(manager.contains("checkPotions(effects, player, hulk, true)"),
                "the milk/world-switch refresh marks its entries as worn");
        assertTrue(manager.contains("if (value == 0 && !(worn && key.equals(PotionEffectType.HEALTH_BOOST))) continue;"),
                "a worn amplifier 0 is applied for Health Boost and for no other type");
        assertTrue(manager.contains("checkPotions(effects, player, Set.of(), false)"),
                "the public path keeps 0 as the absent-enchant sentinel");
    }

    @Test
    void theWornRefreshKeepsASameOrStrongerHealthBoost() throws IOException {
        final String manager = read(MANAGER);

        assertTrue(manager.contains("present.getDuration() != PotionEffect.INFINITE_DURATION && present.getAmplifier() >= value"),
                "an equal or stronger finite buff is never replaced by the refresh");
        assertTrue(manager.contains("present.getDuration() == PotionEffect.INFINITE_DURATION && present.getAmplifier() == value"),
                "an equal worn level is re-added without a removing tick");
        assertTrue(manager.contains("player.removePotionEffect(key);"),
                "any other worn level is removed first, so addPotionEffect never has to merge");
    }

    @Test
    void everyOtherWornEffectKeepsItsOldSelection() throws IOException {
        final String armor = read(ARMOR);

        assertTrue(armor.contains("current.level() != -1 && current.level() <= offset"), "non-boost selection is unchanged");
        assertTrue(armor.contains("new WornPotion(level, enchant == CEnchantments.HULK)"), "non-boost keeps the raw level");
        assertTrue(armor.contains("effect.getValue().level() - 1"), "the amplifier calculation is untouched");
        assertTrue(read(MANAGER).contains("effects.get(type) >= amplifier"), "the milk/world-switch refresh keeps the maximum");
    }

    @Test
    void unequipKeepsASurvivingOrForeignHealthBoost() throws IOException {
        final String armor = read(ARMOR);

        assertTrue(armor.contains("if (worn.containsKey(type)) return;"),
                "a boost another piece still grants is handled by the apply step, so a downgrade lands there");
        assertTrue(armor.contains("if (present != null && present.getDuration() != PotionEffect.INFINITE_DURATION) return;"),
                "a finite Health Boost is not removed with the piece that granted it");
    }

    private static int healthBoostOffset(final String source, final String enchant) {
        final Matcher matcher = Pattern.compile("enchants\\.get\\(CEnchantments\\." + enchant
                + "\\)\\.put\\(PotionEffectType\\.HEALTH_BOOST, (-?\\d+)\\);").matcher(source);

        assertTrue(matcher.find(), "CrazyManager must map " + enchant + " to Health Boost");

        return Integer.parseInt(matcher.group(1));
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative)).replace("\r\n", "\n");
    }
}
