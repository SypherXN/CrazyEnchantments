package com.badbones69.crazyenchantments.paper.commands.types.player;

import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.ProcNotify;
import com.badbones69.crazyenchantments.paper.commands.EnchantCommand;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Syntax;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

/**
 * Cycles a player's own proc feedback: off, action bar, chat.
 */
public class ProcNotifyCommand extends EnchantCommand {

    @Command("procnotify")
    @Permission(value = "crazyenchantments.procnotify", def = PermissionDefault.TRUE)
    @Syntax("/crazyenchantments procnotify")
    public void execute(final Player player) {
        final ProcNotify.Mode mode = ProcNotify.cycle(player.getUniqueId());

        final String state = switch (mode) {
            case OFF -> "&cOFF";
            case ACTIONBAR -> "&aACTION BAR";
            case CHAT -> "&aCHAT";
        };

        player.sendMessage(ColorUtils.color("&7[crazyenchantments] Proc messages are now " + state + "&7."));
    }
}
