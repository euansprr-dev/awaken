package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Piece;

import java.util.ArrayList;
import java.util.List;

/** The checks a plan has to pass before it can be drawn at all (SPEC §50.6): pieces don't stand in each other or in the way. */
public final class LookChecks {
    private LookChecks() {}

    private static double dist(double[] w, double x, double z) {
        double ax = w[0], az = w[1], dx = w[2] - ax, dz = w[3] - az, t = Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)));
        return Math.hypot(x - ax - dx * t, z - az - dz * t);
    }

    /** Why this piece can't stand here, or null: through another piece, or on a road, a stair or a door's way. */
    public static String clash(Piece<?> a, List<? extends Piece<?>> others, List<double[]> ways) {
        if (a.mayOverlap) return null;
        for (Piece<?> b : others) {
            if (b != a && !b.mayOverlap && overlap(a, b)) return a.label + " at " + a.x0 + "," + a.z0 + " goes through " + b.label + " at " + b.x0 + "," + b.z0;
        }
        if (a.startsWay) return null;
        double cx = (a.x0 + a.x1) / 2.0, cz = (a.z0 + a.z1) / 2.0, r = Math.max(a.x1 - a.x0, a.z1 - a.z0) / 2.0;
        for (double[] w : ways) {
            if (dist(w, cx, cz) < w[4] + r * 0.7) return a.label + " at " + a.x0 + "," + a.z0 + " stands in a way";
        }
        return null;
    }

    /** What's wrong with a finished plan, one line each. */
    public static List<String> problems(Land land) {
        List<String> out = new ArrayList<>();
        List<? extends Piece<?>> ps = land.pieces();
        for (int i = 0; i < ps.size(); i++) {
            String why = clash(ps.get(i), ps.subList(i + 1, ps.size()), land.ways());
            if (why != null) out.add(why);
        }
        return out;
    }

    private static boolean overlap(Piece<?> a, Piece<?> b) {
        return a.x1 >= b.x0 && a.x0 <= b.x1 && a.z1 >= b.z0 && a.z0 <= b.z1;
    }
}
