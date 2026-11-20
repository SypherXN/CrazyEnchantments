package com.badbones69.crazyenchantments.paper.api.bleed;

import com.badbones69.crazyenchantments.paper.api.events.BleedTickEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after attributed Bleed damage was applied, when the synthetic hit
 * observes a loss of health or absorption and both entities remain owner-local.
 * The pre-damage, cancellable {@link BleedTickEvent} is the only Bleed tick
 * veto point. A plugin cancellation after the damage observation is invisible
 * here. Requested damage is not the amount lost.
 */
public final class BleedTickLandedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity victim;
    private final Player source;
    private final double requestedDamage;
    private final int stacks;

    BleedTickLandedEvent(final LivingEntity victim, final Player source, final double requestedDamage, final int stacks) {
        this.victim = victim;
        this.source = source;
        this.requestedDamage = requestedDamage;
        this.stacks = stacks;
    }

    public @NotNull LivingEntity getVictim() { return this.victim; }

    public @NotNull Player getSource() { return this.source; }

    public double getRequestedDamage() { return this.requestedDamage; }

    public int getStacks() { return this.stacks; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
