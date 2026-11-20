package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.EnchantUtils;
import com.badbones69.crazyenchantments.paper.api.utils.EventUtils;
import com.badbones69.crazyenchantments.paper.api.utils.SyntheticHit;
import com.badbones69.crazyenchantments.paper.api.utils.ToolEnchantLogic;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public class ToolEnchantments implements Listener {

    @NotNull
    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    // Settings.
    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReforgedWear(PlayerItemDamageEvent event) {
        if (event.getDamage() <= 0 || EventUtils.isIgnoredEvent(event)) return;
        if (preventsWear(event.getPlayer(), event.getItem(), this.enchantmentBookSettings)) event.setCancelled(true);
    }

    public static boolean preventsWear(Player player, ItemStack item, EnchantmentBookSettings books) {
        return preventsWear(player, item, books, null);
    }

    public static boolean preventsWear(Player player, ItemStack item, EnchantmentBookSettings books,
                                       @Nullable Map<CEnchantment, Integer> enchantments) {
        final boolean owned = Bukkit.isOwnedByCurrentRegion(player);
        final boolean empty = item.isEmpty();

        boolean unbreakable = false;
        boolean applicable = false;
        boolean liveHand = false;

        if (owned && !empty) {
            unbreakable = item.hasData(DataComponentTypes.UNBREAKABLE);

            final CEnchantment reforged = CEnchantments.REFORGED.getEnchantment();
            applicable = reforged != null && reforged.canEnchantItem(item);

            // Either hand. Paper returns different Bukkit mirrors for the same live stack, so compare the
            // underlying stack by identity: value equality would also match an identical stack that is not held.
            liveHand = ToolEnchantLogic.isHeldStack(CraftItemStack.unwrap(item),
                    CraftItemStack.unwrap(player.getInventory().getItemInMainHand()),
                    CraftItemStack.unwrap(player.getInventory().getItemInOffHand()));
        }

        if (!ToolEnchantLogic.reforgedMayRoll(SyntheticHit.isBleedTick(), owned, empty, unbreakable, applicable, liveHand)) return false;

        return EnchantUtils.isEventActive(CEnchantments.REFORGED, player, item,
                enchantments == null ? books.getEnchantments(item) : enchantments);
    }

    @EventHandler()
    public void onPlayerClick(PlayerInteractEvent event) {
        // Check what hand is being used as the event fires for each hand.
        if (Objects.equals(event.getHand(), EquipmentSlot.HAND)) updateEffects(event.getPlayer());
    }

    // Registered after PickaxeEnchantments, so Auto Sell has already removed the sold drops.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTelepathy(BlockDropItemEvent event) {
        if (event.getItems().isEmpty()) return;

        Player player = event.getPlayer();
        ItemStack tool = this.methods.getItemInHand(player);

        if (!EnchantUtils.isEventActive(CEnchantments.TELEPATHY, player, tool, this.enchantmentBookSettings.getEnchantments(tool))) return;

        event.setCancelled(true);

        this.methods.addItemToInventory(player, event.getItems());
    }

    private void updateEffects(Player player) {
        ItemStack item = this.methods.getItemInHand(player);
        Map<CEnchantment, Integer> enchantments = this.enchantmentBookSettings.getEnchantments(item);

        int potionTime = 5 * 20;

        if (EnchantUtils.isEventActive(CEnchantments.HASTE, player, item, enchantments)) {
            int power = enchantments.get(CEnchantments.HASTE.getEnchantment());
            player.removePotionEffect(PotionEffectType.HASTE);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, potionTime, power - 1));
        }

        if (EnchantUtils.isEventActive(CEnchantments.OXYGENATE, player, item, enchantments)) {
            player.removePotionEffect(PotionEffectType.WATER_BREATHING);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, potionTime, 5));
        }
    }
}
