package com.badbones69.crazyenchantments.paper;

import com.badbones69.crazyenchantments.paper.api.utils.CurseSettings;
import com.badbones69.crazyenchantments.paper.api.utils.FeignDeathSettings;
import com.badbones69.crazyenchantments.paper.api.utils.FeignVisibility;
import com.badbones69.crazyenchantments.paper.api.utils.PlagueCarrierSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fix rounds for Curse ownership, Plague Carrier's lifecycle and Feign
 * Death's per-viewer visibility. Predicates and the visibility seam are exercised
 * directly; only the parts that exist solely as event wiring keep a narrow source
 * guard.
 */
class ArmorFixLogicTest {

    private static final String JAVA = "src/main/java/com/badbones69/crazyenchantments/paper/";
    private static final String ARMOR = JAVA + "enchantments/ArmorEnchantments.java";

    // ---------------------------------------------------------------------------------------------
    // Curse: gap-filling only, never destructive
    // ---------------------------------------------------------------------------------------------

    @Test
    void curseOnlyFillsAGap() {
        assertTrue(CurseSettings.mayApply(false), "an absent effect is applied");
        assertFalse(CurseSettings.mayApply(true), "a present effect is never applied over");
    }

    @Test
    void aCycleCountsAsAppliedOnlyWhenSomethingWasAdded() {
        assertFalse(CurseSettings.anyApplied(false, false, false), "an all-occupied cycle applied nothing");
        assertTrue(CurseSettings.anyApplied(true, false, false));
        assertTrue(CurseSettings.anyApplied(false, true, false));
        assertTrue(CurseSettings.anyApplied(false, false, true));
    }

    @Test
    void curseNeverRemovesOrRefreshesAnEffectItDidNotJustCreate() throws IOException {
        final String source = read(ARMOR);
        final String apply = method(source, "applyCurseEffect");
        final String tick = method(source, "tickCurse");

        assertFalse(source.contains("clearCurseEffects"), "there is no removal path left");
        assertFalse(source.contains("run.owned"), "ownership is no longer tracked");
        assertTrue(apply.contains("if (!CurseSettings.mayApply(wearer.hasPotionEffect(type))) return false;"),
                "a present effect of any source is never overwritten or refreshed");
        assertTrue(apply.contains("return added[0];"), "only a successful addition is reported");
        assertFalse(apply.contains("removePotionEffect"));
        assertFalse(tick.contains("removePotionEffect"), "recovery and unequip remove nothing");
    }

    @Test
    void curseDisarmsOnlyWhenAnActiveCurseEnds() throws IOException {
        final String source = read(ARMOR);
        final String apply = method(source, "applyCurse");
        final String tick = method(source, "tickCurse");

        assertTrue(apply.contains("if (CurseSettings.anyApplied(strength, slowness, resistance)) run.applied = true;"),
                "the latch is only set by an application that really added something");
        assertTrue(tick.contains("if (run.armed && run.applied) {"),
                "stepping into the hysteresis band with nothing applied must not disarm");
        assertTrue(tick.contains("run.applied = false;"));
        assertTrue(tick.contains("if (settings.rearmed(run.level, health, maxHealth)) run.armed = true;"));
    }

    // ---------------------------------------------------------------------------------------------
    // Plague Carrier
    // ---------------------------------------------------------------------------------------------

    @Test
    void plagueRearmsOnlyStrictlyAboveTheCut() {
        final PlagueCarrierSettings settings = PlagueCarrierSettings.DEFAULTS;

        assertFalse(settings.rearmed(10.0, 20.0), "exactly 50% must not re-arm");
        assertTrue(settings.rearmed(10.01, 20.0));
        assertTrue(settings.rearmed(20.0, 20.0));
        assertFalse(settings.rearmed(0.0, 0.0), "no max health means no cut");
    }

    @Test
    void thePoisonFillsAGapOnly() throws IOException {
        assertTrue(PlagueCarrierSettings.mayPoison(false), "an absent Poison is applied");
        assertFalse(PlagueCarrierSettings.mayPoison(true), "another source's Poison is never extended");

        final String poison = method(read(ARMOR), "poisonEnemies");

        assertTrue(poison.contains("PlagueCarrierSettings.mayPoison(target.hasPotionEffect(PotionEffectType.POISON))"),
                "the target's own effect is read before applying");
        assertTrue(poison.indexOf("mayPoison(") < poison.indexOf("addPotionEffect("),
                "and nothing is added over a present Poison");
        assertTrue(poison.indexOf("Bukkit.isOwnedByCurrentRegion(target)") < poison.indexOf("hasPotionEffect("),
                "the read happens on the target's owning region");

        final int guard = poison.indexOf("if (!PlagueCarrierSettings.mayPoison(");
        final int presence = poison.indexOf("hasPotionEffect(");
        final int add = poison.indexOf("addPotionEffect(");

        assertTrue(guard >= 0 && presence > guard, "the ownership check guards the add");
        assertTrue(add > presence, "the add is only reached after the presence check");
        assertTrue(poison.substring(guard, add).contains("continue;"), "a present Poison skips the add");
        assertEquals(add, poison.lastIndexOf("addPotionEffect("), "the only add in the method is the guarded one");
    }

