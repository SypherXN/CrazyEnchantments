package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.objects.SellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;
import me.gypopo.economyshopgui.util.EconomyType;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Auto Sell: sells the drops of one block break for their EconomyShopGUI sell price.
 *
 * <p>This is the only class that touches EconomyShopGUI types; it is only constructed when EconomyShopGUI or
 * EconomyShopGUI-Premium is enabled (see {@code CrazyEnchantments}).
 *
 * <p>Pricing uses {@code getShopItem}, {@code isSellAble}, {@code hasPermissions} and
 * {@code getSellPrice(OfflinePlayer, ItemStack)}; crediting uses the registered Vault economy directly
 * so the same deposit returns its result, followed on success by {@code SellPrice.updateLimits()}. The plugin's
 * {@code sellItem} transaction is deliberately not used: it works on items in the player's inventory, not on
 * drops in the world.
 *
 * <p>Known limitation: because no shop transaction runs, EconomyShopGUI's {@code PreTransactionEvent},
 * {@code PostTransactionEvent} and its transaction log do not see Auto Sell sales.
 *
 * <p>Only a single positive, finite price in the default Vault currency is sold. Other prices
 * leave the drop untouched. An unconfirmed payment keeps the drop; failure to update shop limits
 * after a confirmed payment does not undo the sale.
 */
public final class AutoSellSupport implements AutoSellLogic.DropSeller<BlockDropItemEvent> {

    private final CrazyEnchantments plugin;
    private final Methods methods;
    private final EnchantmentBookSettings books;

    private volatile boolean disabled;
    private volatile boolean runtimeWarned;
    private final AtomicBoolean paymentWarned = new AtomicBoolean();

    public AutoSellSupport(final CrazyEnchantments plugin) {
        this.plugin = plugin;
        this.methods = plugin.getStarter().getMethods();
        this.books = plugin.getStarter().getEnchantmentBookSettings();
    }

    /** A drop that has been priced but not yet paid. */
    private record Pending(SellPrice sellPrice, double money) {}

    @Override
    public void sell(final BlockDropItemEvent event) {
        if (this.disabled || event.isAsynchronous() || event.isCancelled()) return;

        final Player player = event.getPlayer();

        // The pickaxe is read from the player's inventory, so only on the thread that owns the player.
        if (!Bukkit.isOwnedByCurrentRegion(player)) return;

        final List<Item> drops = event.getItems();
        if (drops.isEmpty()) return;

        final CEnchantment enchantment = CEnchantments.AUTO_SELL.getEnchantment();
        if (enchantment == null) return;

        final ItemStack tool = this.methods.getItemInHand(player);
        if (tool.isEmpty()) return;

        final Map<CEnchantment, Integer> enchantments = this.books.getEnchantments(tool);
        final int level = enchantments.getOrDefault(enchantment, 0);
        if (!AutoSellLogic.qualifies(enchantment.canEnchantItem(tool), level)) return;

        if (!EnchantUtils.isEventActive(CEnchantments.AUTO_SELL, player, tool, enchantments)) return;

        final Map<Item, Pending> pending = new IdentityHashMap<>();

        final AutoSellLogic.Outcome<Item> outcome;
        try {
            outcome = AutoSellLogic.sellAll(drops, drop -> drop.getItemStack().getAmount(), new AutoSellLogic.Appraiser<>() {
                @Override
                public OptionalDouble quote(final Item drop) {
                    return appraise(player, drop, pending);
                }

                @Override
                public boolean settle(final Item drop, final double money) {
                    return !event.isCancelled() && pay(player, drop, money, pending);
                }
            });
        } catch (final LinkageError error) {
            disable(error);
            return;
        } catch (final RuntimeException exception) {
            warnOnce(exception);
            return;
        }

        if (outcome.isEmpty()) return;

        // The drops have already been transformed; remove sold ones before Telepathy or vanilla collects them.
        for (final Item item : outcome.sold()) item.remove();
        AutoSellLogic.removeSold(drops, outcome.sold());

        final String summary = AutoSellLogic.summary(TuningConfig.autoSell().message(), outcome.items(), outcome.money());
        if (summary != null) ColorUtils.sendMessage(player, summary, false);
    }

