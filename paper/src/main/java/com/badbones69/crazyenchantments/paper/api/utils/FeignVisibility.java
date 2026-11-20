package com.badbones69.crazyenchantments.paper.api.utils;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Per-(viewer, target) hide bookkeeping for Feign Death.
 *
 * <p>A hide is not owned by a vanish: {@code hideEntity} belongs to the plugin, the
 * viewer and the target, so the record is keyed by the captured target session and
 * lives until it is released. A replacement vanish therefore never hides the same
 * target twice, and the end of a vanish cannot strand a hide behind a snapshot of
 * "who finished hiding".</p>
 *
 * <p>Every method must be called on the viewer's owning region thread, inside one
 * task. The only shared read is the insertion of the per-viewer instance itself.</p>
 */
public final class FeignVisibility {

    /**
     * The visibility primitive this class needs. Production delegates to Paper's
     * per-player hide/show; a test supplies a recording double so the call sequence
     * can be asserted without a server.
     */
    public interface Visibility {
        void hide(Player viewer, Player target);

        void show(Player viewer, Player target);
    }

    private final Visibility visibility;

    // target session -> true once this plugin hid it from this viewer session.
    private final Map<Player, Boolean> hidden = new ConcurrentHashMap<>();

    public FeignVisibility(final Visibility visibility) {
        this.visibility = visibility;
    }

    /**
     * Settles one viewer/target pair towards the current desired state.
     *
     * @param active         whether the target currently has an active vanish,
     *                       re-read at execution time and again after a hide
     * @param targetConnected whether the target's captured session is still alive
     */
    public void reconcile(final Player viewer, final Player target, final BooleanSupplier active, final boolean targetConnected) {
        final boolean desired = targetConnected && active.getAsBoolean();

        if (!desired) {
            release(viewer, target, targetConnected);

            return;
        }

        if (!this.hidden.containsKey(target)) {
            this.visibility.hide(viewer, target);
            this.hidden.put(target, Boolean.TRUE);
        }

        // The vanish may have ended while the hide was applied; settle again in this call.
        if (!active.getAsBoolean()) release(viewer, target, targetConnected);
    }

    /**
     * Undoes only a hide this instance applied. A disconnected target session cannot
     * be shown, so its record is dropped and the server's discard of that session's
     * hides is left to do the rest.
     *
     * <p>Paper's show is not reference counted: if another plugin hides the same
     * target while the record is held, releasing can override that hide. Only a
     * recorded hide - one this plugin applied - ever reaches the call.</p>
     */
    public boolean release(final Player viewer, final Player target, final boolean targetConnected) {
        if (this.hidden.remove(target) == null) return false;

        if (targetConnected) this.visibility.show(viewer, target);

        return true;
    }

    public boolean isHidden(final Player target) {
        return this.hidden.containsKey(target);
    }

    public boolean isEmpty() {
        return this.hidden.isEmpty();
    }

    public List<Player> targets() {
        return List.copyOf(this.hidden.keySet());
    }
}
