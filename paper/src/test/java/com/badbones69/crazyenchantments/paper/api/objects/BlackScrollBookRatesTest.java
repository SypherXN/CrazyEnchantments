package com.badbones69.crazyenchantments.paper.api.objects;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The books pulled out of a black scroll take their success and destroy rates from
 * {@code Settings.BlackScroll}, over the full 0-100 inclusive range, and each rate is its own draw.
 */
class BlackScrollBookRatesTest {

    /** Hands out the queued values and records the bound of every draw. */
    private static final class ScriptedRandom extends Random {

        private final Deque<Integer> values = new ArrayDeque<>();
        private final List<Integer> bounds = new ArrayList<>();

        private ScriptedRandom(final Integer... values) {
            this.values.addAll(List.of(values));
        }

        @Override
        public int nextInt(final int bound) {
            this.bounds.add(bound);

            return this.values.removeFirst();
        }
    }

    @Test
    void eachRateReachesBothEnds() {
        final ScriptedRandom random = new ScriptedRandom(0, 100);

        // Both ends are reachable, so the roll is 0-100 inclusive rather than 0-99.
        assertEquals(0, CEBook.rollRate(0, 100, random));
        assertEquals(100, CEBook.rollRate(0, 100, random));
        // One draw of 101 values per rate, never a shared value.
        assertEquals(List.of(101, 101), random.bounds);
    }

    @Test
    void invalidBoundsAreSwappedAndClamped() {
        final ScriptedRandom random = new ScriptedRandom(0, 100);

        // Inverted and out-of-range bounds still yield a real percent instead of throwing.
        assertEquals(0, CEBook.rollRate(150, -10, random));
        assertEquals(100, CEBook.rollRate(150, -10, random));
        assertEquals(List.of(101, 101), random.bounds);
    }

    @Test
    void shippedConfigCoversTheFullRange() throws IOException {
        final Map<?, ?> config = new Yaml().load(read("src/main/resources/config.yml"));

        assertEquals(0, value(config, "Settings", "BlackScroll", "SuccessChance", "Min"));
        assertEquals(100, value(config, "Settings", "BlackScroll", "SuccessChance", "Max"));
        assertEquals(0, value(config, "Settings", "BlackScroll", "DestroyChance", "Min"));
        assertEquals(100, value(config, "Settings", "BlackScroll", "DestroyChance", "Max"));
    }

    private static Object value(final Map<?, ?> root, final String... path) {
        Object node = root;

        for (final String key : path) {
            node = ((Map<?, ?>) node).get(key);
        }

        return node;
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative));
    }
}
