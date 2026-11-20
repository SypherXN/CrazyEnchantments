package com.badbones69.crazyenchantments.paper.api.utils;

import java.util.function.Consumer;

/**
 * Auto Sell tunables. The message supports {@code %items%} and {@code %price%}; an empty message
 * sends nothing.
 */
public record AutoSellSettings(String message) {

    public static final String DEFAULT_MESSAGE = "&aAuto Sell: &e%items% &aitems for &6$%price%&a.";
    static final int MAX_MESSAGE_LENGTH = 256;

    public static final AutoSellSettings DEFAULTS = new AutoSellSettings(DEFAULT_MESSAGE);

    public AutoSellSettings {
        if (message == null || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Invalid Auto Sell message: " + message);
        }
    }

    /** A missing key keeps the default silently; a present value that is not a usable string warns. */
    public static AutoSellSettings validated(final Object message, final Consumer<String> warn) {
        if (message == null) return DEFAULTS;

        if (message instanceof String text && text.length() <= MAX_MESSAGE_LENGTH) return new AutoSellSettings(text);

        warn.accept("Invalid CosmicEnchants.AutoSell.Message value '" + message + "'; using the default.");

        return DEFAULTS;
    }
}
