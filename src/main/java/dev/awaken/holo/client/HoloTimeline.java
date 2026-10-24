package dev.awaken.holo.client;

/**
 * The open and close timeline of a wrist panel (docs/proposals/holo-ui.md §5), in milliseconds along the opening:
 * <pre>
 *   0–120   the arm rises            80–200  the band flares
 *   160–360 the panel unfolds (Y, then X) and leaves the wrist; the scan line sweeps
 *   300–450 text types in            from 250 clicks land
 * </pre>
 * Closing plays the same curves backward, 1.8× faster (250 ms). Pressing the key again while it opens skips to open.
 * With reduced motion the panel fades in place instead of unfolding.
 */
public final class HoloTimeline {
    public static final float OPEN_MS = 450f, CLOSE_MS = 250f;
    private float t;
    private boolean active, closing;

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

    /** Jumps to a moment (the autopilot photographs fixed moments). */
    public void seek(float ms) {
        active = true;
        closing = false;
        t = Math.max(0, Math.min(OPEN_MS, ms));
    }

    public void hold(boolean frozen) {
        this.frozen = frozen;
    }

    private boolean frozen;

    public void advance(float dtMs) {
        if (!active || frozen) return;
        if (closing) {
            t -= dtMs * (OPEN_MS / CLOSE_MS);
            if (t <= 0) {
                t = 0;
                active = false;
                closing = false;
            }
        } else {
            t = Math.min(OPEN_MS, t + dtMs);
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

    public float ms() {
        return t;
    }

    public boolean clickable() {
        return active && !closing && t >= 250f;
    }

    // ---- curves ---------------------------------------------------------------------------------------------------

    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    static float easeOut(float v) {
        float u = 1 - clamp01(v);
        return 1 - u * u * u;
    }

    static float easeInOut(float v) {
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

    /** 0 at the wrist, 1 at rest in front of you. */
    public float leave(boolean reduced) {
        return reduced ? 1 : easeInOut((t - 150f) / 220f);
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

    /** The beam that carries the panel off the wrist: rises with the lift-off and fades as it settles. */
    public float beam(boolean reduced) {
        if (reduced) return 0;
        float up = clamp01((t - 120f) / 50f), down = 1 - clamp01((t - 290f) / 90f);
        return up * down;
    }

    /** How much the open panel holds attention (Lens focus): eased with the glass. */
    public float focus() {
        return active ? easeInOut((t - 160f) / 290f) : 0f;
    }
}
