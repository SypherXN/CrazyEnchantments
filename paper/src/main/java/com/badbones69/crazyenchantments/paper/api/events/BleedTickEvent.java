package com.badbones69.crazyenchantments.paper.api.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired once per Bleed damage tick, before the attributed damage is attempted.
 * Blood Lust and future Bleed consumers read this event. The requested damage
 * may still be reduced or cancelled by armor and protection, so this event is
 * not a confirmation that damage was applied.
 */
public class BleedTickEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity victim;
    private final Player source;
    private final double damage;
    private final int stacks;

    private boolean cancelled;

    public BleedTickEvent(
            @NotNull final LivingEntity victim,
            @NotNull final Player source,
            final double damage,
            final int stacks
    ) {
        this.victim = victim;
        this.source = source;
        this.damage = damage;
        this.stacks = stacks;
        this.cancelled = false;
    }

    @NotNull
    public LivingEntity getVictim() {
        return this.victim;
    }

    /**
     * @return the player credited with the Bleed. Ticks without a same-region
     * source are not fired.
     */
    @NotNull
    public Player getSource() {
        return this.source;
    }

    /**
     * @return the damage this tick requests, before armor and protection
     * reduce or cancel it. This is not a confirmation that damage was applied.
     */
    public double getRequestedDamage() {
        return this.damage;
    }

    public int getStacks() {
        return this.stacks;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
