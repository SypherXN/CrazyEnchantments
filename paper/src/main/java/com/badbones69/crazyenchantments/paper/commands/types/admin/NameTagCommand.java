package com.badbones69.crazyenchantments.paper.commands.types.admin;

import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.commands.EnchantCommand;
import com.badbones69.crazyenchantments.paper.listeners.NameTagListener;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Join;
import dev.triumphteam.cmd.core.annotations.Optional;
import dev.triumphteam.cmd.core.annotations.Suggestion;
import dev.triumphteam.cmd.core.annotations.Syntax;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import java.util.HashMap;
import java.util.Map;

public class NameTagCommand extends EnchantCommand {

    /** One full storage worth; a grant larger than this would only become ground items. */
    private static final int MAX_GRANT = 36 * 64;

    private final NameTagListener nameTags = this.starter.getNameTagListener();

    @Command("nametag")
    @Permission(value = "crazyenchantments.nametag", def = PermissionDefault.OP)
    @Syntax("/crazyenchantments nametag <amount> [player]")
    public void give(final CommandSender sender, @Suggestion("numbers") final int amount, @Optional final Player target) {
        final Player player = target != null ? target : sender instanceof Player self ? self : null;

        if (player == null) {
            sender.sendMessage(Messages.PLAYERS_ONLY.getMessage());

            return;
        }

        final int count = Math.min(MAX_GRANT, Math.max(1, amount));

        // Deliver on the region that owns the receiving player, never on the caller's.
        player.getScheduler().run(this.plugin, task -> deliver(sender, player, count), () -> sender.sendMessage(Messages.NOT_ONLINE.getMessage()));
    }

    @Command("nametagset")
    @Syntax("/crazyenchantments nametagset <token> <name>")
    public void setName(final Player player, final String token, @Join final String name) {
        player.getScheduler().run(this.plugin, task -> this.nameTags.preview(player, token, name), null);
    }

    @Command("nametagconfirm")
    @Syntax("/crazyenchantments nametagconfirm <token>")
    public void confirm(final Player player, final String token) {
        player.getScheduler().run(this.plugin, task -> this.nameTags.confirm(player, token), null);
    }

    @Command("nametagcancel")
    @Syntax("/crazyenchantments nametagcancel <token>")
    public void cancel(final Player player, final String token) {
        player.getScheduler().run(this.plugin, task -> this.nameTags.cancel(player, token), null);
    }

    private void deliver(final CommandSender sender, final Player player, final int count) {
        final NameTagListener.Delivery delivery = this.nameTags.deliver(player, count);

        if (delivery.delivered() <= 0) {
            player.sendMessage(Messages.INVENTORY_FULL.getMessage());
            if (!player.equals(sender)) sender.sendMessage(Messages.INVENTORY_FULL.getMessage());

            return;
        }

        final Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%amount%", String.valueOf(delivery.delivered()));
        placeholders.put("%player%", player.getName());

        player.sendMessage(Messages.GET_NAMETAG.getMessage(placeholders));
        sender.sendMessage(Messages.GIVE_NAMETAG.getMessage(placeholders));

        if (delivery.dropped() <= 0) return;

        final Map<String, String> partial = new HashMap<>();

        partial.put("%delivered%", String.valueOf(delivery.delivered()));
        partial.put("%requested%", String.valueOf(delivery.requested()));

        player.sendMessage(Messages.NAMETAG_GRANT_PARTIAL.getMessage(partial));

        if (!player.equals(sender)) sender.sendMessage(Messages.NAMETAG_GRANT_PARTIAL.getMessage(partial));
    }
}