    private OptionalDouble appraise(final Player player, final Item drop, final Map<Item, Pending> pending) {
        try {
            if (!Bukkit.isOwnedByCurrentRegion(drop)) return OptionalDouble.empty();

            final ItemStack stack = drop.getItemStack();
            if (stack.isEmpty()) return OptionalDouble.empty();

            final ShopItem shopItem = EconomyShopGUIHook.getShopItem(player, stack);
            if (shopItem == null || shopItem.hasItemError()) return OptionalDouble.empty();
            if (!EconomyShopGUIHook.isSellAble(shopItem) || !EconomyShopGUIHook.hasPermissions(shopItem, player)) return OptionalDouble.empty();

            final Optional<SellPrice> sellPrice = EconomyShopGUIHook.getSellPrice(player, stack);
            if (sellPrice == null || sellPrice.isEmpty()) return OptionalDouble.empty();

            final Map<EcoType, Double> prices = sellPrice.get().getPrices();
            if (prices == null || prices.size() != 1) return OptionalDouble.empty();

            final Map.Entry<EcoType, Double> entry = prices.entrySet().iterator().next();
            if (entry.getKey() == null || entry.getKey().getType() != EconomyType.VAULT) return OptionalDouble.empty();

            final Double money = entry.getValue();
            if (money == null || !AutoSellLogic.isPayable(money)) return OptionalDouble.empty();

            // The Vault API can credit only its default currency, not a named shop currency.
            if (entry.getKey().getCurrency() != null || EconomyShopGUIHook.getEcon(entry.getKey()) == null) return OptionalDouble.empty();

            pending.put(drop, new Pending(sellPrice.get(), money));

            return OptionalDouble.of(money);
        } catch (final LinkageError error) {
            disable(error);
            return OptionalDouble.empty();
        } catch (final RuntimeException exception) {
            warnOnce(exception);
            return OptionalDouble.empty();
        }
    }

    private boolean pay(final Player player, final Item drop, final double money, final Map<Item, Pending> pending) {
        final Pending priced = pending.remove(drop);
        if (priced == null || this.disabled || priced.money() != money) return false;

        try {
            final Economy vault = Bukkit.getServicesManager().load(Economy.class);
            final EconomyResponse response = vault == null ? null : vault.depositPlayer(player, money);
            if (response == null || !response.transactionSuccess()) {
                paymentUnconfirmed(null);
                return false;
            }
        } catch (final LinkageError error) {
            this.disabled = true;
            paymentUnconfirmed(error);
            return false;
        } catch (final RuntimeException exception) {
            paymentUnconfirmed(exception);
            return false;
        }

        // The money is credited, so the drop is sold whatever happens to the limit bookkeeping.
        try {
            priced.sellPrice().updateLimits();
        } catch (final LinkageError error) {
            disable(error);
        } catch (final RuntimeException exception) {
            warnOnce(exception);
        }

        return true;
    }

    private void disable(final LinkageError error) {
        if (this.disabled) return;

        this.disabled = true;
        this.plugin.getLogger().log(Level.WARNING, "Auto Sell is disabled for this session: the expected EconomyShopGUI or Vault API is unavailable", error);
    }

    private void paymentUnconfirmed(final Throwable exception) {
        if (this.plugin.getLogger().isLoggable(Level.FINE) && this.paymentWarned.compareAndSet(false, true)) {
            this.plugin.getLogger().log(Level.FINE, "Auto Sell could not confirm a Vault deposit; the affected drop was kept (further payment failures are not logged)", exception);
        }
    }

    private void warnOnce(final RuntimeException exception) {
        if (this.runtimeWarned) return;

        this.runtimeWarned = true;
        this.plugin.getLogger().log(Level.WARNING, "Auto Sell could not complete EconomyShopGUI pricing or bookkeeping (further errors are not logged)", exception);
    }
}
