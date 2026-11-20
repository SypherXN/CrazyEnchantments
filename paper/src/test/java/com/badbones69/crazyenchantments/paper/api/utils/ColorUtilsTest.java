package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the tier colouring: the enchant name is recoloured from its category's
 * GUI name, so every shipped category must actually declare a colour.
 */
class ColorUtilsTest {

    private static final Pattern CATEGORY_BLOCK = Pattern.compile(
            "(?m)^ {2}([A-Za-z0-9_-]+):(?:\\s*#.*)?\\s*\\n {4}Name: '([^']*)'");

    @Test
    void firstColourSkipsFormattingCodes() {
        assertEquals("&f", ColorUtils.firstColour("&f&l&nCommon"));
        assertEquals("&7", ColorUtils.firstColour("&7Glowing"));
        assertEquals("&6", ColorUtils.firstColour("&6&lLegendary"));
        assertEquals("&#ff0000", ColorUtils.firstColour("&#ff0000Hex"));
        assertNull(ColorUtils.firstColour("&lJustBold"));
        assertNull(ColorUtils.firstColour("Glowing"));
        assertNull(ColorUtils.firstColour(null));
    }

    @Test
    void stripRemovesEveryColourIncludingBlackAndHex() {
        assertEquals("Dark", ColorUtils.stripStringColour("&0Dark"));
        assertEquals("Hex", ColorUtils.stripStringColour("&#ff0000Hex"));
        assertEquals("Bold", ColorUtils.stripStringColour("&lBold"));
        assertEquals("Frozen", ColorUtils.stripStringColour("&7Frozen"));
    }

    @Test
    void everyShippedCategoryDeclaresAColour() throws IOException {
        final String config = Files.readString(Path.of("src/main/resources/config.yml"));
        final int start = config.indexOf("\nCategories:");

        assertTrue(start > 0, "config.yml must keep a Categories section");

        final Matcher matcher = CATEGORY_BLOCK.matcher(config.substring(start));

        int categories = 0;

        while (matcher.find()) {
            categories++;

            assertNotNull(ColorUtils.firstColour(matcher.group(2)),
                    "Categories." + matcher.group(1) + ".Name needs a colour, otherwise enchant names stay grey");
        }

        assertTrue(categories >= 5, "expected the five shipped tiers, found " + categories);
    }
}
