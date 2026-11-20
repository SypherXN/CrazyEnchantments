package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.utils.McMMOXpLogic;
import com.badbones69.crazyenchantments.paper.api.utils.McMMOXpSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class McMMOExperienceListener implements Listener {

    private final EnchantmentBookSettings books;

    public McMMOExperienceListener(final CrazyEnchantments plugin) {
        this.books = plugin.getStarter().getEnchantmentBookSettings();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onXpGain(final McMMOPlayerXpGainEvent event) {
        // Direct gameplay only: generic API awards default to UNKNOWN; commands and shared awards have separate reasons.
        final XPGainReason reason = event.getXpGainReason();
        if (reason != XPGainReason.PVE && reason != XPGainReason.PVP) return;

        final PrimarySkillType skill = event.getSkill();
        if (skill == null) return;

        final String skillName = skill.name();
        final boolean skilling = McMMOXpLogic.isSkillingSkill(skillName);
        final boolean training = McMMOXpLogic.isTrainingSkill(skillName);
        if (!skilling && !training) return;

        final Player player = event.getPlayer();
        if (event.isAsynchronous() || !Bukkit.isOwnedByCurrentRegion(player)) return;

        final PlayerInventory inventory = player.getInventory();
        final ItemStack mainHand = inventory.getItemInMainHand();
        final ItemStack offHand = inventory.getItemInOffHand();
        final int skillingLevel = skilling ? heldLevel(player, CEnchantments.SKILLING, mainHand, offHand) : 0;
        final int trainingLevel = training ? heldLevel(player, CEnchantments.TRAINING, mainHand, offHand) : 0;
        if (skillingLevel == 0 && trainingLevel == 0) return;

        final McMMOXpSettings tuning = TuningConfig.mcMMOXp();
        final double bonusPp = McMMOXpLogic.combinedBonusPercent(
                McMMOXpLogic.bonusPercent(skilling, skillingLevel, tuning.skillingPerLevelPp()),
                McMMOXpLogic.bonusPercent(training, trainingLevel, tuning.trainingPerLevelPp()));

        // Update the existing award; do not award XP again.
        final float rawXp = event.getRawXpGained();
        final float boostedXp = McMMOXpLogic.applyBonus(rawXp, bonusPp);
        if (boostedXp > rawXp) event.setRawXpGained(boostedXp);
    }

    private int heldLevel(final Player player, final CEnchantments enchant, final ItemStack mainHand, final ItemStack offHand) {
        final CEnchantment key = enchant.getEnchantment();
        if (key == null || !key.isActivated()) return 0;
        // Mirror EnchantUtils: operators bypass the deny permission.
        if (!player.isOp() && player.hasPermission("crazyenchantments.%s.deny".formatted(enchant.getName()))) return 0;

        return McMMOXpLogic.heldLevel(levelInHand(key, mainHand), levelInHand(key, offHand), key.getMaxLevel());
    }

    private int levelInHand(final CEnchantment key, final ItemStack item) {
        if (item == null || item.isEmpty() || !key.canEnchantItem(item)) return 0;

        // getLevel unboxes null if the item has only unrelated CE enchants.
        return this.books.getEnchantments(item).getOrDefault(key, 0);
    }
}