    @Test
    void aTriggerLatchExpiresAfterTenMinutes() {
        assertEquals(TimeUnit.MINUTES.toNanos(10), PlagueCarrierSettings.RECOVERY_TIMEOUT_NANOS);
        assertFalse(PlagueCarrierSettings.expired(0L, PlagueCarrierSettings.RECOVERY_TIMEOUT_NANOS - 1));
        assertTrue(PlagueCarrierSettings.expired(0L, PlagueCarrierSettings.RECOVERY_TIMEOUT_NANOS));
        assertTrue(PlagueCarrierSettings.expired(5L, 5L + PlagueCarrierSettings.RECOVERY_TIMEOUT_NANOS));
    }

    @Test
    void aFuseOnlyDetonatesForItsOwnLiveRun() {
        assertTrue(PlagueCarrierSettings.mayDetonate(false, true, true, false, true, true));
        assertFalse(PlagueCarrierSettings.mayDetonate(true, true, true, false, true, true), "plugin stopped");
        assertFalse(PlagueCarrierSettings.mayDetonate(false, false, true, false, true, true), "settings replaced by a reload");
        assertFalse(PlagueCarrierSettings.mayDetonate(false, true, false, false, true, true), "the run was replaced or detached");
        assertFalse(PlagueCarrierSettings.mayDetonate(false, true, true, true, true, true), "the run was retired");
        assertFalse(PlagueCarrierSettings.mayDetonate(false, true, true, false, false, true), "the creeper is gone");
        assertFalse(PlagueCarrierSettings.mayDetonate(false, true, true, false, true, false), "the wearer left the region");
    }

    @Test
    void everyPlagueFusePathRetiresItsCreeper() throws IOException {
        final String source = read(ARMOR);
        final String fuse = method(source, "scheduleFuse");
        final String spawn = method(source, "spawnPlagueCreepers");
        final String burst = method(source, "spawnPlagueBurst");
        final String explode = method(source, "explodePlagueCreeper");

        assertTrue(fuse.contains("PlagueCarrierSettings.mayDetonate("), "a rejected fuse retires instead of firing");
        assertTrue(fuse.contains("() -> retirePlagueCreeper(run, creeper, false)"),
                "the scheduler's retirement callback cleans up when the creeper disappears");
        assertTrue(fuse.contains("Bukkit.isOwnedByCurrentRegion(wearer)"),
                "a wearer who left the region invalidates the burst");
        assertTrue(fuse.contains("if (task == null) {") && fuse.contains("retirePlagueCreeper(run, creeper, true);"),
                "a null task must not leave the creeper standing");
        assertTrue(spawn.contains("if (run.retired.get() || this.plagueRuns.get(run.wearer.getUniqueId()) != run) {"),
                "identity is re-checked after the spawn callback");
        assertTrue(burst.contains("this.plagueRuns.get(wearer.getUniqueId()) != run || run.retired.get()"),
                "identity is re-checked after spawn before fuses are scheduled");
        assertTrue(explode.contains("this.plagueRuns.get(wearer.getUniqueId()) == run && !run.retired.get()"),
                "identity is re-checked before detonation");
        assertTrue(explode.contains("} finally {"), "cleanup is guaranteed even when detonation throws");
        assertTrue(explode.indexOf("} finally {") < explode.indexOf("retirePlagueCreeper(run, creeper, true);"));
    }

    @Test
    void plagueRetirementIsIdempotent() throws IOException {
        final String source = read(ARMOR);
        final String retireRun = method(source, "retirePlagueRun");
        final String retireCreeper = method(source, "retirePlagueCreeper");
        final String stop = method(source, "stopPlague");

        assertTrue(retireRun.contains("if (!run.retired.compareAndSet(false, true)) return;"),
                "retirement is claimed exactly once, even when two callbacks race");
        assertTrue(retireRun.contains("this.plagueRuns.remove(run.wearer.getUniqueId(), run);"), "identity-safe map removal");
        assertTrue(retireRun.contains("run.fuses.clear();") && retireRun.contains("run.creepers.clear();"));
        assertTrue(retireCreeper.contains("if (!run.creepers.remove(creeper)) return;"), "one creeper is retired once");
        assertTrue(stop.contains("retirePlagueRun(run, mayRemoveDirectly);"));
        assertTrue(method(source, "onArmorQuit").contains("stopRuns("));
    }

