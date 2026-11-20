package com.badbones69.crazyenchantments.paper.api.builders.types;

import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.builders.InventoryBuilder;
import com.badbones69.crazyenchantments.paper.api.builders.types.gkitz.KitsManager;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.enchants.EnchantmentType;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BaseMenu extends InventoryBuilder {

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final EnchantmentBookSettings bookSettings = this.starter.getEnchantmentBookSettings();

    public BaseMenu(Player player, int size, String title) {
        super(player, size, title);
    }

    @Override
    public InventoryBuilder build() {
        final EnchantmentType page = getEnchantmentType();

        if (page != null) {
            final List<Material> materials = page.getEnchantableMaterials();
            final Set<CEnchantment> enchantments = new LinkedHashSet<>();

            // Match on the materials a page covers, so a Sword-and-Axe enchant
            // shows up on both the Sword page and the Axe page.
            for (EnchantmentType type : MenuManager.getEnchantmentTypes()) {
                if (Collections.disjoint(type.getEnchantableMaterials(), materials)) continue;

                enchantments.addAll(type.getEnchantments());
            }

            ItemBuilder book = this.bookSettings.getNormalBook().setGlow(true);

            for (CEnchantment enchantment : enchantments) {
                if (enchantment.isActivated()) {
                    getInventory().addItem(book.setName(enchantment.getInfoName()).setLore(enchantment.getInfoDescription()).build());
                }
            }

            getInventory().setItem(getSize() - 1, KitsManager.getBackRight());
        } else {
            MenuManager.getEnchantmentTypes().stream().filter(EnchantmentType::isInMenu)
                    .forEach(key -> getInventory().setItem(key.getSlot(), key.getDisplayItem()));
        }

        return this;
    }

    public static class InfoMenuListener implements Listener {

        @EventHandler(ignoreCancelled = true)
        public void onInfoClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof BaseMenu holder)) return;

            event.setCancelled(true);

            Player player = holder.getPlayer();

            ItemStack itemStack = event.getCurrentItem();

            if (itemStack == null) return;

            final PersistentDataContainerView view = itemStack.getPersistentDataContainer();

            if (view.has(DataKeys.back_left.getNamespacedKey()) || view.has(DataKeys.back_right.getNamespacedKey())) {
                MenuManager.openInfoMenu(player);

                return;
            }

            for (EnchantmentType enchantmentType : MenuManager.getEnchantmentTypes()) {
                if (!enchantmentType.isInMenu()) continue;

                if (itemStack.isSimilar(enchantmentType.getDisplayItem())) {
                    MenuManager.openInfoMenu(player, enchantmentType);

                    return;
                }
            }
        }
    }
}