package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-only switch that makes custom enchant activation chance always pass for
 * the listed players, so a tester can exercise a proc without rerolling.
 *
 * <p>Activation odds only: {@code EnchantUtils.isActive} skips its
 * {@code chanceSuccessful} roll for these players. Everything else stays enforced -
 * the enchant must still be enabled and equipped, permissions and the cancellable
 * use event still apply, and cooldowns, valid targets and regions are untouched.
 * Book, dust and reward-drop rolls do not pass through here.
 *
 * <p>Not persisted and not an OP bypass: the set empties on quit, reload and disable.
 */
public final class ProcTest {

    // Boolean presence map, so a toggle is one atomic per-key compute rather than
    // two racing set operations that could both report ON.
    private static final Map<UUID, Boolean> players = new ConcurrentHashMap<>();

    private ProcTest() {}

    public static boolean isEnabled(final UUID player) {
        return player != null && players.containsKey(player);
    }

    /**
     * Flips the state atomically. Two concurrent toggles of one player always
     * land ON once and OFF once, whatever order they run in.
     *
     * @return the state after the toggle.
     */
    public static boolean toggle(final UUID player) {
        if (player == null) return false;

        return players.compute(player, (id, current) -> current == null ? Boolean.TRUE : null) != null;
    }

    public static void clear(final UUID player) {
        if (player == null) return;

        players.remove(player);
    }

    public static void clear() {
        players.clear();
    }
}
