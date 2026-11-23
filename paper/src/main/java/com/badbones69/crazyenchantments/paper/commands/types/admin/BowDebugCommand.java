package com.badbones69.crazyenchantments.paper.commands.types.admin;

import com.badbones69.crazyenchantments.paper.api.utils.BowDebug;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.commands.EnchantCommand;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Syntax;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

public class BowDebugCommand extends EnchantCommand {

    @Command(value = "bowdebug", alias = "cebowdebug")
    @Permission(value = "crazyenchantments.bowdebug", def = PermissionDefault.OP)
    @Syntax("/crazyenchantments bowdebug")
    public void execute(final CommandSender sender) {
        BowDebug.toggle();

        sender.sendMessage(ColorUtils.color("&7[bowdebug] now &6" + (BowDebug.isEnabled() ? "ON" : "OFF")));
    }
}