    @Test
    void plagueReadsRealHealthAndPoisonsEvenWhenNoCreeperSpawned() throws IOException {
        final String source = read(ARMOR);
        final String tick = method(source, "tickPlague");
        final String fire = method(source, "firePlague");

        assertFalse(source.contains("onPlagueTrigger"), "the incoming-damage-only trigger is gone");
        assertTrue(tick.contains("final double health = wearer.getHealth();"));
        assertTrue(tick.contains("settings.triggers(health, maxHealth)"), "actual health, not a projected after-hit value");
        assertTrue(method(source, "startWornRuns").contains("startPlagueTicker(player)"), "equipping at low health arms it");
        assertTrue(tick.contains("this.plagueSpent.put(id, now);"));
        assertTrue(fire.indexOf("final Location origin = wearer.getLocation();") < fire.indexOf("poisonEnemies(wearer, origin, settings);"),
                "the poison origin is captured at trigger time");
        assertTrue(fire.indexOf("spawnPlagueBurst(") < fire.indexOf("poisonEnemies(wearer, origin, settings);"));
    }

    @Test
    void plagueLatchIsTimestampedAndPruned() throws IOException {
        final String tick = method(read(ARMOR), "tickPlague");

        assertTrue(tick.contains("final Long spentAt = this.plagueSpent.get(id);"), "the latch carries a timestamp");
        assertTrue(tick.contains("if (!settings.rearmed(health, maxHealth) && !PlagueCarrierSettings.expired(spentAt, now)) return;"),
                "a recovery or the bounded window releases it");
        assertTrue(tick.contains("this.plagueSpent.entrySet().removeIf(entry -> PlagueCarrierSettings.expired(entry.getValue(), now));"),
                "expired entries are pruned on write");
        assertTrue(tick.indexOf("final int level = run.level") < tick.indexOf("this.plagueSpent.get(id)"),
                "a recovery requires the leggings to be worn");
    }

    @Test
    void aRemovedPieceEndsTheScanAndTheLevelIsRefreshedOnACadence() throws IOException {
        final String tick = method(read(ARMOR), "tickPlague");

        assertTrue(tick.contains("PlagueCarrierSettings.REFRESH_TICKS"), "the periodic leggings read is kept");
        assertTrue(tick.indexOf("run.level = wornLevel(") < tick.indexOf("settings.triggers("),
                "the level is refreshed before the trigger is evaluated");
        assertTrue(tick.indexOf("run.level = wornLevel(") < tick.indexOf("settings.claim("),
                "and before the cooldown is claimed or the trigger is marked spent");
        assertTrue(tick.contains("if (!present || run.level < 1)"),
                "an empty or non-enchanted slot ends the run without a parse");
    }

    @Test
    void theBlastAndThePoisonShareOneAllyAndProtectionRule() throws IOException {
        final String source = read(ARMOR);
        final String explode = method(source, "explodePlagueCreeper");
        final String poison = method(source, "poisonEnemies");

        assertTrue(explode.contains("target -> !isSupportAlly(wearer, target)"), "the blast skips the wearer's allies");
        assertTrue(poison.contains("isSupportAlly(wearer, target)"), "and the poison uses the same predicate");
        assertTrue(poison.contains("isCombatEnabled(target.getLocation())"), "protected targets are skipped");
        assertTrue(method(source, "pickDestructionTarget").contains("isCombatEnabled(candidate.getLocation())"),
                "Destruction never picks a target in a protected area");
    }

    // ---------------------------------------------------------------------------------------------
    // Blood Lust, world change and reload admission
    // ---------------------------------------------------------------------------------------------

    @Test
    void bloodLustHealsOnlyFromALandedBleedTick() throws IOException {
        final String source = read(ARMOR);
        final String landed = method(source, "onBleedTickLanded");

        assertFalse(source.contains("onBleedTick(final BleedTickEvent"), "the pre-damage event no longer heals");
        assertTrue(landed.contains("BleedTickLandedEvent"), "the landed event drives the heal");
        assertTrue(landed.contains("bloodLust.isActivated()"), "activation is checked before any scan");
        assertTrue(landed.contains("getNearbyPlayers("), "only players near the victim are scanned");
        assertTrue(landed.indexOf("player.getHealth() >= maxHealth") < landed.indexOf("isEventActive("),
                "full health is rejected before the roll and the use event");
        assertTrue(landed.indexOf("settings.ready(") < landed.indexOf("isEventActive("),
                "a wearer on cooldown neither rolls nor fires the use event");
        assertTrue(landed.indexOf("isEventActive(") < landed.indexOf("settings.claim("),
                "the cooldown is claimed only after the proc was accepted");
    }

