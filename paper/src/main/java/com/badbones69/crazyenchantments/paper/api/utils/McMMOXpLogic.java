package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Set;

public final class McMMOXpLogic {

    public static final double MAX_BONUS_PP = 30.0;
    private static final int MAX_LEVEL = 10;
    private static final Set<String> SKILLING_SKILLS = Set.of("MINING", "WOODCUTTING", "HERBALISM", "EXCAVATION", "FISHING");
    private static final Set<String> TRAINING_SKILLS = Set.of("SWORDS", "AXES", "ARCHERY", "UNARMED", "TAMING");

    /**
     * The {@code XPGainReason} names of an award the player earned by playing.
     *
     * <p>mcMMO awards combat as {@code PVE}/{@code PVP}; once a party shares XP
     * equally it substitutes {@code SHARED_PVE}/{@code SHARED_PVP} for <em>every</em>
     * member, the attacker included, and skips that player's own unshared award
     * (mcMMO 2.2.049: {@code McMMOPlayer.beginXpGain} →
     * {@code ShareHandler.handleXpShare} / {@code getSharedXpGainReason}). All
     * four are the same fight, so all four count. {@code COMMAND},
     * {@code UNKNOWN} (generic API and other plugins) and {@code VAMPIRISM} (a
     * second award for a PvP kill) stay out: boosting those would pay twice or
     * pay for someone else's award.</p>
     */
    private static final Set<String> DIRECT_COMBAT_REASONS = Set.of("PVE", "PVP", "SHARED_PVE", "SHARED_PVP");

    private McMMOXpLogic() {}

    /**
     * @param reason an {@code XPGainReason} name
     * @return true when this award came from the player's own combat
     */
    public static boolean isDirectCombatReason(final String reason) {
        return DIRECT_COMBAT_REASONS.contains(reason);
    }

    public static boolean isSkillingSkill(final String skill) {
        return SKILLING_SKILLS.contains(skill);
    }

    public static boolean isTrainingSkill(final String skill) {
        return TRAINING_SKILLS.contains(skill);
    }

    public static int heldLevel(final int mainHandLevel, final int offHandLevel, final int configuredMaxLevel) {
        return Math.max(0, Math.min(configuredMaxLevel, Math.max(mainHandLevel, offHandLevel)));
    }

    public static double bonusPercent(final boolean eligible, final int level, final double perLevelPp) {
        if (!eligible || level <= 0) return 0.0;

        return Math.min(MAX_BONUS_PP, Math.min(MAX_LEVEL, level) * perLevelPp);
    }

    public static double combinedBonusPercent(final double skillingPp, final double trainingPp) {
        return Math.min(MAX_BONUS_PP, skillingPp + trainingPp);
    }

    public static float applyBonus(final float rawXp, final double bonusPp) {
        if (rawXp <= 0 || !Float.isFinite(rawXp) || bonusPp <= 0) return rawXp;

        final float boosted = (float) (rawXp * (1.0 + bonusPp / 100.0));
        return Float.isFinite(boosted) ? boosted : rawXp;
    }
}
