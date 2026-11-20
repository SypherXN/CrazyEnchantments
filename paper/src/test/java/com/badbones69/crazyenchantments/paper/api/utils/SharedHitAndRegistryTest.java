package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SharedHitAndRegistryTest {

    @Test
    void syntheticResultRequiresAnAcceptedEventAndNetLossIncludingAbsorption() {
        final var event = new EntityDamageEvent(null, DamageCause.ENTITY_ATTACK, null, 5.0);
        assertTrue(SyntheticHit.landed(20.0, 15.0, null, event));
        assertTrue(SyntheticHit.landed(24.0, 22.0, null, event), "a hit can spend absorption alone");
        assertFalse(SyntheticHit.landed(20.0, 15.0, event, event), "a stale event can't confirm a hit");
        assertFalse(SyntheticHit.landed(20.0, 20.0, null, event), "no health or absorption was lost");

        event.setCancelled(true);
        assertFalse(SyntheticHit.landed(24.0, 22.0, null, event), "other changes during a cancelled hit do not count");
    }

    @Test
    void publicationReplacesTheWholeRegistryWithoutChangingAnExistingReader() throws Exception {
        final RegistrySnapshot<String> registry = new RegistrySnapshot<>(name -> name);
        registry.publish(List.of("Old One", "Old Two"));
        final var reading = new CountDownLatch(1);
        final var published = new CountDownLatch(1);
        final var reader = new FutureTask<>(() -> {
            final var previous = registry.entries();
            reading.countDown();
            assertTrue(published.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("Old One", "Old Two"), previous);
            assertEquals(List.of("New One", "New Two"), registry.entries());
            assertNull(registry.byName("Old One"));
            assertEquals("New Two", registry.byName("NEW TWO"));
            assertThrows(UnsupportedOperationException.class, () -> previous.clear());
            return null;
        });
        Thread.ofVirtual().start(reader);

        assertTrue(reading.await(5, TimeUnit.SECONDS));
        final var next = new ArrayList<>(List.of("New One", "New Two"));
        registry.publish(next);
        next.clear();
        published.countDown();
        reader.get(5, TimeUnit.SECONDS);
    }
}
