package dev.awaken.core;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The bookkeeping of holding a player's input still while a scene plays (SPEC §27.6, "Control comes back"), kept free of
 * client classes so a GameTest can drive it ({@code core.client.InputLock} is the client's one instance).
 *
 * <p>Any number of owners may hold at once (a camera shot, a hand-over, the Registry's wait). The player's real input is
 * saved once, by the first, and put back only when the last lets go: an owner never saves another owner's stand-in.
 * Saving the camera's empty stand-in, then putting it back after the camera had already let go, left Euan unable to move
 * after Ilse's scene (2026-10-26). Every hold has a deadline; one that outlives it is let go and named, so a scene that
 * never ends can't keep the player still. A new player object (a respawn) takes the hold over with its own input.
 */
public final class InputHolds<P, I> {
    private final Function<P, I> get;
    private final BiConsumer<P, I> set;
    private final Supplier<I> empty;
    /** Owner → the tick it lets go by itself. */
    private final Map<Object, Long> owners = new LinkedHashMap<>();
    private @Nullable P player;
    private @Nullable I saved;

    public InputHolds(Function<P, I> get, BiConsumer<P, I> set, Supplier<I> empty) {
        this.get = get;
        this.set = set;
        this.empty = empty;
    }

    /** {@code owner} holds the player still until it lets go, or for at most {@code maxTicks}. */
    public void hold(Object owner, @Nullable P p, long now, int maxTicks) {
        owners.put(owner, now + Math.max(1, maxTicks));
        follow(p);
    }

    /** {@code owner} lets go; the real input comes back when nobody holds. */
    public void release(Object owner) {
        owners.remove(owner);
        if (owners.isEmpty()) restore();
    }

    public boolean held(Object owner) {
        return owners.containsKey(owner);
    }

    public boolean held() {
        return !owners.isEmpty();
    }

    /** Once a tick: holds past their deadline let go (returned, to be logged); a new player object is held in turn. */
    public List<Object> tick(@Nullable P p, long now) {
        List<Object> late = new ArrayList<>(0);
        owners.entrySet().removeIf(e -> {
            if (e.getValue() >= now) return false;
            late.add(e.getKey());
            return true;
        });
        if (owners.isEmpty()) restore();
        else follow(p);
        return late;
    }

    /** Everything lets go at once (a logout, a world change). */
    public void clear() {
        owners.clear();
        restore();
    }

    private void follow(@Nullable P p) {
        if (p == null || p == player) return;
        // a respawn or clone: the old body's input is gone with it; hold the new one's real input
        player = p;
        saved = get.apply(p);
        set.accept(p, empty.get());
    }

    private void restore() {
        if (player != null && saved != null) set.accept(player, saved);
        player = null;
        saved = null;
    }
}
