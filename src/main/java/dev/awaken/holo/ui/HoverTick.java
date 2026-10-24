package dev.awaken.holo.ui;

/**
 * When a holo panel ticks under the cursor (docs/modules/holo.md "Sounds"): once as the cursor comes onto a new target
 * (a K-menu tab, an inventory slot with something in it, the recipe button), never more often than every
 * {@link #MIN_GAP_MS}, and never while {@code quiet} (the panel still unfolding, items being spread by a drag). A
 * quiet frame still notes the target, so nothing ticks late for it. Sweeping across a full hotbar ticks about four
 * times, not nine. Plain fields: side-agnostic, checked by GameTests.
 */
public final class HoverTick {
    public static final int NONE = -1;
    /** The closest two ticks may be, real milliseconds. */
    public static final float MIN_GAP_MS = 55f;

    private int last = NONE;
    private float sinceTick = MIN_GAP_MS;

    /**
     * Once a frame: {@code target} is what the cursor is over (an id ≥ 0, or {@link #NONE}), {@code dtMs} the real
     * time since the last frame. True when this frame should tick.
     */
    public boolean update(int target, float dtMs, boolean quiet) {
        sinceTick = Math.min(sinceTick + dtMs, MIN_GAP_MS);
        boolean changed = target != last;
        last = target;
        if (!changed || target == NONE || quiet || sinceTick < MIN_GAP_MS) return false;
        sinceTick = 0;
        return true;
    }

    /** A panel opened: nothing is under the cursor yet. */
    public void reset() {
        last = NONE;
        sinceTick = MIN_GAP_MS;
    }
}
