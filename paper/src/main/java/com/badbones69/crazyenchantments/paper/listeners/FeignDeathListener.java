package com.badbones69.crazyenchantments.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The annotated {@link EventPriority#HIGHEST} trigger sees sword multipliers
 * and cancels eligible hits before armor's {@link EventPriority#MONITOR} observers.
 *
 * <p>The plugin also registers this listener explicitly at MONITOR after
 * Double Strike, bow splash and Creeper Armor's folded-splash reduction, to
 * check hits that only reach the threshold after those adjustments.</p>
 */
public class FeignDeathListener implements Listener {

    // No entry: no HIGHEST decision. False: decided. True: eligible but not yet at the threshold.
    private final Map<EntityDamageEvent, Boolean> retryByEvent = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Predicate<EntityDamageEvent> trigger;

    public FeignDeathListener(final Predicate<EntityDamageEvent> trigger) {
        this.trigger = trigger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) this.retryByEvent.put(event, this.trigger.test(event));
    }

    // Registered with ignoreCancelled = true, after the folds and reduction.
    public void onDamageResolved(final EntityDamageEvent event) {
        if (Boolean.TRUE.equals(this.retryByEvent.remove(event))) this.trigger.test(event);
    }

    // The cancelled dispatches skip onDamageResolved but must still be cleared.
    public void clearDamage(final EntityDamageEvent event) {
        this.retryByEvent.remove(event);
    }
}
