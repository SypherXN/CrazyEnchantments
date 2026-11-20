package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.events.AuraActiveEvent;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuraListener implements Listener {

    @NotNull
    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    private final CEnchantments[] AURA_ENCHANTMENTS = {
            CEnchantments.BLIZZARD,
            CEnchantments.ACIDRAIN,
            CEnchantments.SANDSTORM,
            CEnchantments.RADIANT,
            CEnchantments.INTIMIDATE
    };

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX()
        && from.getBlockY() == to.getBlockY()
        && from.getBlockZ() == to.getBlockZ()) return;

        final var settings = TuningConfig.fork();
        final Map<Double, List<Player>> nearbyByRadius = new HashMap<>();
        List<Player> players = getNearbyPlayers(player, settings.maxAuraRadius());

        if (players.isEmpty()) return;

        nearbyByRadius.put(settings.maxAuraRadius(), players);
        EntityEquipment playerEquipment = player.getEquipment();

        for (ItemStack item : playerEquipment.getArmorContents()) { // The player that moves.
            Map<CEnchantment, Integer> itemEnchantments = this.enchantmentBookSettings.getEnchantments(item);
            itemEnchantments.forEach((enchantment, level) -> {
                CEnchantments enchantmentEnum = getAuraEnchantmentEnum(enchantment);

                if (enchantmentEnum == null) return;
                final double radius = settings.auras().get(enchantmentEnum.getName()).radius();
                nearbyByRadius.computeIfAbsent(radius, value -> getNearbyPlayers(player, value)).forEach(other ->
                        this.plugin.getServer().getPluginManager().callEvent(new AuraActiveEvent(player, other, enchantmentEnum, level)));

            });
        }

        for (Player other : players) {
            EntityEquipment otherEquipment = other.getEquipment();

            for (ItemStack item : otherEquipment.getArmorContents()) { // The other players moving.
                Map<CEnchantment, Integer> itemEnchantments = this.enchantmentBookSettings.getEnchantments(item);
                itemEnchantments.forEach((enchantment, level) -> {
                    CEnchantments enchantmentEnum = getAuraEnchantmentEnum(enchantment);

                    if (enchantmentEnum == null) return;
                    final double radius = settings.auras().get(enchantmentEnum.getName()).radius();
                    if (nearbyByRadius.computeIfAbsent(radius, value -> getNearbyPlayers(player, value)).contains(other)) {
                        this.plugin.getServer().getPluginManager().callEvent(new AuraActiveEvent(other, player, enchantmentEnum, level));
                    }

                });
            }
        }
    }

    private CEnchantments getAuraEnchantmentEnum(CEnchantment enchantment) {
        return Arrays.stream(AURA_ENCHANTMENTS).filter(enchantmentEnum -> enchantmentEnum.getName().equals(enchantment.getName())).findFirst().orElse(null);
    }

    private List<Player> getNearbyPlayers(Player player, double radius) {
        return player.getNearbyEntities(radius, radius, radius).stream().filter((entity) ->
                entity instanceof Player && !entity.getUniqueId().equals(player.getUniqueId())).map(entity -> (Player) entity).collect(Collectors.toList());
    }
}
