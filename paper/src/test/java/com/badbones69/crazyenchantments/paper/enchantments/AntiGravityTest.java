package com.badbones69.crazyenchantments.paper.enchantments;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti Gravity is meant to be a stronger Springs: one Jump Boost level above a Springs of
 * the same level, so I/II/III apply Jump Boost II/III/IV where Springs I/II/III apply
 * I/II/III. It is the only effect that changed, so the listener tests also pin down that
 * every other worn effect keeps its old selection. Listener behaviour is guarded by reading
 * the source as text, in the style of ArmorEnchantLogicTest2. No Bukkit server is loaded.
 */
class AntiGravityTest {

    private static final String MANAGER = "src/main/java/com/badbones69/crazyenchantments/paper/api/CrazyManager.java";

    private static final String ARMOR = "src/main/java/com/badbones69/crazyenchantments/paper/enchantments/ArmorEnchantments.java";

    @Test
    void antiGravityStartsAtTheSameJumpBoostAsSpringsThree() throws IOException {
        final String manager = read(MANAGER);
        final int springs = jumpOffset(manager, "SPRINGS");
        final int antiGravity = jumpOffset(manager, "ANTIGRAVITY");

        assertEquals(0, ArmorEnchantments.jumpAmplifier(1, springs), "Springs I is Jump Boost I");
        assertEquals(2, ArmorEnchantments.jumpAmplifier(3, springs), "Springs III is Jump Boost III");
        assertEquals(2, ArmorEnchantments.jumpAmplifier(1, antiGravity), "Anti Gravity I is Jump Boost III");
        assertEquals(3, ArmorEnchantments.jumpAmplifier(2, antiGravity), "Anti Gravity II is Jump Boost IV");
        assertEquals(4, ArmorEnchantments.jumpAmplifier(3, antiGravity), "Anti Gravity III is Jump Boost V");
    }

    @Test
    void onlyJumpBoostUsesTheOffset() throws IOException {
        final String armor = read(ARMOR);

        // Jump Boost: the strongest effective amplifier wins.
        assertTrue(armor.contains("PotionEffectType.JUMP_BOOST"), "Jump Boost is the branch the offset decides");
        assertTrue(armor.contains("jumpAmplifier(level, offset)"), "Jump Boost uses the effective amplifier");
        assertTrue(armor.contains("current.level() - 1 < amplifier"), "the strongest effective Jump Boost wins");

        // Every other effect keeps the pre-change worn selection: raw level, first entry wins.
        assertTrue(armor.contains("current.level() != -1 && current.level() <= offset"), "non-jump selection is unchanged");
        assertTrue(armor.contains("new WornPotion(level, enchant == CEnchantments.HULK)"), "non-jump keeps the raw level");

        // ...and the pre-change amplifier calculation is untouched.
        assertTrue(armor.contains("effect.getValue().level() - 1"));

        // The milk/world-switch refresh already keeps the strongest effective amplifier, jump included.
        assertTrue(read(MANAGER).contains("effects.get(type) >= amplifier"));
    }

    private static int jumpOffset(final String source, final String enchant) {
        final Matcher matcher = Pattern.compile("enchants\\.get\\(CEnchantments\\." + enchant
                + "\\)\\.put\\(PotionEffectType\\.JUMP_BOOST, (-?\\d+)\\);").matcher(source);

        assertTrue(matcher.find(), "CrazyManager must map " + enchant + " to Jump Boost");

        return Integer.parseInt(matcher.group(1));
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative)).replace("\r\n", "\n");
    }
}
