package com.badbones69.crazyenchantments.paper.api.enums.pdc;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sniper rename: an item stored "Sniper" for the poison enchant and
 * "SniperHeadshot" for the headshot, and today "Sniper" is the headshot while
 * the poison enchant is "Poison Shot". A stored payload without a version is
 * read as the old names exactly once; a payload that has one is read literally,
 * so a migrated item can never be migrated a second time.
 */
class StoredEnchantMigrationTest {

    private static final Gson GSON = new Gson();

    @Test
    void aVersionedGearPayloadIsWrittenWithTheVersion() {
        // Pins the payload shape the rest of this class feeds in.
        assertEquals("{\"enchants\":{\"Sniper\":3},\"version\":1}",
                GSON.toJson(new Enchant(new HashMap<>(Map.of("Sniper", 3)))));
        assertFalse(GSON.toJson(new Enchant(new HashMap<>())).isEmpty());
    }

    @Test
    void anUnversionedPayloadMapsBothDirections() {
        final Enchant legacy = Enchant.fromJson("{\"enchants\":{\"Sniper\":2,\"SniperHeadshot\":5}}");

        assertEquals(2, legacy.getLevel("Poison Shot"), "old \"Sniper\" was the poison enchant");
        assertEquals(5, legacy.getLevel("Sniper"), "old \"SniperHeadshot\" is the headshot's \"Sniper\"");
        assertNull(legacy.getLevel("SniperHeadshot"), "the old headshot key is gone");
    }

    @Test
    void aVersionedPayloadReadsSniperAsTheHeadshot() {
        final Enchant migrated = Enchant.fromJson("{\"enchants\":{\"Sniper\":3},\"version\":1}");

        assertEquals(3, migrated.getLevel("Sniper"));
        assertNull(migrated.getLevel("Poison Shot"));
    }

    @Test
    void migratingTwiceChangesNothing() {
        final Enchant once = Enchant.fromJson("{\"enchants\":{\"Sniper\":2,\"SniperHeadshot\":5}}");
        final Map<String, Integer> first = new HashMap<>(once.getFullEnchantments());

        final Enchant twice = Enchant.fromJson(GSON.toJson(once));

        assertEquals(first, twice.getFullEnchantments(), "a written payload must not be translated again");
        assertEquals(5, twice.getLevel("Sniper"), "the headshot stays the headshot");
        assertEquals(2, twice.getLevel("Poison Shot"), "the poison enchant stays poison");
    }

    @Test
    void theUnderscoredHeadshotSpellingAlsoResolves() {
        final Enchant legacy = Enchant.fromJson("{\"enchants\":{\"Sniper_Headshot\":4}}");

        assertEquals(4, legacy.getLevel("Sniper"));
        assertNull(legacy.getLevel("Poison Shot"));
    }

    @Test
    void aBookNameFollowsTheSameRename() {
        final EnchantedBook headshot = EnchantedBook.fromJson("{\"name\":\"SniperHeadshot\",\"success\":50,\"destroy\":10,\"level\":3}");
        assertEquals("Sniper", headshot.getName());
        assertEquals(3, headshot.getLevel());

        final EnchantedBook poison = EnchantedBook.fromJson("{\"name\":\"Sniper\",\"success\":50,\"destroy\":10,\"level\":2}");
        assertEquals("Poison Shot", poison.getName());

        final EnchantedBook written = EnchantedBook.fromJson(GSON.toJson(new EnchantedBook("Sniper", 50, 10, 4)));
        assertEquals("Sniper", written.getName(), "a written book is not translated again");
        assertTrue(GSON.toJson(new EnchantedBook("Sniper", 50, 10, 4)).contains("\"version\":1"));
    }
}
