package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.api.builders.ItemBuilder;
import com.badbones69.crazyenchantments.paper.api.builders.types.MenuManager;
import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.api.enums.keys.FileKeys;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.objects.enchants.EnchantmentType;
import com.badbones69.crazyenchantments.paper.api.utils.ColorUtils;
import com.badbones69.crazyenchantments.paper.api.utils.NameTagSession;
import com.badbones69.crazyenchantments.paper.api.utils.NameTagStorage;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.text.WordUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A name tag is clicked onto one item in the player's own inventory, then the
 * player types a name and confirms it. Nothing is escrowed: the tag stays in
 * the player's possession and is only consumed when the rename is applied, so
 * a cancel or a timeout returns both items untouched.
 */
public class NameTagListener implements Listener {

    private static final String TAG_VALUE = "ItemNameTag";

    private static final int STORAGE_SIZE = 36;

    private static final long TIMEOUT_TICKS = 20L * 120L;

    /** What a grant actually delivered; {@code dropped} names what had to go on the ground. */
    public record Delivery(int requested, int delivered, int dropped) {}

    private final CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final Map<UUID, NameTagSession> sessions = new ConcurrentHashMap<>();

    private final Map<UUID, ScheduledTask> timeouts = new ConcurrentHashMap<>();

    public ItemStack getNameTag(final int amount) {
        final ItemStack item = new ItemBuilder()
                .setMaterial(Material.NAME_TAG)
                .setName(component(Messages.NAMETAG_ITEM_NAME))
                .setLore(List.of(
                        "&7Click this on an item in your inventory",
                        "&7to give it a custom name.",
                        "&8One item per name tag."))
                .addKey(DataKeys.name_tag.getNamespacedKey(), TAG_VALUE)
                .build();

        item.setAmount(Math.max(1, amount));

        return item;
    }

    /**
     * Places as many tags as fit, then drops the rest in stack-sized pieces so a
     * full inventory neither loses them nor gets a false success message.
     */
    public Delivery deliver(final Player player, final int requested) {
        final ItemStack tag = getNameTag(1);
        final int max = tag.getType().getMaxStackSize();

        final PlayerInventory inventory = player.getInventory();
        final NameTagStorage.Plan plan = NameTagStorage.plan(requested, max, storageSlots(inventory));
        final int delivered = requested - plan.remainder();

        if (delivered <= 0) return new Delivery(requested, 0, 0);

        apply(plan, tag, inventory);
        dropRemainder(player, tag, plan.remainder(), max);

        return new Delivery(requested, delivered, plan.remainder());
    }

    public boolean isNameTag(final ItemStack item) {
        if (item == null || item.isEmpty()) return false;

        return TAG_VALUE.equals(item.getPersistentDataContainer().get(DataKeys.name_tag.getNamespacedKey(), PersistentDataType.STRING));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;

        final ItemStack tag = event.getCursor();
        final ItemStack target = event.getCurrentItem();

        if (!isNameTag(tag) || target == null || target.isEmpty()) return;

        event.setCancelled(true);

        if (isNameTag(target)) return;

        if (target.getAmount() != 1) {
            player.sendMessage(Messages.NEED_TO_UNSTACK_ITEM.getMessage());

            return;
        }

        if (!isEligible(target)) {
            player.sendMessage(Messages.NAMETAG_NOT_ELIGIBLE.getMessage());

            return;
        }

        if (this.sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(Messages.NAMETAG_SESSION_PENDING.getMessage());

            return;
        }

        // Park the whole stack before the session exists: a closed container cannot
        // drop it back out of the cursor, and a partial move is refused untouched.
        if (!parkTag(player)) {
            player.sendMessage(Messages.NAMETAG_NEED_SPACE.getMessage());

            return;
        }

        final ItemStack empty = new ItemStack(Material.AIR);

        event.setCursor(empty);
        player.setItemOnCursor(empty);

        begin(player, event.getSlot(), target);
    }

