package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Set;

public final class McMMOXpLogic {

    public static final double MAX_BONUS_PP = 30.0;
    private static final int MAX_LEVEL = 10;
    private static final Set<String> SKILLING_SKILLS = Set.of("MINING", "WOODCUTTING", "HERBALISM", "EXCAVATION", "FISHING");
    private static final Set<String> TRAINING_SKILLS = Set.of("SWORDS", "AXES", "ARCHERY", "UNARMED", "TAMING");

    /**
     * The {@code XPGainReason} names of an award the player earned by playing,
     * for either enchant: gathering (Skilling) and combat (Training).
     *
     * <p>mcMMO 2.2.049 traced through the jar:</p>
     * <ul>
     *   <li>gathering: every XP site in {@code WoodcuttingManager},
     *       {@code MiningManager}, {@code HerbalismManager},
     *       {@code ExcavationManager} and {@code FishingManager} goes through
     *       {@code SkillManager.applyXpGain(..., PVE, SELF)};</li>
     *   <li>combat: {@code CombatUtils.processCombatXP} picks {@code PVE} for a
     *       mob and {@code PVP} for a player;</li>
     *   <li>a party sharing XP equally replaces both with
     *       {@code SHARED_PVE}/{@code SHARED_PVP} for <em>every</em> member, the
     *       attacker included, and skips that player's own unshared award
     *       ({@code McMMOPlayer.beginXpGain} → {@code ShareHandler.handleXpShare}
     *       / {@code getSharedXpGainReason}). Those are the same award, so they
     *       count too.</li>
     * </ul>
     *
     * <p>Excluded on purpose: {@code COMMAND} ({@code /addxp}), {@code UNKNOWN}
     * (mcMMO's {@code ExperienceAPI} and other plugins) and {@code VAMPIRISM}
     * ({@code EventUtils.handleVampirismEvent} pays a second award for the same
     * PvP kill). Boosting those would pay twice or pay for someone else's
     * award.</p>
     */
    private static final Set<String> OWN_GAMEPLAY_REASONS = Set.of("PVE", "PVP", "SHARED_PVE", "SHARED_PVP");

    private McMMOXpLogic() {}

    /**
     * @param reason an {@code XPGainReason} name
     * @return true when this award came from the player's own gathering or combat
     */
    public static boolean isOwnGameplayAward(final String reason) {
        return OWN_GAMEPLAY_REASONS.contains(reason);
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
