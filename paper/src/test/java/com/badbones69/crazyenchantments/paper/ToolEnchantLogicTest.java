package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.ToolEnchantLogic;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ToolEnchantLogicTest {

    private static final Path JAVA = Path.of("src/main/java/com/badbones69/crazyenchantments/paper");
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String PICKS = "enchantments/PickaxeEnchantments.java";
    private static final String TOOLS = "enchantments/ToolEnchantments.java";

    @Test
    void reforgedNominalChanceAtEveryLevel() throws IOException {
        assertChanceTable("Reforged", 4, 4, List.of(4, 8, 12, 16, 20, 24, 28, 32, 36, 40));
    }

    @Test
    void obsidianDestroyerNominalChanceAtEveryLevel() throws IOException {
        assertChanceTable("Obsidian Destroyer", 10, 5, List.of(10, 15, 20, 25, 30));
    }

    @Test
    void tiersAndApplicationTypesMatchRegistry() throws IOException {
        assertEntry("Reforged", "Elite", "Weapons-and-Tools", 10);
        assertEntry("Fuse", "Ultimate", "Pickaxe", 1);
        assertEntry("Obsidian Destroyer", "Unique", "Pickaxe", 5);
    }

    @Test
    void newTypeIsExactlyTheWeaponAndToolUnion() throws IOException {
        var types = section(yaml("Enchantment-Types.yml"), "Types");
        var expected = new HashSet<Object>(materials(types, "Weapons"));
        expected.addAll(materials(types, "Tool"));
        var actual = materials(types, "Weapons-and-Tools");
        assertEquals(expected, new HashSet<>(actual));
        assertEquals(expected.size(), actual.size());
    }

    @Test
    void newTypeUsesAnUnoccupiedInventoryIndex() throws IOException {
        var types = section(yaml("Enchantment-Types.yml"), "Types");
        var positions = new HashSet<Integer>();
        for (var type : types.keySet()) {
            var display = section(section(types, type), "Display-Item");
            var index = (int) display.get("Slot") - 1;
            assertTrue(positions.add(index), "collision at inventory index " + index);
            if (type.equals("Weapons-and-Tools")) assertEquals(10, index);
        }
    }

    @Test
    void reforgedChecksTheWornStacksIdentityInEitherHandBeforeRolling() throws IOException {
        var wear = between(source(TOOLS), "public static boolean preventsWear(", "public void onPlayerClick(");
        assertInOrder(wear, "DataComponentTypes.UNBREAKABLE", "reforged.canEnchantItem(item)",
                "ToolEnchantLogic.isHeldStack(", "CraftItemStack.unwrap(item)",
                "getItemInMainHand()", "getItemInOffHand()", "EnchantUtils.isEventActive(");
        assertFalse(wear.contains("getArmorContents"));
    }

    @Test
    void reforgedCancelsOneUncancelledPositiveWearEvent() throws IOException {
        var handler = between(source(TOOLS), "@EventHandler(priority = EventPriority.HIGHEST", "public static boolean preventsWear(");
        assertInOrder(handler, "ignoreCancelled = true", "PlayerItemDamageEvent", "event.getDamage() <= 0",
                "EventUtils.isIgnoredEvent(event)", "preventsWear(", "event.setCancelled(true)");
        assertEquals(1, handler.split("preventsWear\\(", -1).length - 1);
    }

    @Test
    void directCeWearConsultsReforgedWithoutFiringASecondDamageEvent() throws IOException {
        var wear = between(source("Methods.java"), "public void removeDurability(", "public void explode(");
        assertInOrder(wear, "DataComponentTypes.UNBREAKABLE", "ToolEnchantments.preventsWear(", "setDurability(");
        assertEquals(1, wear.split("ToolEnchantments.preventsWear\\(", -1).length - 1);
        assertFalse(wear.contains("PlayerItemDamageEvent"));
    }

    @Test
    void fuseHasNoChanceSystem() throws IOException {
        assertFalse(enchant("Fuse").containsKey("Chance-System"));
        assertTrue(source("api/enums/CEnchantments.java").contains("FUSE(\"Fuse\", \"Pickaxe\"),"));
    }

    @Test
    void fuseUsesOnlyTheExistingBlastDropSeam() throws IOException {
        var picks = source(PICKS);
        var blast = between(picks, "public void onBlastBreak(", "public void onVeinMinerBreak(");
        assertInOrder(blast, "massBlockBreakCheck(player, blockList)", "CEnchantments.FUSE",
                "isOwnedByCurrentRegion(block.getLocation())", "breakBlastBlock(player, block, currentItem, blockList, fuse)",
                "blastSmelting.set(new ToolEnchantLogic.FuseBinding<>(blastBlocks))", "this.methods.playerBreakBlock(", "finally");
        var vein = between(picks, "public void onVeinMinerBreak(", "private boolean massBlockBreakCheck(");
        assertFalse(vein.contains("breakBlastBlock"));
    }

    @Test
    void fuseUsesAutoSmeltMappingAtOriginalAmountWithoutRollingAutoSmelt() throws IOException {
        var drops = between(source(PICKS), "public void onDropAlter(", "public void onExperience(");
        assertInOrder(drops, "isOreBlock(", "blast.matches(event, event.getBlock())",
                "getOreDrop(drop, drop.getAmount())", "return; // Fuse", "EnchantUtils.isEventActive(CEnchantments.AUTOSMELT",
                "getOreDrop(drop, drop.getAmount() + amountToAdd)");
        var fuse = between(drops, "final ToolEnchantLogic.FuseBinding", "return; // Fuse");
        assertFalse(fuse.contains("chanceSuccessful"));
        assertFalse(fuse.contains("isEventActive"));
    }

    @Test
    void obsidianDestroyerWhitelistsOnlyPlainObsidian() throws IOException {
        var dig = between(source(PICKS), "public void onObsidianDamage(", "public void onObsidianFinish(");
        assertInOrder(dig, "Bukkit.isOwnedByCurrentRegion(block.getLocation())",
                "block.getType() != Material.OBSIDIAN",
                "enchantment.canEnchantItem(item)", "setInstaBreak(true)");
        assertEquals(1, dig.split("Material\\.", -1).length - 1);
    }

    @Test
    void obsidianDestroyerRollsOnlyAfterRecordingANewAttempt() throws IOException {
        var dig = between(source(PICKS), "public void onObsidianDamage(", "public void onObsidianFinish(");
        assertInOrder(dig, "this.obsidianAttempts.start(player.getUniqueId(), block)",
                "return;", "EnchantUtils.isEventActive(CEnchantments.OBSIDIAN_DESTROYER");
        assertEquals(1, dig.split("EnchantUtils.isEventActive\\(", -1).length - 1);
    }

    @Test
    void releasingDoesNotPermitAnotherRollButFinishingTheTargetDoes() throws IOException {
        var picks = source(PICKS);
        assertFalse(picks.contains("BlockDamageAbortEvent"));
        var cleanup = between(picks, "public void onObsidianFinish(", "public void onBlockClick(");
        assertInOrder(cleanup, "BlockBreakEvent", "obsidianAttempts.finish(",
                "PlayerChangedWorldEvent", "PlayerTeleportEvent", "PlayerQuitEvent");
        var attempts = new ToolEnchantLogic.DigAttempts<String, String>();
        assertTrue(attempts.start("player", "target"));
        attempts.finish("player", "other");
        assertFalse(attempts.start("player", "target"));
        attempts.finish("player", "target");
        assertTrue(attempts.start("player", "target"));
    }

    @Test
    void protectionCanVetoBothNativeAndBlastBreaksBeforeAnyBlockMutation() throws IOException {
        var picks = source(PICKS);
        assertTrue(picks.contains("event.setInstaBreak(true)"));
        assertFalse(picks.contains("setType("));
        assertFalse(picks.contains("breakNaturally("));
        var manualBreak = between(source("Methods.java"), "public boolean playerBreakBlock(", "private void dropXP(");
        assertInOrder(manualBreak, "new BlockBreakEvent(", "callEvent(blockBreak)", "if (blockBreak.isCancelled()) return true;",
                "block.setType(Material.AIR)");
    }

    private static void assertChanceTable(String name, int base, int increase, List<Integer> expected) throws IOException {
        var entry = enchant(name);
        var chance = section(entry, "Chance-System");
        assertEquals(base, chance.get("Base"));
        assertEquals(increase, chance.get("Increase"));
        assertEquals(expected, IntStream.rangeClosed(1, (int) entry.get("MaxPower"))
                .map(level -> base + increase * (level - 1)).boxed().toList());
        assertTrue(source("api/enums/CEnchantments.java")
                .contains("\"" + name + "\", \"" + entry.get("Enchantment-Type") + "\", " + base + ", " + increase + ")"));
    }

    private static void assertEntry(String name, String tier, String type, int max) throws IOException {
        var entry = enchant(name);
        assertEquals(true, entry.get("Enabled"));
        assertEquals(List.of(tier), entry.get("Categories"));
        assertEquals(type, entry.get("Enchantment-Type"));
        assertEquals(max, entry.get("MaxPower"));
    }

    private static Map<?, ?> enchant(String name) throws IOException {
        return section(section(yaml("Enchantments.yml"), "Enchantments"), name);
    }

    private static List<?> materials(Map<?, ?> types, String name) {
        return (List<?>) section(types, name).get("Enchantable-Items");
    }

    private static Map<?, ?> yaml(String name) throws IOException {
        return new Yaml().load(Files.readString(RESOURCES.resolve(name)));
    }

    private static Map<?, ?> section(Map<?, ?> map, Object key) {
        return assertInstanceOf(Map.class, map.get(key));
    }

    private static String source(String file) throws IOException {
        return Files.readString(JAVA.resolve(file));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to >= 0, end);
        return source.substring(from, to);
    }

    private static void assertInOrder(String source, String... snippets) {
        int offset = 0;
        for (String snippet : snippets) {
            int found = source.indexOf(snippet, offset);
            assertTrue(found >= 0, snippet);
            offset = found + snippet.length();
        }
    }
}
