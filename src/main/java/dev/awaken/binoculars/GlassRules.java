package dev.awaken.binoculars;

import net.minecraft.core.BlockPos;

/**
 * The binoculars' side-agnostic rules (SPEC §39): the view a magnification gives, the mouse speed that keeps aim the same
 * at every step, the sway, the readout's words and which centre points can be marked. Pure maths: GameTests check it and
 * the client follows it.
 */
public final class GlassRules {
    private GlassRules() {}

    /** The narrowest view any step and FOV setting can give, in degrees. */
    public static final double MIN_FOV = 1.0;

    /** The view through magnification {@code mag}: the tangent of the half-angle divided by it, never under 1°. */
    public static double fov(double base, double mag) {
        if (mag <= 1.0) return base;
        double half = Math.toRadians(base) * 0.5;
        return Math.max(MIN_FOV, Math.toDegrees(2.0 * Math.atan(Math.tan(half) / mag)));
    }

    /**
     * The mouse sensitivity that turns the view {@code k} times as fast. Vanilla turns by {@code (s*0.6+0.2)^3 * 8} a count,
     * so the cube root of {@code k} goes on the inner term; the result can be under zero, which vanilla's maths takes.
     */
    public static double sensitivity(double sens, double k) {
        double ss = sens * 0.6 + 0.2;
        return (ss * Math.cbrt(k) - 0.2) / 0.6;
    }

    /** Vanilla's turn per mouse count at a sensitivity (MouseHandler.turnPlayer, not scoping). */
    public static double turn(double sens) {
        double ss = sens * 0.6 + 0.2;
        return ss * ss * ss * 8.0;
    }

    /**
     * How much faster than normal the view turns at magnification {@code mag}: things cross the middle of the screen at the
     * same speed as unzoomed when {@code k = 1/mag} (the tangents scale exactly by it), times the player's slider.
     */
    public static double lookScale(double mag, double slider) {
        return Math.max(0.01, slider) / Math.max(1.0, mag);
    }

    /** The breathing sway's size in degrees: grows with the square root of the step, a third of it crouched. */
    public static double sway(double mag, double perStep, double crouchShare, boolean crouching) {
        if (mag <= 1.0) return 0;
        return perStep * Math.sqrt(mag) * (crouching ? crouchShare : 1.0);
    }

    /**
     * Steps for one scroll event: vanilla's wheel handler hands over whole notches it has added up (a trackpad's many small
     * scrolls make one); a fast flick still moves one step at a time. Up zooms in.
     */
    public static int notches(int accumulated) {
        return Integer.signum(accumulated);
    }

    // ---- the readout ------------------------------------------------------------------------------------------------

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** Compass words for a look yaw (Minecraft's: 0 faces south, 90 west, 180 north). */
    public static String bearing(float yRot) {
        double c = ((yRot + 180.0) % 360.0 + 360.0) % 360.0;
        return DIRS[(int) Math.round(c / 45.0) % 8];
    }

    /** "312 m", "5.1 km", or "far" when nothing answered (sky, unknown land). */
    public static String distance(double metres) {
        if (Double.isNaN(metres) || metres < 0) return "far";
        if (metres < 1000) return Math.round(metres) + " m";
        double km = Math.round(metres / 100.0) / 10.0;
        return km == Math.floor(km) && km >= 10 ? (int) km + " km" : km + " km";
    }

    /** "8× · 312 m · NE". */
    public static String readout(float step, double metres, float yRot) {
        return stepWords(step) + " · " + distance(metres) + " · " + bearing(yRot);
    }

    public static String stepWords(float step) {
        return (step == Math.floor(step) ? Integer.toString((int) step) : Float.toString(step)) + "×";
    }

    // ---- marking a spot ---------------------------------------------------------------------------------------------

    /** The name a spot marked through the binoculars gets on the Now line (the server trims names to 48). */
    public static final String SPOT_NAME = "A spot you saw through binoculars";

    /**
     * Whether a centre point can become the destination: something was hit (never the sky), inside the world's height
     * and border, and within the range the readout could tell.
     */
    public static boolean markable(boolean hit, BlockPos at, int minY, int maxY, double metres, double maxMetres, boolean insideBorder) {
        if (!hit || at == null || Double.isNaN(metres)) return false;
        if (at.getY() < minY || at.getY() > maxY || !insideBorder) return false;
        return metres <= maxMetres;
    }
}
