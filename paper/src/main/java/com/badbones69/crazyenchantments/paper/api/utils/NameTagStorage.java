package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans where a name tag stack goes before anything is moved.
 *
 * <p>Existing tag stacks are topped up first, then empty storage slots are
 * filled in max-stack chunks. Everything else is left alone. The plan is pure
 * so the caller can refuse an all-or-nothing move without mutating anything.</p>
 */
public final class NameTagStorage {

    /** One storage slot as the caller sees it. {@code compatible} means it holds another tag. */
    public record Slot(int index, int amount, boolean compatible) {
        public boolean empty() {
            return this.amount <= 0;
        }
    }

    /** {@code merge} adds to the slot's current amount; otherwise the slot is empty and is filled. */
    public record Move(int index, int amount, boolean merge) {}

    public record Plan(List<Move> moves, int remainder) {
        public boolean fits() {
            return this.remainder <= 0;
        }
    }

    private NameTagStorage() {}

    /**
     * @param amount The whole stack to place.
     * @param maxStack The tag's max stack size.
     * @param slots The storage slots; a slot index must be unique.
     * @return The moves and whatever did not fit, never null.
     */
    public static Plan plan(final int amount, final int maxStack, final List<Slot> slots) {
        if (amount <= 0) return new Plan(List.of(), 0);

        final int max = Math.max(1, maxStack);
        int remaining = amount;
        final List<Move> moves = new ArrayList<>();

        for (final Slot slot : slots) {
            if (remaining <= 0) break;
            if (!slot.compatible()) continue;

            final int room = max - Math.max(0, slot.amount());

            if (room <= 0) continue;

            final int place = Math.min(room, remaining);

            moves.add(new Move(slot.index(), place, true));
            remaining -= place;
        }

        for (final Slot slot : slots) {
            if (remaining <= 0) break;
            if (!slot.empty()) continue;

            final int place = Math.min(max, remaining);

            moves.add(new Move(slot.index(), place, false));
            remaining -= place;
        }

        return new Plan(List.copyOf(moves), Math.max(0, remaining));
    }
}
