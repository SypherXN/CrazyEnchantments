package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Validated tuning for the damage-modifier enchants (Armored, Heavy, Tank,
 * Marksman, Deathbringer). Values are FORK defaults from the CosmicPvP
 * balance sheet (S04); none of them are historical CosmicPvP numbers.
 * Deathbringer's multiplier is fixed at 1.5x by task #70 and is not tunable.
 */
public record DamageModifierSettings(
        double incomingReductionCapPp,
        double bonusDamageCapHp,
        double armoredReductionPerLevelPp,
        double heavyReductionPerLevelPp,
        double tankReductionPerLevelPp,
        double marksmanBonusPerLevelPp,
        double marksmanBonusCapPp,
        double strengthDamagePerLevel
) {

    public static final DamageModifierSettings DEFAULTS = new DamageModifierSettings(
            80.0,
            0.0,
            1.85,
            2.0,
            1.85,
            1.5,
            25.0,
            1.0
    );

    public static DamageModifierSettings validated(
            final double incomingReductionCapPp,
            final double bonusDamageCapHp,
            final double armoredReductionPerLevelPp,
            final double heavyReductionPerLevelPp,
            final double tankReductionPerLevelPp,
            final double marksmanBonusPerLevelPp,
            final double marksmanBonusCapPp,
            final double strengthDamagePerLevel,
            final Consumer<String> warn
    ) {
        final DamageModifierSettings fallback = DEFAULTS;

        return new DamageModifierSettings(
                number("CosmicEnchants.DamageModifiers.Incoming-Reduction-Cap-PP", incomingReductionCapPp, fallback.incomingReductionCapPp(), 0.0, 100.0, warn),
                number("CosmicEnchants.DamageModifiers.Bonus-Damage-Cap-HP", bonusDamageCapHp, fallback.bonusDamageCapHp(), 0.0, 100.0, warn),
                number("CosmicEnchants.Armored.Reduction-Per-Level-PP", armoredReductionPerLevelPp, fallback.armoredReductionPerLevelPp(), 0.0, 100.0, warn),
                number("CosmicEnchants.Heavy.Reduction-Per-Level-PP", heavyReductionPerLevelPp, fallback.heavyReductionPerLevelPp(), 0.0, 100.0, warn),
                number("CosmicEnchants.Tank.Reduction-Per-Level-PP", tankReductionPerLevelPp, fallback.tankReductionPerLevelPp(), 0.0, 100.0, warn),
                number("CosmicEnchants.Marksman.Bonus-Per-Level-PP", marksmanBonusPerLevelPp, fallback.marksmanBonusPerLevelPp(), 0.0, 100.0, warn),
                number("CosmicEnchants.Marksman.Bonus-Cap-PP", marksmanBonusCapPp, fallback.marksmanBonusCapPp(), 0.0, 100.0, warn),
                number("CosmicEnchants.DamageModifiers.Strength-Damage-Per-Level", strengthDamagePerLevel, fallback.strengthDamagePerLevel(), 0.0, 3.0, warn)
        );
    }

    private static double number(
            final String path,
            final double value,
            final double fallback,
            final double min,
            final double max,
            final Consumer<String> warn
    ) {
        if (Double.isNaN(value) || value < min || value > max) {
            if (warn != null) {
                warn.accept("Invalid " + path + " value '" + value + "'; using " + fallback + ".");
            }

            return fallback;
        }

        return value;
    }
}