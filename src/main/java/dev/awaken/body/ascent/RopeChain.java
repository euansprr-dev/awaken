package dev.awaken.body.ascent;

/**
 * The held rope's chain, judged one link at a time (SPEC §62, "The held chain, a link at a time"). Held, the rope
 * carries you on: when a link ends it picks the next anchor once and throws once. What it never does is throw again at
 * what it couldn't take:
 * <ul>
 * <li>a link that ends with the key held and went nowhere stops the chain until the next press. It went nowhere when
 * it failed (blocked, stalled, cut or refused) without carrying the body {@link #PROGRESS} blocks; when it neither
 * arrived nor carried the body that far (a swing that landed where it began); or when it arrived under
 * {@link #NOWHERE} blocks off in under {@link #NOWHERE_TICKS} ticks;</li>
 * <li>a link that failed after carrying the body on lets the chain go on, but its anchor is never thrown at again
 * during that press ({@link #spent});</li>
 * <li>a press that missed stops the chain until the next press: one press at one thing throws one rope, never a
 * second half a second later at something else (Euan 12 Nov 2026: "it quickly sends two different ropes ... without
 * me letting go ... just me trying to go onto one block"; the 11 Nov pause of 10 ticks did just that).</li>
 * </ul>
 * Without this the chain threw again two ticks after every failed link, at the same top or the next one along, so one
 * press showed three or four ropes before one held (Euan 11 Nov 2026). Pure: the client's moves call it, and the
 * GameTest drives it directly.
 */
public final class RopeChain {
    /** A held link carried the body on from this far (blocks). */
    public static final double PROGRESS = 3.0;
    /** Under this far (blocks) and this soon (ticks) even an arrival went nowhere. */
    public static final double NOWHERE = 1.0;
    public static final int NOWHERE_TICKS = 10;
    /** No anchor (a link whose rope held no block). */
    public static final long NO_ANCHOR = Long.MIN_VALUE;

    private boolean open, stopped;
    private int at, throwsThisPress;
    private double fromX, fromY, fromZ;
    private long spent = NO_ANCHOR;
    private String why = "";

    /** The key pressed: the chain starts again (nothing spent, no pause). */
    public void press() {
        stopped = false;
        spent = NO_ANCHOR;
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

    /** The press's rope flew out at nothing: the held key throws nothing more until the next press. */
    public void missed(int tick) {
        open = false;
        stop("the press missed");
    }

    /**
     * The open link ended (the rope left). {@code held}: the key is still down; {@code arrived}: it got where it was
     * thrown; {@code failed}: why it failed, or null; {@code anchor}: its block ({@link #NO_ANCHOR} when none). True when
     * this stops the chain.
     */
    public boolean ended(int tick, double x, double y, double z, boolean held, boolean arrived,
            @org.jspecify.annotations.Nullable String failed, long anchor) {
        if (!open) return false;
        open = false;
        if (!held || stopped) return false;
        double dx = x - fromX, dy = y - fromY, dz = z - fromZ, d2 = dx * dx + dy * dy + dz * dz;
        boolean far = d2 >= PROGRESS * PROGRESS;
        if (failed != null) {
            if (!far) return stop(failed);
            // it failed, but on: the chain goes on, never again to this anchor
            spent = anchor;
            return false;
        }
        boolean progress = arrived ? tick - at >= NOWHERE_TICKS || d2 >= NOWHERE * NOWHERE : far;
        return !progress && stop("the link went nowhere");
    }

    private boolean stop(String reason) {
        stopped = true;
        why = reason;
        return true;
    }

    /** The held key may not throw: the last link went nowhere (until the next press). */
    public boolean stopped() {
        return stopped;
    }

    /** This press's chain threw at this anchor and failed there: it isn't thrown at again until the next press. */
    public boolean spent(long anchor) {
        return anchor != NO_ANCHOR && anchor == spent;
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
        open = false;
        press();
    }
}
