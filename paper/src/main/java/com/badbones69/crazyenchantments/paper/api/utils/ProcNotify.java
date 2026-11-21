package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-only, per-player "tell me when an enchant procs" switch.
 *
 * <p>Modes cycle OFF -> ACTIONBAR -> CHAT -> OFF. The message names the enchant
 * and its level, and is only sent for activations that passed the real chance
 * roll, so a silent enchant looks silent instead of looking broken.
 *
 * <p>Not persisted: quits, reloads and disable clear the map, exactly like
 * {@link ProcTest}.
 */
public final class ProcNotify {

    public enum Mode {
        OFF,
        ACTIONBAR,
        CHAT
    }

    // How long one player+enchant pair is treated as already reported. Several
    // handlers evaluate the same enchant twice in one event; without this a single
    // hit could print twice.
    private static final long DEDUPE_NANOS = 100_000_000L;

    private static final Map<UUID, Mode> modes = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<CEnchantments, Long>> recent = new ConcurrentHashMap<>();

    private ProcNotify() {}

    public static Mode mode(final UUID player) {
        return player == null ? Mode.OFF : modes.getOrDefault(player, Mode.OFF);
    }

    /**
     * Advances the player one step through OFF -> ACTIONBAR -> CHAT -> OFF.
     *
     * @return the mode after the cycle.
     */
    public static Mode cycle(final UUID player) {
        if (player == null) return Mode.OFF;

        return modes.compute(player, (id, current) -> switch (current == null ? Mode.OFF : current) {
            case OFF -> Mode.ACTIONBAR;
            case ACTIONBAR -> Mode.CHAT;
            case CHAT -> null;
        }) == null ? Mode.OFF : modes.get(player);
    }

    public static void set(final UUID player, final Mode mode) {
        if (player == null) return;

        if (mode == null || mode == Mode.OFF) {
            modes.remove(player);
        } else {
            modes.put(player, mode);
        }
    }

    /**
     * Reports one activation to the player whose item it came from.
     */
    public static void send(final Player player, final CEnchantments enchant, final int level) {
        if (player == null || enchant == null) return;

        final Mode mode = mode(player.getUniqueId());

        if (mode == Mode.OFF) return;
        if (isRecent(player.getUniqueId(), enchant)) return;

        final String message = "&7\u00bb &d" + enchant.getName() + (level > 0 ? " &7(" + level + ")" : "");
        final var component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);

        if (mode == Mode.CHAT) {
            player.sendMessage(component);
        } else {
            player.sendActionBar(component);
        }
    }

    private static boolean isRecent(final UUID player, final CEnchantments enchant) {
        final long now = System.nanoTime();
        final Map<CEnchantments, Long> last = recent.computeIfAbsent(player, id -> new ConcurrentHashMap<>());
        final Long previous = last.get(enchant);

        if (previous != null && now - previous < DEDUPE_NANOS) return true;

        last.put(enchant, now);

        return false;
    }

    public static void clear(final UUID player) {
        if (player == null) return;

        modes.remove(player);
        recent.remove(player);
    }

    public static void clear() {
        modes.clear();
        recent.clear();
    }
}
