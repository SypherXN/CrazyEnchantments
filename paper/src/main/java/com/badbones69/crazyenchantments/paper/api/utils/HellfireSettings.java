package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.ArrayList;
import java.util.List;

public record HellfireSettings(double radius, double baseDamageHp, double damagePerLevelHp, double igniteSeconds) {

    public static final HellfireSettings DEFAULTS = new HellfireSettings(2.0, 1.5, 0.25, 3.0);

    public HellfireSettings {
        TuningConfig.BowValues.checkedNumber(radius, 0.5, 16.0);
        TuningConfig.BowValues.checkedNumber(baseDamageHp, 0.0, 100.0);
        TuningConfig.BowValues.checkedNumber(damagePerLevelHp, 0.0, 100.0);
        TuningConfig.BowValues.checkedNumber(igniteSeconds, 0.0, 600.0);
    }

    public double damageHp(final int level) {
        return this.baseDamageHp + this.damagePerLevelHp * (Math.clamp(level, 1, 5) - 1);
    }

    public int fireTicks() {
        return (int) Math.round(this.igniteSeconds * 20.0);
    }

    public static final class Window {

        public static final Window EMPTY = new Window(List.of());

        private final List<Long> recent;

        private Window(final List<Long> recent) {
            this.recent = List.copyOf(recent);
        }

        /** Returns this window on denial, or a new window with the accepted claim. */
        public Window claim(final long nowMillis) {
            int first = 0;
            while (first < this.recent.size() && nowMillis - this.recent.get(first) >= 1_000L) first++;
            if (this.recent.size() - first >= 5) return this;

            final List<Long> updated = new ArrayList<>(this.recent.subList(first, this.recent.size()));
            updated.add(nowMillis);
            return new Window(updated);
        }

        public boolean expired(final long nowMillis) {
            return this.recent.isEmpty() || nowMillis - this.recent.getLast() >= 1_000L;
        }
    }
}
