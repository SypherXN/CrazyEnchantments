package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.DisintegrateSettings;
import com.badbones69.crazyenchantments.paper.api.utils.DoubleStrikeSettings;
import com.badbones69.crazyenchantments.paper.api.utils.GreatswordSettings;
import com.badbones69.crazyenchantments.paper.api.utils.InversionSettings;
import com.badbones69.crazyenchantments.paper.api.utils.TuningConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five new sword/all-weapon enchants: Double Strike, Inversion,
 * Disintegrate, Greatsword and Shackle. Pure-math rules are tested directly;
 * behaviour that lives inside a listener is guarded by reading its source as
 * text, in the style of AxeEnchantLogicTest and MeleeEnchantLogicTest. No
 * Bukkit server is loaded.
 */
class SwordEnchantLogicTest {

    private static final double EPS = 1.0e-9;

    private static final String ENUM_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/api/enums/CEnchantments.java";

    private static final String SWORD_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/enchantments/SwordEnchantments.java";

    private static final String SHACKLE_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/listeners/ShackleListener.java";

    private static final String SHACKLE_SUPPORT_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/api/utils/ShackleSupport.java";

    private static final String INVERSION_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/listeners/InversionListener.java";

    private static final String PLUGIN_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/CrazyEnchantments.java";

    private static final String TUNING_SOURCE = "src/main/java/com/badbones69/crazyenchantments/paper/api/utils/TuningConfig.java";

    private static final String ENCHANTMENTS_YML = "src/main/resources/Enchantments.yml";

    // ---- Double Strike: 5 / +3, second hit, 1.0 s per attacker ----

    @Test
    void doubleStrikeCooldownIsOneSecondPerAttacker() {
        final DoubleStrikeSettings settings = DoubleStrikeSettings.DEFAULTS;
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        assertEquals(1_000L, settings.cooldownMillis());
        assertFalse(settings.onCooldown(cooldowns, first, 0L));

        assertTrue(settings.claim(cooldowns, first, 0L));
        assertTrue(settings.onCooldown(cooldowns, first, 999L));
        assertFalse(settings.onCooldown(cooldowns, first, 1_000L), "exactly one second later it is ready");
        assertTrue(settings.claim(cooldowns, first, 1_000L));

        assertTrue(settings.claim(cooldowns, second, 500L), "another attacker has its own cooldown");

        cooldowns.remove(first);

        assertFalse(settings.onCooldown(cooldowns, first, 1_001L), "quit clears the cooldown");
    }

