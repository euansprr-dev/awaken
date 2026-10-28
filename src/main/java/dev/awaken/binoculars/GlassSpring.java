package dev.awaken.binoculars;

/**
 * The zoom's spring (SPEC §39.3), on the log of the magnification so 2× to 4× feels like 4× to 8×. Slightly underdamped:
 * a step overshoots by {@link GlassFeel#overshoot} of itself and settles (within 2%) in the time asked. Stepped per frame
 * in small fixed slices so it behaves the same at 30 and 240 fps. Pure, no allocation.
 */
public final class GlassSpring {
    private double x, v;

    public GlassSpring() {
        snap(1.0);
    }

    /** The magnification now. */
    public double mag() {
        return Math.exp(x);
    }

    public void snap(double mag) {
        x = Math.log(Math.max(1.0, mag));
        v = 0;
    }

    /** The damping ratio that overshoots by {@code overshoot} (0.02 is 2%); 1 (none) for zero or less. */
    public static double zeta(double overshoot) {
        if (overshoot <= 0) return 1.0;
        double l = Math.log(overshoot);
        return -l / Math.sqrt(Math.PI * Math.PI + l * l);
    }

    /** Moves toward {@code target} for {@code ms}, settling in about {@code settleMs}. */
    public void step(double ms, double target, double settleMs, double zeta) {
        double goal = Math.log(Math.max(1.0, target));
        double w = 4.0 / (zeta * Math.max(1.0, settleMs) / 1000.0);
        double left = Math.min(ms, 250.0) / 1000.0;
        while (left > 1e-9) {
            double dt = Math.min(left, 0.002);
            double a = -2.0 * zeta * w * v - w * w * (x - goal);
            v += a * dt;
            x += v * dt;
            left -= dt;
        }
    }

    /** Close enough to the target to call it there. */
    public boolean settled(double target) {
        return Math.abs(x - Math.log(Math.max(1.0, target))) < 0.004 && Math.abs(v) < 0.05;
    }
}
