package dev.awaken.body.ascent;

/**
 * The held rope's chain, judged one link at a time (SPEC §62, "One press, one throw"). Held, the rope throws the next
 * link by itself once the last one ends. A link that ends with the key still held stops the chain until the key is
 * pressed again when it failed (blocked, stalled, cut or refused), or when it neither arrived (a zip at its lip, a sling
 * let go at its point, a let-go or a launch off a swing) nor carried the body {@link #PROGRESS} blocks (a swing that
 * landed where it began). An arrival under {@link #NOWHERE} blocks and {@link #NOWHERE_TICKS} ticks went nowhere too.
 * A press that missed stops it as well. Without this the chain threw again two ticks after every failed link, at the
 * same top or the next one along, so one press showed three or four ropes before one held (Euan 11 Nov 2026). Pure:
 * the client's moves call it, and the GameTest drives it directly.
 */
public final class RopeChain {
    /** A held link that didn't arrive is progress from this far on (blocks). */
    public static final double PROGRESS = 3.0;
    /** Under this far (blocks) and this soon (ticks) even an arrival went nowhere. */
    public static final double NOWHERE = 1.0;
    public static final int NOWHERE_TICKS = 10;

    private boolean open, stopped;
    private int at, throwsThisPress;
    private double fromX, fromY, fromZ;
    private String why = "";

    /** The key pressed: the chain may throw again. */
    public void press() {
        stopped = false;
        throwsThisPress = 0;
        why = "";
    }

    /** A rope thrown (by the press or by the chain): a link opens here. */
    public void thrown(int tick, double x, double y, double z) {
        open = true;
        at = tick;
        fromX = x;
        fromY = y;
        fromZ = z;
        throwsThisPress++;
    }

    /** The press's rope flew out at nothing: one miss, and the held key throws nothing more. */
    public void missed() {
        open = false;
        stop("the press missed");
    }

    /**
     * The open link ended (the rope left). {@code held}: the key is still down; {@code arrived}: it got where it was
     * thrown; {@code failed}: why it failed, or null. True when this stops the chain.
     */
    public boolean ended(int tick, double x, double y, double z, boolean held, boolean arrived, @org.jspecify.annotations.Nullable String failed) {
        if (!open) return false;
        open = false;
        if (!held || stopped) return false;
        if (failed != null) {
            stop(failed);
            return true;
        }
        double dx = x - fromX, dy = y - fromY, dz = z - fromZ, d2 = dx * dx + dy * dy + dz * dz;
        boolean progress = arrived ? tick - at >= NOWHERE_TICKS || d2 >= NOWHERE * NOWHERE : d2 >= PROGRESS * PROGRESS;
        if (progress) return false;
        stop("the link went nowhere");
        return true;
    }

    private void stop(String reason) {
        stopped = true;
        why = reason;
    }

    /** True while the held key may not throw: the last link failed or the press missed. */
    public boolean stopped() {
        return stopped;
    }

    /** Why the chain stopped ("" when it hasn't). */
    public String why() {
        return why;
    }

    /** Ropes thrown since the last press. */
    public int throwsThisPress() {
        return throwsThisPress;
    }

    public void reset() {
        open = stopped = false;
        throwsThisPress = 0;
        why = "";
    }
}
