package dev.awaken.arms;

import java.util.Locale;

/**
 * Development only: the server's side of every blow, kept so a hit the hands showed and the server never confirmed (a
 * ghost: SPEC §25 "Weight in the Hand", stage 0) can be given its cause. Euan's logs of 4 to 9 Nov counted 99 of 334
 * shown hits as ghosts and could not say why: the server never told anyone what it did with a press or a body. In single
 * player the server runs in the client's JVM (as {@link FilmLog}), so the server writes here and the client reads when a
 * blow it played goes unconfirmed. Off (a production run, a dedicated server), nothing is kept. Fixed arrays; the game's
 * rules never read it.
 */
public final class HitWhy {
    private HitWhy() {}

    /** Set by the client in a development run. */
    public static volatile boolean on;

    /** How far past a move's reach the server looks for bodies to explain (blocks). */
    public static final double LOOK = 4.0;
    /** A blow landed on the same body under another press this near in time is the same blow, filed differently (ns). */
    static final long SAME_BLOW = 1_500_000_000L;

    // what the server did with a body in one move (the higher, the nearer it came to landing)
    public static final byte REACH = 1, ARC = 2, HEIGHT = 3, WALL = 4, NO_DAMAGE = 5, LANDED = 6;
    /** A blow landed by the hands' word past the sweep's own reach ({@code ArmsServer.claimed}): how many, and how far past in all. */
    private static int granted;
    private static float grantedBeyond;

    /** The server landed a blow by the hands' word, {@code beyond} blocks past its own reach (0 or less: inside it, the arc or the timing was the miss). */
    public static synchronized void granted(float beyond) {
        if (!on) return;
        granted++;
        grantedBeyond += Math.max(0F, beyond);
    }

    /** "N landed by the hands' word (x beyond reach on average)", or nothing when none were. */
    public static synchronized String grants() {
        return granted == 0 ? "" : String.format(Locale.ROOT, "%d landed by the hands' word (%.2f past the server's reach on average)", granted, grantedBeyond / granted);
    }
    // what it did with a press
    public static final byte NOTHING = 10, TOO_EARLY = 11, NOT_GRANTED = 12, QUEUED = 13, RAN = 14;

    /** Why a shown hit did no damage. */
    public enum Cause {
        OUT_OF_REACH("out of reach"), OUT_OF_ARC("outside the arc"), OUT_OF_HEIGHT("above or below"), NO_DAMAGE("struck, no damage"),
        OUT_OF_SIGHT("behind a wall"), OTHER_PRESS("landed under another press"), NOT_NEAR("nowhere near on the server"), NEVER_RAN("the press ran no move"),
        UNMATCHED("landed, word not matched"), NO_RECORD("no server record");

        public final String label;

        Cause(String label) {
            this.label = label;
        }
    }

    /** A ghost's cause, and for one out of reach how far beyond the reach the body was on the server (else 0). */
    public record Why(Cause cause, float beyond) {}

    private static final int SIZE = 512;
    private static final byte[] KIND = new byte[SIZE];
    private static final int[] SEQ = new int[SIZE], BODY = new int[SIZE];
    private static final long[] TICK = new long[SIZE], NANOS = new long[SIZE];
    private static final float[] A = new float[SIZE], B = new float[SIZE];
    private static int next;
    private static long kept;

    /**
     * The server looked at a body in a move's sweep. A miss by reach carries the gap and the reach, by the arc the degrees
     * outside it and the arc, by height the box's foot against the eye; a struck one whether damage landed, with the
     * damage cooldown vanilla saw. One record a (press, body): the nearest it came to landing.
     */
    public static synchronized void body(int seq, int body, long tick, byte kind, float a, float b) {
        if (!on) return;
        int i = find(seq, body);
        if (i >= 0 && !nearer(kind, a, b, KIND[i], A[i], B[i])) return;
        put(i, kind, seq, body, tick, a, b);
    }

    /** What the server did with a press: counted for nothing, too early, a move it couldn't grant, queued, or run. */
    public static synchronized void press(int seq, long tick, byte kind) {
        if (!on) return;
        int i = find(seq, -1);
        if (i >= 0 && KIND[i] >= kind) return; // RAN outlasts QUEUED
        put(i, kind, seq, -1, tick, 0F, 0F);
    }

    /** Whether a new record of a body came nearer to landing than the one kept. */
    static boolean nearer(byte kind, float a, float b, byte old, float oldA, float oldB) {
        if (kind != old) return kind > old;
        return (kind == REACH || kind == ARC) && shortfall(kind, a, b) < shortfall(old, oldA, oldB);
    }

