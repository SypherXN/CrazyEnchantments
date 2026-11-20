package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Aegis: always-on armor enchant (no chance system). Pure data and math, no
 * Bukkit types.
 *
 * <p>Each wearer owns a map of distinct attacker UUID to that attacker's hit
 * times (nanoTime) inside the window. Attackers are ranked by their FIRST hit
 * still inside the window (earliest first, UUID as the deterministic tie break).
 * With {@code count} distinct attackers and the threshold
 * {@code THRESHOLD_BASE - level}, nothing happens until {@code count > threshold}
 * (strict). Every attacker ranked beyond the first {@code threshold} then has its
 * hit multiplied by {@code damageMultiplier}, once.</p>
 *
 * <p>Anti Gank keeps a private map of the <em>latest</em> hit per attacker and is
 * outside this change's write scope, so it cannot answer "first hit inside the
 * window". This is the one small shared tracker for Aegis; Anti Gank's map is
 * not duplicated or touched.</p>
 *
 * <p>An entry older than the window is expired (a hit exactly
 * {@code windowSeconds} old is already gone, matching Anti Gank) and is dropped
 * whenever the map is read or written.</p>
 */
public record AegisSettings(double windowSeconds, double damageMultiplier) {

    public static final int MAX_LEVEL = 6;

    /** The threshold at level L is {@code THRESHOLD_BASE - L} attackers; a hit needs strictly more. */
    public static final int THRESHOLD_BASE = 8;

    /** Hit times kept per attacker; extra hits inside one window add nothing but memory. */
    public static final int MAX_HITS_PER_ATTACKER = 256;

    /** Distinct attackers kept per wearer; the same bound Anti Gank uses for its own history. */
    public static final int MAX_ATTACKERS = TuningConfig.MAX_ANTI_GANK_HISTORY;

    public static final AegisSettings DEFAULTS = new AegisSettings(5.0, 0.5);

    public AegisSettings {
        windowSeconds = TuningConfig.BowValues.checkedNumber(windowSeconds, 0.001, 600.0);
        damageMultiplier = TuningConfig.BowValues.checkedNumber(damageMultiplier, 0.0, 1.0);
    }

    public long windowNanos() {
        return Math.round(this.windowSeconds * 1_000_000_000.0);
    }

    /**
     * Distinct attackers that stay unaffected at {@code level} (2 at VI).
     */
    public int threshold(final int level) {
        return THRESHOLD_BASE - Math.clamp(level, 1, MAX_LEVEL);
    }

    /**
     * Whether the hit of an attacker ranked {@code rank} (1 = earliest) among
     * {@code count} distinct attackers is halved. Strict: needs
     * {@code count > threshold} and a rank beyond it.
     */
    public boolean affects(final int level, final int count, final int rank) {
        if (level < 1) return false;

        final int threshold = threshold(level);

        return count > threshold && rank > threshold;
    }

    /**
     * The damage multiplier for one incoming hit: {@code damageMultiplier} when
     * {@link #affects}, otherwise 1. Applied once; it never stacks with itself.
     */
    public double multiplier(final int level, final int count, final int rank) {
        return affects(level, count, rank) ? this.damageMultiplier : 1.0;
    }

    /**
     * Records a landed hit by {@code attacker} at {@code nowNanos} and drops
     * expired hits. Only {@link #MAX_ATTACKERS} distinct attackers are kept: a new
     * attacker evicts the one whose first in-window hit is the earliest, so the map
     * can never grow with a long fight.
     */
    public void record(final Map<UUID, Deque<Long>> hits, final UUID attacker, final long nowNanos) {
        prune(hits, nowNanos);

        if (!hits.containsKey(attacker) && hits.size() >= MAX_ATTACKERS) evictOldest(hits);

        final Deque<Long> times = hits.computeIfAbsent(attacker, id -> new ConcurrentLinkedDeque<>());

        if (times.size() < MAX_HITS_PER_ATTACKER) times.addLast(nowNanos);
    }

