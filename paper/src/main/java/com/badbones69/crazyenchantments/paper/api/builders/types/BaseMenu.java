package com.badbones69.crazyenchantments.paper.api.builders.types;

import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.builders.InventoryBuilder;
import com.badbones69.crazyenchantments.paper.api.builders.types.gkitz.KitsManager;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.Category;
import com.badbones69.crazyenchantments.paper.api.objects.enchants.EnchantmentType;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BaseMenu extends InventoryBuilder {

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final EnchantmentBookSettings bookSettings = this.starter.getEnchantmentBookSettings();

    private final int page;

    public int getPage() {
        return this.page;
    }

    public BaseMenu(Player player, int size, String title) {
        this(player, size, title, 0);
    }

    public BaseMenu(Player player, int size, String title, int page) {
        super(player, size, title);

        this.page = page;
    }

    @Override
    public InventoryBuilder build() {
        final EnchantmentType type = getEnchantmentType();

        if (type != null) {
            final List<CEnchantment> books = matchedEnchantments(type);
            final int perPage = Math.max(1, getSize() - 9);
            final int lastPage = Math.max(0, (books.size() - 1) / perPage);
            final int current = Math.min(this.page, lastPage);
            final ItemBuilder book = this.bookSettings.getNormalBook().setGlow(true);

            for (int index = current * perPage; index < books.size() && index < (current + 1) * perPage; index++) {
                final CEnchantment enchantment = books.get(index);

                getInventory().setItem(index - current * perPage, book.setName(enchantment.getInfoName()).setLore(enchantment.getInfoDescription()).build());
            }

            // Back sits in the middle of the bottom row with the page arrows on
            // the outer edges, so the pages read left-to-right either side of it.
            getInventory().setItem(getSize() - 5, KitsManager.getBackRight());

            if (current > 0) getInventory().setItem(getSize() - 9, navigation(DataKeys.previous_page, "ARROW", "&c&lPrevious Page"));

            if (current < lastPage) getInventory().setItem(getSize() - 1, navigation(DataKeys.next_page, "ARROW", "&a&lNext Page"));
        } else {
            MenuManager.getEnchantmentTypes().stream().filter(EnchantmentType::isInMenu)
                    .forEach(key -> getInventory().setItem(key.getSlot(), key.getDisplayItem()));
        }

        return this;
    }

    /**
     * The books a page shows: every activated enchantment from any type whose
     * enchantable materials overlap the page's, so a Sword-and-Axe enchant
     * appears on both the Sword page and the Axe page. Highest tier first, and
     * the sort is stable, so books sharing a tier keep registration order.
     */
    public static List<CEnchantment> matchedEnchantments(final EnchantmentType page) {
        final List<Material> materials = page.getEnchantableMaterials();
        final Set<CEnchantment> enchantments = new LinkedHashSet<>();

        for (EnchantmentType type : MenuManager.getEnchantmentTypes()) {
            if (Collections.disjoint(type.getEnchantableMaterials(), materials)) continue;

            enchantments.addAll(type.getEnchantments());
        }

        return enchantments.stream()
                .filter(CEnchantment::isActivated)
                .sorted(Comparator.comparingInt(BaseMenu::rarityRank).reversed())
                .toList();
    }

    /**
     * A page arrow. The key is what the click listener matches on, so the page
     * is re-opened with the arrow that was clicked.
     */
    private static ItemStack navigation(final DataKeys key, final String material, final String name) {
        return new ItemBuilder()
                .setMaterial(material)
                .setName(name)
                .addKey(key.getNamespacedKey(), "")
                .build();
    }

    /**
     * How high an enchantment's tier sits in {@link TuningConfig#RARITIES}, or
     * {@code -1} when it has no canonical tier. The highest tier an enchantment
     * is listed under wins, so a book in several tiers sorts as its best one.
     */
    private static int rarityRank(final CEnchantment enchantment) {
        int rank = -1;

        for (final Category category : enchantment.getCategories()) {
            rank = Math.max(rank, TuningConfig.RARITIES.indexOf(category.getIdentity()));
        }

        return rank;
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

            final EnchantmentType current = holder.getEnchantmentType();

            if (current != null && (view.has(DataKeys.next_page.getNamespacedKey()) || view.has(DataKeys.previous_page.getNamespacedKey()))) {
                MenuManager.openInfoMenu(player, current, holder.getPage() + (view.has(DataKeys.next_page.getNamespacedKey()) ? 1 : -1));

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