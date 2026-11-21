package com.badbones69.crazyenchantments.paper.commands.types.admin;

import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.ProcTest;
import com.badbones69.crazyenchantments.paper.commands.EnchantCommand;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Syntax;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

/**
 * Toggles the session-only 100% activation-chance test switch for one online player.
 */
public class ProcTestCommand extends EnchantCommand {

    @Command("proctest")
    @Permission(value = "crazyenchantments.proctest", def = PermissionDefault.OP)
    @Syntax("/crazyenchantments proctest <player>")
    public void execute(final CommandSender sender, final Player target) {
        // Run on the target's own region so a toggle and its quit cleanup are ordered;
        // a target that quits first reports instead of toggling a stale id.
        target.getScheduler().run(this.plugin,
                task -> toggle(sender, target),
                () -> sender.sendMessage(Messages.NOT_ONLINE.getMessage()));
    }

    private void toggle(final CommandSender sender, final Player target) {
        final boolean enabled = ProcTest.toggle(target.getUniqueId());
        final String state = enabled ? "&aON" : "&cOFF";

        target.sendMessage(ColorUtils.color("&7[crazyenchantments] Custom enchant procs are now " + state + "&7 for you."));

        if (!target.equals(sender)) {
            sender.sendMessage(ColorUtils.color("&7[crazyenchantments] Custom enchant procs for &6" + target.getName() + "&7 are now " + state + "&7."));
        }
    }
}
