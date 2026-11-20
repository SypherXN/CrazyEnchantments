package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Immutable snapshot of the fork-only enchant effect settings. */
public record ForkEnchantSettings(
        Map<String, Aura> auras, double maxAuraRadius, Crouch crouch, Nursery nursery,
        Summon beeKeeper, Summon infestation, Summon tamer, int hulkDurationTicks,
        BattleCry battleCry, Cursed cursed, double rektMultiplier, TreeFeller treeFeller,
        Boom boom, double pullStrength, MultiArrow multiArrow, int stickyShotDurationTicks,
        VeinMiner veinMiner, int greenThumbDurabilityPoints, Effect famished, double fastTurnBonusDivisor,
        int chargeBaseRadius, int revengeRadius
) {
    static final ForkEnchantSettings DEFAULTS = load(path -> null, warning -> {});

    public record Aura(double radius, int durationTicks, int amplifier, boolean scalesWithLevel) {
        public int amplifierAt(int level) {
            return this.scalesWithLevel ? this.amplifier + (level - 1) : this.amplifier;
        }
    }

    public record Crouch(double baseReductionPp, double reductionPerLevelPp) {
        public double reduction(int level) {
            return (this.baseReductionPp + this.reductionPerLevelPp * level) / 100.0;
        }
    }

    public record Nursery(double healHp, int intervalTicks) {}
    public record Summon(int countPerLevel, int durationSeconds) {}
    public record BattleCry(double radius, double strength, double verticalOffset) {}
    public record Cursed(int baseDurationSeconds, int amplifier) {
        public int durationTicks(int level) {
            return (level + this.baseDurationSeconds) * 20;
        }
    }

    public record Effect(int durationTicks, int amplifier) {}
    public record TreeFeller(int horizontalRadius, int logLimitPerLevel) {}
    public record Boom(double radius, double damageHp, double knockbackStrength, double knockbackUpward) {}
    public record MultiArrow(int extraArrowsPerLevel, float spread) {}
    public record VeinMiner(int searchDepthPerLevel, int maxBlocks) {}

    static ForkEnchantSettings load(Function<String, Object> get, Consumer<String> warn) {
        final Values read = new Values(get, warn);
        final Map<String, Aura> auras = Map.of(
                "Acid Rain", read.aura("Acid Rain", 80, 1, false),
                "Blizzard", read.aura("Blizzard", 100, 0, true),
                "Intimidate", read.aura("Intimidate", 60, 0, true),
                "Radiant", read.aura("Radiant", 100, 0, false),
                "Sand Storm", read.aura("Sand Storm", 200, 0, false));
        final double maxAuraRadius = auras.values().stream().mapToDouble(Aura::radius).max().orElseThrow();

        return new ForkEnchantSettings(
                auras, maxAuraRadius,
                new Crouch(read.number("Crouch", "Base-Reduction-PP", 10, 0, 100),
                        read.number("Crouch", "Reduction-Per-Level-PP", 5, 0, 100)),
                new Nursery(read.number("Nursery", "Heal-HP", 1, 0, 100),
                        read.integer("Nursery", "Interval-Ticks", 20, 1, 12000)),
                read.summon("Bee Keeper", 1), read.summon("Infestation", 3), read.summon("Tamer", 1),
                read.integer("Hulk", "Duration-Ticks", -1, 1, 12000),
                new BattleCry(read.radius("Battle Cry"), read.number("Battle Cry", "Knockback-Strength", 1, 0, 10),
                        read.number("Battle Cry", "Vertical-Offset", .5, 0, 10)),
                new Cursed(read.integer("Cursed", "Base-Duration-Seconds", 9, 0, 600), read.amplifier("Cursed", 1)),
                read.multiplier("Rekt"),
                new TreeFeller(read.integer("Tree Feller", "Horizontal-Radius", 5, 1, 10),
                        read.integer("Tree Feller", "Log-Limit-Per-Level", 5, 1, 64)),
                new Boom(read.radius("Boom"), read.number("Boom", "Damage-HP", 5, 0, 100),
                        read.number("Boom", "Knockback-Strength", 1, 0, 10),
                        read.number("Boom", "Knockback-Upward", .5, 0, 10)),
                read.number("Pull", "Pull-Strength", 3, 0, 10),
                new MultiArrow(read.integer("Multi Arrow", "Extra-Arrows-Per-Level", 1, 0, 8),
                        (float) read.number("Multi Arrow", "Spread", .2, 0, 2)),
                read.integer("Sticky Shot", "Web-Duration-Ticks", 100, 1, 12000),
                new VeinMiner(read.integer("Vein Miner", "Search-Depth-Per-Level", 1, 1, 2),
                        read.integer("Vein Miner", "Max-Blocks", 0, 0, 4096)),
                read.integer("Green Thumb", "Durability-Points", 1, 0, 16),
                new Effect(read.integer("Famished", "Duration-Ticks", 200, 1, 12000), read.amplifier("Famished", 1)),
                read.number("Fast Turn", "Bonus-Damage-Divisor", 3, .1, 1000),
                read.integer("Charge", "Base-Radius", 4, 0, 16),
                read.integer("Revenge", "Radius", 10, 1, 16));
    }

    private record Values(Function<String, Object> get, Consumer<String> warn) {
        private Aura aura(String enchant, int duration, int amplifier, boolean scales) {
            return new Aura(radius(enchant), integer(enchant, "Duration-Ticks", duration, 1, 12000),
                    enchant.equals("Radiant") ? amplifier : amplifier(enchant, amplifier), scales);
        }

        private Summon summon(String enchant, int count) {
            return new Summon(integer(enchant, "Count-Per-Level", count, 1, 8),
                    integer(enchant, "Duration-Seconds", 60, 1, 600));
        }

        private double radius(String enchant) {
            return number(enchant, "Radius", 3, .5, 10);
        }

        private int amplifier(String enchant, int fallback) {
            return integer(enchant, "Amplifier", fallback, 0, 255);
        }

        private double multiplier(String enchant) {
            return number(enchant, "Damage-Multiplier", 2, 0, 10);
        }

        private double number(String enchant, String key, double fallback, double min, double max) {
            return read(enchant, key, fallback, min, max, false);
        }

        private int integer(String enchant, String key, int fallback, int min, int max) {
            return (int) read(enchant, key, fallback, min, max, true);
        }

        private double read(String enchant, String key, double fallback, double min, double max, boolean integral) {
            final String path = "Enchantments." + enchant + ".Tuning." + key;
            final Object value = this.get.apply(path);
            if (value == null) return fallback;

            if (value instanceof Number number) {
                final double result = number.doubleValue();
                if (result == fallback || (result >= min && result <= max && (!integral || result == Math.rint(result)))) {
                    return result == 0.0 ? 0.0 : result; // Normalize signed zero.
                }
            }

            this.warn.accept("Invalid " + path + " value '" + value + "'; using " + fallback + ".");
            return fallback;
        }
    }
}