    @Test
    void aWorldChangeAndADeathRetireEveryArmorRun() throws IOException {
        final String source = read(ARMOR);
        final String world = method(source, "onArmorWorldChange");

        assertTrue(world.contains("stopSupportRuns("), "Spirits blazes must not linger in the old world");
        assertTrue(world.contains("stopRuns("), "Feign, Plague and the three tickers end as well");
        assertTrue(world.contains("rearmWornRuns("), "the always-on tickers are re-armed in the destination");
        assertTrue(method(source, "rearmWornRuns").contains("startWornRuns("));
        assertTrue(method(source, "onRunDeath").contains("stopRuns("), "death ends them immediately");
        assertTrue(method(source, "startWornRuns").contains("this.registryUnpublished"),
                "worn-run admission is part of the reload gate");
    }

    // ---------------------------------------------------------------------------------------------
    // Feign Death: the visibility seam
    // ---------------------------------------------------------------------------------------------

    @Test
    void aRecordedHideIsAlwaysReleasedWhenTheVanishEnds() {
        final RecordingVisibility recorder = new RecordingVisibility();
        final FeignVisibility subject = new FeignVisibility(recorder);
        final Player viewer = stubPlayer();
        final Player target = stubPlayer();
        final AtomicBoolean active = new AtomicBoolean(true);

        subject.reconcile(viewer, target, active::get, true);

        assertTrue(subject.isHidden(target), "the hide is recorded");
        assertEquals(List.of("hide"), recorder.calls);

        active.set(false);
        subject.reconcile(viewer, target, active::get, true);

        assertFalse(subject.isHidden(target), "ending the vanish must release the hide");
        assertEquals(List.of("hide", "show"), recorder.calls, "exactly one release");
    }

    @Test
    void aReplacementVanishDoesNotHideTheSameTargetTwice() {
        final RecordingVisibility recorder = new RecordingVisibility();
        final FeignVisibility subject = new FeignVisibility(recorder);
        final Player viewer = stubPlayer();
        final Player target = stubPlayer();
        final AtomicBoolean active = new AtomicBoolean(true);

        subject.reconcile(viewer, target, active::get, true);
        // A vanish was replaced by a newer one: still active, a new callback arrives.
        subject.reconcile(viewer, target, active::get, true);

        assertEquals(List.of("hide"), recorder.calls, "one hide per viewer and target");

        // The old vanish ends and the replacement starts again: one release, then one hide.
        active.set(false);
        subject.reconcile(viewer, target, active::get, true);
        active.set(true);
        subject.reconcile(viewer, target, active::get, true);

        assertEquals(List.of("hide", "show", "hide"), recorder.calls);
        assertTrue(subject.isHidden(target), "the replacement owns the single hide");
    }

    @Test
    void anEndThenQueuedCallbackLeavesNoHide() {
        final RecordingVisibility recorder = new RecordingVisibility();
        final FeignVisibility subject = new FeignVisibility(recorder);
        final Player viewer = stubPlayer();
        final Player target = stubPlayer();
        final AtomicBoolean active = new AtomicBoolean(true);

        subject.reconcile(viewer, target, active::get, true); // hide queued and applied
        active.set(false);
        subject.reconcile(viewer, target, active::get, true); // the end fan-out releases

        // The stale callback queued before the end drains afterwards and reads "not active".
        subject.reconcile(viewer, target, active::get, true);

        assertEquals(List.of("hide", "show"), recorder.calls, "the stale callback neither hides nor shows again");
        assertFalse(subject.isHidden(target), "no hide is left behind");
    }

    @Test
    void aVanishThatEndsDuringTheHideIsReleasedInTheSameCall() {
        final RecordingVisibility recorder = new RecordingVisibility();
        final FeignVisibility subject = new FeignVisibility(recorder);
        final Player viewer = stubPlayer();
        final Player target = stubPlayer();
        final AtomicInteger reads = new AtomicInteger();

        // Active when the decision is made, inactive by the time the hide returns.
        subject.reconcile(viewer, target, () -> reads.getAndIncrement() == 0, true);

        assertEquals(List.of("hide", "show"), recorder.calls, "the re-read releases immediately");
        assertEquals(2, reads.get(), "the desired state is read again after the hide");
        assertFalse(subject.isHidden(target));
    }

    @Test
    void aGoneTargetIsForgottenWithoutAShow() {
        final RecordingVisibility recorder = new RecordingVisibility();
        final FeignVisibility subject = new FeignVisibility(recorder);
        final Player viewer = stubPlayer();
        final Player target = stubPlayer();
        final AtomicBoolean active = new AtomicBoolean(true);

        subject.reconcile(viewer, target, active::get, true);
        active.set(false);
        subject.reconcile(viewer, target, active::get, false); // the target's session is gone

        assertEquals(List.of("hide"), recorder.calls, "a disconnected target cannot be shown");
        assertFalse(subject.isHidden(target), "but the record is dropped with the session");
    }

    // ---------------------------------------------------------------------------------------------
    // Feign Death: wiring
    // ---------------------------------------------------------------------------------------------

