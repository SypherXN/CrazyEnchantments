package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.Objects;
import java.util.UUID;

/**
 * One name tag click, from typing a name to confirming it.
 *
 * <p>Nothing here touches Bukkit. The listener owns the items and asks the
 * session whether a step is still allowed, so a stale or repeated confirmation
 * is rejected before anything is written.</p>
 */
public final class NameTagSession {

    public enum Phase {
        AWAITING_NAME,
        AWAITING_CONFIRM
    }

    public enum Result {
        OK,
        BAD_TOKEN,
        NOT_READY,
        STALE,
        ALREADY_CLAIMED
    }

    /** Vanilla anvils cap item names at 50; this leaves room for colour codes. */
    public static final int MAX_NAME_LENGTH = 64;

    private final UUID token;
    private final int slot;
    private final String gear;
    private Phase phase = Phase.AWAITING_NAME;
    private String pendingName;
    private boolean claimed;

    public NameTagSession(final UUID token, final int slot, final String gear) {
        this.token = Objects.requireNonNull(token, "token");
        this.slot = slot;
        this.gear = gear;
    }

    public UUID getToken() {
        return this.token;
    }

    public int getSlot() {
        return this.slot;
    }

    public Phase getPhase() {
        return this.phase;
    }

    public String getPendingName() {
        return this.pendingName;
    }

    public boolean isClaimed() {
        return this.claimed;
    }

    public boolean tokenMatches(final UUID candidate) {
        return this.token.equals(candidate);
    }

    public boolean gearMatches(final String liveGear) {
        return Objects.equals(this.gear, liveGear);
    }

    /**
     * Stores the name the player typed, ready for confirmation.
     */
    public Result submit(final UUID candidate, final String name) {
        if (this.claimed) return Result.ALREADY_CLAIMED;
        if (!tokenMatches(candidate)) return Result.BAD_TOKEN;
        if (name == null || name.isEmpty()) return Result.NOT_READY;

        this.pendingName = name;
        this.phase = Phase.AWAITING_CONFIRM;

        return Result.OK;
    }

    /**
     * Claims the session exactly once. A second call, a changed item or a
     * different token never succeeds, so two confirm clicks cannot rename twice.
     */
    public Result claim(final UUID candidate, final String liveGear) {
        if (this.claimed) return Result.ALREADY_CLAIMED;
        if (!tokenMatches(candidate)) return Result.BAD_TOKEN;
        if (this.phase != Phase.AWAITING_CONFIRM || this.pendingName == null) return Result.NOT_READY;
        if (!gearMatches(liveGear)) return Result.STALE;

        this.claimed = true;

        return Result.OK;
    }

    public boolean cancel(final UUID candidate) {
        return !this.claimed && tokenMatches(candidate);
    }

    /**
     * Trimmed input, or null when it is blank or only colour codes.
     */
    public static String clean(final String raw) {
        if (raw == null) return null;

        final String value = raw.trim();

        if (value.isEmpty()) return null;
        if (ColorUtils.stripStringColour(value).trim().isEmpty()) return null;

        return value;
    }

    public static boolean tooLong(final String raw) {
        return raw != null && raw.trim().length() > MAX_NAME_LENGTH;
    }

    /**
     * Keeps the Transmog count suffix the item already shows, using the same
     * renderer as the scroll so a config written with {@code §} or upper-case
     * hex still matches. The entered name is stripped first so the count is not
     * stacked twice.
     *
     * @param entered The name the player typed, in legacy form.
     * @param existing The item's current visible name, in legacy form.
     * @param template The configured suffix, e.g. {@code " &7[&6&n%amount%&7]"}.
     * @param enabled Whether the suffix is enabled in the config.
     * @return The name to preview and write.
     */
    public static String withSuffix(final String entered, final String existing, final String template, final boolean enabled) {
        if (!enabled || template == null || template.isEmpty() || existing == null) return entered;

        final String base = LoreLines.withoutSuffix(existing, template);

        if (base.equals(existing)) return entered;

        return LoreLines.withoutSuffix(entered, template) + existing.substring(base.length());
    }
}
