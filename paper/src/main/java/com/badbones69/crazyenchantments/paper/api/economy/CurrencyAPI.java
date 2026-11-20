package com.badbones69.crazyenchantments.paper.api.economy;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyPlatform;
import com.badbones69.crazyenchantments.paper.api.economy.vault.VaultSupport;
import com.badbones69.crazyenchantments.paper.api.enums.ShopOption;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.objects.LostBook;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.FusionPaper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class CurrencyAPI {

    @NotNull
    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final CrazyPlatform platform = this.plugin.getPlatform();

    private final FusionPaper fusion = this.platform.getFusion();

    @NotNull
    private final Starter starter = plugin.getStarter();

    /**
     * Get the amount that a player has from a specific currency.
     * @param player The player you wish to get the amount from.
     * @param currency The currency you wish to get from.
     * @return The amount that the player has of that currency.
     */
    public int getCurrency(Player player, Currency currency) {
        try {
            return switch (currency) {
                case VAULT -> (int) this.starter.getVaultSupport().getVault().getBalance(player);
                case XP_LEVEL -> player.getLevel();
                case XP_TOTAL -> player.calculateTotalExperiencePoints();
            };
        } catch (Exception | NoClassDefFoundError ignored) {}
        return 0;
    }
    
    /**
     * Take an amount from a player's currency.
     * @param player The player you wish to take from.
     * @param category The category you wish to use.
     */
    public void takeCurrency(Player player, Category category) {
        takeCurrency(player, category.getCurrency(), category.getCost());
    }
    
    /**
     * Take an amount from a player's currency.
     * @param player The player you wish to take from.
     * @param lostBook The lostbook you wish to use.
     */
    public void takeCurrency(Player player, LostBook lostBook) {
        takeCurrency(player, lostBook.getCurrency(), lostBook.getCost());
    }
    
    /**
     * Take an amount from a player's currency.
     * @param player The player you wish to take from.
     * @param option The ShopOption you wish to use.
     */
    public void takeCurrency(Player player, ShopOption option) {
        takeCurrency(player, option.getCurrency(), option.getCost());
    }
    
    /**
     * Take an amount from a player's currency.
     * @param player The player you wish to take from.
     * @param currency The currency you wish to use.
     * @param amount The amount you want to take.
     */
    public void takeCurrency(Player player, Currency currency, int amount) {
        tryTakeCurrency(player, currency, amount);
    }

    /**
     * Attempts a withdrawal and reports whether it succeeded.
     */
    public boolean tryTakeCurrency(Player player, Currency currency, int amount) {
        try {
            return withdrawCurrency(player, currency, amount);
        } catch (Exception | NoClassDefFoundError ignored) {}
        return false;
    }

    /**
     * Debits currency, returning false on rejection and propagating errors with an unknown outcome.
     */
    public boolean withdrawCurrency(Player player, Currency currency, int amount) {
        return switch (currency) {
            case VAULT -> this.starter.getVaultSupport().getVault().withdrawPlayer(player, amount).transactionSuccess();
            case XP_LEVEL -> {
                int level = player.getLevel();
                if (level < amount) yield false;
                player.setLevel(level - amount);
                yield true;
            }
            case XP_TOTAL -> changeExperiencePoints(player, -(long) amount);
        };
    }
    
    /**
     * Give an amount to a player's currency.
     * @param player The player you are giving to.
     * @param currency The currency you want to use.
     * @param amount The amount you are giving to the player.
     */
    public void giveCurrency(Player player, Currency currency, int amount) {
        tryGiveCurrency(player, currency, amount);
    }

    /**
     * Attempts a deposit and reports whether it succeeded.
     */
    public boolean tryGiveCurrency(Player player, Currency currency, int amount) {
        try {
            return depositCurrency(player, currency, amount);
        } catch (Exception | NoClassDefFoundError ignored) {}
        return false;
    }

    /**
     * Credits currency, returning false on rejection and propagating errors with an unknown outcome.
     */
    public boolean depositCurrency(Player player, Currency currency, int amount) {
        return switch (currency) {
            case VAULT -> this.starter.getVaultSupport().getVault().depositPlayer(player, amount).transactionSuccess();
            case XP_LEVEL -> {
                player.setLevel(player.getLevel() + amount);
                yield true;
            }
            case XP_TOTAL -> changeExperiencePoints(player, amount);
        };
    }
    
    /**
     * Checks if the player has enough of a currency.
     * @param player The player you are checking.
     * @param category The category you wish to check.
     * @return True if they have enough to buy it or false if they don't.
     */
    public boolean canBuy(Player player, Category category) {
        return canBuy(player, category.getCurrency(), category.getCost());
    }
    
    /**
     * Checks if the player has enough of a currency.
     * @param player The player you are checking.
     * @param lostBook The lostBook you wish to check.
     * @return True if they have enough to buy it or false if they don't.
     */
    public boolean canBuy(Player player, LostBook lostBook) {
        return canBuy(player, lostBook.getCurrency(), lostBook.getCost());
    }
    
    /**
     * Checks if the player has enough of a currency.
     * @param player The player you are checking.
     * @param option The ShopOption you wish to check.
     * @return True if they have enough to buy it or false if they don't.
     */
    public boolean canBuy(Player player, ShopOption option) {
        return canBuy(player, option.getCurrency(), option.getCost());
    }
    
    /**
     * Checks if the player has enough of a currency.
     * @param player The player you are checking.
     * @param currency The currency you wish to check.
     * @param cost The cost of the item you are checking.
     * @return True if they have enough to buy it or false if they don't.
     */
    public boolean canBuy(Player player, Currency currency, int cost) {
        return getCurrency(player, currency) >= cost;
    }
    
    static boolean changeExperiencePoints(Player player, long delta) {
        long remaining = player.calculateTotalExperiencePoints() + delta;
        if (remaining < 0 || remaining > Integer.MAX_VALUE) return false;

        int points = (int) remaining;
        player.setExperienceLevelAndProgress(points);
        // Keep the legacy counter in sync, but never use it as the balance.
        player.setTotalExperience(points);
        return true;
    }

    /**
     * Moves up to the requested raw XP, limited by both players' balances.
     * The caller must own both players on the current region thread.
     */
    public static int transferExperiencePoints(Player from, Player to, int limit) {
        if (from == to || limit <= 0) return 0;

        int available = from.calculateTotalExperiencePoints();
        int received = to.calculateTotalExperiencePoints();
        if (available <= 0 || received < 0) return 0;
        int amount = Math.min(limit, Math.min(available, Integer.MAX_VALUE - received));
        if (amount == 0) return 0;

        try {
            from.setExperienceLevelAndProgress(available - amount);
            to.setExperienceLevelAndProgress(received + amount);
        } catch (RuntimeException failure) {
            try {
                // Undo any partial credit before restoring the debit, so a failed credit cannot duplicate XP.
                if (to.calculateTotalExperiencePoints() != received) to.setExperienceLevelAndProgress(received);
                if (from.calculateTotalExperiencePoints() != available) from.setExperienceLevelAndProgress(available);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }

        from.setTotalExperience(available - amount);
        to.setTotalExperience(received + amount);
        return amount;
    }

    /**
     * Loads the currency if it is on the server.
     */
    public void loadCurrency() {
        if (this.fusion.isPluginEnabled("Vault")) {
            this.starter.setVaultSupport(new VaultSupport());

            return;
        }

        this.fusion.log(Level.WARNING, "No economy plugin was found, or the economy plugin did not enable. All economy based features will not work.");
    }
}
