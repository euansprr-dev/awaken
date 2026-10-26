package dev.awaken.body;

/**
 * The action camera's arm (SPEC §17.13): how the over-the-shoulder camera stands for each thing the body is doing, as
 * changes to the shoulder view's own offset, and the spring that carries it there. Pure numbers, no client classes,
 * so a GameTest can hold it to its shape. Close on the ground and in the city; it pulls back and trails as speed grows,
 * moves to the open side on a wall-run, rises over a hang, drops low on a slide, swings wide on the rope and wider
 * still for wings and flight, where it also comes round behind you.
 */
public final class ActionRig {
    private ActionRig() {}

    /** What the body is doing, as the camera sees it. */
    public enum Pose { GROUND, SLIDE, WALL_RUN, HANG, ROPE, ZIP, CARRY, GLIDE, FLIGHT, AIM, SOVEREIGN, COMBAT }

    /** The arm's channels: back (further behind), up, open (0..1: over to the side away from a wall), centre (0..1: in behind you). */
    public static final int BACK = 0, UP = 1, OPEN = 2, CENTRE = 3, N = 4;

    /** The furthest it ever pulls back, in blocks over the shoulder view's own distance. */
    public static final double MAX_BACK = 5.5;
    /** Speed (blocks a tick) under which it doesn't trail; the trail per block a tick over it; the most the trail adds. */
    public static final double TRAIL_FROM = 0.22, TRAIL = 2.0, TRAIL_MAX = 3.0;
    /** The spring's stiffness going out and coming back (critically damped: it never overshoots). */
    public static final double OUT = 0.22, IN = 0.32;

    /** The arm's shape for a pose at a speed, into {@code out[0..N)}. */
    public static void target(Pose pose, double speed, double[] out) {
        double trail = Math.min(TRAIL_MAX, Math.max(0, speed - TRAIL_FROM) * TRAIL);
        double back = 0, up = 0, open = 0, centre = 0;
        switch (pose) {
            case GROUND -> back = trail * 0.6;
            case SLIDE -> {
                back = trail * 0.5 - 0.2;
                up = -0.55;
            }
            case WALL_RUN -> {
                back = 0.6 + trail * 0.6;
                up = 0.2;
                open = 1;
            }
            case HANG -> {
                back = 0.5;
                up = 0.45;
            }
            case ROPE -> {
                back = 2.2 + trail;
                up = 0.8;
                centre = 0.6;
            }
            case ZIP -> {
                back = 1.2 + trail * 0.5;
                up = 0.3;
                centre = 0.3;
            }
            case CARRY -> {
                back = 1.5 + trail;
                up = 0.4;
                centre = 0.4;
            }
            case GLIDE -> {
                back = 2.6 + trail;
                up = 0.6;
                centre = 0.7;
            }
            case FLIGHT -> {
                back = 3.0 + trail;
                up = 0.5;
                centre = 0.8;
            }
            case SOVEREIGN -> {
                // the god form: pulled far back and up, the whole figure and the field round it in view
                back = 4.5 + trail * 0.5;
                up = 1.4;
                centre = 0.9;
            }
            case AIM -> {
                // casting or drawing a bow: the shoulder's own aim view, nothing added
            }
            case COMBAT -> {
                // in a fight (Arms, SPEC §25): a little closer and lower, the foe big in the frame over the shoulder
                back = -0.45;
                up = -0.18;
            }
        }
        out[BACK] = Math.min(MAX_BACK, back);
        out[UP] = up;
        out[OPEN] = open;
        out[CENTRE] = centre;
    }

    /** The arm in motion: where it is and how fast it's moving, per channel. Stepped once a tick. */
    public static final class Arm {
        public final double[] at = new double[N], was = new double[N];
        private final double[] v = new double[N];

        /** One tick toward {@code target}: out slowly (the camera trails), back a little quicker. */
        public void step(double[] target) {
            for (int i = 0; i < N; i++) {
                was[i] = at[i];
                double w = Math.abs(target[i]) > Math.abs(at[i]) ? OUT : IN;
                double a = w * w * (target[i] - at[i]) - 2 * w * v[i];
                v[i] += a;
                at[i] += v[i];
            }
        }

        /** A blow to one channel (a landing drops the camera; the spring brings it back). */
        public void kick(int channel, double dv) {
            v[channel] += dv;
        }

        /** Between the last two ticks. */
        public double get(int channel, float partial) {
            return was[channel] + (at[channel] - was[channel]) * partial;
        }

        public void reset() {
            for (int i = 0; i < N; i++) at[i] = was[i] = v[i] = 0;
        }
    }

    /**
     * The shoulder view's offset (x left, y up, z back, Shoulder Surfing's convention) shaped by the arm: over to the
     * open side (the wall's on {@code wallRight}: true, the right), in behind you, back and up. Into {@code out[0..3)}.
     */
    public static void shape(double x, double y, double z, double back, double up, double open, double centre, boolean wallRight,
                             double[] out) {
        // the open side: away from the wall, a little further out than the shoulder's own
        double openX = (wallRight ? 1 : -1) * Math.max(0.75, Math.abs(x)) * 1.2;
        double sx = x + (openX - x) * open;
        out[0] = sx * (1 - centre);
        out[1] = y + up;
        out[2] = z + back;
    }
}
