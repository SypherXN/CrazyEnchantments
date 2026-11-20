package com.badbones69.crazyenchantments.paper.api.utils;

public record McMMOXpSettings(double skillingPerLevelPp, double trainingPerLevelPp) {

    static final double DEFAULT_PER_LEVEL_PP = 3.0;
    public static final McMMOXpSettings DEFAULTS = new McMMOXpSettings(DEFAULT_PER_LEVEL_PP, DEFAULT_PER_LEVEL_PP);

    public McMMOXpSettings {
        check(skillingPerLevelPp);
        check(trainingPerLevelPp);
    }

    private static void check(final double rate) {
        if (!(rate >= 0 && rate <= McMMOXpLogic.MAX_BONUS_PP)) {
            throw new IllegalArgumentException("Invalid mcMMO XP bonus per level: " + rate);
        }
    }
}
