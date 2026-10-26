package dev.awaken.atlas;

/**
 * Where the map scene's camera stands (SPEC §30.5): up and back over one shoulder, looking the way you face and 38°
 * down, so the ground (never the sky) is behind the map and you stand in the corner of the frame. Pure math, shared by
 * the client's camera and the GameTests.
 */
public final class MapShot {
    private MapShot() {}

    /** Metres behind the feet, to the side, and above them; the pitch down. */
    public static final double BACK = 2.4, SIDE = 2.2, UP = 4.6;
    public static final float PITCH = 38f;
    /** The higher shot, for when neither shoulder has room: closer in, steeper. */
    public static final double HIGH_BACK = 1.4, HIGH_UP = 6.0;
    public static final float HIGH_PITCH = 50f;

    /** Timings in milliseconds (SPEC §30.5): the camera's move, after the wrist's rise; again within 12 s, quicker. */
    public static final float RISE_MS = 120f, DELAY_MS = 60f, MOVE_MS = 320f, QUICK_DELAY_MS = 30f, QUICK_MOVE_MS = 190f,
            CLOSE_MS = 250f, FOLD_MS = 150f;
    public static final long QUICK_WITHIN_MS = 12_000L;

    /**
     * The shot for a player standing at (x, y, z) (the feet) facing {@code yaw}: {@code side} +1 over the right
     * shoulder, -1 the left; {@code high} the higher shot. out: x, y, z of the eyes, yaw, pitch.
     */
    public static void pose(double x, double y, double z, float yaw, int side, boolean high, double[] out) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r);  // forward (yaw 0 faces south)
        double rx = -Math.cos(r), rz = -Math.sin(r); // the player's right
        double back = high ? HIGH_BACK : BACK, up = high ? HIGH_UP : UP;
        out[0] = x - fx * back + rx * SIDE * side;
        out[1] = y + up;
        out[2] = z - fz * back + rz * SIDE * side;
        out[3] = yaw;
        out[4] = high ? HIGH_PITCH : PITCH;
    }

    /** Smooth ease in and out (cubic), clamped to 0..1. */
    public static double ease(double t) {
        t = Math.clamp(t, 0.0, 1.0);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Blends pose {@code a} into {@code b} by {@code t} (yaw the short way round); out may be either. */
    public static void blend(double[] a, double[] b, double t, double[] out) {
        double dyaw = ((b[3] - a[3]) % 360 + 540) % 360 - 180;
        double yaw = a[3] + dyaw * t;
        for (int i = 0; i < 3; i++) out[i] = a[i] + (b[i] - a[i]) * t;
        out[4] = a[4] + (b[4] - a[4]) * t;
        out[3] = yaw;
    }
}
