package com.badbones69.crazyenchantments.paper.support.factions.uuid;

import com.badbones69.crazyenchantments.paper.support.api.interfaces.factions.FactionsSupport;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.listeners.FactionsBlockListener;
import com.massivecraft.factions.listeners.FactionsPlayerListener;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.WorldUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class FactionsUUIDSupport extends FactionsSupport<FPlayer, Faction, BlockState, Location> {

    private final FPlayers players = FPlayers.getInstance();

    @Override
    public String getPluginName() {
        return "Factions";
    }

    private FPlayer resolve(final Player player) {
        return this.players.getByPlayer(player);
    }

    private Faction resolveFaction(final Location location) {
        return Board.getInstance().getFactionAt(new FLocation(location));
    }

    // faction unique methods
    @Override
    public boolean isWilderness(final FPlayer player) {
        if (!isPluginReady()) {
            return true;
        }

        final Faction faction = player.getFaction();

        return faction == null || faction.isWilderness();
    }

    @Override
    public boolean isSafezone(final FPlayer player) {
        if (!isPluginReady()) {
            return false;
        }

        final Faction faction = player.getFaction();

        return faction != null && faction.isSafeZone();
    }

    @Override
    public boolean isWarzone(final FPlayer player) {
        if (!isPluginReady()) {
            return false;
        }

        final Faction faction = player.getFaction();

        return faction != null && faction.isWarZone();
    }

    @Override
    public boolean isFactionMember(final Faction faction, final Player player) {
        if (!isPluginReady()) {
            return false;
        }

        if (!WorldUtil.isParticipating(player.getWorld())) {
            return true;
        }

        final FPlayer fPlayer = resolve(player);

        return fPlayer != null && fPlayer.getFactionId().equals(faction.getId());
    }

    // generic methods
    @Override
    public boolean isProtected(final Location location) {
        if (!isPluginReady()) {
            return false;
        }

        return WorldUtil.isParticipating(location.getWorld()) && !resolveFaction(location).isWilderness();
    }

    @Override
    public boolean canBreakBlock(final Player player, final BlockState blockState) {
        if (!isPluginReady()) {
            return true;
        }

        final World world = blockState.getWorld();

        return !WorldUtil.isParticipating(world) || FactionsBlockListener.playerCanBuildDestroyBlock(
                player,
                blockState.getLocation(),
                "destroy",
                true
        );
    }

    @Override
    public boolean canPlaceBlock(final Player player, final BlockState blockState) {
        if (!isPluginReady()) {
            return true;
        }

        final World world = blockState.getWorld();

        return !WorldUtil.isParticipating(world) || FactionsBlockListener.playerCanBuildDestroyBlock(
                player,
                blockState.getLocation(),
                "build",
                true
        );
    }

    @Override
    public boolean canExplodeBlock(final Entity entity, final Location location) {
        if (!isPluginReady()) {
            return true;
        }

        if (!WorldUtil.isParticipating(location.getWorld())) {
            return true;
        }

        final Faction faction = resolveFaction(location);

        if (faction.isWilderness()) {
            return true;
        }

        if (faction.isSafeZone()) {
            return false;
        }

        return !faction.noExplosionsInTerritory();
    }

    @Override
    public boolean canInteract(final Player player, final BlockState blockState) {
        if (!isPluginReady()) {
            return true;
        }

        if (!WorldUtil.isParticipating(blockState.getWorld())) {
            return true;
        }

        return FactionsPlayerListener.canPlayerUseBlock(player, blockState.getBlock(), true);
    }

    @Override
    public boolean canInteract(final Player player, final Location location) {
        if (!isPluginReady()) {
            return true;
        }

        if (!WorldUtil.isParticipating(location.getWorld())) {
            return true;
        }

        return FactionsPlayerListener.canPlayerUseBlock(player, location.getBlock(), true);
    }

    @Override
    public boolean isFriendly(final Entity damager, final Entity target) {
        if (!isPluginReady() ||
                !(damager instanceof Player player) ||
                !(target instanceof Player receiver) ||
                !WorldUtil.isParticipating(damager.getWorld())
        ) {
            return false;
        }

        final FPlayer source = resolve(player);

        if (source == null) {
            return false;
        }

        final FPlayer output = resolve(receiver);

        if (output == null) {
            return false;
        }

        final Faction sourceFaction = source.getFaction();

        if (sourceFaction == null || sourceFaction.isWilderness() || sourceFaction.isSafeZone() || sourceFaction.isWarZone()) {
            return false;
        }

        return sourceFaction.getRelationTo(output).isAtLeast(Relation.ALLY);
    }

    @Override
    public boolean isTerritory(final Player player, final Location location) {
        return isTerritory(player);
    }

    @Override
    public boolean isTerritory(final Player player) {
        if (!isPluginReady()) {
            return false;
        }

        if (!WorldUtil.isParticipating(player.getWorld())) {
            return true;
        }

        final FPlayer fPlayer = resolve(player);

        return fPlayer != null && fPlayer.isInOwnTerritory();
    }

    @Override
    public boolean isCombatEnabled(final Location location) {
        if (!isPluginReady() || !WorldUtil.isParticipating(location.getWorld())) {
            return true;
        }

        final Faction faction = resolveFaction(location);

        if (faction.isSafeZone()) {
            return false;
        }

        return faction.isWarZone() || !faction.noPvPInTerritory();
    }

    @Override
    public boolean isMember(final Player player) {
        if (!isPluginReady() || !WorldUtil.isParticipating(player.getWorld())) {
            return false;
        }

        final Faction faction = resolveFaction(player.getLocation());

        if (faction.isWilderness() || faction.isWarZone() || faction.isSafeZone()) {
            return false;
        }

        return isFactionMember(faction, player);
    }

    @Override
    public boolean isOwner(final Player player) {
        if (!isPluginReady() || !WorldUtil.isParticipating(player.getWorld())) {
            return false;
        }

        final FPlayer fPlayer = resolve(player);

        if (fPlayer == null) {
            return false;
        }

        if (isWilderness(fPlayer) || isWarzone(fPlayer) || isSafezone(fPlayer)) {
            return false;
        }

        final Role role = fPlayer.getRole();

        return role != null && role.isAtLeast(Role.COLEADER);
    }
}