    /**
     * A marked tag must never fall through to vanilla mob naming.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(final PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();

        if (!isNameTag(player.getInventory().getItem(event.getHand()))) return;

        event.setCancelled(true);
        player.sendMessage(Messages.NAMETAG_USE_ON_ITEM.getMessage());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        discard(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        discard(event.getEntity().getUniqueId());
    }

    public void preview(final Player player, final String token, final String rawName) {
        final NameTagSession session = this.sessions.get(player.getUniqueId());

        if (session == null || !session.tokenMatches(parseToken(token)) || session.isClaimed()) {
            noSession(player);

            return;
        }

        if (NameTagSession.tooLong(rawName)) {
            player.sendMessage(Messages.NAMETAG_NAME_TOO_LONG.getMessage("%max%", String.valueOf(NameTagSession.MAX_NAME_LENGTH)));

            return;
        }

        final String name = NameTagSession.clean(rawName);

        if (name == null) {
            player.sendMessage(Messages.NAMETAG_EMPTY_NAME.getMessage());

            return;
        }

        final ItemStack live = player.getInventory().getItem(session.getSlot());
        final String liveFingerprint = live == null || live.isEmpty() ? null : fingerprint(live);

        if (liveFingerprint == null || !session.gearMatches(liveFingerprint)) {
            discard(player.getUniqueId());
            player.sendMessage(Messages.NAMETAG_ITEM_CHANGED.getMessage());

            return;
        }

        final String preview = NameTagSession.withSuffix(name, visibleName(live), suffixTemplate(), suffixEnabled());

        if (session.submit(parseToken(token), preview) != NameTagSession.Result.OK) {
            noSession(player);

            return;
        }

        player.sendMessage(prefix()
                .append(component(Messages.NAMETAG_PREVIEW))
                .append(ColorUtils.legacyTranslateColourCodes(preview)));

        player.sendMessage(prefix()
                .append(component(Messages.NAMETAG_CONFIRM)
                        .clickEvent(ClickEvent.runCommand("/ce nametagconfirm " + session.getToken()))
                        .hoverEvent(HoverEvent.showText(component(Messages.NAMETAG_CONFIRM_HOVER))))
                .append(Component.text("  "))
                .append(component(Messages.NAMETAG_CANCEL_BUTTON)
                        .clickEvent(ClickEvent.runCommand("/ce nametagcancel " + session.getToken()))
                        .hoverEvent(HoverEvent.showText(component(Messages.NAMETAG_CANCEL_HOVER)))));
    }

    public void confirm(final Player player, final String token) {
        final UUID id = player.getUniqueId();
        final NameTagSession session = this.sessions.get(id);
        final UUID candidate = parseToken(token);

        if (session == null || candidate == null || !session.tokenMatches(candidate)) {
            noSession(player);

            return;
        }

        final ItemStack live = player.getInventory().getItem(session.getSlot());
        final String liveFingerprint = live == null || live.isEmpty() ? null : fingerprint(live);

        if (liveFingerprint == null || !session.gearMatches(liveFingerprint)) {
            discard(id);
            player.sendMessage(Messages.NAMETAG_ITEM_CHANGED.getMessage());

            return;
        }

        if (!hasTag(player)) {
            discard(id);
            player.sendMessage(Messages.NAMETAG_TAG_MISSING.getMessage());

            return;
        }

        final String pendingName = session.getPendingName();

        if (pendingName == null) {
            noSession(player);

            return;
        }

        if (pendingName.equals(visibleName(live))) {
            discard(id);
            player.sendMessage(Messages.NAMETAG_SAME_NAME.getMessage());

            return;
        }

        final NameTagSession.Result result = session.claim(candidate, liveFingerprint);

        if (result != NameTagSession.Result.OK) {
            discard(id);
            player.sendMessage(result == NameTagSession.Result.STALE ? Messages.NAMETAG_ITEM_CHANGED.getMessage() : Messages.NAMETAG_NO_SESSION.getMessage());

            return;
        }

        // Verified on this thread in this tick, so the slot and the tag are unchanged.
        player.getInventory().setItem(session.getSlot(), rename(live, pendingName));
        consumeTag(player);
        discard(id);

        player.sendMessage(prefix()
                .append(component(Messages.NAMETAG_RENAMED))
                .append(ColorUtils.legacyTranslateColourCodes(pendingName)));
    }

    public void cancel(final Player player, final String token) {
        final NameTagSession session = this.sessions.get(player.getUniqueId());
        final UUID candidate = parseToken(token);

        if (session == null || candidate == null || !session.cancel(candidate)) {
            noSession(player);

            return;
        }

        discard(player.getUniqueId());
        player.sendMessage(Messages.NAMETAG_CANCELLED.getMessage());
    }

    /** Called on reload and disable; a session holds no items, so dropping it is enough. */
    public void clearAll() {
        this.sessions.clear();

        this.timeouts.values().forEach(ScheduledTask::cancel);
        this.timeouts.clear();
    }

