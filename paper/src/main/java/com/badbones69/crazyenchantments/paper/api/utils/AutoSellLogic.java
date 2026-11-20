package com.badbones69.crazyenchantments.paper.api.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Bukkit-free and EconomyShopGUI-free decision logic for Auto Sell, kept apart from the
 * gated {@code AutoSellSupport} so it stays on the test classpath.
 */
public final class AutoSellLogic {

    /** Highest level of Auto Sell; the enchant is always-on with no chance system. */
    public static final int MAX_LEVEL = 1;

    private AutoSellLogic() {}

    /**
     * Neutral hook {@code PickaxeEnchantments} calls first for every drop event, so nothing in that class
     * has to know which shop plugin prices the drops. Generic so no Bukkit type is needed here.
     */
    @FunctionalInterface
    public interface DropSeller<E> {

        void sell(E event);
    }

    /** Prices and pays for individual drops. */
    public interface Appraiser<D> {

        /** Price of the whole drop, or empty when it is not sellable. Must not change any state. */
        OptionalDouble quote(D drop);

        /** Credits {@code money} for the drop; {@code false} means nothing was credited. */
        boolean settle(D drop, double money);
    }

    /** What one drop event sold: the drops to remove, their total item count and the total money. */
    public record Outcome<D>(List<D> sold, int items, double money) {

        public boolean isEmpty() {
            return this.sold.isEmpty();
        }
    }

    /** Only a pickaxe-applicable item carrying the enchant at a real level qualifies. */
    public static boolean qualifies(final boolean applicable, final int level) {
        return applicable && level >= 1;
    }

    /** A price is payable only when it is a finite number above zero. */
    public static boolean isPayable(final double money) {
        return Double.isFinite(money) && money > 0.0;
    }

    /**
     * Sells every priced drop of one break. A drop whose quote is empty or not payable is never settled, so
     * it is left untouched and no money is credited for it. Only the given drops are looked at.
     */
    public static <D> Outcome<D> sellAll(final List<D> drops, final ToIntFunction<D> amountOf, final Appraiser<D> appraiser) {
        final List<D> sold = new ArrayList<>();
        int items = 0;
        double money = 0.0;

        for (final D drop : List.copyOf(drops)) {
            final OptionalDouble quote = appraiser.quote(drop);
            if (quote.isEmpty() || !isPayable(quote.getAsDouble())) continue;

            final int amount = amountOf.applyAsInt(drop);
            if (amount <= 0) continue;

            if (!appraiser.settle(drop, quote.getAsDouble())) continue;

            sold.add(drop);
            items += amount;
            money += quote.getAsDouble();
        }

        return new Outcome<>(Collections.unmodifiableList(sold), items, money);
    }

    /** Removes the sold drops, by identity, keeping the order of everything that stays. */
    public static <D> void removeSold(final List<D> drops, final List<D> sold) {
        if (sold.isEmpty()) return;

        final Set<D> gone = Collections.newSetFromMap(new IdentityHashMap<>());
        gone.addAll(sold);
        drops.removeIf(gone::contains);
    }

    /**
     * The single summary of one break, or {@code null} when nothing was sold or the template is blank.
     * Callers send at most this one message per drop event, however many drops were sold.
     */
    public static String summary(final String template, final int items, final double money) {
        if (items <= 0 || !isPayable(money) || template == null || template.isBlank()) return null;

        return template.replace("%items%", Integer.toString(items)).replace("%price%", formatPrice(money));
    }

    public static String formatPrice(final double money) {
        return BigDecimal.valueOf(money).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