    /**
     * Distinct unexpired attackers, counting {@code attacker} even when it has
     * not been recorded yet. Expired hits are dropped first.
     */
    public int count(final Map<UUID, Deque<Long>> hits, final UUID attacker, final long nowNanos) {
        return reach(hits, attacker, nowNanos).count();
    }

    /**
     * 1-based rank of {@code attacker} among the distinct unexpired attackers,
     * ordered by each one's first hit inside the window. An attacker that has
     * not hit yet counts as hitting now, so it ranks last. Expired hits are
     * dropped first, so an attacker whose old hit expired is ranked by its first
     * hit that is still inside the window.
     */
    public int rank(final Map<UUID, Deque<Long>> hits, final UUID attacker, final long nowNanos) {
        return reach(hits, attacker, nowNanos).rank();
    }

    /**
     * Count and rank in one pass over the history: both are needed for the same
     * hit, and pruning twice per hit is pure waste.
     */
    public Reach reach(final Map<UUID, Deque<Long>> hits, final UUID attacker, final long nowNanos) {
        prune(hits, nowNanos);

        final Deque<Long> own = hits.get(attacker);
        final long ownFirst = own == null || own.isEmpty() ? nowNanos : own.peekFirst();

        int count = 0;
        int rank = 1;

        for (final Map.Entry<UUID, Deque<Long>> entry : hits.entrySet()) {
            final Long first = entry.getValue().peekFirst();

            if (first == null) continue;

            count++;

            if (entry.getKey().equals(attacker)) continue;

            // Signed difference keeps the comparison correct across nanoTime wrap.
            final long delta = first - ownFirst;

            if (delta < 0 || (delta == 0 && entry.getKey().compareTo(attacker) < 0)) rank++;
        }

        // An attacker that has not hit yet is not in the map, but it is still one of
        // the distinct attackers for the hit being decided.
        if (own == null || own.isEmpty()) count++;

        return new Reach(count, rank);
    }

    /**
     * The attackers ordered by first in-window hit, earliest first. For tests
     * and diagnostics; the hot path only needs {@link #rank}.
     */
    public List<UUID> ranking(final Map<UUID, Deque<Long>> hits, final long nowNanos) {
        prune(hits, nowNanos);

        final List<UUID> order = new ArrayList<>(hits.keySet());

        order.sort((a, b) -> {
            final long delta = hits.get(a).peekFirst() - hits.get(b).peekFirst();

            if (delta != 0) return delta < 0 ? -1 : 1;

            return a.compareTo(b);
        });

        return order;
    }

    private void prune(final Map<UUID, Deque<Long>> hits, final long nowNanos) {
        final long window = windowNanos();

        for (final Deque<Long> times : hits.values()) {
            while (true) {
                final Long oldest = times.peekFirst();

                if (oldest == null || nowNanos - oldest < window) break;

                times.pollFirst();
            }
        }

        hits.values().removeIf(Deque::isEmpty);
    }

    /**
     * Drops the attacker whose first in-window hit is the earliest, making room for a
     * new one when the map is full. Ties break on the UUID, like the ranking.
     */
    private void evictOldest(final Map<UUID, Deque<Long>> hits) {
        UUID oldest = null;
        long oldestFirst = 0L;

        for (final Map.Entry<UUID, Deque<Long>> entry : hits.entrySet()) {
            final Long first = entry.getValue().peekFirst();

            if (first == null) continue;

            if (oldest == null || first - oldestFirst < 0 || (first == oldestFirst && entry.getKey().compareTo(oldest) < 0)) {
                oldest = entry.getKey();
                oldestFirst = first;
            }
        }

        if (oldest != null) hits.remove(oldest);
    }

    /** Count and rank of one attacker for one hit, both from a single pass. */
    public record Reach(int count, int rank) {}
}
