package dev.awaken.arms;

/**
 * The draw (SPEC §25 "Tap or hold"; Euan 2026-11-09: "the moment you start clicking long... it brings the weapon into its
 * charge form", then "it just sometimes thinks you're going into a heavy hold when you're not"). A press says nothing until
 * it has outlasted a click, so until then nothing on screen knows about it: the hands go on with what they were doing. A
 * tap launches its strike from there. A press still down after {@link #GRACE} is a hold: the hands leave for the heavy's
 * drawn pose on one slow curve and the strike the press began never shows. Plain maths, so a GameTest can hold it to its
 * rules; the hands in {@code ArmsHands} use exactly this.
 */
public final class ChargeDraw {
    private ChargeDraw() {}

    /** Ticks a press stays down before it is taken for a hold: longer than a click, even a lazy one. */
    public static final float GRACE = 4F;
    /** How much faster than its clip a tapped strike runs its wind-up (what the press held for, given back). */
    public static final float TAP_RATE = 2F;
    /** The shortest launch from a press barely drawn, in ticks, and what a fully drawn press adds to it. */
    private static final float LAUNCH_MIN = 1F, LAUNCH_DRAWN = 1F;
    /** A source no running move can have: the draw and the charge held after it are one source, so the mark changes nothing. */
    private static final long DRAWN_SOURCE = Long.MIN_VALUE;

    /**
     * Whether the hands may leave for the charge form: the press's own strike is out of sight under the draw
     * ({@code waiting}), or nothing is running, or what is running has landed its blow ({@code t} past {@code activeEnd}).
     */
    public static boolean handsFree(boolean waiting, boolean running, float t, int activeEnd) {
        return waiting || !running || t >= activeEnd;
    }

    /** Whether the hands are on their way to the charge form: a press that has outlasted a click, may charge, and hands free for it. */
    public static boolean drawing(boolean pressHeld, boolean holdOpen, boolean plunging, boolean handsFree, float heldFor) {
        return pressHeld && holdOpen && !plunging && handsFree && heldFor >= GRACE;
    }

    /**
     * Ticks from wherever the hands are to the charge form, once the grace is over. Slower for a weapon whose charge is
     * longer, and always there before its glint (a heavy weapon comes back heavily; six ticks at every weight read as a snap).
     */
    public static float ticks(int glint) {
        return glint * 0.6F;
    }

    /** What identifies the hands' source: one value for the whole draw and charge, else the running move's start. */
    public static long source(boolean drawn, long moveStart) {
        return drawn ? DRAWN_SOURCE : moveStart;
    }

    /** How far along a draw of {@code ticks} the hands are {@code elapsed} ticks in (0..1), as the blend carries them. */
    public static float drawn(float elapsed, float ticks) {
        float u = Math.clamp(elapsed / ticks, 0F, 1F);
        return u * u * u * (u * (u * 6F - 15F) + 10F);
    }

    /** Ticks from a tap to its strike's hit: its wind-up at {@link #TAP_RATE}, longer the further the hands have to come back. */
    public static float launchTicks(float windup, float drawn) {
        return Math.max(windup / TAP_RATE, LAUNCH_MIN + LAUNCH_DRAWN * drawn);
    }

    /** Ticks a release gives the hands to reach the loosed heavy: one from a full draw, up to three from one barely begun. */
    public static float releaseTicks(float drawn) {
        return 1F + 2F * (1F - drawn);
    }

    /** The clock's speed from a tap to its hit (below 1 for a one-tick strike loosed from far into the draw). */
    public static float launchRate(float windup, float drawn) {
        return windup / launchTicks(windup, drawn);
    }

    /**
     * A tapped strike's clock {@code t} ticks after the tap: at {@code rate} to its hit, then on at the normal speed, with
     * no step where the two meet.
     */
    public static float clock(float t, float hit, float rate) {
        float run = t * rate;
        return run < hit ? run : t + hit * (1F - 1F / rate);
    }
}
