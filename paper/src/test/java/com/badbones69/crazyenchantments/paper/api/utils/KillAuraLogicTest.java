package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraLogicTest {

    private static final long RECENT_DAMAGE_WINDOW_MILLIS = KillAuraLogic.RECENT_DAMAGE_WINDOW_MILLIS;

    @Test
    void recentDamageWindowIsInclusiveAndRejectsFutureTimestamps() {
        assertTrue(KillAuraLogic.isRecent(1_000L, 1_000L, RECENT_DAMAGE_WINDOW_MILLIS));
        assertTrue(KillAuraLogic.isRecent(1_000L, 6_000L, RECENT_DAMAGE_WINDOW_MILLIS));
        assertFalse(KillAuraLogic.isRecent(1_000L, 6_001L, RECENT_DAMAGE_WINDOW_MILLIS));
        assertFalse(KillAuraLogic.isRecent(1_000L, 999L, RECENT_DAMAGE_WINDOW_MILLIS));
    }

    @Test
    void extraKillsAreLimitedByLevelAndRemainingStack() {
        assertEquals(5, KillAuraLogic.extraKills(5, 20, 1));
        assertEquals(2, KillAuraLogic.extraKills(5, 7, 5));
        assertEquals(0, KillAuraLogic.extraKills(5, 1, 1));
        assertEquals(0, KillAuraLogic.extraKills(5, 1, 2));
        assertEquals(0, KillAuraLogic.extraKills(0, 20, 1));
        assertEquals(0, KillAuraLogic.extraKills(-1, 20, 1));
    }

    @Test
    void listenerIsOnlyRegisteredWhenWildStackerIsEnabled() throws IOException {
        final String plugin = read("CrazyEnchantments.java");
        final String registration = "this.pluginManager.registerEvents(new KillAuraListener(this), this);";
        final String check = "if (Bukkit.getPluginManager().isPluginEnabled(\"WildStacker\")) {";

        assertTrue(Pattern.compile(Pattern.quote(check) + "\\s*" + Pattern.quote(registration) + "\\s*}")
                .matcher(plugin).find());
        assertEquals(1L, Pattern.compile("new\\s+KillAuraListener\\s*\\(").matcher(plugin).results().count());
    }

    @Test
    void listenerOnlyIncreasesTheExistingSynchronousPlayerUnstack() throws IOException {
        final String listener = read("listeners/KillAuraListener.java");
        final String asyncGuard = "if (event.isAsynchronous()) return;";
        final String playerGuard = "if (!(event.getUnstackSource() instanceof Player player)) return;";

        assertTrue(listener.contains("@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)"));
        assertTrue(Pattern.compile(Pattern.quote("public void onUnstack(EntityUnstackEvent event) {")
                + "\\s*" + Pattern.quote(asyncGuard) + "\\s*" + Pattern.quote(playerGuard)).matcher(listener).find());
        assertTrue(listener.contains("EnchantUtils.isEventActive(CEnchantments.KILL_AURA, player, item, enchantments)"));
        assertTrue(listener.contains("KillAuraLogic.extraKills(level, stackAmount, event.getAmount())"));
        assertTrue(listener.contains("event.setAmount(event.getAmount() + extra);"));
        // The roll must come after the extra-kill decision so a proc is never spent on an exhausted stack.
        assertTrue(listener.indexOf("KillAuraLogic.extraKills(") < listener.indexOf("EnchantUtils.isEventActive("));
        assertFalse(Pattern.compile("\\brunUnstack\\s*\\(").matcher(listener).find());
    }

    @Test
    void recentDamageGatePrecedesTheRollAndIsFedByPlayerDamage() throws IOException {
        final String listener = read("listeners/KillAuraListener.java");

        assertTrue(listener.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)"));
        assertTrue(listener.contains("public void onDamage(EntityDamageByEntityEvent event)"));
        assertTrue(listener.contains("projectile.getShooter() instanceof Player"));

        // The gate must sit after the level check and before the roll, or a proc is spent on a rejected unstack.
        final int gate = listener.indexOf("if (stack == null || !recentlyDamaged(player, stack)) return;");
        assertTrue(gate > listener.indexOf("if (level <= 0) return;"));
        assertTrue(gate < listener.indexOf("EnchantUtils.isEventActive("));
        assertTrue(listener.contains("recentlyDamaged(victims, stack.getUniqueId(), now)"));
        assertTrue(listener.contains("recentlyDamaged(victims, living.getUniqueId(), now)"));

        // Tracked damage must not outlive the player.
        assertTrue(listener.contains("public void onQuit(PlayerQuitEvent event)"));
        assertTrue(listener.contains("this.recentDamage.remove(event.getPlayer().getUniqueId());"));
    }

    private static String read(String relative) throws IOException {
        final Path source = Path.of("src/main/java/com/badbones69/crazyenchantments/paper", relative);
        return Files.readString(Files.exists(source) ? source : Path.of("paper").resolve(source));
    }
}
