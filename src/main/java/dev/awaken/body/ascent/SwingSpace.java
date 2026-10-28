package dev.awaken.body.ascent;

import java.util.ArrayList;
import java.util.List;

/**
 * What the assisted swing asks of the world (SPEC §17.31): rays, whether a body fits, the floor under a point and the
 * top of the column a face belongs to. {@link LevelSpace} answers from a real level; {@link Boxes} from a list of boxes,
 * which is how the swing bench (GameTest {@code ascent_swing_bench}) plays its six places without building them.
 */
public interface SwingSpace {
    /** The body's half width and height (a player's box). */
    double HALF = 0.3, TALL = 1.8;

    /** A ray's hit, reused: the point a hair off the face, the face's normal, the distance, and whether it was foliage. */
    final class Hit {
        public double x, y, z, t;
        public int nx, ny, nz;
        public boolean leaf;
        /** The block cell hit (a level), or the box's index (boxes). */
        public int bx, by, bz;
    }

    /** The first face along a unit direction within {@code range}; false when nothing is there. Leaves count. */
    boolean ray(double ox, double oy, double oz, double dx, double dy, double dz, double range, Hit out);

    /** The top of the solid column whose side {@code h} hit, when it is at most {@code maxUp} above the hit; else NaN. */
    double topOver(Hit h, double maxUp);

    /** Whether anything solid is inside the box. */
    boolean boxHits(double x0, double y0, double z0, double x1, double y1, double z1);

    /** The floor under (x, z) at or below {@code from}. */
    double floorUnder(double x, double from, double z);

    /** Whether a player's box with its feet at (x, y, z) meets anything. */
    default boolean bodyHits(double x, double y, double z) {
        return boxHits(x - HALF, y + 0.001, z - HALF, x + HALF, y + TALL, z + HALF);
    }

    /** A world of boxes over a ground at y 0: the bench's places. Plain maths, no Minecraft. */
    final class Boxes implements SwingSpace {
        /** min x y z, max x y z per box. */
        private final List<double[]> boxes = new ArrayList<>();
        private final List<Boolean> leaves = new ArrayList<>();

        public Boxes add(double x0, double y0, double z0, double x1, double y1, double z1, boolean leaf) {
            boxes.add(new double[] {x0, y0, z0, x1, y1, z1});
            leaves.add(leaf);
            return this;
        }

        public int size() {
            return boxes.size();
        }

        public double[] box(int i) {
            return boxes.get(i);
        }

        public boolean leaf(int i) {
            return leaves.get(i);
        }

        @Override
        public boolean ray(double ox, double oy, double oz, double dx, double dy, double dz, double range, Hit out) {
            double best = range;
            int axis = -2, sign = 0, which = -1;
            if (dy < 0) {
                double t = -oy / dy;
                if (t > 0 && t < best) {
                    best = t;
                    axis = 1;
                    sign = 1;
                    which = -1;
                }
            }
            double[] o = {ox, oy, oz}, d = {dx, dy, dz};
            for (int bi = 0; bi < boxes.size(); bi++) {
                double[] b = boxes.get(bi);
                double t0 = 0, t1 = best;
                int ax = -1, sg = 0;
                boolean ok = true;
                for (int i = 0; i < 3; i++) {
                    double lo = b[i], hi = b[i + 3];
                    if (Math.abs(d[i]) < 1e-12) {
                        if (o[i] < lo || o[i] > hi) {
                            ok = false;
                            break;
                        }
                        continue;
                    }
                    double ta = (lo - o[i]) / d[i], tb = (hi - o[i]) / d[i];
                    int s = -1;
                    if (ta > tb) {
                        double q = ta;
                        ta = tb;
                        tb = q;
                        s = 1;
                    }
                    if (ta > t0) {
                        t0 = ta;
                        ax = i;
                        sg = s;
                    }
                    if (tb < t1) t1 = tb;
                    if (t0 > t1) {
                        ok = false;
                        break;
                    }
                }
                if (ok && ax >= 0 && t0 < best && t0 > 1e-6) {
                    best = t0;
                    axis = ax;
                    sign = sg;
                    which = bi;
                }
            }
            if (axis == -2) return false;
            out.nx = axis == 0 ? sign : 0;
            out.ny = axis == 1 ? sign : 0;
            out.nz = axis == 2 ? sign : 0;
            out.t = best;
            out.x = ox + dx * best + out.nx * 0.04;
            out.y = oy + dy * best + out.ny * 0.04;
            out.z = oz + dz * best + out.nz * 0.04;
            out.leaf = which >= 0 && leaves.get(which);
            out.bx = which;
            out.by = out.bz = 0;
            return true;
        }

        @Override
        public double topOver(Hit h, double maxUp) {
            if (h.bx < 0 || h.leaf) return Double.NaN;
            double top = boxes.get(h.bx)[4];
            return top - h.y <= maxUp ? top : Double.NaN;
        }

        @Override
        public boolean boxHits(double x0, double y0, double z0, double x1, double y1, double z1) {
            if (y0 < 0) return true;
            for (double[] b : boxes) {
                if (x1 > b[0] && x0 < b[3] && y1 > b[1] && y0 < b[4] && z1 > b[2] && z0 < b[5]) return true;
            }
            return false;
        }

        @Override
        public double floorUnder(double x, double from, double z) {
            double f = 0;
            for (double[] b : boxes) {
                if (x >= b[0] - HALF && x <= b[3] + HALF && z >= b[2] - HALF && z <= b[5] + HALF && b[4] <= from + 1e-6) f = Math.max(f, b[4]);
            }
            return f;
        }
    }
}
