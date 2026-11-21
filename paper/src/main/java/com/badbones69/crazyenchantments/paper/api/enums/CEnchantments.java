package com.badbones69.crazyenchantments.paper.api.enums;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.api.objects.enchants.EnchantmentType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public enum CEnchantments {

    //	----------------Boots----------------  \\
    GEARS("Gears", "Boots"),
    WINGS("Wings", "Boots"),
    ADRENALINE("Ender Shift", "Helmet-and-Boots"),
    ROCKET("Rocket Escape", "Boots"),
    SPRINGS("Springs", "Boots"),
    ANTIGRAVITY("Anti Gravity", "Boots"),
    ENDER_WALKER("Ender Walker", "Boots"),
    //	----------------Bows----------------  \\
    BOOM("Boom", "Bow", 20, 10),
    PULL("Pull", "Bow", 25, 10),
    VENOM("Venom", "Bow", 10, 5),
    DOCTOR("Healing", "Bow"),
    PIERCING("Piercing", "Bow", 5, 5),
    SNARE("Snare", "Bow", 25, 10),
    LIGHTNING("Lightning", "Bow", 25, 10),
    MULTIARROW("Multi Arrow", "Bow", 25, 10),
    STICKY_SHOT("Sticky Shot", "Bow", 10, 10),
    POISON_SHOT("Poison Shot", "Bow", 25, 5),
    UNFOCUS("Unfocus", "Bow", 8, 3),
    ARROW_LIFESTEAL("Arrow Lifesteal", "Bow", 5, 3),
    PACIFY("Pacify", "Bow", 10, 5),
    LONGBOW("Longbow", "Bow"),
    EAGLE_EYE("Eagle Eye", "Bow", 6, 3),
    INFERNAL("Infernal", "Bow", 6, 3),
    EXPLOSIVE("Explosive", "Bow", 8, 3),
    HELLFIRE("Hellfire", "Bow"),
    SNIPER("Sniper", "Bow"),
    VIRUS("Virus", "Bow", 8, 4),
    TELEPORTATION("Teleportation", "Bow", 8, 4),
    FARCAST("Farcast", "Bow", 8, 4),
    //	----------------Helmets----------------  \\
    GLOWING("Glowing", "Helmet"),
    MERMAID("Aquatic", "Helmet"),
    IMPLANTS("Implants", "Helmet"),
    COMMANDER("Commander", "Helmet"),
    //	----------------Swords----------------  \\
    TRAP("Trap", "Sword", 10, 5),
    RAGE("Rage", "Sword"),
    VIPER("Poison", "Sword", 10, 5),
    SLOWMO("Ice Aspect", "Sword", 5, 5),
    VAMPIRE("Vampire", "Sword", 5, 5),
    EXECUTE("Execute", "Sword"),
    FASTTURN("Fast Turn", "Sword", 5, 5),
    HEADLESS("Headless", "Sword", 10, 10),
    PARALYZE("Paralyze", "Sword", 15, 5),
    BLINDNESS("Blind", "Sword", 5, 1),
    LIFESTEAL("Lifesteal", "Sword", 15, 5),
    NUTRITION("Nutrition", "Sword", 15, 5),
    SKILLSWIPE("Skill Swipe", "Sword", 5, 5),
    OBLITERATE("Obliterate", "Sword", 10, 5),
    INQUISITIVE("Inquisitive", "Sword", 50, 25),
    LIGHTWEIGHT("Featherweight", "Sword", 15, 5),
    CHARGE("Charge", "Sword"),
    REVENGE("Revenge", "Sword"),
    FAMISHED("Famished", "Sword", 10, 5),
    ASSASSIN("Assassin", "Sword"),
    ENRAGE("Enrage", "Sword"),
    BLOCK("Block", "Sword", 6, 3),
    THUNDERING_BLOW("Thundering Blow", "Sword", 5, 3),
    DOUBLE_STRIKE("Double Strike", "Sword", 5, 3),
    INVERSION("Inversion", "Sword", 4, 2),
    DISINTEGRATE("Disintegrate", "Sword", 5, 3),
    GREATSWORD("Greatsword", "Sword"),
    //	----------------Armor----------------  \\
    HULK("Hulk", "Armor"),
    VALOR("Valor", "Armor"),
    WITHER("Wither", "Armor", 10, 3),
    DRUNK("Drunk", "Armor"),
    NINJA("Ninja", "Armor"),
    ANGEL("Protection", "Armor"),
    TAMER("Tamer", "Armor"),
    GUARDS("Guardians", "Armor"),
    VOODOO("Voodoo", "Armor", 15, 5),
    MOLTEN("Molten", "Armor", 10, 1),
    SAVIOR("Diminish", "Armor", 15, 5),
    CACTUS("Cactus", "Armor", 25, 25),
    FREEZE("Frozen", "Armor", 10, 5),
    RECOVER("Recover", "Armor"),
    NURSERY("Nursery", "Armor", 5, 5),
    RADIANT("Radiant", "Armor"),
    OVERLOAD("Overload", "Armor"),
    BLIZZARD("Blizzard", "Armor"),
    INSOMNIA("Insomnia", "Sword", 10, 5),
    ACIDRAIN("Acid Rain", "Armor", 5, 5),
    SANDSTORM("Sand Storm", "Armor", 5, 5),
    SMOKEBOMB("Smoke Bomb", "Armor", 5, 5),
    PAINGIVER("Poisoned", "Armor", 10, 5),
    INTIMIDATE("Intimidate", "Armor"),
    BURNSHIELD("Obsidianshield", "Armor"),
    LEADERSHIP("Leadership", "Armor", 10, 5),
    INFESTATION("Infestation", "Armor"),
    NECROMANCER("Undead Ruse", "Armor"),
    STORMCALLER("Stormcaller", "Armor", 10, 5),
    ENLIGHTENED("Enlightened", "Armor", 10, 5),
    BLOODLUST("Blood Lust", "Chestplate", 5, 3),
    SELFDESTRUCT("Self Destruct", "Armor"),
    CYBORG("Cyborg", "Armor"),
    BEEKEEPER("Bee Keeper", "Armor"),
    MANEUVER("Dodge", "Armor", 10, 5),
    CROUCH("Crouch", "Armor", 10, 5),
    SHOCKWAVE("Shockwave", "Armor", 10, 5),
    SYSTEMREBOOT("Death God", "Armor", 10, 5),
    ARMORED("Armored", "Armor"),
    HEAVY("Heavy", "Armor"),
    TANK("Tank", "Armor"),
    MARKSMAN("Marksman", "Armor"),
    DEATHBRINGER("Deathbringer", "Armor", 3, 2),
    CLARITY("Clarity", "Armor"),
    ARROW_DEFLECT("Arrow Deflect", "Armor"),
    STICKY("Sticky", "Armor"),
    AEGIS("Aegis", "Armor"),
    CREEPER_ARMOR("Creeper Armor", "Armor"),
    HARDENED("Hardened", "Armor"),
    RAGDOLL("Ragdoll", "Armor"),
    METAPHYSICAL("Metaphysical", "Boots"),
    ANGELIC("Angelic", "Armor", 6, 3),
    SPIRITS("Spirits", "Armor", 4, 1),
    SPIRIT_LINK("Spirit Link", "Chestplate", 10, 5),
    LIFEBLOOM("Lifebloom", "Leggings", 10, 5),
    CURSE("Curse", "Chestplate"),
    DESTRUCTION("Destruction", "Helmet", 10, 5),
    PLAGUE_CARRIER("Plague Carrier", "Leggings"),
    FEIGN_DEATH("Feign Death", "Armor", 5, 5),
    //	----------------Axes----------------  \\
    REKT("Rekt", "Axe", 5, 1),
    CONFUSION("Confusion", "Axe", 10, 5),
    CURSED("Cursed", "Axe", 10, 5),
    FEEDME("Ravenous", "Axe", 10, 5),
    BERSERK("Berserk", "Axe", 10, 1),
    BLESSED("Blessed", "Axe", 10, 5),
    BLEED("Bleed", "Axe", 8, 2),
    DEVOUR("Devour", "Axe"),
    DECAPITATION("Decapitation", "Axe", 10, 10),
    BATTLECRY("Battle Cry", "Axe", 10, 5),
    DEMONFORGED("Demon Forged", "Sword-and-Axe", 10, 5),
    TREEFELLER("Tree Feller", "Axe"),
    PUMMEL("Pummel", "Axe", 8, 4),
    CLEAVE("Cleave", "Axe", 12, 2),
    ARROW_BREAK("Arrow Break", "Axe", 10, 5),
    BLACKSMITH("Blacksmith", "Axe", 6, 3),
    ANTI_GANK("Anti Gank", "Axe"),
    BARBARIAN("Barbarian", "Axe"),
    INSANITY("Insanity", "Axe"),
    ROGUE("Rogue", "Axe"),
    //	----------------PickAxes----------------  \\
    VEINMINER("Vein Miner", "Pickaxe"),
    BLAST("Detonate", "Pickaxe"),
    AUTOSMELT("Auto Smelt", "Pickaxe", 25, 25),
    EXPERIENCE("Experience", "Pickaxe", 25, 25),
    FUSE("Fuse", "Pickaxe"),
    OBSIDIAN_DESTROYER("Obsidian Destroyer", "Pickaxe", 10, 5),
    AUTO_SELL("Auto Sell", "Pickaxe"),
    //	----------------Tools----------------  \\
    HASTE("Haste", "Tool"),
    TELEPATHY("Telepathy", "Tool"),
    OXYGENATE("Oxygenate", "Tool"),
    REFORGED("Reforged", "Weapons-and-Tools", 4, 4),
    //	----------------Hoes----------------  \\
    GREENTHUMB("Green Thumb", "Hoe", 10, 10),
    HARVESTER("Harvester", "Hoe"),
    TILLER("Tiller", "Hoe"),
    PLANTER("Planter", "Hoe"),
    //	----------------All----------------  \\
    SHACKLE("Shackle", "Weapons", 50, 25),
    KILL_AURA("Kill Aura", "Sword", 3, 1),
    DEEP_WOUNDS("Deep Wounds", "Axe"),
    SKILLING("Skilling", "Tool"),
    TRAINING("Training", "Weapons"),
    HELLFORGED("Hell Forged", "Damaged-Items", 5, 5);

    @NotNull
    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    @NotNull
    private final Starter starter = this.plugin.getStarter();

    @NotNull
    private final Methods methods = this.starter.getMethods();

    private final String name;
    private final String typeName;
    private final boolean hasChanceSystem;
    private final int chance;
    private final int chanceIncrease;

    /** @deprecated Registry lookups now follow the published snapshot without a separate cache. */
    @Deprecated
    public static void invalidateCachedEnchants() {
    }

    /**
     * Looks an enchantment up by its display name, which may contain spaces.
     *
     * @param name Display name of the enchantment.
     * @return The matching enchantment, or {@code null} when none has that display name.
     */
    public static CEnchantments fromName(@NotNull String name) {
        for (CEnchantments value : values()) {
            if (value.name.equalsIgnoreCase(name)) return value;
        }

        return null;
    }

    /**
     * @param name Name of the enchantment.
     * @param typeName Type of items it goes on.
     */
    CEnchantments(String name, String typeName) {
        this.name = name;
        this.typeName = typeName;
        this.chance = 0;
        this.chanceIncrease = 0;
        this.hasChanceSystem = false;
    }

    /**
     * @param name Name of the enchantment.
     * @param typeName Type of items it goes on.
     * @param chance The chance the enchantment has to active.
     * @param chanceIncrease The amount the chance increases by every level.
     */
    CEnchantments(String name, String typeName, int chance, int chanceIncrease) {
        this.name = name;
        this.typeName = typeName;
        this.chance = chance;
        this.chanceIncrease = chanceIncrease;
        this.hasChanceSystem = true;
    }

    
    /**
     * @return The name of the enchantment.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return The custom name in the Enchantment.yml.
     */
    public String getCustomName() {
        return getEnchantment().getCustomName();
    }

    /**
     * Get the chance the enchantment will activate.
     * Not all enchantments have a chance to activate.
     *
     * @return The chance of the enchantment activating.
     */
    public int getChance() {
        return this.chance;
    }

    /**
     * Get the amount the enchantment chance increases by every level.
     * Not all enchantments have a chance to activate.
     *
     * @return The amount the chance increases by every level.
     */
    public int getChanceIncrease() {
        return this.chanceIncrease;
    }

    /**
     * @return The description of the enchantment in the Enchantments.yml.
     */
    public List<String> getDescription() {
        return getEnchantment().getInfoDescription();
    }

    /**
     * @return The type the enchantment is.
     */
    public EnchantmentType getType() {
        final CEnchantment enchantment = getEnchantment();
        return enchantment == null || enchantment.getEnchantmentType() == null
                ? getDefaultType() : enchantment.getEnchantmentType();
    }

    public EnchantmentType getDefaultType() {
        return this.methods.getFromName(this.typeName);
    }

    /**
     * @return True if the enchantment is enabled and false if not.
     */
    public boolean isActivated() {
        final CEnchantment enchantment = getEnchantment();
        return enchantment != null && enchantment.isActivated();
    }

    /**
     * Get the enchantment that this is tied to.
     * @return The enchantment this is tied to.
     */
    public CEnchantment getEnchantment() {
        return this.starter.getEnchantmentBookSettings().getRegisteredEnchantment(this.name);
    }

    /**
     * Check to see if the enchantment's chance is successful.
     * @return True if the chance was successful and false if not.
     */
    public boolean chanceSuccessful(int level) {
        return this.chanceSuccessful(level, 1.0);
    }

    /**
     * Check to see if the enchantment's chance is successful.
     * @return True if the chance was successful and false if not.
     */
    public boolean chanceSuccessful(int level, double multiplier) {
        return getEnchantment().chanceSuccessful(level, multiplier);
    }

    /**
     * Check if the CEnchantments uses a chance system.
     */
    public boolean hasChanceSystem() {
        return this.hasChanceSystem;
    }
}
