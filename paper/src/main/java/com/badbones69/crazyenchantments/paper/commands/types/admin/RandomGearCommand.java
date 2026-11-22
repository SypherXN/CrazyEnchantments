package com.badbones69.crazyenchantments.paper.commands.types.admin;

import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.commands.EnchantCommand;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Optional;
import dev.triumphteam.cmd.core.annotations.Suggestion;
import dev.triumphteam.cmd.core.annotations.Syntax;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Admin-only test helper: fills a held item, or a freshly built material item,
 * with random custom enchantments so the quality of a roll can be inspected
 * without going through books.
 *
 * <p>Rarity modes: an exact tier keeps the old single-tier pool, {@code any}
 * keeps the old uniform pool across all registered enchants, and {@code mixed}
 * draws a tier first. Mixed draws pick uniformly among currently viable
 * canonical tiers, preferring tiers not used by this roll; once every viable
 * tier has been used it repeats one. Distinct tiers are preferred, never
 * guaranteed, when conflicts or the item's slot count leave no alternative.
 *
 * <p>Counts accept a fixed number or an inclusive {@code min-max} range. The
 * actual count is chosen from the feasible part of that range and is never
 * below {@code min}. Selection is bounded greedy sampling, not an exact
 * solver; if no viable count can be fitted the command reports that instead of
 * applying a partial result.
 */
public class RandomGearCommand extends EnchantCommand {

    private static final int MAX_ATTEMPTS = 64;

    private static final List<String> TIERS = List.of("Simple", "Unique", "Elite", "Ultimate", "Legendary");

    private static final Random RANDOM = new Random();

    @Command("randomgear")
    @Permission(value = "crazyenchantments.randomgear", def = PermissionDefault.OP)
    @Syntax("/crazyenchantments randomgear <hand|material> <rarity|any|mixed> <count|min-max> [player]")
    public void execute(final Player player, final String source, final String rarity, final String count,
                        @Suggestion("players") @Optional final Player target) {
        final Player destination = target == null ? player : target;

        // The target's inventory and item are read, so run on the region that owns them.
        destination.getScheduler().run(this.plugin,
                task -> roll(player, destination, source, rarity, count), null);
    }

    private void roll(final Player sender, final Player target, final String source, final String rarity,
                      final String countArgument) {
        final CountRange requested;

        try {
            requested = parseCount(countArgument);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ColorUtils.color("&cInvalid count: &e" + countArgument
                    + "&c. Use a number or min-max, e.g. 2-5."));

            return;
        }

        final boolean fromHand = source.equalsIgnoreCase("hand");
        final ItemStack item;

        if (fromHand) {
            item = target.getInventory().getItemInMainHand();

            if (item.isEmpty()) {
                sender.sendMessage(target == sender ? Messages.DOESNT_HAVE_ITEM_IN_HAND.getMessage()
                        : ColorUtils.color("&c" + target.getName() + " is not holding an item."));

                return;
            }

            if (item.getAmount() != 1) {
                sender.sendMessage(Messages.NEED_TO_UNSTACK_ITEM.getMessage());

                return;
            }
        } else {
            final Material material = Material.matchMaterial(source);

            if (material == null || material.isAir() || !material.isItem()) {
                sender.sendMessage(ColorUtils.color("&cUnknown material: &e" + source));

                return;
            }

            item = new ItemStack(material);
        }

        final boolean any = rarity.equalsIgnoreCase("any");
        final boolean mixed = rarity.equalsIgnoreCase("mixed");

        final Category category;

        if (any || mixed) {
            category = null;
        } else {
            category = findTier(canonicalRarity(rarity));

            if (category == null) {
                sender.sendMessage(ColorUtils.color("&cUnknown rarity: &e" + rarity
                        + "&c. Use Simple, Unique, Elite, Ultimate, Legendary, Common, any, or mixed."));

                return;
            }
        }

        final List<Candidate> existing = new ArrayList<>();

