package com.badbones69.crazyenchantments.paper.api.utils;

import com.badbones69.crazyenchantments.paper.api.bleed.BleedSettings;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reload-safe holder for enchantment tunables. {@link #load} rebuilds
 * every value; invalid values fall back to the shipped default with one
 * warning. No other class keeps mutable tuning state.
 *
 * <p>Values are read from {@code Enchantments.<enchant>.Tuning}. A value that
 * is still only in {@code config.yml}'s CosmicEnchants section is read from
 * there with one deprecation warning, so an upgraded server keeps its numbers.
 * Only {@code CosmicEnchants.DamageModifiers} remains global in config.yml.</p>
 */
public final class TuningConfig {

    public static final int MAX_ANTI_GANK_HISTORY = 32;
    private static final double MAX_AOE_RADIUS = 10.0;
    private static final double MAX_WEAPON_MATCHUP_BONUS_PP = 20.0;
    private static final String LEGACY_PREFIX = "CosmicEnchants.";

    /** Canonical rarity names, lowest tier first. Every load agrees on these. */
    public static final List<String> RARITIES = List.of("Simple", "Unique", "Elite", "Ultimate", "Legendary");

    /** The names used before {@code Rarity-Format: 2}, lowest tier first. */
    public static final List<String> LEGACY_RARITIES = List.of("Common", "Rare", "Legendary", "Ultimate", "Mythical");

    /**
     * Root-level Enchantments.yml key that picks the rarity vocabulary: absent
     * or 1 reads the legacy names, 2 reads the Cosmic names literally. It sits
     * outside the Enchantments section so it can never collide with an enchant
     * key.
     */
    public static final String RARITY_FORMAT = "Rarity-Format";

    /**
     * Format 1 spelling to canonical name. {@code Legendary} is the trap: the
     * legacy name is today's third tier, so it becomes Elite, not the new top
     * tier.
     */
    private static final Map<String, String> LEGACY_TO_CURRENT = Map.of(
            "Common", "Simple",
            "Rare", "Unique",
            "Legendary", "Elite",
            "Ultimate", "Ultimate",
            "Mythical", "Legendary");

    /**
     * The pre-move CosmicEnchants section that owns a set of values, and the
     * Enchantments.yml key those values now live under as
     * {@code Enchantments.<key>.Tuning}. The key must equal the CEnchantments
     * display name because {@code CrazyManager.load()} reads
     * {@code "Enchantments." + name}. The mapping is not derivable by name
     * alone: Sniper is the new SniperHeadshot, DeepWounds is "Deep Wounds",
     * BloodLust is "Blood Lust", CreeperArmor is "Creeper Armor" and AntiGank
     * is "Anti Gank". DamageModifiers is global and stays in config.yml;
     * KillAura shipped empty, so it has no Tuning block and no entry here.
     */
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("Bleed", "Bleed"),
            Map.entry("DeepWounds", "Deep Wounds"),
            Map.entry("Devour", "Devour"),
            Map.entry("BloodLust", "Blood Lust"),
            Map.entry("Armored", "Armored"),
            Map.entry("Heavy", "Heavy"),
            Map.entry("Tank", "Tank"),
            Map.entry("Marksman", "Marksman"),
            Map.entry("Clarity", "Clarity"),
            Map.entry("ArrowDeflect", "Arrow Deflect"),
            Map.entry("EnderWalker", "Ender Walker"),
            Map.entry("Assassin", "Assassin"),
            Map.entry("Enrage", "Enrage"),
            Map.entry("Block", "Block"),
            Map.entry("ThunderingBlow", "Thundering Blow"),
            Map.entry("Pummel", "Pummel"),
            Map.entry("Cleave", "Cleave"),
            Map.entry("Blacksmith", "Blacksmith"),
            Map.entry("AntiGank", "Anti Gank"),
            Map.entry("Barbarian", "Barbarian"),
            Map.entry("Insanity", "Insanity"),
            Map.entry("Rogue", "Rogue"),
            Map.entry("DoubleStrike", "Double Strike"),
            Map.entry("Inversion", "Inversion"),
            Map.entry("Disintegrate", "Disintegrate"),
            Map.entry("Greatsword", "Greatsword"),
            Map.entry("Unfocus", "Unfocus"),
            Map.entry("ArrowLifesteal", "Arrow Lifesteal"),
            Map.entry("Pacify", "Pacify"),
            Map.entry("Longbow", "Longbow"),
            Map.entry("EagleEye", "Eagle Eye"),
            Map.entry("Infernal", "Infernal"),
            Map.entry("Explosive", "Explosive"),
            Map.entry("Hellfire", "Hellfire"),
            Map.entry("Sniper", "SniperHeadshot"),
            Map.entry("Virus", "Virus"),
            Map.entry("Teleportation", "Teleportation"),
            Map.entry("Farcast", "Farcast"),
            Map.entry("Aegis", "Aegis"),
            Map.entry("CreeperArmor", "Creeper Armor"),
            Map.entry("Hardened", "Hardened"),
            Map.entry("Ragdoll", "Ragdoll"),
            Map.entry("Metaphysical", "Metaphysical"),
            Map.entry("Angelic", "Angelic"),
            Map.entry("Spirits", "Spirits"),
            Map.entry("SpiritLink", "Spirit Link"),
            Map.entry("Lifebloom", "Lifebloom"),
            Map.entry("Curse", "Curse"),
            Map.entry("Destruction", "Destruction"),
            Map.entry("PlagueCarrier", "Plague Carrier"),
            Map.entry("FeignDeath", "Feign Death"),
            Map.entry("AutoSell", "Auto Sell"),
            Map.entry("Skilling", "Skilling"),
            Map.entry("Training", "Training"),
            Map.entry("Valor", "Valor"),
            Map.entry("Implants", "Implants"),
            Map.entry("RocketEscape", "Rocket Escape"),
            Map.entry("EnderShift", "Ender Shift"),
            Map.entry("Vampire", "Vampire"),
            Map.entry("Execute", "Execute"),
            Map.entry("Wither", "Wither"),
            Map.entry("DemonForged", "Demon Forged")
    );

    /** @return the legacy section to Enchantments.yml key map, for tests and diagnostics. */
    public static Map<String, String> sectionKeys() {
        return SECTIONS;
    }

    private static volatile BleedSettings bleed = BleedSettings.DEFAULTS;

    private static volatile DeepWoundsSettings deepWounds = DeepWoundsSettings.DEFAULTS;

    private static volatile DevourSettings devour = DevourSettings.DEFAULTS;

    private static volatile BloodLustSettings bloodLust = BloodLustSettings.DEFAULTS;

    private static volatile DamageModifierSettings damageModifiers = DamageModifierSettings.DEFAULTS;

    private static volatile ClaritySettings clarity = ClaritySettings.DEFAULTS;

    private static volatile ArrowDeflectSettings arrowDeflect = ArrowDeflectSettings.DEFAULTS;

    private static volatile EnderWalkerSettings enderWalker = EnderWalkerSettings.DEFAULTS;

    private static volatile AssassinSettings assassin = AssassinSettings.DEFAULTS;

    private static volatile EnrageSettings enrage = EnrageSettings.DEFAULTS;

    private static volatile BlockSettings block = BlockSettings.DEFAULTS;

    private static volatile ThunderingBlowSettings thunderingBlow = ThunderingBlowSettings.DEFAULTS;

    private static volatile PummelSettings pummel = PummelSettings.DEFAULTS;

    private static volatile CleaveSettings cleave = CleaveSettings.DEFAULTS;

    private static volatile BlacksmithSettings blacksmith = BlacksmithSettings.DEFAULTS;

    private static volatile AntiGankSettings antiGank = AntiGankSettings.DEFAULTS;

    private static volatile BarbarianSettings barbarian = BarbarianSettings.DEFAULTS;

    private static volatile InsanitySettings insanity = InsanitySettings.DEFAULTS;

    private static volatile RogueSettings rogue = RogueSettings.DEFAULTS;

    private static volatile DoubleStrikeSettings doubleStrike = DoubleStrikeSettings.DEFAULTS;

    private static volatile InversionSettings inversion = InversionSettings.DEFAULTS;

    private static volatile DisintegrateSettings disintegrate = DisintegrateSettings.DEFAULTS;

    private static volatile GreatswordSettings greatsword = GreatswordSettings.DEFAULTS;

    private static volatile UnfocusSettings unfocus = UnfocusSettings.DEFAULTS;
    private static volatile ArrowLifestealSettings arrowLifesteal = ArrowLifestealSettings.DEFAULTS;
    private static volatile PacifySettings pacify = PacifySettings.DEFAULTS;
    private static volatile LongbowSettings longbow = LongbowSettings.DEFAULTS;
    private static volatile EagleEyeSettings eagleEye = EagleEyeSettings.DEFAULTS;
    private static volatile InfernalSettings infernal = InfernalSettings.DEFAULTS;
    private static volatile ExplosiveSettings explosive = ExplosiveSettings.DEFAULTS;
    private static volatile HellfireSettings hellfire = HellfireSettings.DEFAULTS;
    private static volatile SniperSettings sniper = SniperSettings.DEFAULTS;
    private static volatile VirusSettings virus = VirusSettings.DEFAULTS;
    private static volatile TeleportationSettings teleportation = TeleportationSettings.DEFAULTS;
    private static volatile FarcastSettings farcast = FarcastSettings.DEFAULTS;
    private static volatile AegisSettings aegis = AegisSettings.DEFAULTS;
    private static volatile CreeperArmorSettings creeperArmor = CreeperArmorSettings.DEFAULTS;
    private static volatile HardenedSettings hardened = HardenedSettings.DEFAULTS;
    private static volatile RagdollSettings ragdoll = RagdollSettings.DEFAULTS;
    private static volatile MetaphysicalSettings metaphysical = MetaphysicalSettings.DEFAULTS;
    private static volatile AngelicSettings angelic = AngelicSettings.DEFAULTS;
    private static volatile SpiritsSettings spirits = SpiritsSettings.DEFAULTS;
    private static volatile SpiritLinkSettings spiritLink = SpiritLinkSettings.DEFAULTS;
    private static volatile LifebloomSettings lifebloom = LifebloomSettings.DEFAULTS;

    private static volatile CurseSettings curse = CurseSettings.DEFAULTS;

    private static volatile DestructionSettings destruction = DestructionSettings.DEFAULTS;

    private static volatile PlagueCarrierSettings plagueCarrier = PlagueCarrierSettings.DEFAULTS;

    private static volatile FeignDeathSettings feignDeath = FeignDeathSettings.DEFAULTS;

    private static volatile McMMOXpSettings mcMMOXp = McMMOXpSettings.DEFAULTS;

    private static volatile AutoSellSettings autoSell = AutoSellSettings.DEFAULTS;

    private static volatile ValorSettings valor = ValorSettings.DEFAULTS;
    private static volatile ImplantsSettings implants = ImplantsSettings.DEFAULTS;
    private static volatile RocketEscapeSettings rocketEscape = RocketEscapeSettings.DEFAULTS;
    private static volatile EnderShiftSettings enderShift = EnderShiftSettings.DEFAULTS;
    private static volatile VampireSettings vampire = VampireSettings.DEFAULTS;
    private static volatile ExecuteSettings execute = ExecuteSettings.DEFAULTS;
    private static volatile WitherSettings wither = WitherSettings.DEFAULTS;
    private static volatile DemonForgedSettings demonForged = DemonForgedSettings.DEFAULTS;
    private static volatile ForkEnchantSettings fork = ForkEnchantSettings.DEFAULTS;

    private TuningConfig() {}

    public static ForkEnchantSettings fork() { return fork; }

    public static ValorSettings valor() { return valor; }
    public static ImplantsSettings implants() { return implants; }
    public static RocketEscapeSettings rocketEscape() { return rocketEscape; }
    public static EnderShiftSettings enderShift() { return enderShift; }
    public static VampireSettings vampire() { return vampire; }
    public static ExecuteSettings execute() { return execute; }
    public static WitherSettings wither() { return wither; }
    public static DemonForgedSettings demonForged() { return demonForged; }

    public static AegisSettings aegis() { return aegis; }
    public static CreeperArmorSettings creeperArmor() { return creeperArmor; }
    public static HardenedSettings hardened() { return hardened; }
    public static RagdollSettings ragdoll() { return ragdoll; }
    public static MetaphysicalSettings metaphysical() { return metaphysical; }
    public static AngelicSettings angelic() { return angelic; }
    public static SpiritsSettings spirits() { return spirits; }
    public static SpiritLinkSettings spiritLink() { return spiritLink; }
    public static LifebloomSettings lifebloom() { return lifebloom; }

    public static CurseSettings curse() { return curse; }
    public static DestructionSettings destruction() { return destruction; }
    public static PlagueCarrierSettings plagueCarrier() { return plagueCarrier; }
    public static FeignDeathSettings feignDeath() { return feignDeath; }
    public static McMMOXpSettings mcMMOXp() { return mcMMOXp; }
    public static AutoSellSettings autoSell() { return autoSell; }

    public static UnfocusSettings unfocus() { return unfocus; }
    public static ArrowLifestealSettings arrowLifesteal() { return arrowLifesteal; }
    public static PacifySettings pacify() { return pacify; }
    public static LongbowSettings longbow() { return longbow; }
    public static EagleEyeSettings eagleEye() { return eagleEye; }
    public static InfernalSettings infernal() { return infernal; }
    public static ExplosiveSettings explosive() { return explosive; }
    public static HellfireSettings hellfire() { return hellfire; }
    public static SniperSettings sniper() { return sniper; }
    public static VirusSettings virus() { return virus; }
    public static TeleportationSettings teleportation() { return teleportation; }
    public static FarcastSettings farcast() { return farcast; }

    public static PummelSettings pummel() {
        return pummel;
    }

    public static CleaveSettings cleave() {
        return cleave;
    }

    public static BlacksmithSettings blacksmith() {
        return blacksmith;
    }

    public static AntiGankSettings antiGank() {
        return antiGank;
    }

    public static BarbarianSettings barbarian() {
        return barbarian;
    }

    public static InsanitySettings insanity() {
        return insanity;
    }

    public static RogueSettings rogue() {
        return rogue;
    }

    public static DoubleStrikeSettings doubleStrike() {
        return doubleStrike;
    }

    public static InversionSettings inversion() {
        return inversion;
    }

    public static DisintegrateSettings disintegrate() {
        return disintegrate;
    }

    public static GreatswordSettings greatsword() {
        return greatsword;
    }

    public static AssassinSettings assassin() {
        return assassin;
    }

    public static EnrageSettings enrage() {
        return enrage;
    }

    public static BlockSettings block() {
        return block;
    }

    public static ThunderingBlowSettings thunderingBlow() {
        return thunderingBlow;
    }

    public static BleedSettings bleed() {
        return bleed;
    }

    public static DeepWoundsSettings deepWounds() {
        return deepWounds;
    }

    public static DevourSettings devour() {
        return devour;
    }

    public static BloodLustSettings bloodLust() {
        return bloodLust;
    }

    public static DamageModifierSettings damageModifiers() {
        return damageModifiers;
    }

    public static ClaritySettings clarity() {
        return clarity;
    }

    public static ArrowDeflectSettings arrowDeflect() {
        return arrowDeflect;
    }

    public static EnderWalkerSettings enderWalker() {
        return enderWalker;
    }

    public static void load(final FileConfiguration legacy, final FileConfiguration enchantments, final Consumer<String> warn) {
        final Source config = new Source(legacy::get, legacy::contains, enchantments::get, enchantments::contains, warn);
        fork = ForkEnchantSettings.load(config::get, warn);
        final BowValues values = new BowValues(config::get, warn);
        final BleedSettings defaults = BleedSettings.DEFAULTS;

        bleed = BleedSettings.validated(
                config.getDouble("CosmicEnchants.Bleed.Cadence-Seconds", defaults.cadenceSeconds()),
                config.getDouble("CosmicEnchants.Bleed.Damage-Per-Stack-Per-Tick", defaults.damagePerStackPerTick()),
                config.getInt("CosmicEnchants.Bleed.Stack-Cap", defaults.stackCap()),
                config.getDouble("CosmicEnchants.Bleed.Duration-Seconds", defaults.durationSeconds()),
                config.getDouble("CosmicEnchants.Bleed.Slow-Per-Stack-PP", defaults.slowPerStackPp()),
                config.getDouble("CosmicEnchants.Bleed.Slow-Cap-PP", defaults.slowCapPp()),
                warn
        );

        deepWounds = new DeepWoundsSettings(
                new BowValues(config::get, warn).table("DeepWounds.Factors", DeepWoundsSettings.DEFAULTS.factors(), 1.0, 100.0)
        );

        final DevourSettings devourDefaults = DevourSettings.DEFAULTS;
        devour = DevourSettings.validated(
                config.getDouble("CosmicEnchants.Devour.Level-I", devourDefaults.levelOne()),
                config.getDouble("CosmicEnchants.Devour.Level-II", devourDefaults.levelTwo()),
                config.getDouble("CosmicEnchants.Devour.Level-III", devourDefaults.levelThree()),
                config.getDouble("CosmicEnchants.Devour.Level-IV", devourDefaults.levelFour()),
                warn
        );

        final BloodLustSettings bloodLustDefaults = BloodLustSettings.DEFAULTS;

        bloodLust = BloodLustSettings.validated(
                config.getDouble("CosmicEnchants.BloodLust.Heal", bloodLustDefaults.heal()),
                config.getDouble("CosmicEnchants.BloodLust.Cooldown-Seconds", bloodLustDefaults.cooldownSeconds()),
                (int) values.number("BloodLust.Radius", bloodLustDefaults.radius(), 0.0, MAX_AOE_RADIUS),
                config.getDouble("CosmicEnchants.BloodLust.Chance-Multiplier", bloodLustDefaults.chanceMultiplier()),
                warn
        );

        final DamageModifierSettings damageModifierDefaults = DamageModifierSettings.DEFAULTS;

        damageModifiers = DamageModifierSettings.validated(
                config.getDouble("CosmicEnchants.DamageModifiers.Incoming-Reduction-Cap-PP", damageModifierDefaults.incomingReductionCapPp()),
                values.number("DamageModifiers.Bonus-Damage-Cap-HP", damageModifierDefaults.bonusDamageCapHp(),
                        0.0, DamageModifiers.MAX_CONFIGURED_BONUS_HP),
                config.getDouble("CosmicEnchants.Armored.Reduction-Per-Level-PP", damageModifierDefaults.armoredReductionPerLevelPp()),
                config.getDouble("CosmicEnchants.Heavy.Reduction-Per-Level-PP", damageModifierDefaults.heavyReductionPerLevelPp()),
                config.getDouble("CosmicEnchants.Tank.Reduction-Per-Level-PP", damageModifierDefaults.tankReductionPerLevelPp()),
                config.getDouble("CosmicEnchants.Marksman.Bonus-Per-Level-PP", damageModifierDefaults.marksmanBonusPerLevelPp()),
                config.getDouble("CosmicEnchants.Marksman.Bonus-Cap-PP", damageModifierDefaults.marksmanBonusCapPp()),
                config.getDouble("CosmicEnchants.DamageModifiers.Strength-Damage-Per-Level", damageModifierDefaults.strengthDamagePerLevel()),
                warn
        );

        clarity = ClaritySettings.validated(
                config.getInt("CosmicEnchants.Clarity.Max-Blocked-Level", ClaritySettings.DEFAULTS.maxBlockedLevel()),
                warn
        );

        arrowDeflect = ArrowDeflectSettings.validated(
                config.getInt("CosmicEnchants.ArrowDeflect.Interval-Per-Level-Millis", ArrowDeflectSettings.DEFAULTS.intervalPerLevelMillis()),
                warn
        );

        enderWalker = EnderWalkerSettings.validated(
                config.getDouble("CosmicEnchants.EnderWalker.Heal", EnderWalkerSettings.DEFAULTS.heal()),
                config.getDouble("CosmicEnchants.EnderWalker.Cooldown-Seconds", EnderWalkerSettings.DEFAULTS.cooldownSeconds()),
                warn
        );

        final AssassinSettings assassinDefaults = AssassinSettings.DEFAULTS;

        assassin = AssassinSettings.validated(
                config.getDouble("CosmicEnchants.Assassin.Bonus-Per-Level-PP", assassinDefaults.bonusPerLevelPp()),
                config.getDouble("CosmicEnchants.Assassin.Close-Range-Blocks", assassinDefaults.closeRangeBlocks()),
                config.getDouble("CosmicEnchants.Assassin.Far-Multiplier", assassinDefaults.farMultiplier()),
                warn
        );

        enrage = EnrageSettings.validated(
                config.getDouble("CosmicEnchants.Enrage.PP-Per-Level", EnrageSettings.DEFAULTS.ppPerLevel()),
                warn
        );

        final BlockSettings blockDefaults = BlockSettings.DEFAULTS;

        block = BlockSettings.validated(
                config.getDouble("CosmicEnchants.Block.Level-I", blockDefaults.levelOne()),
                config.getDouble("CosmicEnchants.Block.Level-II", blockDefaults.levelTwo()),
                config.getDouble("CosmicEnchants.Block.Level-III", blockDefaults.levelThree()),
                warn
        );

        thunderingBlow = ThunderingBlowSettings.validated(
                config.getDouble("CosmicEnchants.ThunderingBlow.HP-Per-Level", ThunderingBlowSettings.DEFAULTS.hpPerLevel()),
                warn
        );

        final PummelSettings pummelDefaults = PummelSettings.DEFAULTS;

        pummel = PummelSettings.validated(
                values.number("Pummel.Radius", pummelDefaults.radius(), 0.5, MAX_AOE_RADIUS),
                config.getInt("CosmicEnchants.Pummel.Slowness-Amplifier", pummelDefaults.amplifier()),
                doubles(config, "CosmicEnchants.Pummel.Duration-Seconds", pummelDefaults.durationSeconds()),
                warn
        );

        final CleaveSettings cleaveDefaults = CleaveSettings.DEFAULTS;
        double cleaveBase = values.number("Cleave.Base-Radius", cleaveDefaults.baseRadius(), 0.0, MAX_AOE_RADIUS);
        double cleavePerLevel = values.number("Cleave.Radius-Per-Level", cleaveDefaults.radiusPerLevel(), 0.0,
                MAX_AOE_RADIUS / CleaveSettings.MAX_LEVEL);
        if (cleaveBase + cleavePerLevel * CleaveSettings.MAX_LEVEL > MAX_AOE_RADIUS) {
            warn.accept("Invalid CosmicEnchants.Cleave radii: Base-Radius + Radius-Per-Level × "
                    + CleaveSettings.MAX_LEVEL + " exceeds " + MAX_AOE_RADIUS + " blocks; using both radius defaults.");
            cleaveBase = cleaveDefaults.baseRadius();
            cleavePerLevel = cleaveDefaults.radiusPerLevel();
        }

        cleave = CleaveSettings.validated(
                config.getDouble("CosmicEnchants.Cleave.Splash-Share", cleaveDefaults.splashShare()),
                config.getInt("CosmicEnchants.Cleave.Max-Targets", cleaveDefaults.maxTargets()),
                cleaveBase, cleavePerLevel,
                warn
        );

        final BlacksmithSettings blacksmithDefaults = BlacksmithSettings.DEFAULTS;

        blacksmith = BlacksmithSettings.validated(
                config.getDouble("CosmicEnchants.Blacksmith.Damage-Multiplier", blacksmithDefaults.damageMultiplier()),
                config.contains("CosmicEnchants.Blacksmith.Repair-Points")
                        ? config.getIntegerList("CosmicEnchants.Blacksmith.Repair-Points")
                        : blacksmithDefaults.repairPoints(),
                warn
        );

        final AntiGankSettings antiGankDefaults = AntiGankSettings.DEFAULTS;

        antiGank = AntiGankSettings.validated(
                config.getDouble("CosmicEnchants.AntiGank.Window-Seconds", antiGankDefaults.windowSeconds()),
                config.getDouble("CosmicEnchants.AntiGank.Bonus-Per-Extra-Attacker", antiGankDefaults.bonusPerExtraAttacker()),
                config.getDouble("CosmicEnchants.AntiGank.Cap", antiGankDefaults.cap()),
                warn
        );

        barbarian = BarbarianSettings.validated(
                values.number("Barbarian.Bonus-Per-Level-PP", BarbarianSettings.DEFAULTS.bonusPerLevelPp(),
                        0.0, MAX_WEAPON_MATCHUP_BONUS_PP / BarbarianSettings.MAX_LEVEL),
                warn
        );

        insanity = InsanitySettings.validated(
                values.number("Insanity.Bonus-Per-Level-PP", InsanitySettings.DEFAULTS.bonusPerLevelPp(),
                        0.0, MAX_WEAPON_MATCHUP_BONUS_PP / InsanitySettings.MAX_LEVEL),
                warn
        );

        final RogueSettings rogueDefaults = RogueSettings.DEFAULTS;

        rogue = RogueSettings.validated(
                doubles(config, "CosmicEnchants.Rogue.Rear-Multipliers", rogueDefaults.rearMultipliers()),
                config.getDouble("CosmicEnchants.Rogue.Rear-Angle-Degrees", rogueDefaults.rearAngleDegrees()),
                config.getDouble("CosmicEnchants.Rogue.Cooldown-Seconds", rogueDefaults.cooldownSeconds()),
                warn
        );

        doubleStrike = DoubleStrikeSettings.validated(
                config.getDouble("CosmicEnchants.DoubleStrike.Cooldown-Seconds", DoubleStrikeSettings.DEFAULTS.cooldownSeconds()),
                warn
        );

        final InversionSettings inversionDefaults = InversionSettings.DEFAULTS;

        inversion = InversionSettings.validated(
                doubles(config, "CosmicEnchants.Inversion.Heal", inversionDefaults.healByLevel()),
                config.getDouble("CosmicEnchants.Inversion.Cooldown-Seconds", inversionDefaults.cooldownSeconds()),
                warn
        );

        final DisintegrateSettings disintegrateDefaults = DisintegrateSettings.DEFAULTS;

        disintegrate = DisintegrateSettings.validated(
                config.getDouble("CosmicEnchants.Disintegrate.Damage-Per-Point", disintegrateDefaults.damagePerPoint()),
                config.getInt("CosmicEnchants.Disintegrate.Cap-Per-Piece", disintegrateDefaults.capPerPiece()),
                warn
        );

        greatsword = GreatswordSettings.validated(
                values.number("Greatsword.Bonus-Per-Level-PP", GreatswordSettings.DEFAULTS.bonusPerLevelPp(),
                        0.0, MAX_WEAPON_MATCHUP_BONUS_PP / GreatswordSettings.MAX_LEVEL),
                warn
        );

        loadBow(config, warn);
        loadArmor(config, warn);
        loadCosmicReworks(config, warn);
        loadMcMMOXp(config::get, warn);

        autoSell = AutoSellSettings.validated(config.get("CosmicEnchants.AutoSell.Message"), warn);
    }

    static void loadMcMMOXp(final Function<String, Object> get, final Consumer<String> warn) {
        final double fallback = McMMOXpSettings.DEFAULT_PER_LEVEL_PP;
        final BowValues values = new BowValues(path -> {
            final Object raw = get.apply(path);
            return raw == null ? fallback : raw;
        }, warn);
        mcMMOXp = new McMMOXpSettings(
                values.number("Skilling.Bonus-Per-Level-PP", fallback, 0.0, McMMOXpLogic.MAX_BONUS_PP),
                values.number("Training.Bonus-Per-Level-PP", fallback, 0.0, McMMOXpLogic.MAX_BONUS_PP));
    }

    private static void loadArmor(final Source config, final Consumer<String> warn) {
        final BowValues values = new BowValues(config::get, warn);

        final AegisSettings aegisDefaults = AegisSettings.DEFAULTS;

        aegis = new AegisSettings(
                values.number("Aegis.Window-Seconds", aegisDefaults.windowSeconds(), 0.001, 600.0),
                values.number("Aegis.Damage-Multiplier", aegisDefaults.damageMultiplier(), 0.0, 1.0));

        final CreeperArmorSettings creeperDefaults = CreeperArmorSettings.DEFAULTS;

        creeperArmor = new CreeperArmorSettings(
                values.table("CreeperArmor.Reduction-PP", creeperDefaults.reductionPp(), 0.0, 100.0),
                values.number("CreeperArmor.Heal", creeperDefaults.healHp(), 0.0, 100.0),
                values.number("CreeperArmor.Heal-Cooldown-Seconds", creeperDefaults.healCooldownSeconds(), 0.001, 600.0));

        hardened = new HardenedSettings(
                values.table("Hardened.Skip-PP", HardenedSettings.DEFAULTS.skipPp(), 0.0, 100.0));

        final RagdollSettings ragdollDefaults = RagdollSettings.DEFAULTS;

        ragdoll = new RagdollSettings(
                values.table("Ragdoll.Horizontal-Velocity", ragdollDefaults.horizontal(), 0.0, 10.0),
                values.number("Ragdoll.Vertical-Velocity", ragdollDefaults.vertical(), 0.0, 10.0),
                values.number("Ragdoll.Cooldown-Seconds", ragdollDefaults.cooldownSeconds(), 0.0, 600.0));

        metaphysical = new MetaphysicalSettings(
                values.table("Metaphysical.Resist-PP", MetaphysicalSettings.DEFAULTS.resistPp(), 0.0, 100.0));

        final AngelicSettings angelicDefaults = AngelicSettings.DEFAULTS;
        angelic = new AngelicSettings(
                values.number("Angelic.Heal-Per-Second", angelicDefaults.healPerSecond(), 0.0, 100.0),
                values.number("Angelic.Duration-Seconds", angelicDefaults.durationSeconds(), 1.0, 600.0));

        final SpiritsSettings spiritsDefaults = SpiritsSettings.DEFAULTS;
        spirits = new SpiritsSettings(
                values.number("Spirits.Radius", spiritsDefaults.radius(), 0.5, MAX_AOE_RADIUS),
                values.number("Spirits.Heal", spiritsDefaults.heal(), 0.0, 100.0),
                values.number("Spirits.Cadence-Seconds", spiritsDefaults.cadenceSeconds(), 0.05, 600.0),
                values.number("Spirits.Lifetime-Seconds", spiritsDefaults.lifetimeSeconds(), 0.05, 600.0),
                values.number("Spirits.Cooldown-Seconds", spiritsDefaults.cooldownSeconds(), 0.05, 600.0));

        final SpiritLinkSettings spiritLinkDefaults = SpiritLinkSettings.DEFAULTS;
        spiritLink = new SpiritLinkSettings(
                values.number("SpiritLink.Radius", spiritLinkDefaults.radius(), 0.5, MAX_AOE_RADIUS),
                values.number("SpiritLink.Base-Heal", spiritLinkDefaults.baseHeal(), 0.0, 100.0),
                values.number("SpiritLink.Heal-Per-Level", spiritLinkDefaults.healPerLevel(), 0.0, 100.0),
                values.number("SpiritLink.Cooldown-Seconds", spiritLinkDefaults.cooldownSeconds(), 0.05, 600.0));

        lifebloom = new LifebloomSettings(
                values.number("Lifebloom.Radius", LifebloomSettings.DEFAULTS.radius(), 0.5, MAX_AOE_RADIUS));

        final CurseSettings curseDefaults = CurseSettings.DEFAULTS;

        curse = new CurseSettings(
                values.table("Curse.Trigger-PP", curseDefaults.triggerPp(), 0.0, 100.0),
                values.number("Curse.Hysteresis-PP", curseDefaults.hysteresisPp(), 0.0, 100.0));

        final DestructionSettings destructionDefaults = DestructionSettings.DEFAULTS;

        destruction = new DestructionSettings(
                values.number("Destruction.Cadence-Seconds", destructionDefaults.cadenceSeconds(), 0.05, 600.0),
                values.number("Destruction.Radius", destructionDefaults.radiusBlocks(), 0.5, MAX_AOE_RADIUS),
                values.number("Destruction.Damage-HP", destructionDefaults.damageHp(), 0.0, 100.0),
                values.bool("Destruction.Apply-Slowness", destructionDefaults.applySlowness()),
                values.number("Destruction.Slowness-Seconds", destructionDefaults.slownessSeconds(), 0.05, 600.0));

        final PlagueCarrierSettings plagueDefaults = PlagueCarrierSettings.DEFAULTS;

        plagueCarrier = new PlagueCarrierSettings(
                values.number("PlagueCarrier.Trigger-PP", plagueDefaults.triggerPp(), 0.0, 100.0),
                values.number("PlagueCarrier.Rearm-PP", plagueDefaults.rearmPp(), 0.0, 100.0),
                values.number("PlagueCarrier.Cooldown-Seconds", plagueDefaults.cooldownSeconds(), 0.0, 600.0),
                values.number("PlagueCarrier.Fuse-Seconds", plagueDefaults.fuseSeconds(), 0.05, 600.0),
                values.number("PlagueCarrier.Explosion-Radius", plagueDefaults.explosionRadius(), 0.5, MAX_AOE_RADIUS),
                values.number("PlagueCarrier.Explosion-Damage-HP", plagueDefaults.explosionDamageHp(), 0.0, 100.0),
                values.number("PlagueCarrier.Poison-Radius", plagueDefaults.poisonRadius(), 0.5, MAX_AOE_RADIUS),
                values.number("PlagueCarrier.Poison-Seconds", plagueDefaults.poisonSeconds(), 0.05, 600.0));

        final FeignDeathSettings feignDefaults = FeignDeathSettings.DEFAULTS;

        feignDeath = new FeignDeathSettings(
                values.number("FeignDeath.Health-PP", feignDefaults.healthPp(), 0.0, 100.0),
                values.number("FeignDeath.Duration-Seconds", feignDefaults.durationSeconds(),
                        0.05, FeignDeathSettings.MAX_DURATION_SECONDS),
                values.number("FeignDeath.Cooldown-Seconds", feignDefaults.cooldownSeconds(), 0.0, 600.0));
    }

    /**
     * The eight CE enchants that now behave as their CosmicPvP counterparts: Valor,
     * Implants, Rocket Escape, Ender Shift, Vampire, Execute, Wither and DemonForged.
     */
    private static void loadCosmicReworks(final Source config, final Consumer<String> warn) {
        final BowValues values = new BowValues(config::get, warn);

        final ValorSettings valorDefaults = ValorSettings.DEFAULTS;

        valor = new ValorSettings(
                values.number("Valor.Reduction-Per-Level-PP", valorDefaults.reductionPerLevelPp(), 0.0, 20.0));

        final ImplantsSettings implantsDefaults = ImplantsSettings.DEFAULTS;

        implants = new ImplantsSettings(
                values.table("Implants.Interval-Seconds", implantsDefaults.intervalSeconds(), 1.0, 600.0),
                values.number("Implants.Heal", implantsDefaults.healHp(), 0.0, 20.0),
                (int) values.number("Implants.Hunger", implantsDefaults.hunger(), 0.0, ImplantsSettings.MAX_FOOD));

        final RocketEscapeSettings rocketDefaults = RocketEscapeSettings.DEFAULTS;

        rocketEscape = new RocketEscapeSettings(
                values.number("RocketEscape.Trigger-PP", rocketDefaults.triggerPp(), 0.0, 100.0),
                values.table("RocketEscape.Velocity", rocketDefaults.velocity(), 0.0, 5.0),
                values.table("RocketEscape.Cooldown-Seconds", rocketDefaults.cooldownSeconds(), 0.0, 3_600.0));

        final EnderShiftSettings shiftDefaults = EnderShiftSettings.DEFAULTS;
        double shiftTrigger = values.number("EnderShift.Trigger-PP", shiftDefaults.triggerPp(), 0.0, 100.0);
        double shiftClear = values.number("EnderShift.Clear-PP", shiftDefaults.clearPp(), 0.0, 100.0);

        if (shiftClear < shiftTrigger) {
            warn.accept("Invalid CosmicEnchants.EnderShift cuts: Clear-PP " + shiftClear + " is below Trigger-PP "
                    + shiftTrigger + "; using " + shiftDefaults.triggerPp() + " and " + shiftDefaults.clearPp() + ".");
            shiftTrigger = shiftDefaults.triggerPp();
            shiftClear = shiftDefaults.clearPp();
        }

        enderShift = new EnderShiftSettings(
                shiftTrigger,
                shiftClear,
                values.table("EnderShift.Speed-Amplifier", shiftDefaults.speedAmplifier(), 0.0, 10.0),
                (int) values.number("EnderShift.Health-Boost-Amplifier", shiftDefaults.healthBoostAmplifier(), 0.0, 10.0),
                values.number("EnderShift.Effect-Seconds", shiftDefaults.effectSeconds(), 1.0, 600.0));

        final VampireSettings vampireDefaults = VampireSettings.DEFAULTS;

        vampire = new VampireSettings(
                values.table("Vampire.Heal", vampireDefaults.healHp(), 0.0, VampireSettings.MAX_HEAL_HP),
                values.number("Vampire.Delay-Seconds", vampireDefaults.delaySeconds(), 0.05, 60.0));

        final ExecuteSettings executeDefaults = ExecuteSettings.DEFAULTS;

        execute = new ExecuteSettings(
                values.number("Execute.Threshold-PP", executeDefaults.thresholdPp(), 0.0, 100.0),
                values.number("Execute.Bonus-Per-Level-PP", executeDefaults.bonusPerLevelPp(),
                        0.0, 100.0 / ExecuteSettings.MAX_LEVEL));

        final WitherSettings witherDefaults = WitherSettings.DEFAULTS;

        wither = new WitherSettings(
                (int) values.number("Wither.Amplifier", witherDefaults.amplifier(), 0.0, 10.0),
                values.number("Wither.Duration-Seconds", witherDefaults.durationSeconds(), 0.05, 600.0));

        demonForged = new DemonForgedSettings(
                (int) values.number("DemonForged.Durability-Points", DemonForgedSettings.DEFAULTS.durabilityPoints(),
                        1.0, DemonForgedSettings.MAX_POINTS));
    }

    private static void loadBow(final Source config, final Consumer<String> warn) {
        final BowValues values = new BowValues(config::get, warn);
        final UnfocusSettings unfocusDefaults = UnfocusSettings.DEFAULTS;
        final ArrowLifestealSettings lifestealDefaults = ArrowLifestealSettings.DEFAULTS;
        final InfernalSettings infernalDefaults = InfernalSettings.DEFAULTS;
        final ExplosiveSettings explosiveDefaults = ExplosiveSettings.DEFAULTS;
        final HellfireSettings hellfireDefaults = HellfireSettings.DEFAULTS;

        unfocus = new UnfocusSettings(
                values.table("Unfocus.Reduction-PP", unfocusDefaults.reductionPp(), 0.0, 100.0),
                values.table("Unfocus.Duration-Seconds", unfocusDefaults.durationSeconds(), 0.001, 600.0));
        arrowLifesteal = new ArrowLifestealSettings(
                values.number("ArrowLifesteal.Damage-Share", lifestealDefaults.damageShare(), 0.0, 1.0),
                values.number("ArrowLifesteal.Cap-HP", lifestealDefaults.capHp(), 0.0, 100.0));
        pacify = new PacifySettings(
                values.table("Pacify.Duration-Seconds", PacifySettings.DEFAULTS.durationSeconds(), 0.001, 600.0));
        longbow = new LongbowSettings(
                values.number("Longbow.Bonus-Per-Level-PP", LongbowSettings.DEFAULTS.bonusPerLevelPp(), 0.0, 100.0));
        eagleEye = new EagleEyeSettings(
                values.number("EagleEye.Band-Width-Blocks", EagleEyeSettings.DEFAULTS.bandWidthBlocks(), 0.01, 1_000.0));
        infernal = new InfernalSettings(
                values.number("Infernal.Radius", infernalDefaults.radius(), 0.5, MAX_AOE_RADIUS),
                values.number("Infernal.Damage-HP", infernalDefaults.damageHp(), 0.0, 100.0),
                values.table("Infernal.Ignite-Seconds", infernalDefaults.igniteSeconds(), 0.0, 600.0));
        explosive = new ExplosiveSettings(
                values.number("Explosive.Radius", explosiveDefaults.radius(), 0.5, MAX_AOE_RADIUS),
                values.number("Explosive.Base-Damage-HP", explosiveDefaults.baseDamageHp(), 0.0, 100.0),
                values.number("Explosive.Damage-Per-Level-HP", explosiveDefaults.damagePerLevelHp(), 0.0, 100.0));
        hellfire = new HellfireSettings(
                values.number("Hellfire.Radius", hellfireDefaults.radius(), 0.5, MAX_AOE_RADIUS),
                values.number("Hellfire.Base-Damage-HP", hellfireDefaults.baseDamageHp(), 0.0, 100.0),
                values.number("Hellfire.Damage-Per-Level-HP", hellfireDefaults.damagePerLevelHp(), 0.0, 100.0),
                values.number("Hellfire.Ignite-Seconds", hellfireDefaults.igniteSeconds(), 0.0, 600.0));

        final SniperSettings sniperDefaults = SniperSettings.DEFAULTS;

        sniper = new SniperSettings(
                values.table("Sniper.Multipliers", sniperDefaults.multipliers(), 1.0, 100.0),
                values.number("Sniper.Min-Draw-Force", sniperDefaults.minDrawForce(), 0.0, 1.0),
                values.number("Sniper.Head-Fraction", sniperDefaults.headFraction(), 0.01, 1.0),
                values.number("Sniper.Force-TTL-Seconds", sniperDefaults.forceTtlSeconds(), 0.001, 600.0));

        final VirusSettings virusDefaults = VirusSettings.DEFAULTS;

        virus = new VirusSettings(
                values.table("Virus.Extension-PP", virusDefaults.extensionPp(), 0.0, 1_000.0),
                values.number("Virus.Max-Remaining-Seconds", virusDefaults.maxRemainingSeconds(), 0.001, 600.0));

        final TeleportationSettings teleportationDefaults = TeleportationSettings.DEFAULTS;

        teleportation = new TeleportationSettings(
                values.number("Teleportation.Range-Blocks", teleportationDefaults.rangeBlocks(), 1.0, 1_000.0),
                values.number("Teleportation.Search-Radius-Blocks", teleportationDefaults.searchRadiusBlocks(), 0.5, 16.0),
                values.number("Teleportation.Cooldown-Seconds", teleportationDefaults.cooldownSeconds(), 0.0, 600.0));

        final FarcastSettings farcastDefaults = FarcastSettings.DEFAULTS;

        farcast = new FarcastSettings(
                values.number("Farcast.Push-Strength", farcastDefaults.pushStrength(), 0.0, 100.0),
                values.number("Farcast.Cooldown-Seconds", farcastDefaults.cooldownSeconds(), 0.0, 600.0),
                values.number("Farcast.Low-Health-PP", farcastDefaults.lowHealthPp(), 0.0, 100.0),
                values.number("Farcast.Low-Health-Chance-Multiplier", farcastDefaults.lowHealthChanceMultiplier(), 0.0, 10.0));
    }

    /**
     * A missing key keeps the default silently; a present but unusable list
     * (empty, wrong type) reaches validation and warns there.
     */
    private static List<Double> doubles(final Source config, final String path, final List<Double> defaults) {
        return config.contains(path) ? config.getDoubleList(path) : defaults;
    }

    /**
     * The rarity vocabulary of one load, selected by {@code Rarity-Format}. A
     * load uses one instance for both files, so config.yml's Categories and
     * Enchantments.yml's Categories/Rarity always resolve to the same
     * canonical names. Warnings are deduped here, so {@link #validate} and the
     * loader warn once per value instead of twice.
     *
     * <p>Format 1 reads the legacy names and maps them; a name only format 2
     * knows is refused rather than guessed, so a half-migrated file warns
     * instead of silently moving a tier. Format 2 reads the canonical names
     * literally: {@code Legendary} is the top tier and an old-only name such as
     * {@code Common} is unknown. A category name outside both vocabularies is a
     * custom tier and keeps its own spelling.</p>
     */
    public static final class Rarities {

        public static final int LEGACY_FORMAT = 1;
        public static final int CURRENT_FORMAT = 2;

        private final int format;
        private final Consumer<String> warn;
        private final Set<String> warned = new HashSet<>();
        private boolean legacyWarned;

        /**
         * @param declaredFormat the raw {@code Rarity-Format} value
         * @param warn where warnings go
         */
        public Rarities(final Object declaredFormat, final Consumer<String> warn) {
            this.warn = warn;

            if (declaredFormat == null) {
                this.format = LEGACY_FORMAT;

                return;
            }

            final String text = String.valueOf(declaredFormat).trim();

            if (Integer.toString(CURRENT_FORMAT).equals(text)) {
                this.format = CURRENT_FORMAT;

                return;
            }

            this.format = LEGACY_FORMAT;

            if (!Integer.toString(LEGACY_FORMAT).equals(text)) {
                this.warn.accept("Unknown Rarity-Format '" + declaredFormat + "' in Enchantments.yml; using 1, the legacy rarity names.");
            }
        }

        public int format() {
            return this.format;
        }

        /**
         * A block's {@code Rarity}: its canonical name, or null when the block
         * declares none or a name this format does not know. An unknown value
         * warns and leaves the block's Categories in charge.
         */
        public String rarity(final Object declared, final String key) {
            if (declared == null) return null;

            final String where = "Enchantments." + key + ".Rarity";

            if (declared instanceof String text) {
                final String known = identity(text.trim());

                if (known != null) return known;
            }

            warn(where, String.valueOf(declared).trim(), "using its Categories");

            return null;
        }

        /**
         * A category name read from config.yml or a block's Categories list:
         * its canonical name, or null when this format does not know it.
         *
         * <p>A category name is free-form in this plugin, so a name outside
         * both vocabularies is a custom tier and keeps its own spelling, as it
         * always did. Only a name belonging to the other vocabulary is refused:
         * that is the mixed file this discriminator exists to catch.</p>
         */
        public String category(final String name, final String where) {
            if (name == null) return null;

            final String value = name.trim();
            final String known = identity(value);

            if (known != null) return known;

            if (!isVocabularyName(value)) return value;

            warn(where, value, "ignoring it");

            return null;
        }

        /** The canonical name for one spelling, or null; warns for nothing. */
        private String identity(final String value) {
            if (this.format == CURRENT_FORMAT) {
                for (final String allowed : RARITIES) {
                    if (allowed.equalsIgnoreCase(value)) return allowed;
                }

                return null;
            }

            for (final Map.Entry<String, String> entry : LEGACY_TO_CURRENT.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(value)) {
                    warnLegacy();

                    return entry.getValue();
                }
            }

            return null;
        }

        /** A name that belongs to one of the two vocabularies. */
        private static boolean isVocabularyName(final String value) {
            for (final String allowed : RARITIES) {
                if (allowed.equalsIgnoreCase(value)) return true;
            }

            for (final String allowed : LEGACY_RARITIES) {
                if (allowed.equalsIgnoreCase(value)) return true;
            }

            return false;
        }

        private void warnLegacy() {
            if (this.legacyWarned) return;

            this.legacyWarned = true;

            this.warn.accept("Rarity-Format 1: reading the legacy rarity names, which resolve to "
                    + "Simple, Unique, Elite, Ultimate, Legendary. Set Rarity-Format: 2 after renaming them.");
        }

        private void warn(final String where, final String value, final String fallback) {
            if (!this.warned.add(where + '\u0000' + value)) return;

            this.warn.accept("Unknown rarity '" + value + "' in " + where + " for Rarity-Format " + this.format
                    + "; " + fallback + " (allowed: "
                    + String.join(", ", this.format == CURRENT_FORMAT ? RARITIES : LEGACY_RARITIES) + ").");
        }
    }

    /**
     * Load-time checks for Enchantments.yml. Never throws: an unknown enchant
     * key, an unknown Rarity value and a Tuning block with no matching enchant
     * each warn once and fall back to defaults.
     *
     * @param keys every key found under Enchantments in Enchantments.yml
     * @param known every enchant key the plugin knows
     * @param get reads a raw value from Enchantments.yml, e.g. {@code enchants::get}
     * @param rarities the vocabulary of this load
     */
    public static void validate(final Collection<String> keys, final Set<String> known,
                                final Function<String, Object> get, final Rarities rarities,
                                final Consumer<String> warn) {
        for (final String key : keys) {
            if (!known.contains(key)) {
                final boolean tuning = get.apply("Enchantments." + key + ".Tuning") != null;

                warn.accept("Unknown enchantment '" + key + "' in Enchantments.yml; ignoring it"
                        + (tuning ? " and its Tuning block." : "."));

                continue;
            }

            rarities.rarity(get.apply("Enchantments." + key + ".Rarity"), key);
        }
    }

    /**
     * Resolves a path under the old CosmicEnchants section to the file that
     * owns it now: {@code CosmicEnchants.<section>.<sub>} becomes
     * {@code Enchantments.<key>.Tuning.<sub>}. DamageModifiers is global and
     * stays in config.yml. A value still only in config.yml is read from there
     * with one deprecation warning, so an upgraded server keeps its numbers.
     */
    static final class Source {

        private final Function<String, Object> legacyGet;
        private final Predicate<String> legacyHas;
        private final Function<String, Object> movedGet;
        private final Predicate<String> movedHas;
        private final Consumer<String> warn;
        private final Set<String> warned = new HashSet<>();

        Source(final Function<String, Object> legacyGet, final Predicate<String> legacyHas,
               final Function<String, Object> movedGet, final Predicate<String> movedHas, final Consumer<String> warn) {
            this.legacyGet = legacyGet;
            this.legacyHas = legacyHas;
            this.movedGet = movedGet;
            this.movedHas = movedHas;
            this.warn = warn;
        }

        Object get(final String path) {
            final Location at = at(path);

            return at.moved ? this.movedGet.apply(at.path) : this.legacyGet.apply(at.path);
        }

        boolean contains(final String path) {
            final Location at = at(path);

            return at.moved ? this.movedHas.test(at.path) : this.legacyHas.test(at.path);
        }

        double getDouble(final String path, final double fallback) {
            final Object value = get(path);

            return value instanceof Number number ? number.doubleValue() : fallback;
        }

        int getInt(final String path, final int fallback) {
            final Object value = get(path);

            return value instanceof Number number ? number.intValue() : fallback;
        }

        /** A missing or malformed list reads as empty, which validation then warns about. */
        List<Integer> getIntegerList(final String path) {
            final List<Integer> numbers = new ArrayList<>();

            if (get(path) instanceof List<?> list) {
                for (final Object item : list) {
                    if (item instanceof Number number) numbers.add(number.intValue());
                }
            }

            return numbers;
        }

        /** A missing or malformed list reads as empty, which validation then warns about. */
        List<Double> getDoubleList(final String path) {
            final List<Double> numbers = new ArrayList<>();

            if (get(path) instanceof List<?> list) {
                for (final Object item : list) {
                    if (item instanceof Number number) numbers.add(number.doubleValue());
                }
            }

            return numbers;
        }

        private Location at(final String legacyPath) {
            if (legacyPath.startsWith("Enchantments.")) return new Location(true, legacyPath);
            if (!legacyPath.startsWith(LEGACY_PREFIX)) return new Location(false, legacyPath);

            final String rest = legacyPath.substring(LEGACY_PREFIX.length());
            final int dot = rest.indexOf('.');
            final String key = SECTIONS.get(dot < 0 ? rest : rest.substring(0, dot));

            if (key == null) return new Location(false, legacyPath);

            final String moved = "Enchantments." + key + ".Tuning" + (dot < 0 ? "" : rest.substring(dot));

            if (this.movedHas.test(moved)) return new Location(true, moved);

            if (!this.legacyHas.test(legacyPath)) return new Location(true, moved);

            if (this.warned.add(legacyPath)) {
                this.warn.accept("CosmicEnchants." + rest + " is deprecated; move it to " + moved + ". Using the old value for now.");
            }

            return new Location(false, legacyPath);
        }
    }

    private record Location(boolean moved, String path) {}

    // Read raw values: Bukkit's typed getters silently replace values of the wrong type.
    record BowValues(java.util.function.Function<String, Object> get, Consumer<String> warn) {

        double number(final String key, final double fallback, final double min, final double max) {
            final String path = "CosmicEnchants." + key;
            final Object value = this.get.apply(path);

            try {
                return checkedNumber(value, min, max);
            } catch (final IllegalArgumentException exception) {
                this.warn.accept("Invalid " + path + " value '" + value + "'; using " + fallback + ".");
                return fallback;
            }
        }

        List<Double> table(final String key, final List<Double> fallback, final double min, final double max) {
            final String path = "CosmicEnchants." + key;
            final Object value = this.get.apply(path);

            try {
                return checkedTable(value, fallback.size(), min, max);
            } catch (final IllegalArgumentException exception) {
                this.warn.accept("Invalid " + path + " value '" + value + "'; using " + fallback + ".");
                return fallback;
            }
        }

        /**
         * A missing key keeps the default silently, like a missing number; a
         * present value that is not a boolean warns.
         */
        boolean bool(final String key, final boolean fallback) {
            final String path = "CosmicEnchants." + key;
            final Object value = this.get.apply(path);

            if (value == null) return fallback;
            if (value instanceof Boolean result) return result;

            this.warn.accept("Invalid " + path + " value '" + value + "'; using " + fallback + ".");

            return fallback;
        }

        static double checkedNumber(final Object value, final double min, final double max) {
            if (value instanceof Number number) {
                final double result = number.doubleValue();
                if (result >= min && result <= max) return result;
            }

            throw new IllegalArgumentException("Expected a number between " + min + " and " + max + ": " + value);
        }

        static List<Double> checkedTable(final Object value, final int size, final double min, final double max) {
            if (!(value instanceof List<?> list) || list.size() != size) {
                throw new IllegalArgumentException("Expected " + size + " levels: " + value);
            }

            final List<Double> checked = new ArrayList<>(size);
            for (final Object item : list) checked.add(checkedNumber(item, min, max));

            return List.copyOf(checked);
        }
    }
}
