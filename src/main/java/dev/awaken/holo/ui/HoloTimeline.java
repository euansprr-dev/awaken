package dev.awaken.holo.ui;

/**
 * The open and close timeline of a wrist panel (docs/proposals/holo-ui.md §5), in milliseconds along the K menu's
 * opening (its choreography):
 * <pre>
 *   0–120   the arm rises            80–200  the band flares
 *   140–370 the panel grows out of the wrist into its place, unfolding (Y, then X); the scan line sweeps
 *   300–450 text types in            from 250 clicks land
 *   370–450 the panel lands (a panel that hands over to a flat screen, the inventory, crossfades to it here)
 * </pre>
 * Closing plays the same curves backward, 1.8× faster (250 ms). Pressing the key again while it opens skips to open.
 * With reduced motion the panel fades in place instead of unfolding. {@link #speed} plays the same choreography faster
 * (the inventory: 2.5×, open in 180 ms, closed in 100 ms). Plain floats: side-agnostic, so GameTests check the curves.
 */
public final class HoloTimeline {
    public static final float OPEN_MS = 450f, CLOSE_MS = 250f;
    /** The inventory's speed: the K menu's choreography in 180 ms (docs/proposals/holo-ui.md principle 3). */
    public static final float INVENTORY_SPEED = 2.5f;
    /** Choreography ms per real ms. */
    public final float speed;
    private float t;
    private boolean active, closing, frozen;

    public HoloTimeline() {
        this(1f);
    }

    public HoloTimeline(float speed) {
        this.speed = speed;
    }

    public void open() {
        if (!active) t = 0;
        active = true;
        closing = false;
    }

    public void close() {
        if (active) closing = true;
    }

    /** Gone at once, no animation. */
    public void stop() {
        active = false;
        closing = false;
        t = 0;
    }

    public void skip() {
        if (active && !closing) t = OPEN_MS;
    }

    /** Jumps to a moment along the choreography (the autopilot photographs fixed moments). */
    public void seek(float ms) {
        active = true;
        closing = false;
        t = Math.max(0, Math.min(OPEN_MS, ms));
    }

    public void hold(boolean frozen) {
        this.frozen = frozen;
    }

    /** Advances by {@code dtMs} real milliseconds. */
    public void advance(float dtMs) {
        if (!active || frozen) return;
        float d = dtMs * speed;
        if (closing) {
            t -= d * (OPEN_MS / CLOSE_MS);
            if (t <= 0) {
                t = 0;
                active = false;
                closing = false;
            }
        } else {
            t = Math.min(OPEN_MS, t + d);
        }
    }

    public boolean active() {
        return active;
    }

    public boolean closing() {
        return closing;
    }

    public boolean fullyOpen() {
        return active && !closing && t >= OPEN_MS;
    }

    /** Where along the choreography it is (K menu milliseconds). */
    public float ms() {
        return t;
    }

    /** Real milliseconds the whole opening takes at this speed. */
    public float openRealMs() {
        return OPEN_MS / speed;
    }

    /** Real milliseconds a close from fully open takes at this speed. */
    public float closeRealMs() {
        return CLOSE_MS / speed;
    }

    public boolean clickable() {
        return active && !closing && t >= 250f;
    }

    // ---- curves ---------------------------------------------------------------------------------------------------

    public static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    public static float easeOut(float v) {
        float u = 1 - clamp01(v);
        return 1 - u * u * u;
    }

    public static float easeInOut(float v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    /** 0 lowered, 1 raised (with a 4% overshoot that settles). */
    public float arm() {
        float v = clamp01(t / 120f);
        float u = 1 - v;
        return 1 - u * u * u + (float) Math.sin(v * Math.PI) * 0.04f * v;
    }

    /** The band's brightness: off, a flare peaking at 140 ms, then a steady glow. */
    public float band() {
        if (t <= 60f) return 0.15f * clamp01(t / 60f);
        if (t <= 140f) return 0.15f + 2.35f * easeOut((t - 60f) / 80f);
        return 1f + 1.5f * (1 - easeOut((t - 140f) / 120f));
    }

    public float unfoldY(boolean reduced) {
        return reduced ? (t > 160 ? 1 : 0) : easeOut((t - 160f) / 120f);
    }

    public float unfoldX(boolean reduced) {
        return reduced ? (t > 160 ? 1 : 0) : easeOut((t - 220f) / 140f);
    }

    /** The frame and corners show before the glass fills in. */
    public float edges(boolean reduced) {
        return reduced ? clamp01((t - 160f) / 150f) : clamp01((t - 170f) / 60f);
    }

    public float glass(boolean reduced) {
        return reduced ? clamp01((t - 160f) / 150f) : easeOut((t - 230f) / 150f);
    }

    /** Where the scan line is, 0 top to 1 bottom, or a negative number when it isn't showing. */
    public float scan(boolean reduced) {
        if (reduced || closing) return -1;
        float v = (t - 250f) / 140f;
        return v < 0 || v > 1.15f ? -1 : v;
    }

    /** Milliseconds of typing so far (text shows in full once it reaches {@code Float.MAX_VALUE}). */
    public float typing(boolean reduced) {
        if (reduced) return t >= 300 ? Float.MAX_VALUE : -1;
        if (fullyOpen()) return Float.MAX_VALUE;
        return t - 300f;
    }

    /** How far the panel has grown out of the wrist: 0 at the emitter, 1 in its place. */
    public float grow(boolean reduced) {
        return reduced ? 1 : easeOut((t - 140f) / 230f);
    }

    /** The beams from the wrist to the panel: bright while it opens, a faint steady link while it is open. */
    public float beam(boolean reduced) {
        if (reduced) return clamp01((t - 300f) / 150f) * 0.35f;
        return Math.max(beamBoost(false), 0.35f * clamp01((t - 260f) / 120f));
    }

    /** The opening surge: up from 110 ms, peaking around 200, gone by 360 (also how much wider the beams are). */
    public float beamBoost(boolean reduced) {
        if (reduced) return 0;
        return clamp01((t - 110f) / 50f) * (1 - clamp01((t - 250f) / 110f));
    }

    /**
     * 0..1: the panel has grown into its place and settles (370–450). A panel that hands over to a flat screen (the
     * inventory) crossfades here: the flat screen comes up by this, the 3D panel goes down by it.
     */
    public float landed() {
        return easeInOut((t - 370f) / 80f);
    }

    /** How much the open panel holds attention (Lens focus): eased with the glass. */
    public float focus() {
        return active ? easeInOut((t - 160f) / 290f) : 0f;
    }
}
