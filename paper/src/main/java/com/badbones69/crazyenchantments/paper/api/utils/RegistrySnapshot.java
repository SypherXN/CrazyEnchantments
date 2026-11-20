package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Publishes the ordered registry and its name index in one volatile swap. */
public final class RegistrySnapshot<T> {

    private record Generation<T>(List<T> entries, Map<String, T> byName) {}

    private final Function<T, String> name;
    private volatile Generation<T> generation = new Generation<>(List.of(), Map.of());

    public RegistrySnapshot(final Function<T, String> name) {
        this.name = name;
    }

    public List<T> entries() {
        return this.generation.entries();
    }

    public T byName(final String name) {
        return this.generation.byName().get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized void publish(final List<T> entries) {
        final List<T> copy = List.copyOf(entries);
        final Map<String, T> byName = new HashMap<>();
        for (final T entry : copy) byName.putIfAbsent(this.name.apply(entry).toLowerCase(Locale.ROOT), entry);

        this.generation = new Generation<>(copy, Map.copyOf(byName));
    }

    public synchronized void add(final T entry) {
        final List<T> next = new ArrayList<>(entries());
        next.add(entry);
        publish(next);
    }

    public synchronized void remove(final T entry) {
        final List<T> next = new ArrayList<>(entries());
        if (next.remove(entry)) publish(next);
    }
}
