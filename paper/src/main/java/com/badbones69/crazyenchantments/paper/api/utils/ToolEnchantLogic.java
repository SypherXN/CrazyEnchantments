package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit-free decision logic for Reforged, Fuse and Obsidian Destroyer, kept apart from the listeners so it can be
 * tested directly. The listeners gather the facts; these methods decide.
 */
public final class ToolEnchantLogic {

    private ToolEnchantLogic() {}

    /**
     * Reforged only protects the stack the player is live-holding in either hand. Identity, not equality: an
     * identical stack that is not held must not match.
     */
    public static boolean isHeldStack(final Object worn, final Object mainHand, final Object offHand) {
        return worn != null && (worn == mainHand || worn == offHand);
    }

    /** Every precondition for rolling Reforged on one wear event; the roll itself happens only when this is true. */
    public static boolean reforgedMayRoll(final boolean bleedTick, final boolean owned, final boolean emptyItem,
                                          final boolean unbreakable, final boolean applicable, final boolean liveMainHand) {
        return !bleedTick && owned && !emptyItem && !unbreakable && applicable && liveMainHand;
    }

    /** Whether Fuse smelts one drop of a Blast-owned drop event. {@code oreDrop} is Auto Smelt's own ingredient test. */
    public static boolean fuseSmelts(final boolean bleedTick, final boolean ignoredEvent, final boolean oreDrop) {
        return !bleedTick && !ignoredEvent && oreDrop;
    }

    /**
     * Binds Fuse to one exact drop event created by one Blast block break, and to the exact set of blocks that Blast
     * invocation is breaking. A nested drop event for the same block is a different event object and never matches.
     */
    public static final class FuseBinding<E, B> {

        private final Set<B> blastBlocks;
        private volatile E event;

        public FuseBinding(final Set<B> blastBlocks) {
            this.blastBlocks = Set.copyOf(blastBlocks);
        }

        /** Records the event; only the first call counts. */
        public void bind(final E event) {
            if (this.event == null) this.event = event;
        }

        public boolean matches(final E event, final B block) {
            return event != null && event == this.event && this.blastBlocks.contains(block);
        }
    }

    /** One Obsidian Destroyer roll for the current eligible target, even if the player releases and re-clicks it. */
    public static final class DigAttempts<K, B> {

        private final Map<K, B> tracked = new ConcurrentHashMap<>();

        /** Records an eligible target; only a change of target or cleanup permits another roll. */
        public boolean start(final K player, final B block) {
            return !block.equals(this.tracked.put(player, block));
        }

        /** The break completed; only that block's attempt ends. */
        public void finish(final K player, final B block) {
            this.tracked.remove(player, block);
        }

        /** Quit, world change or teleport. */
        public void clear(final K player) {
            this.tracked.remove(player);
        }
    }
}
