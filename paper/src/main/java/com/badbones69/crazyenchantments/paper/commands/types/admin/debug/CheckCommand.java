package com.badbones69.crazyenchantments.paper.commands.types.admin.debug;

import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.commands.EnchantCommand;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Suggestion;
import dev.triumphteam.cmd.core.annotations.Syntax;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CheckCommand extends EnchantCommand {

    @Command(value = "check", alias = "checkenchants")
    @Permission(value = "crazyenchantments.check", def = PermissionDefault.OP)
    @Syntax("/crazyenchantments check [player]")
    public void execute(final CommandSender sender, @Suggestion("players") final Player player) {
        // The held item matters as much as the armour: a weapon can look enchanted
        // through its lore while its stored enchantment data is what the handlers read.
        final List<ItemStack> items = new ArrayList<>(Arrays.asList(player.getEquipment().getArmorContents()));

        items.add(player.getInventory().getItemInMainHand());

        items.stream().filter(Objects::nonNull).filter(item -> !item.getType().isAir()).forEach(item -> {
            final StringBuilder enchantmentsString = new StringBuilder();

            String main = Messages.MAIN_UPDATE_ENCHANTS.getMessageNoPrefix();
            main = main.replace("%item%", item.getType().toString());

            this.bookSettings.getEnchantments(item).forEach((enchantment, level) -> {
                final Map<String, String> placeholders = new HashMap<>();

                placeholders.put("%enchant%", enchantment.getName());
                placeholders.put("%level%", String.valueOf(level));

                enchantmentsString.append(Messages.BASE_UPDATE_ENCHANTS.getMessageNoPrefix(placeholders));
            });

            main = main.replace("%itemEnchants%", enchantmentsString.toString());

            sender.sendMessage(main);
        });
    }
}