    @Test
    void aQueuedVisibilityChangeOnlyAppliesToTheCurrentRun() {
        final ConcurrentHashMap<UUID, Object> runs = new ConcurrentHashMap<>();
        final UUID wearer = new UUID(0, 7);
        final Object run = new Object();

        assertFalse(FeignDeathSettings.active(runs, wearer), "nothing registered yet");
        runs.put(wearer, run);
        assertTrue(FeignDeathSettings.active(runs, wearer));
        runs.remove(wearer, run);
        assertFalse(FeignDeathSettings.active(runs, wearer), "an ended vanish is not active");
    }

    @Test
    void feignSettlesEachViewerOnItsOwnRegionAndReleasesOnEnd() throws IOException {
        final String source = read(ARMOR);
        final String hide = method(source, "hideFromOthers");
        final String view = method(source, "reconcileViewer");
        final String end = method(source, "endFeign");
        final String reveal = method(source, "revealToAll");

        assertTrue(hide.contains("for (final Player viewer : Bukkit.getOnlinePlayers())"));
        assertTrue(hide.contains("reconcileViewer(wearer, viewer)"));
        assertTrue(view.contains("runOnOwner(viewer,"), "the viewer's client is changed on the viewer's region");
        assertTrue(view.contains("if (!viewer.isConnected()) return;"), "a dead session is skipped");
        assertTrue(view.contains("final FeignView view = viewOf(viewer);"),
                "the session record is resolved first");
        assertTrue(view.contains("if (view == null) return;"), "a callback never creates a session");
        assertTrue(view.contains("view.visibility.reconcile(viewer, target,"),
                "one task settles the record and the visibility call together");
        assertTrue(view.contains("FeignDeathSettings.active(this.feignRuns, target.getUniqueId())"),
                "the desired state is read at execution time");
        assertTrue(end.contains("revealToAll(run.wearer)"), "the reconcile trigger is not removed");
        assertTrue(reveal.contains("Bukkit.getOnlinePlayers()"), "the end fans out to every online session");
        assertFalse(view.contains("Bukkit.getPlayer("), "never re-resolve a session from a UUID in the callback");
        assertFalse(reveal.contains("Bukkit.getPlayer("), "nor in the fan-out");
        assertFalse(source.contains("setInvisible"), "Feign never owns the wearer's invisibility flag");
    }

    @Test
    void aRejoinStartsWithNoHides() throws IOException {
        final RecordingVisibility recorder = new RecordingVisibility();
        final Player viewer = stubPlayer();
        final Player target = stubPlayer();

        final FeignVisibility before = new FeignVisibility(recorder);
        before.reconcile(viewer, target, () -> true, true);
        assertTrue(before.isHidden(target));

        // A rejoin builds a fresh session: nothing hidden from the old client carries over.
        final FeignVisibility rejoined = new FeignVisibility(recorder);

        assertTrue(rejoined.isEmpty(), "a new session records no hides");
        assertFalse(rejoined.isHidden(target));

        final String source = read(ARMOR);
        final String join = method(source, "onArmorJoin");
        final String quit = method(source, "onArmorQuit");

        assertTrue(join.contains("this.feignViews.put(player.getUniqueId(), new FeignView(player, this.bukkitVisibility));"),
                "a rejoin replaces the session record");
        assertTrue(join.contains("hideActiveVanishFrom(player)"),
                "and reconciles a vanish that is already running for it");
        assertTrue(method(source, "hideActiveVanishFrom").contains("reconcileViewer(run.wearer, viewer)"),
                "which hides the wearer from the new session");
        assertTrue(quit.contains("this.feignViews.computeIfPresent(id, (key, view) -> view.player == player ? null : view);"),
                "a quit discards this exact viewer session");
        assertFalse(quit.contains("isEmpty()"), "even when that session still records hides");
    }

    @Test
    void disableReleasesEveryRecordedHide() throws IOException {
        final String source = read(ARMOR);
        final String stop = method(source, "stop");
        final String release = method(source, "releaseAll");

        assertTrue(stop.contains("for (final FeignView view : this.feignViews.values()) releaseAll(view);"),
                "stop releases every recorded hide");
        assertTrue(stop.indexOf("releaseAll(view)") < stop.indexOf("this.feignViews.clear();"),
                "before the sessions are dropped");
        assertTrue(release.contains("view.visibility.release(viewer, target, target.isConnected())"),
                "through the recorded hide only");
        assertTrue(release.contains("runOnOwner(viewer,"), "on the viewer's owning region");
    }

    @Test
    void feignCountsHitsOnTheWearersThread() throws IOException {
        final String hit = method(read(ARMOR), "onFeignHit");

        assertTrue(hit.contains("damager instanceof Projectile && causing instanceof Player"));
        assertTrue(hit.contains("FeignDeathSettings.countsAsOutgoingHit("));
        assertTrue(hit.contains("runOnOwner(run.wearer,"), "the counter is updated on the wearer's owner thread");
        assertTrue(hit.contains("FeignDeathSettings.endsAfter(run.level, ++run.hits)"));
        assertTrue(hit.contains("if (this.feignRuns.get(attacker.getUniqueId()) != run) return;"), "a stale run never counts");
    }