    private static float shortfall(byte kind, float a, float b) {
        return kind == REACH ? a - b : a;
    }

    private static int find(int seq, int body) {
        for (int i = 0; i < SIZE; i++) if (KIND[i] != 0 && SEQ[i] == seq && BODY[i] == body) return i;
        return -1;
    }

    private static void put(int at, byte kind, int seq, int body, long tick, float a, float b) {
        int i = at;
        if (i < 0) {
            i = next;
            next = (next + 1) % SIZE;
        }
        KIND[i] = kind;
        SEQ[i] = seq;
        BODY[i] = body;
        TICK[i] = tick;
        NANOS[i] = System.nanoTime();
        A[i] = a;
        B[i] = b;
        kept++;
    }

    /**
     * Why the blow the hands showed under {@code seq} on {@code body} at {@code shownNanos} was never confirmed, with a
     * sentence of the server's numbers appended to {@code say}.
     */
    public static synchronized Why explain(int seq, int body, long shownNanos, StringBuilder say) {
        int own = find(seq, body);
        if (own >= 0 && KIND[own] == LANDED) {
            say.append(String.format(Locale.ROOT, "the server landed it at tick %d; its word never matched", TICK[own]));
            return new Why(Cause.UNMATCHED, 0F);
        }
        int other = -1;
        for (int i = 0; i < SIZE; i++) {
            if (KIND[i] != LANDED || BODY[i] != body || SEQ[i] == seq || Math.abs(NANOS[i] - shownNanos) > SAME_BLOW) continue;
            if (other < 0 || Math.abs(NANOS[i] - shownNanos) < Math.abs(NANOS[other] - shownNanos)) other = i;
        }
        if (other >= 0) {
            say.append(String.format(Locale.ROOT, "the server landed a blow on it under press %d, %+d ms from when it was shown", SEQ[other],
                    (NANOS[other] - shownNanos) / 1_000_000L));
            return new Why(Cause.OTHER_PRESS, 0F);
        }
        if (own >= 0) {
            long ms = (NANOS[own] - shownNanos) / 1_000_000L;
            switch (KIND[own]) {
                case NO_DAMAGE -> {
                    say.append(String.format(Locale.ROOT, "the server struck it %+d ms from the show and no damage landed (damage cooldown %.0f)", ms, A[own]));
                    return new Why(Cause.NO_DAMAGE, 0F);
                }
                case REACH -> {
                    say.append(String.format(Locale.ROOT, "on the server it was %.2f beyond reach (gap %.2f, reach %.2f) at its nearest, %+d ms from the show",
                            A[own] - B[own], A[own], B[own], ms));
                    return new Why(Cause.OUT_OF_REACH, A[own] - B[own]);
                }
                case WALL -> {
                    say.append(String.format(Locale.ROOT, "on the server a block stood between the eye and it, %+d ms from the show", ms));
                    return new Why(Cause.OUT_OF_SIGHT, 0F);
                }
                case ARC -> {
                    say.append(String.format(Locale.ROOT, "on the server it was %.0f degrees outside the arc of %.0f, %+d ms from the show", A[own], B[own], ms));
                    return new Why(Cause.OUT_OF_ARC, 0F);
                }
                default -> {
                    say.append(String.format(Locale.ROOT, "on the server it stood out of the sweep's height (its foot %.2f from the eye), %+d ms from the show", A[own], ms));
                    return new Why(Cause.OUT_OF_HEIGHT, 0F);
                }
            }
        }
        int press = find(seq, -1);
        if (press >= 0 && KIND[press] == RAN) {
            say.append(String.format(Locale.ROOT, "the server ran the move and the body was not within %.0f blocks past its reach, or already gone", LOOK));
            return new Why(Cause.NOT_NEAR, 0F);
        }
        if (press >= 0) {
            say.append(switch (KIND[press]) {
                case NOTHING -> "the hands' own word was that this press counted for nothing";
                case TOO_EARLY -> "the server took the press as too early to count";
                case NOT_GRANTED -> "the server could not grant the move the hands began";
                default -> "the server queued the strike and never ran it";
            });
            return new Why(Cause.NEVER_RAN, 0F);
        }
        if (kept > 0) {
            say.append("the server has nothing under this press");
            return new Why(Cause.NEVER_RAN, 0F);
        }
        say.append("the server is not in this game's process, or kept nothing");
        return new Why(Cause.NO_RECORD, 0F);
    }

    /** Forgets everything (a world left; a test). */
    public static synchronized void clear() {
        java.util.Arrays.fill(KIND, (byte) 0);
        next = 0;
        kept = 0;
        granted = 0;
        grantedBeyond = 0;
    }
}
