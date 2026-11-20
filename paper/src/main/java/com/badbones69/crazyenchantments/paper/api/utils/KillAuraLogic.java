package com.badbones69.crazyenchantments.paper.api.utils;

public final class KillAuraLogic {

    public static final long RECENT_DAMAGE_WINDOW_MILLIS = 5000L;

    private KillAuraLogic() {}

    public static int extraKills(int level, int stackAmount, int amount) {
        return (int) Math.max(0, Math.min(level, (long) stackAmount - amount));
    }

    /** WildStacker cannot report why a stack unstacked, so a kill is only trusted when it is recent. */
    public static boolean isRecent(long recordedAt, long now, long windowMillis) {
        final long elapsed = now - recordedAt;
        // Negative elapsed means the timestamp is in the future, which we treat as untrusted.
        return elapsed >= 0 && elapsed <= windowMillis;
    }
}
