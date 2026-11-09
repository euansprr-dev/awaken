package dev.awaken.gates.realms.kit;

import java.util.List;

/** A road through its bends: how far a point is from it, and the ways nothing may be built on. */
public final class Road {
    private final double[][] p;

    public Road(double[][] points) {
        p = points;
    }

    public double d(double u, double v) {
        double best = 1e9;
        for (int i = 0; i + 1 < p.length; i++) {
            double ax = p[i][0], az = p[i][1], bx = p[i + 1][0], bz = p[i + 1][1];
            double dx = bx - ax, dz = bz - az, t = Math.max(0, Math.min(1, ((u - ax) * dx + (v - az) * dz) / (dx * dx + dz * dz)));
            best = Math.min(best, Math.hypot(u - ax - dx * t, v - az - dz * t));
        }
        return best;
    }

    public void addWays(List<double[]> ways, double halfWidth) {
        for (int i = 0; i + 1 < p.length; i++) ways.add(new double[]{p[i][0], p[i][1], p[i + 1][0], p[i + 1][1], halfWidth});
    }
}