    @Test
    void doubleStrikeValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(DoubleStrikeSettings.DEFAULTS, DoubleStrikeSettings.validated(1.0, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(DoubleStrikeSettings.DEFAULTS, DoubleStrikeSettings.validated(-1.0, warnings::add));
        assertEquals(1, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new DoubleStrikeSettings(Double.NaN));
    }

    // ---- Inversion: 4 / +2, heal 1/2/2/3, 2.0 s per wearer ----

    @Test
    void inversionHealsOneTwoTwoThreeAndNeverOverMaxHealth() {
        final InversionSettings settings = InversionSettings.DEFAULTS;

        assertEquals(1.0, settings.heal(1), EPS);
        assertEquals(2.0, settings.heal(2), EPS);
        assertEquals(2.0, settings.heal(3), EPS);
        assertEquals(3.0, settings.heal(4), EPS);
        assertEquals(0.0, settings.heal(0), EPS);
        assertEquals(3.0, settings.heal(9), EPS, "a level past the list uses the last entry");

        assertEquals(20.0, settings.healedHealth(4, 19.0, 20.0), EPS, "never above max health");
        assertEquals(13.0, settings.healedHealth(4, 10.0, 20.0), EPS);
        assertEquals(20.0, settings.healedHealth(4, 20.0, 20.0), EPS, "full health is left alone");
        assertEquals(0.0, settings.healedHealth(4, 0.0, 20.0), EPS, "a dead wearer is not healed");
        assertEquals(5.0, settings.healedHealth(4, 5.0, 0.0), EPS, "an unusable max health is left alone");
        assertEquals(5.0, settings.healedHealth(0, 5.0, 20.0), EPS, "no level heals nothing");
    }

    @Test
    void inversionCooldownIsTwoSecondsPerWearer() {
        final InversionSettings settings = InversionSettings.DEFAULTS;
        final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
        final UUID wearer = UUID.randomUUID();

        assertEquals(2_000L, settings.cooldownMillis());
        assertTrue(settings.claim(cooldowns, wearer, 0L));
        assertTrue(settings.onCooldown(cooldowns, wearer, 1_999L));
        assertFalse(settings.onCooldown(cooldowns, wearer, 2_000L));
        assertTrue(settings.claim(cooldowns, wearer, 2_000L));
    }

    @Test
    void inversionValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(InversionSettings.DEFAULTS,
                InversionSettings.validated(List.of(1.0, 2.0, 2.0, 3.0), 2.0, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(InversionSettings.DEFAULTS, InversionSettings.validated(List.of(-1.0), -1.0, warnings::add));
        assertEquals(2, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new InversionSettings(List.of(), 2.0));
        assertThrows(IllegalArgumentException.class, () -> new InversionSettings(List.of(1.0), Double.NaN));
    }

    // ---- Disintegrate: 5 / +3, one point per 4 final damage, cap 4 ----

    @Test
    void disintegrateWearsOnePointPerFourFinalDamageWithAMinimumOfOneAndACapOfFour() {
        final DisintegrateSettings settings = DisintegrateSettings.DEFAULTS;

        assertEquals(0, settings.points(0.0));
        assertEquals(0, settings.points(-4.0));
        assertEquals(0, settings.points(Double.NaN));
        assertEquals(0, settings.points(Double.POSITIVE_INFINITY));

        assertEquals(1, settings.points(0.5), "at least one point");
        assertEquals(1, settings.points(3.9));
        assertEquals(1, settings.points(4.0));
        assertEquals(1, settings.points(4.1));
        assertEquals(2, settings.points(8.0));
        assertEquals(4, settings.points(16.0));
        assertEquals(4, settings.points(20.0), "capped at four per piece");
        assertEquals(4, settings.points(1_000.0));
    }

    @Test
    void disintegrateValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(DisintegrateSettings.DEFAULTS, DisintegrateSettings.validated(4.0, 4, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(DisintegrateSettings.DEFAULTS, DisintegrateSettings.validated(0.0, 0, warnings::add));
        assertEquals(2, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new DisintegrateSettings(0.0, 4));
        assertThrows(IllegalArgumentException.class, () -> new DisintegrateSettings(4.0, 0));
    }

    // ---- Greatsword: always-on, 4 x level percent vs a bow or crossbow ----

    @Test
    void greatswordIsFourPercentPerLevelOnlyAgainstABowOrCrossbow() {
        final GreatswordSettings settings = GreatswordSettings.DEFAULTS;

        assertEquals(1.04, settings.multiplier(1, true), EPS);
        assertEquals(1.08, settings.multiplier(2, true), EPS);
        assertEquals(1.12, settings.multiplier(3, true), EPS);
        assertEquals(1.16, settings.multiplier(4, true), EPS);
        assertEquals(1.20, settings.multiplier(5, true), EPS);
        assertEquals(1.20, settings.multiplier(9, true), EPS, "past V uses the V bonus");
        assertEquals(1.0, settings.multiplier(0, true), EPS);
        assertEquals(1.0, settings.multiplier(5, false), EPS, "no bow, no bonus");

        assertTrue(GreatswordSettings.holdsBow("BOW"));
        assertTrue(GreatswordSettings.holdsBow("CROSSBOW"));
        assertFalse(GreatswordSettings.holdsBow("BOW_X"));
        assertFalse(GreatswordSettings.holdsBow("DIAMOND_SWORD"));
        assertFalse(GreatswordSettings.holdsBow("AIR"));
        assertFalse(GreatswordSettings.holdsBow(null));
    }

    @Test
    void greatswordValidationFallsBackOnBadValues() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(GreatswordSettings.DEFAULTS, GreatswordSettings.validated(4.0, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(GreatswordSettings.DEFAULTS, GreatswordSettings.validated(-1.0, warnings::add));
        assertEquals(1, warnings.size());

        assertThrows(IllegalArgumentException.class, () -> new GreatswordSettings(101.0));
    }

    // ---- Registry: enum, Enchantments.yml and the chance formula agree ----

    @Test
    void theNewEnchantsUseTheApprovedEnumAndYamlOdds() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertTrue(source.contains("DOUBLE_STRIKE(\"Double Strike\", \"Sword\", 5, 3),"), "Double Strike must be 5 / +3");
        assertTrue(source.contains("INVERSION(\"Inversion\", \"Sword\", 4, 2),"), "Inversion must be 4 / +2");
        assertTrue(source.contains("DISINTEGRATE(\"Disintegrate\", \"Sword\", 5, 3),"), "Disintegrate must be 5 / +3");
        assertTrue(source.contains("GREATSWORD(\"Greatsword\", \"Sword\"),"), "Greatsword must have no chance values");
        assertTrue(source.contains("SHACKLE(\"Shackle\", \"Weapons\", 50, 25),"), "Shackle must be 50 / +25");

        assertChance(yaml, "Double Strike", 5, 3);
        assertChance(yaml, "Inversion", 4, 2);
        assertChance(yaml, "Disintegrate", 5, 3);
        assertChance(yaml, "Shackle", 50, 25);

        // chance + increase x (level - 1) at the maximum level.
        assertEquals(11, 5 + 3 * (3 - 1), "Double Strike is 11% at III");
        assertEquals(10, 4 + 2 * (4 - 1), "Inversion is 10% at IV");
        assertEquals(14, 5 + 3 * (4 - 1), "Disintegrate is 14% at IV");
        assertEquals(100, 50 + 25 * (3 - 1), "Shackle is 100% at III");
    }

    @Test
    void alwaysOnGreatswordCarriesNoChanceValuesAndNoChanceSystem() throws IOException {
        final String source = read(ENUM_SOURCE);
        final String yaml = read(ENCHANTMENTS_YML);

        assertFalse(entry(yaml, "Greatsword").contains("Chance-System"), "Greatsword must not declare a Chance-System");
        assertFalse(source.contains("GREATSWORD(\"Greatsword\", \"Sword\", 0"), "a zero chance must never be declared");
    }

    @Test
    void yamlEntriesDeclareTheApprovedMaxPowerTypeAndCategory() throws IOException {
        final String yaml = read(ENCHANTMENTS_YML);

        final Map<String, String[]> expected = new HashMap<>();

        expected.put("Double Strike", new String[]{"3", "Sword", "Elite"});
        expected.put("Inversion", new String[]{"4", "Sword", "Elite"});
        expected.put("Disintegrate", new String[]{"4", "Sword", "Ultimate"});
        expected.put("Greatsword", new String[]{"5", "Sword", "Elite"});
        expected.put("Shackle", new String[]{"3", "Weapons", "Elite"});

        expected.forEach((name, values) -> {
            final String entry = entry(yaml, name);

            assertTrue(entry.contains("Enabled: true"), name + " must be enabled");
            assertTrue(entry.contains("MaxPower: " + values[0] + "\n"), name + " MaxPower");
            assertTrue(entry.contains("Enchantment-Type: '" + values[1] + "'"), name + " item type");
            assertTrue(entry.contains("Name: '&7" + name + "'"), name + " needs its &7 name");
            assertTrue(entry.contains("Categories:\n      - '" + values[2] + "'\n"), name + " must be only in " + values[2]);
            assertTrue(entry.contains("Info:") && entry.contains("Description:"), name + " needs Info and a Description");
        });
    }

    @Test
    void configShipsEveryNewTunableAtItsRecordDefault() throws IOException {
        final String yaml = read(ENCHANTMENTS_YML);

        assertEquals("1.0", value(yaml, "DoubleStrike", "Cooldown-Seconds"));
        assertEquals("2.0", value(yaml, "Inversion", "Cooldown-Seconds"));
        assertEquals("[1.0, 2.0, 2.0, 3.0]", value(yaml, "Inversion", "Heal"));
        assertEquals("4.0", value(yaml, "Disintegrate", "Damage-Per-Point"));
        assertEquals("4", value(yaml, "Disintegrate", "Cap-Per-Piece"));
        assertEquals("4.0", value(yaml, "Greatsword", "Bonus-Per-Level-PP"));

        assertEquals(DoubleStrikeSettings.DEFAULTS.cooldownSeconds(),
                Double.parseDouble(value(yaml, "DoubleStrike", "Cooldown-Seconds")), EPS);
        assertEquals(InversionSettings.DEFAULTS.cooldownSeconds(),
                Double.parseDouble(value(yaml, "Inversion", "Cooldown-Seconds")), EPS);
        assertEquals(DisintegrateSettings.DEFAULTS.damagePerPoint(),
                Double.parseDouble(value(yaml, "Disintegrate", "Damage-Per-Point")), EPS);
        assertEquals(GreatswordSettings.DEFAULTS.bonusPerLevelPp(),
                Double.parseDouble(value(yaml, "Greatsword", "Bonus-Per-Level-PP")), EPS);
    }

    @Test
    void tuningConfigReadsEveryNewKeyThroughItsValidatedSettings() throws IOException {
        final String tuning = read(TUNING_SOURCE);

        for (final String key : List.of(
                "CosmicEnchants.DoubleStrike.Cooldown-Seconds",
                "CosmicEnchants.Inversion.Cooldown-Seconds", "CosmicEnchants.Inversion.Heal",
                "CosmicEnchants.Disintegrate.Damage-Per-Point", "CosmicEnchants.Disintegrate.Cap-Per-Piece",
                "CosmicEnchants.Greatsword.Bonus-Per-Level-PP")) {
            assertTrue(tuning.contains("\"" + key + "\"")
                    || tuning.contains("values.number(\"" + key.substring("CosmicEnchants.".length()) + "\""),
                    "TuningConfig must read " + key);
        }

        for (final String settings : List.of("DoubleStrikeSettings", "InversionSettings", "DisintegrateSettings", "GreatswordSettings")) {
            assertTrue(tuning.contains(settings + ".validated("), "TuningConfig must validate " + settings);
        }
    }

    // ---- Listener behaviour, guarded by reading the source ----

    @Test
    void doubleStrikeUsesTheCapturedBaseDamageAndDefersTheSecondHitToMonitor() throws IOException {
        final String sword = read(SWORD_SOURCE);
        final String damage = method(sword, "onPlayerDamage");
        final String duel = method(sword, "applyDuelEnchants");

        assertTrue(damage.contains("final double baseDamage = event.getDamage();"),
                "the base damage is captured before any CE multiplier");

        // The capture must sit above the Rage mutation, not just above applyDuelEnchants.
        final int capture = damage.indexOf("final double baseDamage = event.getDamage();");
        final int rage = damage.indexOf("event.setDamage(event.getDamage() * cePlayer.getRageMultiplier());");

        assertTrue(rage > 0 && capture < rage, "the base capture must precede the Rage multiplier");

        assertTrue(damage.contains("applyDuelEnchants(event, damager, en, item, enchantments, baseDamage);"),
                "the captured base damage reaches the duel layer");

        // HIGHEST only records the pending hit; MONITOR folds it in.
        assertFalse(duel.contains("SyntheticHit.damage("), "the HIGHEST stage must not damage directly");
        assertTrue(duel.contains("this.pendingDoubleStrike.put(event, new PendingStrike(baseDamage));"));
        assertTrue(duel.contains("baseDamage > 0.0"), "an empty hit cannot proc");
        assertTrue(duel.contains("TuningConfig.doubleStrike()"));
        assertTrue(duel.contains("doubleStrike.onCooldown("));
        assertFalse(duel.contains("doubleStrike.claim("));

        // The roll must pass before the proc can reach MONITOR.
        assertTrue(duel.indexOf("EnchantUtils.isEventActive(CEnchantments.DOUBLE_STRIKE, damager, item, enchantments)")
                        < duel.indexOf("this.pendingDoubleStrike.put("),
                "only a successful use-event roll is queued");

        assertTrue(duel.contains("EntityDamageEvent.DamageCause.ENTITY_ATTACK")
                && duel.contains("EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK"), "direct melee only");
    }

    @Test
    void doubleStrikeMonitorDropsACancelledOrEmptyHitAndFoldsTheSecondInstanceIntoTheEvent() throws IOException {
        final String body = method(read(SWORD_SOURCE), "onDoubleStrikeResolved");

        assertTrue(body.contains("this.pendingDoubleStrike.remove(event);"), "the entry is removed unconditionally");
        final int reject = body.indexOf("strike == null || event.isCancelled() || !(event.getFinalDamage() > 0.0)");
        final int claim = body.indexOf("doubleStrike.claim(");
        assertTrue(reject >= 0 && reject < claim && claim < body.indexOf("event.setDamage("));
        // The second instance rides in the same event: a nested damage call would be
        // absorbed by the victim's hurt window that the outer hit is about to claim.
        assertTrue(body.contains("event.setDamage(event.getDamage() + strike.amount());"));
        assertFalse(body.contains("victim.damage("), "no nested damage call");
        assertFalse(body.contains("SyntheticHit.damage("), "no synthetic second hit");
        assertFalse(body.contains("setNoDamageTicks"), "vanilla hit immunity is never touched");
        assertFalse(read(SWORD_SOURCE).contains("setNoDamageTicks"), "vanilla hit immunity is never touched");
    }

    @Test
    void inversionCancelsOnlyADirectMeleeHitOnTheOwningRegion() throws IOException {
        final String source = read(INVERSION_SOURCE);
        final String body = method(source, "onInversion");

        assertTrue(source.contains("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n    public void onInversion"),
                "HIGHEST with ignoreCancelled, early enough to cancel");
        assertFalse(read(SWORD_SOURCE).contains("public void onInversion"),
                "Inversion lives in its own listener so it runs before the sword damage handler");
        assertTrue(read(PLUGIN_SOURCE).contains("new InversionListener()"), "the listener is registered");
        assertTrue(read(PLUGIN_SOURCE).indexOf("new BlockListener()") < read(PLUGIN_SOURCE).indexOf("new InversionListener()"),
                "Inversion is registered next to Block, before SwordEnchantments");

        assertTrue(body.contains("if (SyntheticHit.isBleedTick() || EventUtils.isIgnoredEvent(event)) return;"));
        assertTrue(body.contains("EntityDamageEvent.DamageCause.ENTITY_ATTACK")
                && body.contains("EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK"), "direct melee only");
        assertFalse(body.contains("PROJECTILE"), "a projectile must never proc Inversion");
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(victim)") && body.contains("Bukkit.isOwnedByCurrentRegion(attacker)"));
        assertTrue(body.contains("this.support.isFriendly(player, victim)"), "an ally's hit never procs it");
        assertTrue(body.contains("EnchantUtils.isEventActive(CEnchantments.INVERSION, victim, item, enchantments)"));
        assertTrue(body.contains("inversion.healedHealth(level, victim.getHealth(), maxHealth.getValue())"),
                "the heal is clamped by the settings record");

        // A zero-damage event must not spend the cooldown or heal.
        assertTrue(body.contains("if (!(event.getDamage() > 0.0)) return;"), "a hit that deals nothing never procs");

        // The cancel must come after every guard, never before them.
        final int cancel = body.indexOf("event.setCancelled(true)");

        assertTrue(cancel > body.indexOf("if (level < 1) return;"), "the cancel must follow the level guard");
        assertTrue(cancel > body.indexOf("EnchantUtils.isEventActive(CEnchantments.INVERSION"), "the cancel must follow the roll");
        assertTrue(cancel > body.indexOf("Bukkit.isOwnedByCurrentRegion(victim)"), "the cancel must follow the ownership guards");
        assertTrue(cancel > body.indexOf("if (!(event.getDamage() > 0.0)) return;"), "the cancel must follow the damage guard");
    }

    @Test
    void disintegrateWearsEveryArmorPieceAtMonitorWithoutDestroyingIt() throws IOException {
        final String sword = read(SWORD_SOURCE);
        final String body = method(sword, "onDisintegrate");
        final String wear = method(sword, "wearArmor");

        assertTrue(sword.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onDisintegrate"),
                "MONITOR so the wear is based on final damage");
        assertTrue(body.contains("event.getFinalDamage() <= 0.0"), "a hit that did no damage wears nothing");
        assertTrue(body.contains("event.getEntity() instanceof Player victim")
                && body.contains("event.getDamager() instanceof Player damager"));
        assertTrue(body.contains("TuningConfig.disintegrate().points(event.getFinalDamage())"));
        assertTrue(body.contains("this.support.isFriendly(damager, victim)"));

        assertTrue(wear.contains("EquipmentSlot.HEAD") && wear.contains("EquipmentSlot.CHEST")
                && wear.contains("EquipmentSlot.LEGS") && wear.contains("EquipmentSlot.FEET"), "every worn piece");
        assertTrue(wear.contains("piece.getType().isAir()"), "air is skipped");
        assertTrue(wear.contains("meta instanceof Damageable"), "pieces with no durability are skipped");
        assertTrue(wear.contains("meta.isUnbreakable()"), "unbreakable armor is skipped");
        assertTrue(wear.contains("maxDurability <= 0"));
        assertTrue(wear.contains("Math.min(points, maxDurability - 1 - damageable.getDamage())"), "never destroyed");
        // Vanilla wear, so Hardened, Unbreaking and PlayerItemDamageEvent can reduce it.
        assertTrue(wear.contains("Methods.wearArmor(victim, piece, amount)"), "the wear goes through the vanilla path");
        assertTrue(wear.contains("equipment.setItem(slot, worn)"), "the resulting stack is written back");
        assertFalse(wear.contains("next.setDamage("), "durability is no longer written directly");
    }

    @Test
    void shackleCancelsKnockbackOnSpawnerMobsOnly() throws IOException {
        final String body = method(read(SHACKLE_SOURCE), "onKnockback");
        final String support = read(SHACKLE_SUPPORT_SOURCE);

        // Plan #45 keys on the spawner tag, with the vanilla reason only as a fallback.
        assertTrue(body.contains("ShackleSupport.isSpawnerMob(victim)"), "the tag decides, not the live reason");
        assertFalse(body.contains("getEntitySpawnReason"), "the listener no longer reads the spawn reason itself");
        assertTrue(support.contains("entity.getPersistentDataContainer().has(key(), PersistentDataType.BYTE)"),
                "the tag is read first");
        assertTrue(support.contains("entity.getEntitySpawnReason() == SpawnReason.SPAWNER"),
                "an untagged mob still matches its vanilla spawn reason");
        assertTrue(support.contains("getPersistentDataContainer().set(key(), PersistentDataType.BYTE"),
                "the spawner lane can tag a mob");

        assertTrue(body.contains("source instanceof Projectile projectile ? projectile.getShooter() : source")
                && body.contains("attacker instanceof Player wielder"), "only the wielder's knockback");
        assertTrue(body.contains("case AbstractArrow arrow -> arrow.getWeapon()")
                && body.contains("case Trident trident -> trident.getItemStack()"), "ranged shots use their firing weapon");
        assertTrue(body.contains("Bukkit.isOwnedByCurrentRegion(wielder)") && body.contains("Bukkit.isOwnedByCurrentRegion(victim)"));
        assertTrue(body.contains("ShackleSupport.suppressesKnockback(victim, wielder, item, enchantments)"));
        assertTrue(support.contains("EnchantUtils.isEventActive(CEnchantments.SHACKLE, wielder, weapon, enchantments)"));
        assertTrue(body.contains("event.setCancelled(true)"));

        // The spawner and source guards must both come before the cancel.
        final int cancel = body.indexOf("event.setCancelled(true)");

        assertTrue(body.indexOf("ShackleSupport.isSpawnerMob(victim)") < cancel,
                "the spawner guard must precede the cancel");
        assertTrue(body.indexOf("attacker instanceof Player wielder") < cancel,
                "the source guard must precede the cancel");
        assertTrue(read(PLUGIN_SOURCE).contains("new ShackleListener()"), "the listener is registered");

        // Obliterate sets a velocity by hand, which never fires the knockback event, so
        // it asks the same rule before pushing a spawner mob.
        final String damage = method(read(SWORD_SOURCE), "onPlayerDamage");

        assertTrue(damage.contains("ShackleSupport.suppressesKnockback(en, damager, item, enchantments)"),
                "Obliterate honours Shackle without the knockback event");
    }

    @Test
    void bothCooldownMapsAreClearedOnQuitDeathAndWorldChange() throws IOException {
        final String sword = read(SWORD_SOURCE);
        final String inversion = read(INVERSION_SOURCE);

        assertTrue(method(sword, "onQuit").contains("this.doubleStrikeCooldowns.remove("));
        assertTrue(method(inversion, "onQuit").contains("this.inversionCooldowns.remove("));

        assertTrue(method(sword, "onPlayerDeath").contains("this.doubleStrikeCooldowns.remove("),
                "a respawn starts a fresh Double Strike window");
        assertTrue(method(inversion, "onDeath").contains("this.inversionCooldowns.remove("),
                "a respawn starts a fresh Inversion window");

        assertTrue(method(sword, "onWorldChange").contains("this.doubleStrikeCooldowns.remove("));
        assertTrue(method(inversion, "onWorldChange").contains("this.inversionCooldowns.remove("));

        // A reload publishes a new settings record, and the first proc under it drops
        // the cooldowns claimed under the old one.
        assertTrue(method(sword, "applyDuelEnchants").contains("doubleStrike != this.doubleStrikeSettings"));
        assertTrue(method(sword, "applyDuelEnchants").contains("this.doubleStrikeCooldowns.clear()"));
        assertTrue(method(inversion, "onInversion").contains("inversion != this.inversionSettings"));
        assertTrue(method(inversion, "onInversion").contains("this.inversionCooldowns.clear()"));
    }

    // ---- helpers ----

    private static void assertChance(final String yaml, final String name, final int base, final int increase) {
        final Matcher chance = Pattern.compile("(?s)Chance-System:\\s*Base: (\\d+)\\s*Increase: (\\d+)").matcher(entry(yaml, name));

        assertTrue(chance.find(), name + " must declare Base/Increase");
        assertEquals(base, Integer.parseInt(chance.group(1)), name + " Base");
        assertEquals(increase, Integer.parseInt(chance.group(2)), name + " Increase");
    }

    /**
     * The text of one top-level Enchantments.yml entry, up to the next
     * "#Do not change this line." marker (or the end of the file).
     */
    private static String entry(final String yaml, final String name) {
        final Matcher start = Pattern.compile("(?m)^  " + Pattern.quote(name) + ":\\s*$").matcher(yaml);

        assertTrue(start.find(), "Enchantments.yml must define " + name);

        final int from = start.end();
        final int to = yaml.indexOf("#Do not change this line.", from);

        return to < 0 ? yaml.substring(from) : yaml.substring(from, to);
    }

    /**
     * The value of one key inside one enchant's Tuning block in Enchantments.yml.
     */
    private static String value(final String yaml, final String block, final String key) {
        final String enchant = TuningConfig.sectionKeys().get(block);

        assertNotNull(enchant, "no enchant key for tuning block " + block);

        final Matcher start = Pattern.compile("(?m)^  " + Pattern.quote(enchant) + ":[ \\t]*$").matcher(yaml);

        assertTrue(start.find(), "Enchantments.yml must define " + enchant);

        final Matcher next = Pattern.compile("(?m)^  [^ #]").matcher(yaml);
        final int from = start.end();
        final int to = next.find(from) ? next.start() : yaml.length();

        final Matcher line = Pattern.compile("(?m)^      " + Pattern.quote(key) + ": (.+?)\\s*$").matcher(yaml.substring(from, to));

        assertTrue(line.find(), "Enchantments.yml must define " + enchant + ".Tuning." + key);

        return line.group(1);
    }

    /**
     * The source of one method, from its declaration to its closing brace at four-space indent.
     */
    private static String method(final String source, final String name) {
        final Matcher start = Pattern.compile("(?m)^    (?:public |private )?(?:static )?\\w+ " + Pattern.quote(name) + "\\(").matcher(source);

        assertTrue(start.find(), "SwordEnchantments must declare " + name);

        final int end = source.indexOf("\n    }\n", start.start());

        assertTrue(end > 0, "could not find the end of " + name);

        return source.substring(start.start(), end + 6);
    }

    private static String read(final String relative) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));

        if (!Files.exists(root.resolve(relative))) root = root.resolve("paper");

        return Files.readString(root.resolve(relative)).replace("\r\n", "\n");
    }
}