    private void begin(final Player player, final int slot, final ItemStack target) {
        final UUID id = player.getUniqueId();
        final NameTagSession session = new NameTagSession(UUID.randomUUID(), slot, fingerprint(target));

        if (this.sessions.put(id, session) != null) cancelTimeout(id);

        scheduleTimeout(player, session);

        player.sendMessage(prefix()
                .append(component(Messages.NAMETAG_PROMPT))
                .append(ColorUtils.legacyTranslateColourCodes(visibleName(target))));

        player.sendMessage(prefix().append(component(Messages.NAMETAG_HINT)));

        player.sendMessage(prefix()
                .append(component(Messages.NAMETAG_TYPE_NAME)
                        .clickEvent(ClickEvent.suggestCommand("/ce nametagset " + session.getToken() + " "))
                        .hoverEvent(HoverEvent.showText(component(Messages.NAMETAG_TYPE_NAME_HOVER))))
                .append(Component.text("  "))
                .append(component(Messages.NAMETAG_CANCEL_BUTTON)
                        .clickEvent(ClickEvent.runCommand("/ce nametagcancel " + session.getToken()))));
    }

    private void scheduleTimeout(final Player player, final NameTagSession session) {
        final UUID id = player.getUniqueId();

        final ScheduledTask task;

        try {
            task = player.getScheduler().runDelayed(this.plugin, ignored -> {
                if (!this.sessions.remove(id, session)) return;

                this.timeouts.remove(id);

                if (player.isOnline()) player.sendMessage(Messages.NAMETAG_TIMEOUT.getMessage());
            }, () -> this.timeouts.remove(id), TIMEOUT_TICKS);
        } catch (final IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;

            return;
        }

        if (task == null) return;

        final ScheduledTask previous = this.timeouts.put(id, task);

        if (previous != null) previous.cancel();
    }

    private void cancelTimeout(final UUID id) {
        final ScheduledTask task = this.timeouts.remove(id);

        if (task != null) task.cancel();
    }

    private void discard(final UUID id) {
        this.sessions.remove(id);
        cancelTimeout(id);
    }

    /**
     * Moves the whole cursor stack into storage, all or nothing. Returns false
     * without touching a single slot when it does not fully fit.
     */
    private boolean parkTag(final Player player) {
        final ItemStack tag = player.getItemOnCursor();

        if (!isNameTag(tag)) return false;

        final PlayerInventory inventory = player.getInventory();
        final NameTagStorage.Plan plan = NameTagStorage.plan(tag.getAmount(), tag.getType().getMaxStackSize(), storageSlots(inventory));

        if (!plan.fits()) return false;

        apply(plan, tag, inventory);

        return true;
    }

    private List<NameTagStorage.Slot> storageSlots(final PlayerInventory inventory) {
        final List<NameTagStorage.Slot> slots = new ArrayList<>();

        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            final ItemStack stack = inventory.getItem(slot);

            if (stack == null || stack.isEmpty()) {
                slots.add(new NameTagStorage.Slot(slot, 0, false));
            } else if (isNameTag(stack)) {
                slots.add(new NameTagStorage.Slot(slot, stack.getAmount(), true));
            }
        }