        for (final CEnchantment enchantment : this.bookSettings.getEnchantments(item).keySet()) {
            existing.add(candidate(enchantment));
        }

        final int free = freeSlots(target, item);

        final Map<String, CEnchantment> byKey = new LinkedHashMap<>();
        final Selection selection;

        if (mixed) {
            final List<TierCandidate> pool = new ArrayList<>();

            for (final String tier : TIERS) {
                final Category tierCategory = findTier(tier);

                if (tierCategory == null) continue;

                for (final CEnchantment enchantment : tierCategory.getEnabledEnchantments()) {
                    if (!eligible(enchantment, item)) continue;

                    byKey.putIfAbsent(enchantment.getName(), enchantment);
                    pool.add(new TierCandidate(tier, candidate(enchantment)));
                }
            }

            if (pool.isEmpty()) {
                sender.sendMessage(ColorUtils.color("&cNo enabled tiered enchantments fit "
                        + item.getType().name() + "."));

                return;
            }

            selection = rollMixed(pool, existing, requested, free, RANDOM);
        } else {
            final List<CEnchantment> sourceList = any
                    ? this.crazyManager.getRegisteredEnchantments()
                    : category.getEnabledEnchantments();

            final List<Candidate> pool = new ArrayList<>();

            for (final CEnchantment enchantment : sourceList) {
                if (!eligible(enchantment, item)) continue;

                byKey.putIfAbsent(enchantment.getName(), enchantment);
                pool.add(candidate(enchantment));
            }

            if (pool.isEmpty()) {
                sender.sendMessage(ColorUtils.color("&cNo enabled "
                        + (any ? "" : category.getIdentity() + " ")
                        + "enchantments fit " + item.getType().name() + "."));

                return;
            }

            selection = rollStandard(pool, existing, requested, free, RANDOM);
        }

        if (selection.failure != null) {
            sender.sendMessage(ColorUtils.color("&cCannot roll that request: &7" + selection.failure));

            return;
        }

        final Map<CEnchantment, Integer> additions = new LinkedHashMap<>();
        final StringBuilder report = new StringBuilder();

        for (final Roll roll : selection.rolls) {
            final CEnchantment enchantment = byKey.get(roll.key());

            if (enchantment == null) continue;

            additions.put(enchantment, roll.level());

            if (!report.isEmpty()) report.append("&7, ");

            report.append(enchantment.getCustomName()).append(" &7(");

            if (mixed) report.append("&b").append(roll.tier()).append("&7, ");

            report.append("lvl &e").append(roll.level()).append("&7)");
        }

        this.crazyManager.addEnchantments(item, additions);

        if (fromHand) {
            this.methods.setItemInHand(target, item);
        } else {
            final Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);

