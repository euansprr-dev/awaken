package dev.awaken.holo.ui;

/**
 * The calm HUD's tier 1 (docs/proposals/holo-ui.md §8): Awaken's own always-on HUD beside the hotbar (the power bar
 * and the level readout) fades to {@link #IDLE_ALPHA} once nothing has needed it for {@link #IDLE_TICKS} (out of
 * combat, energy full, no cooldown running, no fresh XP), and comes back the moment something does. It leaves slowly
 * ({@link #FADE_OUT_MS}) so the change is never noticed, and returns fast ({@link #FADE_IN_MS}) so a fight never waits
 * for its HUD. Plain fields: side-agnostic, checked by GameTests.
 */
public final class HudFade {
    public static final float IDLE_ALPHA = 0.35f;
    /** Client ticks of nothing happening before the fade starts (3 s). */
    public static final int IDLE_TICKS = 60;
    public static final float FADE_OUT_MS = 1200f, FADE_IN_MS = 150f;

    private int quietTicks;
    /** 0 faded, 1 full, linear in time; shown through an ease. */
    private float k = 1f;

    /** Once a client tick: whether anything needs the tier-1 HUD right now. */
    public void tick(boolean busy) {
        quietTicks = busy ? 0 : Math.min(quietTicks + 1, IDLE_TICKS);
    }

    /** Once a frame, real milliseconds since the last: moves toward full or faded. */
    public void advance(float dtMs) {
        if (quietTicks < IDLE_TICKS) k = Math.min(1f, k + dtMs / FADE_IN_MS);
        else k = Math.max(0f, k - dtMs / FADE_OUT_MS);
    }

    /** The alpha the tier-1 layers draw at, {@link #IDLE_ALPHA}..1. */
    public float alpha() {
        return IDLE_ALPHA + (1f - IDLE_ALPHA) * HoloTimeline.easeInOut(k);
    }

    public boolean idle() {
        return quietTicks >= IDLE_TICKS;
    }

    /** Full again at once, the idle wait starting over (a new world, the setting switched off). */
    public void reset() {
        quietTicks = 0;
        k = 1f;
    }
}