        return slots;
    }

    private void apply(final NameTagStorage.Plan plan, final ItemStack tag, final PlayerInventory inventory) {
        for (final NameTagStorage.Move move : plan.moves()) {
            if (move.merge()) {
                final ItemStack existing = inventory.getItem(move.index());

                if (existing != null && !existing.isEmpty()) {
                    final ItemStack merged = existing.clone();
                    merged.setAmount(existing.getAmount() + move.amount());
                    inventory.setItem(move.index(), merged);

                    continue;
                }
            }

            final ItemStack placed = tag.clone();
            placed.setAmount(move.amount());
            inventory.setItem(move.index(), placed);
        }
    }

    private void dropRemainder(final Player player, final ItemStack tag, final int amount, final int max) {
        int left = amount;

        while (left > 0) {
            final int size = Math.min(max, left);
            final ItemStack drop = tag.clone();
            drop.setAmount(size);
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
            left -= size;
        }
    }

    private boolean hasTag(final Player player) {
        if (isNameTag(player.getItemOnCursor())) return true;

        final PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isNameTag(inventory.getItem(slot))) return true;
        }

        return false;
    }

    private void consumeTag(final Player player) {
        final ItemStack cursor = player.getItemOnCursor();

        if (isNameTag(cursor)) {
            player.setItemOnCursor(decrement(cursor));

            return;
        }

        final PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);

            if (!isNameTag(stack)) continue;

            inventory.setItem(slot, decrement(stack));

            return;
        }
    }

    private ItemStack decrement(final ItemStack stack) {
        final ItemStack copy = stack.clone();

        copy.setAmount(copy.getAmount() - 1);

        return copy.getAmount() <= 0 ? new ItemStack(Material.AIR) : copy;
    }

    private boolean isEligible(final ItemStack item) {
        if (item.getType().getMaxDurability() > 0) return true;

        for (final EnchantmentType type : MenuManager.getEnchantmentTypes()) {
            if (type.getEnchantableMaterials().contains(item.getType())) return true;
        }

        return false;
    }

    private ItemStack rename(final ItemStack source, final String name) {
        final ItemStack item = source.clone();

        final boolean custom = item.hasData(DataComponentTypes.CUSTOM_NAME);

        item.setData(custom ? DataComponentTypes.CUSTOM_NAME : DataComponentTypes.ITEM_NAME, ColorUtils.legacyTranslateColourCodes(name));

        return item;
    }

    private String visibleName(final ItemStack item) {
        if (item == null || item.isEmpty()) return "";

        final boolean custom = item.hasData(DataComponentTypes.CUSTOM_NAME);

        final Component current = custom ? item.getData(DataComponentTypes.CUSTOM_NAME) : item.getData(DataComponentTypes.ITEM_NAME);

        if (current != null) return ColorUtils.toLegacy(current);

        return "&b" + WordUtils.capitalizeFully(item.getType().toString().replace("_", " "));
    }

    private String suffixTemplate() {
        return FileKeys.CONFIG.getConfiguration().getString("Settings.TransmogScroll.Amount-of-Enchantments", " &7[&6&n%amount%&7]");
    }

    private boolean suffixEnabled() {
        return FileKeys.CONFIG.getConfiguration().getBoolean("Settings.TransmogScroll.Amount-Toggle", true);
    }

    private String fingerprint(final ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private UUID parseToken(final String token) {
        try {
            return UUID.fromString(token);
        } catch (final IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private void noSession(final Player player) {
        player.sendMessage(Messages.NAMETAG_NO_SESSION.getMessage());
    }

    private Component prefix() {
        return LegacyComponentSerializer.legacySection().deserialize(ColorUtils.getPrefix());
    }

    private Component component(final Messages message) {
        return LegacyComponentSerializer.legacySection().deserialize(message.getMessageNoPrefix());
    }
}
