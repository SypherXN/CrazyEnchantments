package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyManager;
import com.badbones69.crazyenchantments.paper.api.builders.types.MenuManager;
import com.badbones69.crazyenchantments.paper.api.builders.types.ShopMenu;
import com.badbones69.crazyenchantments.paper.api.economy.Currency;
import com.badbones69.crazyenchantments.paper.api.economy.CurrencyAPI;
import com.badbones69.crazyenchantments.paper.api.enums.Dust;
import com.badbones69.crazyenchantments.paper.api.enums.Scrolls;
import com.badbones69.crazyenchantments.paper.api.enums.ShopOption;
import com.badbones69.crazyenchantments.paper.api.events.BuyBookEvent;
import com.badbones69.crazyenchantments.paper.api.managers.ShopManager;
import com.badbones69.crazyenchantments.paper.api.objects.CEBook;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.objects.LostBook;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.ProtectionCrystalSettings;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.logging.Level;

public class ShopListener implements Listener {

    @NotNull
    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final CrazyManager crazyManager = starter.getCrazyManager();

    @NotNull
    private final Methods methods = starter.getMethods();

    // Settings.
    @NotNull
    private final ProtectionCrystalSettings protectionCrystalSettings = this.starter.getProtectionCrystalSettings();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    // Plugin Managers.
    @NotNull
    private final ShopManager shopManager = this.starter.getShopManager();

    // Plugin Listeners.
    @NotNull
    private final ScramblerListener scramblerListener = this.starter.getScramblerListener();
    @NotNull
    private final SlotCrystalListener slotCrystalListener = this.starter.getSlotCrystalListener();

    // Economy Management.
    @NotNull
    private final CurrencyAPI currencyAPI = this.starter.getCurrencyAPI();

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        Inventory inventory = event.getInventory();

        if (!(inventory.getHolder(false) instanceof ShopMenu holder)) return;

        if (item == null) return;

        Player player = holder.getPlayer();

        event.setCancelled(true);

        if (event.getClickedInventory() != player.getOpenInventory().getTopInventory()) return;

        for (Category category : this.enchantmentBookSettings.getCategories()) {
            if (category.isInGUI() && item.isSimilar(category.getDisplayItem().build())) {

                if (this.methods.isInventoryFull(player)) return;

                // FORK The shop sells the category's lost book, not a pre-rolled enchantment:
                // the player applies it to gear themselves, so the price buys the tier, not a random roll.
                this.crazyManager.getCEPlayer(player).ifPresent(cePlayer ->
                        buyBook(player, category.getCurrency(), category.getCost(),
                                category.getLostBook().getLostBook(category).build()));

                return;
            }

            // FORK the separate lost-book tile no longer exists: the category tile above
            // hands out that category's lost book, so there is nothing left to sell here.
        }

