package com.badbones69.crazyenchantments.paper.api.enums.pdc;

import java.util.Map;

/**
 * The Sniper rename, applied once to a stored payload that predates it.
 *
 * <p>Before the rename the poison enchant stored its level under "Sniper" and
 * the headshot enchant under "SniperHeadshot". The headshot now owns "Sniper",
 * so an unversioned "Sniper" key means poison and becomes "Poison Shot", while
 * a versioned "Sniper" means the headshot. Those two readings are otherwise
 * indistinguishable, so every payload written from now on carries
 * {@link #CURRENT_VERSION} and only a payload without it is translated.</p>
 *
 * <p>The version lives inside the payload rather than in a separate item marker
 * so the map and the vocabulary that reads it can never drift apart: the same
 * write that stores a translated map also stamps it. That is why reading an
 * untranslated item needs no write at all.</p>
 *
 * <p>A single rename exception, not a general alias system.</p>
 */
final class EnchantRenames {

    /** The payload version that reads every enchant name as it is today. */
    static final int CURRENT_VERSION = 1;

    /** Old stored key -> the enchant that key means today. */
    private static final Map<String, String> RENAMED = Map.of(
            "Sniper", "Poison Shot", // the poison enchant has its own name now
            "SniperHeadshot", "Sniper", // the headshot enchant took "Sniper"
            "Sniper_Headshot", "Sniper"); // the underscored spelling some dumps show

    private EnchantRenames() {}

    /**
     * @param stored The enchant name as an old payload wrote it.
     * @return The name that enchant has today.
     */
    static String current(final String stored) {
        if (stored == null) return null;

        return RENAMED.getOrDefault(stored, stored);
    }
}