            if (!leftovers.isEmpty()) {
                sender.sendMessage(Messages.INVENTORY_FULL.getMessage());

                return;
            }
        }

        sender.sendMessage(ColorUtils.color("&aRolled &e" + selection.rolls.size()
                + (mixed ? "&a mixed enchant(s): " : "&a enchant(s): ")
                + (target == sender ? "" : "&afor &e" + target.getName() + "&a: ") + report));

        if (target != sender) {
            target.sendMessage(ColorUtils.color("&aYou were given random enchanted gear by &e"
                    + sender.getName() + "&a."));
        }
    }

    /**
     * Mirrors the normal {@code canAddEnchantment} limits: a bypass permission
     * or a disabled max check means no numeric cap, otherwise the player's
     * base/max limits and the item's slot-crystal upgrades both apply.
     */
    private int freeSlots(final Player player, final ItemStack item) {
        if (!this.crazyManager.checkMaxEnchantments()
                || player.hasPermission("crazyenchantments.bypass.limit")) return Integer.MAX_VALUE;

        final int cap = Math.min(
                this.crazyManager.getPlayerBaseEnchantments(player) - this.crazyManager.getEnchantmentLimiter(item),
                this.crazyManager.getPlayerMaxEnchantments(player));

        return cap - this.bookSettings.getEnchantmentAmount(item, this.crazyManager.checkVanillaLimit());
    }

    private Category findTier(final String name) {
        return this.bookSettings.getCategories().stream()
                .filter(category -> category.getIdentity().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private static boolean eligible(final CEnchantment enchantment, final ItemStack item) {
        return enchantment.isActivated() && enchantment.canEnchantItem(item) && enchantment.getMaxLevel() >= 1;
    }

    private Candidate candidate(final CEnchantment enchantment) {
        return new Candidate(enchantment.getName(), enchantment.getMaxLevel(),
                new HashSet<>(enchantment.getConflicts()));
    }

    static String canonicalRarity(final String rarity) {
        return rarity.equalsIgnoreCase("common") ? "Simple" : rarity;
    }

    /**
     * Fixed {@code n} means exactly n; {@code min-max} is inclusive. Malformed,
     * reversed, zero/negative and overflowing values are rejected.
     */
    static CountRange parseCount(final String value) {
        final String text = value.trim();
        final int dash = text.indexOf('-');

        if (dash < 0) {
            final int fixed = positive(text);

            return new CountRange(fixed, fixed);
        }

        if (dash == 0 || dash == text.length() - 1 || text.indexOf('-', dash + 1) >= 0) {
            throw new IllegalArgumentException("expected a number or min-max");
        }

        final int min = positive(text.substring(0, dash));
        final int max = positive(text.substring(dash + 1));

        if (max < min) throw new IllegalArgumentException("min is greater than max");

        return new CountRange(min, max);
    }

    private static int positive(final String text) {
        final int value;

        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("not a whole number", exception);
        }

        if (value <= 0) throw new IllegalArgumentException("must be greater than 0");

        return value;
    }

    static String slotsFailure(final int needed, final int free) {
        final StringBuilder message = new StringBuilder("needs ").append(needed)
                .append(" free custom enchantment slot(s), only ").append(Math.max(0, free)).append(" available.");

        if (free <= 0) {
            message.append(" The default Base-Limit is 0; grant crazyenchantments.base-limit.<n>")
                    .append(" or crazyenchantments.bypass.limit.");
        }

        return message.toString();
    }

    /**
     * Exact-tier and {@code any} rolling. Counts above the feasible greedy set
     * are never drawn, and a request below {@code min} fails without changes.
     */
    static Selection rollStandard(final List<Candidate> pool, final List<Candidate> existing,
                                  final CountRange requested, final int free, final Random random) {
        if (free < requested.min()) return Selection.failed(slotsFailure(requested.min(), free));

        final List<Roll> best = bestStandard(pool, existing, requested.max(), random);

        final int upper = Math.min(free, best.size());

        if (upper < requested.min()) {
            return Selection.failed("only " + upper + " compatible enchantment(s) can fit, but the minimum requested is "
                    + requested.min() + ".");
        }

        final int actual = requested.min() + random.nextInt(upper - requested.min() + 1);

        final Selection selection = select(pool, existing, actual, free, random);

        if (selection.failure == null) return selection;

        // A greedy set of at least 'actual' exists; its prefix is a valid roll.
        return Selection.success(best.subList(0, actual));
    }

    /**
     * Mixed-tier rolling. Tiers are drawn uniformly from the viable ones, with
     * unused tiers preferred until every currently viable tier has appeared.
     */
    static Selection rollMixed(final List<TierCandidate> pool, final List<Candidate> existing,
                               final CountRange requested, final int free, final Random random) {
        if (free < requested.min()) return Selection.failed(slotsFailure(requested.min(), free));

        final List<Roll> best = bestMixed(pool, existing, requested.max(), random);

        final int upper = Math.min(free, best.size());

        if (upper < requested.min()) {
            return Selection.failed("only " + upper
                    + " compatible enchantment(s) across the tiers can fit, but the minimum requested is "
                    + requested.min() + ".");
        }

        final int actual = requested.min() + random.nextInt(upper - requested.min() + 1);

        final Selection selection = selectMixed(pool, existing, actual, random);

        if (selection.failure == null) return selection;

        return Selection.success(best.subList(0, actual));
    }

    static List<Roll> bestStandard(final List<Candidate> pool, final List<Candidate> existing,
                                   final int limit, final Random random) {
        List<Roll> best = List.of();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final List<Roll> candidate = standardGreedy(pool, existing, limit, random);

            if (candidate.size() > best.size()) best = candidate;
        }

        return best;
    }

    static List<Roll> bestMixed(final List<TierCandidate> pool, final List<Candidate> existing,
                                final int limit, final Random random) {
        List<Roll> best = List.of();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final List<Roll> candidate = mixedGreedy(pool, existing, limit, random);

            if (candidate.size() > best.size()) best = candidate;
        }

        return best;
    }

    static List<Roll> standardGreedy(final List<Candidate> pool, final List<Candidate> existing,
                                     final int limit, final Random random) {
        final Set<String> taken = new HashSet<>();

        for (final Candidate candidate : existing) taken.add(candidate.key());

        final List<Candidate> usable = new ArrayList<>();

        for (final Candidate candidate : pool) {
            if (candidate.maxLevel() < 1) continue;
            if (taken.contains(candidate.key())) continue;
            if (!compatible(candidate, existing)) continue;

            usable.add(candidate);
        }

        final List<Candidate> chosen = new ArrayList<>();
        final Set<String> chosenKeys = new HashSet<>();

        while (chosen.size() < limit) {
            final List<Candidate> next = new ArrayList<>();

            for (final Candidate candidate : usable) {
                if (chosenKeys.contains(candidate.key())) continue;
                if (!compatible(candidate, chosen)) continue;

                next.add(candidate);
            }

            if (next.isEmpty()) break;

            final Candidate pick = next.get(random.nextInt(next.size()));

            chosen.add(pick);
            chosenKeys.add(pick.key());
        }

        final List<Roll> rolls = new ArrayList<>();

        for (final Candidate candidate : chosen) {
            rolls.add(new Roll(candidate.key(), 1 + random.nextInt(candidate.maxLevel()), null));
        }

        return rolls;
    }

    /**
     * One bounded greedy mixed pass: each draw picks a viable tier (unused ones
     * first) then one compatible enchant inside it. Stops at {@code limit} or
     * when no tier has a compatible enchant left.
     */
    static List<Roll> mixedGreedy(final List<TierCandidate> pool, final List<Candidate> existing,
                                  final int limit, final Random random) {
        final Map<String, List<Candidate>> byTier = new LinkedHashMap<>();

        for (final TierCandidate entry : pool) {
            if (entry.candidate().maxLevel() < 1) continue;

            byTier.computeIfAbsent(entry.tier(), key -> new ArrayList<>()).add(entry.candidate());
        }

        final List<Candidate> chosen = new ArrayList<>();
        final List<String> chosenTiers = new ArrayList<>();
        final Set<String> chosenKeys = new HashSet<>();
        final Set<String> usedTiers = new HashSet<>();

        while (chosen.size() < limit && !byTier.isEmpty()) {
            final Map<String, List<Candidate>> viable = new LinkedHashMap<>();

            for (final Map.Entry<String, List<Candidate>> entry : byTier.entrySet()) {
                final List<Candidate> fits = new ArrayList<>();

                for (final Candidate candidate : entry.getValue()) {
                    if (chosenKeys.contains(candidate.key())) continue;
                    if (!compatible(candidate, existing)) continue;
                    if (!compatible(candidate, chosen)) continue;

                    fits.add(candidate);
                }

                if (!fits.isEmpty()) viable.put(entry.getKey(), fits);
            }

            if (viable.isEmpty()) break;

            final List<String> tiers = new ArrayList<>(viable.keySet());
            final List<String> unused = new ArrayList<>();

            for (final String tier : tiers) {
                if (!usedTiers.contains(tier)) unused.add(tier);
            }

            final List<String> options = unused.isEmpty() ? tiers : unused;
            final String tier = options.get(random.nextInt(options.size()));
            final List<Candidate> candidates = viable.get(tier);
            final Candidate pick = candidates.get(random.nextInt(candidates.size()));

            chosen.add(pick);
            chosenTiers.add(tier);
            chosenKeys.add(pick.key());
            usedTiers.add(tier);
        }

        final List<Roll> rolls = new ArrayList<>();

        for (int index = 0; index < chosen.size(); index++) {
            final Candidate candidate = chosen.get(index);

            rolls.add(new Roll(candidate.key(), 1 + random.nextInt(candidate.maxLevel()), chosenTiers.get(index)));
        }

        return rolls;
    }

    /**
     * Bounded random sampling for a fixed count: distinct candidates, levels
     * 1..max, and known conflicts checked in both directions.
     */
    static Selection select(final List<Candidate> pool, final List<Candidate> existing, final int count,
                            final int free, final Random random) {
        if (count < 1) return Selection.failed("count must be at least 1.");

        if (free < count) return Selection.failed(slotsFailure(count, free));

        final Set<String> taken = new HashSet<>();

        for (final Candidate candidate : existing) taken.add(candidate.key());

        final List<Candidate> usable = new ArrayList<>();

        for (final Candidate candidate : pool) {
            if (candidate.maxLevel() < 1) continue;
            if (taken.contains(candidate.key())) continue;
            if (!compatible(candidate, existing)) continue;

            usable.add(candidate);
        }

        if (usable.size() < count) {
            return Selection.failed("only " + usable.size()
                    + " eligible non-conflicting enchantment(s) fit this item.");
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final List<Candidate> shuffled = new ArrayList<>(usable);

            Collections.shuffle(shuffled, random);

            final List<Candidate> chosen = new ArrayList<>();

            for (final Candidate candidate : shuffled) {
                if (!compatible(candidate, chosen)) continue;

                chosen.add(candidate);

                if (chosen.size() == count) break;
            }

            if (chosen.size() < count) continue;

            final List<Roll> rolls = new ArrayList<>();

            for (final Candidate candidate : chosen) {
                rolls.add(new Roll(candidate.key(), 1 + random.nextInt(candidate.maxLevel()), null));
            }

            return Selection.success(rolls);
        }

        return Selection.failed("could not fit " + count
                + " non-conflicting enchantments; lower the count or choose another rarity.");
    }

    /**
     * Bounded retries of the mixed draw for a fixed count. Falls back to the
     * caller's feasible greedy set only when every attempt stalls.
     */
    static Selection selectMixed(final List<TierCandidate> pool, final List<Candidate> existing,
                                 final int count, final Random random) {
        if (count < 1) return Selection.failed("count must be at least 1.");

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final List<Roll> rolls = mixedGreedy(pool, existing, count, random);

            if (rolls.size() == count) return Selection.success(rolls);
        }

        return Selection.failed("could not fit " + count
                + " non-conflicting enchantments across the tiers; lower the count or choose another rarity.");
    }

    private static boolean compatible(final Candidate candidate, final List<Candidate> others) {
        for (final Candidate other : others) {
            if (candidate.key().equals(other.key())) return false;
            if (candidate.conflicts().contains(other.key())) return false;
            if (other.conflicts().contains(candidate.key())) return false;
        }

        return true;
    }

    record Candidate(String key, int maxLevel, Set<String> conflicts) {}

    record TierCandidate(String tier, Candidate candidate) {}

    record Roll(String key, int level, String tier) {}

    record CountRange(int min, int max) {}

    static final class Selection {

        final List<Roll> rolls;

        final String failure;

        private Selection(final List<Roll> rolls, final String failure) {
            this.rolls = rolls;
            this.failure = failure;
        }

        static Selection success(final List<Roll> rolls) {
            return new Selection(List.copyOf(rolls), null);
        }

        static Selection failed(final String failure) {
            return new Selection(List.of(), failure);
        }
    }
}