        for (ShopOption option : ShopOption.values()) {
            if (option.isInGUI() && item.isSimilar(option.getItem())) {
                // If the option is buy-able then it check to see if they player can buy it and take the money.

                if (option.isBuyable()) {
                    if (this.methods.isInventoryFull(player)) return;

                    if (option.getCurrency() != null && player.getGameMode() != GameMode.CREATIVE) {
                        if (this.currencyAPI.canBuy(player, option)) {
                            this.currencyAPI.takeCurrency(player, option);
                        } else {
                            String needed = String.valueOf(option.getCost() - this.currencyAPI.getCurrency(player, option.getCurrency()));
                            this.methods.switchCurrency(player, option.getCurrency(), "%Money_Needed%", "%XP%", needed);
                            return;
                        }
                    }
                }

                switch (option) {
                    case GKITZ -> {
                        if (!this.methods.hasPermission(player, "gkitz", true)) return;

                        if (!this.crazyManager.isGkitzEnabled()) return;

                        MenuManager.openKitsMenu(player);
                    }

                    case BLACKSMITH -> {
                        if (!this.methods.hasPermission(player, "blacksmith", true)) return;

                        MenuManager.openBlackSmithMenu(player);
                    }

                    case TINKER -> {
                        if (!this.methods.hasPermission(player, "tinker", true)) return;

                        MenuManager.openTinkererMenu(player);
                    }

                    case INFO -> MenuManager.openInfoMenu(player);
                    case PROTECTION_CRYSTAL -> player.getInventory().addItem(this.protectionCrystalSettings.getCrystal());
                    case SCRAMBLER -> player.getInventory().addItem(this.scramblerListener.getScramblers());
                    case SUCCESS_DUST -> player.getInventory().addItem(Dust.SUCCESS_DUST.getDust());
                    case DESTROY_DUST -> player.getInventory().addItem(Dust.DESTROY_DUST.getDust());
                    case BLACK_SCROLL -> player.getInventory().addItem(Scrolls.BLACK_SCROLL.getScroll());
                    case WHITE_SCROLL -> player.getInventory().addItem(Scrolls.WHITE_SCROLL.getScroll());
                    case TRANSMOG_SCROLL -> player.getInventory().addItem(Scrolls.TRANSMOG_SCROLL.getScroll());
                    case SLOT_CRYSTAL -> player.getInventory().addItem(this.slotCrystalListener.getSlotCrystal());
                }

                return;
            }
        }
    }

    private boolean buyBook(Player player, Currency currency, int cost, ItemStack reward) {
        if (reward.isEmpty() || reward.getAmount() != 1) return false;

        boolean charge = currency != null && player.getGameMode() != GameMode.CREATIVE && cost != 0;
        if (charge) {
            if (cost < 0) return false;
            int balance = this.currencyAPI.getCurrency(player, currency);
            if (balance < cost) {
                String needed = String.valueOf((long) cost - balance);
                this.methods.switchCurrency(player, currency, "%Money_Needed%", "%XP%", needed);
                return false;
            }
            try {
                if (!this.currencyAPI.withdrawCurrency(player, currency, cost)) return false;
            } catch (RuntimeException | NoClassDefFoundError failure) {
                this.plugin.getLogger().log(Level.SEVERE, "Book withdrawal outcome unknown; no delivery for " + player.getUniqueId()
                        + " (attempted charge " + cost + " " + currency + ")", failure);
                return false;
            }
        }

        ItemStack leftover;
        try {
            // addItem keys leftovers by input index; there is exactly one input and one book.
            leftover = player.getInventory().addItem(reward).get(0);
        } catch (RuntimeException failure) {
            // Insertion may already have succeeded. Refunding or dropping here could duplicate value.
            this.plugin.getLogger().log(Level.SEVERE, "Book insertion outcome unknown; no automatic refund or drop for "
                    + player.getUniqueId() + " (charged " + (charge ? cost + " " + currency : "nothing") + ")", failure);
            return false;
        }
        if (leftover == null) return true;
        if (!charge) return false;

        try {
            if (this.currencyAPI.depositCurrency(player, currency, cost)) return false;
        } catch (RuntimeException | NoClassDefFoundError failure) {
            this.plugin.getLogger().log(Level.SEVERE, "Book refund outcome unknown; no drop for " + player.getUniqueId()
                    + " (charged " + cost + " " + currency + ")", failure);
            return false;
        }

        // Only a known rejection and a known leftover can use the drop fallback.
        if (currency == Currency.VAULT) {
            try {
                if (player.getWorld().dropItem(player.getLocation(), leftover, item -> item.setOwner(player.getUniqueId())).isValid()) return true;
            } catch (RuntimeException failure) {
                this.plugin.getLogger().log(Level.SEVERE, "Book drop outcome unknown after rejected refund for " + player.getUniqueId()
                        + " (charged " + cost + " " + currency + ")", failure);
                return false;
            }
        }
        this.plugin.getLogger().severe("Book delivery and refund failed for " + player.getUniqueId()
                + " (charged " + cost + " " + currency + ")");
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantmentTableClick(PlayerInteractEvent event) {
        if (this.shopManager.isEnchantmentTableShop()) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();

            if (block != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getType() == Material.ENCHANTING_TABLE) {
                event.setCancelled(true);

                player.openInventory(new ShopMenu(player, this.shopManager.getInventorySize(), this.shopManager.getInventoryName()).build().getInventory());
            }
        }
    }
}
