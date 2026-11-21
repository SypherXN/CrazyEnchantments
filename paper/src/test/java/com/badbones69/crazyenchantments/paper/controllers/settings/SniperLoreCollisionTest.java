package com.badbones69.crazyenchantments.paper.controllers.settings;

import com.badbones69.crazyenchantments.paper.api.enums.pdc.Enchant;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.LoreLines;
import com.badbones69.crazyenchantments.paper.api.utils.NumberUtils;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sniper (headshot) enchant's display name is also the label the poison
 * enchant wore before it became "Poison Shot", so both enchants' lore lines can
 * read "Sniper II". These pin which enchant owns such a line, and that removing
 * an enchant takes one line rather than two identical ones.
 */
class SniperLoreCollisionTest {

    @Test
    void theHeadshotDoesNotClaimThePoisonLine() {
        final Enchant poisonOnly = stored(Map.of("Poison Shot", 2));
        final String line = "Sniper " + NumberUtils.convertLevelString(2);

        assertFalse(matches(line, "Sniper", poisonOnly), "an item with only the poison enchant must keep its line");
        assertTrue(matches(line, "Poison Shot", poisonOnly), "the poison enchant still recognises its old label");
    }

    @Test
    void bothEnchantsMatchTheirOwnLineOnTheSameItem() {
        final Enchant both = stored(Map.of("Sniper", 3, "Poison Shot", 2));

        assertTrue(matches("Sniper " + NumberUtils.convertLevelString(3), "Sniper", both));
        assertTrue(matches("Poison Shot " + NumberUtils.convertLevelString(2), "Poison Shot", both));
        assertFalse(matches("Heavy " + NumberUtils.convertLevelString(3), "Poison Shot", both), "another enchant's line is not poison's");
    }

    @Test
    void aStaleHeadshotLineStillMatchesWhenNothingElseOwnsIt() {
        // Data and lore out of step: the item shows "Sniper II" but stores no
        // headshot and no poison. The headshot still matches, so upgrading it
        // replaces that line instead of adding a second one.
        assertTrue(matches("Sniper " + NumberUtils.convertLevelString(2), "Sniper", stored(Map.of())));
    }

    @Test
    void aRemovalTakesOneIdenticalLineOnly() {
        final Enchant both = stored(Map.of("Sniper", 2, "Poison Shot", 2));
        final String line = "Sniper " + NumberUtils.convertLevelString(2);
        final List<Component> lore = new ArrayList<>(List.of(Component.text(line), Component.text(line)));

        assertTrue(matches(line, "Sniper", both), "the headshot owns one of the two");
        assertTrue(LoreLines.removeFirst(lore, component -> matches(component, "Sniper", both)));
        assertEquals(1, lore.size(), "the poison label that reads the same must survive");
        assertEquals(line, ColorUtils.toPlainText(lore.getFirst()));
    }

    @Test
    void removalUsesOneLineAtATime() throws IOException {
        final String source = Files.readString(root().resolve("src/main/java/com/badbones69/crazyenchantments/paper/"
                + "controllers/settings/EnchantmentBookSettings.java"));

        assertTrue(source.contains("LoreLines.removeFirst(lore, line -> matchesLore(line, enchant, data))"),
                "removal must not delete two identical lines in one pass");
    }

    private static boolean matches(final String plain, final String enchantName, final Enchant stored) {
        return EnchantmentBookSettings.matchesLore(plain, enchantName, enchantName, stored);
    }

    private static boolean matches(final Component line, final String enchantName, final Enchant stored) {
        return EnchantmentBookSettings.matchesLore(ColorUtils.toPlainText(line), enchantName, enchantName, stored);
    }

    private static Enchant stored(final Map<String, Integer> levels) {
        return new Enchant(new HashMap<>(levels));
    }

    private static Path root() {
        Path root = Path.of(System.getProperty("user.dir"));

        return Files.exists(root.resolve("src/main/java")) ? root : root.resolve("paper");
    }
}