    @Test
    void feignRunsAfterSwordBonusesAndRetriesOnlyUndecidedHitsAfterTheFolds() throws IOException {
        final String plugin = read(JAVA + "CrazyEnchantments.java");
        final String listener = read(JAVA + "listeners/FeignDeathListener.java");

        final int modifiers = plugin.indexOf("new DamageModifierListener()");
        final int sword = plugin.indexOf("new SwordEnchantments()");
        final int feign = plugin.indexOf("registerEvents(feignDeath, this)");
        final int armor = plugin.indexOf("new ArmorEnchantments()");
        final int defensive = plugin.indexOf("new DefensiveArmorListener()");
        final int resolved = plugin.indexOf("registerEvent(EntityDamageEvent.class, feignDeath, EventPriority.MONITOR");

        assertTrue(modifiers >= 0 && sword > modifiers && feign > sword, "registered after sword and shared damage bonuses");
        assertTrue(armor > feign, "registered before the armor listener");
        assertTrue(defensive > armor && resolved > defensive, "late Feign sees the folds and Creeper Armor's reduction");
        assertTrue(plugin.contains("feignDeath.onDamageResolved(hit);\n        }, this, true);"));
        assertTrue(plugin.contains("hit.isCancelled()) feignDeath.clearDamage(hit);\n        }, this, false);"));
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n    public void onDamage("));
        assertTrue(method(listener, "onDamageResolved").contains("if (Boolean.TRUE.equals(this.retryByEvent.remove(event))) this.trigger.test(event);"),
                "consume the exact event's decision before retrying only a below-threshold hit");
        final String trigger = method(read(ARMOR), "tryFeignDeath");
        assertTrue(trigger.indexOf("!settings.ready(") < trigger.indexOf("if (!settings.triggers("));
        assertTrue(trigger.contains("&& wornHighest(wearer, CEnchantments.FEIGN_DEATH, armor) > 0;"));
        assertTrue(trigger.contains("if (level < 1 || event.isCancelled()) return false;"), "a failed or vetoed attempt is final");
        assertTrue(trigger.contains("event.setCancelled(true)"));
    }

    // ---------------------------------------------------------------------------------------------
    // Reload ordering
    // ---------------------------------------------------------------------------------------------

    @Test
    void reloadReconciliationRunsAfterRegistryPublication() throws IOException {
        final String source = read(ARMOR);
        final String manager = read(JAVA + "api/CrazyManager.java");

        assertTrue(source.contains("public void reconcileAfterReload()"));
        assertFalse(source.contains("TuningConfig.onLoad("), "the trigger is no longer registered from the constructor");
        assertTrue(manager.contains("armor.reconcileAfterReload()"), "the reload triggers reconciliation");
        assertTrue(manager.indexOf("loadInternal();") < manager.indexOf("reconcileAfterReload()"),
                "only after the whole registry and manager load has published");
        assertTrue(source.contains("if (feign != null && feign.settings != TuningConfig.feignDeath()) endFeign(id, feign);"),
                "a run is retired by the settings snapshot it carries");
        assertTrue(method(source, "startFeign").contains("run.settings = settings;"),
                "and stamped with the record it actually uses");
    }

    @Test
    void aReloadRetiresALivePlagueBurstAndAdmitsTheNext() throws IOException {
        final String source = read(ARMOR);
        final String burst = method(source, "spawnPlagueBurst");
        final String retire = method(source, "retirePlagueRun");

        assertTrue(burst.contains("run.settings = settings;"), "the burst is stamped at admission");
        assertTrue(burst.contains("if (this.plagueRuns.putIfAbsent(wearer.getUniqueId(), run) != null) return;"),
                "one burst per wearer");
        assertTrue(source.contains("if (burst != null && burst.settings != TuningConfig.plagueCarrier()) {"),
                "reconciliation retires a burst started under the old record");
        assertTrue(source.contains("this.plagueRuns.remove(id, burst);"),
                "and detaches exactly the entry it checked");
        assertTrue(retire.contains("this.plagueRuns.remove(run.wearer.getUniqueId(), run);"),
                "retirement drops the registry entry by identity");
        assertTrue(retire.contains("fuse.cancel();"), "and cancels every fuse task");
        assertTrue(retire.contains("removeSummon(creeper, mayRemoveDirectly);"), "and removes every creeper");

        // Retirement is what re-opens admission.
        final ConcurrentHashMap<UUID, Object> runs = new ConcurrentHashMap<>();
        final UUID wearer = new UUID(0, 21);
        final Object oldBurst = new Object();
        final Object newBurst = new Object();

        runs.put(wearer, oldBurst);
        assertNotNull(runs.putIfAbsent(wearer, newBurst), "a live burst blocks admission");
        runs.remove(wearer, oldBurst);
        assertNull(runs.putIfAbsent(wearer, newBurst), "retiring it admits the next burst");
    }

    @Test
    void procEntryPointsAreInertWhileTheRegistryIsUnpublished() throws IOException {
        final String source = read(ARMOR);
        final String manager = read(JAVA + "api/CrazyManager.java");

        assertTrue(method(source, "beginReload").contains("this.registryUnpublished = true;"));
        assertTrue(method(source, "endReload").contains("this.registryUnpublished = false;"));

        for (final String entry : List.of("onPlayerDamage", "onSupportDamage", "onSupportDeath", "tryFeignDeath",
                "onAegisHit", "onAegisRecord", "onExplosionDamage",
                "onExplosionKnockback", "onArmorWear", "onRagdollHit", "onSlowApplied", "onAura")) {
            final String body = method(source, entry);

            assertTrue(body.contains("if (this.registryUnpublished) return;")
                            || body.contains("if (this.registryUnpublished || "),
                    entry + " must refuse procs during a reload");
        }

        final String schedule = method(source, "scheduleRun");

        assertTrue(schedule.contains("if (this.registryUnpublished) return;"),
                "Curse, Destruction and Plague pulses are skipped while the gate is set");
        assertTrue(schedule.indexOf("this.registryUnpublished") < schedule.indexOf("pulse.accept("));

        assertTrue(manager.contains("armor.beginReload();"));
        assertTrue(manager.indexOf("armor.beginReload();") < manager.indexOf("TuningConfig.load("),
                "the gate closes before the first tuning record is replaced");
        assertTrue(manager.indexOf("armor.endReload();") > manager.indexOf("} finally {"),
                "and is cleared from a finally");
        assertTrue(manager.indexOf("armor.reconcileAfterReload();") > manager.indexOf("armor.endReload();"),
                "admission is open again before reconciliation runs");
    }

    @Test
    void aFailedReloadStillClearsTheGate() throws IOException {
        final String manager = read(JAVA + "api/CrazyManager.java");
        final int begin = manager.indexOf("armor.beginReload();");
        final int guarded = manager.indexOf("try {", begin);
        final int cleared = manager.indexOf("armor.endReload();", guarded);
        final int finallyAt = manager.indexOf("} finally {", guarded);

        assertTrue(begin >= 0 && guarded > begin, "the gate is set before the guarded load");
        assertTrue(finallyAt > guarded, "the guarded load has a finally block");
        assertTrue(cleared > finallyAt && cleared - finallyAt < 200,
                "the clear sits in that finally, so a throwing reload cannot wedge every proc off");
    }

    // ---------------------------------------------------------------------------------------------
    // Movement pass, session state and the defensive listener
    // ---------------------------------------------------------------------------------------------

    @Test
    void theArmorPassRunsOnThePlayersOwnRegionThread() throws IOException {
        final String processor = read(JAVA + "tasks/processors/ArmorProcessor.java");

        assertFalse(processor.contains("PoolProcessor"), "no pass is handed to the shared worker pool");
        assertFalse(processor.contains("executor"), "the pool is not reached through a base class either");
        assertTrue(processor.contains("player.getScheduler().run(this.plugin"), "the pass is queued on the owner");
        assertTrue(processor.contains("if (!this.pending.add(id)) return;"), "one queued pass per player");
        assertTrue(processor.contains("this.pending.remove(id)"), "the entry is released when the pass runs");

        assertTrue(method(read(ARMOR), "onPlayerMovement").contains("armorProcessor.add("));
    }

    @Test
    void angelIsReadBeforeItIsUsed() throws IOException {
        final String processor = read(JAVA + "tasks/processors/ArmorProcessor.java");
        final String angel = method(processor, "checkAngel");

        assertTrue(angel.contains("enchantments.get(CEnchantments.ANGEL.getEnchantment())"));
        assertTrue(angel.contains("angel == null || angel < 1"), "a piece without Angel is skipped cleanly");
        assertTrue(angel.indexOf("angel == null") < angel.indexOf("isMoveEventActive("),
                "the level is read before anything else uses it");
        assertFalse(processor.contains("4 + enchantments.get(CEnchantments.ANGEL.getEnchantment())"),
                "no null unboxing call site is left");
        assertFalse(angel.contains("players.isEmpty()"), "targets are no longer collected across regions");
        assertTrue(angel.contains("new FoliaScheduler(plugin, null, otherPlayer)"),
                "each target's effect is applied on that target's own region");
    }

    @Test
    void combatStateIsClearedOnDeathWorldChangeReloadAndQuit() throws IOException {
        final String source = read(ARMOR);
        final String clear = method(source, "clearCombatState");

        for (final String field : List.of("this.aegisHits.remove(id)", "this.creeperHeals.remove(id)",
                "this.ragdollCooldowns.remove(id)", "this.ragdollSessions.remove(id)",
                "this.bloodLustCooldowns.remove(id)")) {
            assertTrue(clear.contains(field), field);
        }

        assertTrue(method(source, "onArmorQuit").contains("clearCombatState(id)"));
        assertTrue(method(source, "onRunDeath").contains("clearCombatState(id)"), "a death ends the fight too");
        assertTrue(method(source, "onArmorWorldChange").contains("clearCombatState(event.getPlayer().getUniqueId())"));
        assertTrue(method(source, "reconcileWornRuns").contains("clearCombatState(id)"), "a reload drops it as well");

        // The Ragdoll push carries the session it was queued under.
        final String ragdoll = method(source, "onRagdollHit");

        assertTrue(ragdoll.contains("this.ragdollSessions.computeIfAbsent("));
        assertTrue(ragdoll.contains("if (this.ragdollSessions.get(id) != session) return;"),
                "a queued push is dropped by a death, world change, reload or rejoin");
    }

    @Test
    void theDefensiveListenerSharesTheReloadGateAndStateLifecycle() throws IOException {
        final String listener = read(JAVA + "listeners/DefensiveArmorListener.java");

        assertTrue(listener.contains("armor.isReloading()"),
                "Clarity, Arrow Deflect and Ender Walker refuse during a reload");
        assertTrue(listener.contains("armor.reloadEpoch()"), "and rebuild their state after one");

        for (final String entry : List.of("onBlindness", "onArrowDamage", "onDamageTick")) {
            assertTrue(method(listener, entry).contains("if (reloading()) return;"), entry);
        }

        final String clear = method(listener, "clear");

        assertTrue(clear.contains("this.nextArrowHit.remove(id)") && clear.contains("this.lastEnderHeal.remove(id)"));
        assertTrue(method(listener, "onQuit").contains("clear(event.getPlayer().getUniqueId())"));
        assertTrue(method(listener, "onDeath").contains("clear(event.getEntity().getUniqueId())"));
        assertTrue(method(listener, "onWorldChange").contains("clear(event.getPlayer().getUniqueId())"));
    }

    @Test
    void reactiveArmorNeverMutatesAForeignEntity() throws IOException {
        final String source = read(ARMOR);
        final String hit = method(source, "onPlayerDamage");

        assertTrue(hit.contains("runOnOwner(damager,"), "retaliation is applied on the attacker's region");
        // Rocket Escape launches straight up: it never reads the attacker, so there is nothing to snapshot.
        assertFalse(method(source, "launchRocketEscape").contains("damager"),
                "Rocket Escape never reads the attacker's position");
        assertTrue(method(source, "onAura").contains("runOnOwner(other,"),
                "auras touch the other player on its own region");
    }

    // ---------------------------------------------------------------------------------------------
    // Shared
    // ---------------------------------------------------------------------------------------------

    @Test
    void aReconnectDoesNotResetTheNewCooldowns() throws IOException {
        final String quit = method(read(ARMOR), "onArmorQuit");

        assertFalse(quit.contains("plagueCooldowns.remove("), "the 60 s window survives the reconnect");
        assertFalse(quit.contains("feignCooldowns.remove("), "the 120 s window survives the reconnect");
        assertFalse(quit.contains("plagueSpent.remove("), "the required recovery survives the reconnect");
        assertTrue(quit.contains("stopRuns("), "but summons and visibility changes still end");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Records the visibility calls the seam makes. The Player arguments are never
     * dereferenced, so plain proxies keep the test off a Bukkit server.
     */
    private static final class RecordingVisibility implements FeignVisibility.Visibility {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void hide(final Player viewer, final Player target) {
            this.calls.add("hide");
        }

        @Override
        public void show(final Player viewer, final Player target) {
            this.calls.add("show");
        }
    }

    /**
     * A Player identity without a server. Only hashCode/equals are ever called, and
     * both are identity-based, matching the session objects the seam keys on.
     */
    private static Player stubPlayer() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("toString")) return "PlayerStub@" + System.identityHashCode(proxy);

                    return null;
                });
    }

    private static String method(final String source, final String name) {
        final var start = Pattern.compile("(?m)^    (?:public |private )(?:static )?\\S+ " + name + "\\(").matcher(source);

        assertTrue(start.find(), name);

        final int end = source.indexOf("\n    }", start.end());

        assertTrue(end > start.end(), name);

        return source.substring(start.start(), end);
    }

    private static String read(final String relative) throws IOException {
        final Path path = Path.of(relative);

        return Files.readString(Files.exists(path) ? path : Path.of("paper").resolve(path)).replace("\r\n", "\n");
    }
}
