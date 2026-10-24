package dev.awaken.terrain.hero;

import net.minecraft.util.RandomSource;

/**
 * The Elder Colossus (docs/modules/lands.md, "Elderwood"): a fluted pale trunk 6-9 across and 55-85 tall that swells
 * into buttress roots arching 8-14 blocks out like walls, four to six great limbs reaching 14-22 blocks and rising as
 * they go, each carrying billowing gold-green masses with hollow cores, and a broad crown on top. A landmark from far
 * terrain: its layered canopy stands well above the Elderwood's own.
 */
final class ElderColossus {
    private ElderColossus() {}

    static final int EXTENT = 44, MIN_Y = -3, MAX_Y = 106;

    static Blueprint build(long seed) {
        RandomSource r = RandomSource.create(seed);
        Blueprint bp = new Blueprint(EXTENT, MIN_Y, MAX_Y);
        int h = 55 + r.nextInt(31);
        double r0 = 3.25 + r.nextDouble() * 1.25;
        double swayAngle = r.nextDouble() * Math.PI * 2, swayBy = 1.0 + r.nextDouble() * 2.0;
        double flutePhase = r.nextDouble() * Math.PI * 2;
        int lobes = 5 + r.nextInt(3);
        Trunk trunk = new Trunk(h, swayAngle, swayBy);

        // the trunk: fluted, tapering, swelling into the roots near the ground
        for (int y = MIN_Y; y <= h; y++) {
            double t = Math.max(0, y) / (double) h;
            double rad = r0 * (1 - 0.45 * Math.pow(t, 1.3)) + (y < 10 ? Math.pow((10 - Math.max(0, y)) / 10.0, 2) * 2.6 : 0);
            double cx = trunk.x(y), cz = trunk.z(y);
            int span = (int) Math.ceil(rad * 1.2) + 1;
            for (int x = (int) Math.floor(cx) - span; x <= (int) Math.ceil(cx) + span; x++) {
                for (int z = (int) Math.floor(cz) - span; z <= (int) Math.ceil(cz) + span; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d2 = dx * dx + dz * dz;
                    if (d2 > rad * rad * (1 + 0.11) * (1 + 0.11)) continue; // outside even the widest flute: skip the trig
                    double a = Math.atan2(dz, dx);
                    double reff = rad * (1 + 0.11 * Math.sin(lobes * a + flutePhase + y * 0.045));
                    if (dx * dx + dz * dz > reff * reff) continue;
                    bp.set(x, y, z, Blueprint.LOG);
                    if (y == MIN_Y) bp.ground(x, y, z);
                }
            }
        }

        // buttress roots: fins leaving the trunk high up and arching down to the ground, walls of bark to the earth
        int roots = 5 + r.nextInt(3);
        double ra = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < roots; k++) {
            double a = ra + k * Math.PI * 2 / roots + (r.nextDouble() - 0.5) * 0.5;
            double bend = (r.nextDouble() - 0.5) * 0.6;
            double tip = r0 + 5 + r.nextInt(7);
            double top = 7 + r.nextInt(6);
            Roots.fin(bp, a, bend, r0 * 0.7, tip, top, 1.7, 0.6, seed + k);
        }

        // the great limbs, lowest first, round the trunk by golden-angle steps
        int limbs = 4 + r.nextInt(3);
        double a = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < limbs; k++) {
            a += 2.39996 + (r.nextDouble() - 0.5) * 0.5;
            int hy = (int) (h * (0.44 + 0.34 * k / (double) Math.max(1, limbs - 1))) + r.nextInt(3) - 1;
            double len = 16 + r.nextInt(9) - k;
            double elev = 0.18 + r.nextDouble() * 0.22;
            double rad = 2.1 - 0.15 * k;
            double x = trunk.x(hy), y = hy, z = trunk.z(hy), dir = a;
            double[] mid = null;
            int segs = 3;
            for (int s = 0; s < segs; s++) {
                double sl = len / segs;
                double e = elev + s * 0.09;
                dir += (r.nextDouble() - 0.5) * 0.3;
                double nx = x + Math.cos(dir) * Math.cos(e) * sl, ny = y + Math.sin(e) * sl, nz = z + Math.sin(dir) * Math.cos(e) * sl;
                double r1 = rad - (rad - 0.7) * (s + 1) / segs, r0s = rad - (rad - 0.7) * s / segs;
                bp.tube(x, y, z, nx, ny, nz, r0s, r1, Blueprint.WOOD);
                x = nx;
                y = ny;
                z = nz;
                if (s == 1) mid = new double[]{x, y, z, dir, e};
            }
            mass(bp, r, seed + 31L * k, x, y, z, 1.5, 7.0 + r.nextDouble() * 2.5, 3.2 + r.nextDouble());
            if (mid != null) mass(bp, r, seed + 31L * k + 3, mid[0], mid[1], mid[2], 2.0, 4.5 + r.nextDouble() * 1.5, 2.6);
            // side branches from the limb's middle, each with a mass of its own: the crown reads as tiers, not lollipops
            if (mid != null) {
                for (int side = -1; side <= 1; side += 2) {
                    double sd = mid[3] + side * (0.75 + r.nextDouble() * 0.5);
                    double sl = 6 + r.nextInt(5), se = mid[4] + 0.12;
                    double ex = mid[0] + Math.cos(sd) * Math.cos(se) * sl, ey = mid[1] + Math.sin(se) * sl, ez = mid[2] + Math.sin(sd) * Math.cos(se) * sl;
                    bp.tube(mid[0], mid[1], mid[2], ex, ey, ez, 1.0, 0.6, Blueprint.WOOD);
                    mass(bp, r, seed + 31L * k + 7 + side, ex, ey, ez, 1.0, 5.0 + r.nextDouble() * 1.5, 2.6 + r.nextDouble() * 0.6);
                }
            }
        }

        // the crown: the trunk's top splits into a few short leaders under one broad mass
        double tx = trunk.x(h), tz = trunk.z(h);
        int leaders = 3 + r.nextInt(2);
        double la = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < leaders; k++) {
            la += Math.PI * 2 / leaders + (r.nextDouble() - 0.5) * 0.6;
            double ll = 8 + r.nextInt(5);
            double ex = tx + Math.cos(la) * ll, ez = tz + Math.sin(la) * ll, ey = h + 3 + r.nextInt(4);
            bp.tube(tx, h - 6, tz, ex, ey, ez, 1.4, 0.7, Blueprint.WOOD);
            mass(bp, r, seed + 101L * k, ex, ey, ez, 1.0, 5.0 + r.nextDouble() * 1.5, 3.0);
        }
        bp.tube(tx, h - 2, tz, tx, h + 2, tz, 1.4, 1.0, Blueprint.WOOD);
        // a small crown right over the trunk: the sky must reach the trunk between the limbs, or the giant stands in its own shade
        mass(bp, r, seed + 977, tx, h + 3, tz, 1.0, 5.5 + r.nextDouble() * 1.5, 3.5 + r.nextDouble());
        return bp;
    }

    /**
     * A canopy mass {@code lift} above the branch end at (x, y, z), joined to it by a short stem: a billowing cumulus of
     * one big ellipsoid and two to four smaller ones round its shoulders, every one hollow, with twig spokes from the
     * centre into each (they hold the leaves within reach of wood and show through the gaps).
     */
    static void mass(Blueprint bp, RandomSource r, long seed, double bx, double by, double bz, double lift, double rx, double up) {
        double x = bx, y = by + lift, z = bz;
        bp.line((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz), (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), Blueprint.WOOD);
        int puffs = 2 + r.nextInt(3);
        double a = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < puffs; i++) {
            a += Math.PI * 2 / puffs + (r.nextDouble() - 0.5) * 0.8;
            double d = rx * (0.45 + r.nextDouble() * 0.25), pr = rx * (0.5 + r.nextDouble() * 0.2);
            double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d, py = y + (r.nextDouble() - 0.35) * up * 0.8;
            spoke(bp, x, y, z, px, py, pz);
            bp.cloud(seed + 17L * i, px, py, pz, pr, up * (0.65 + r.nextDouble() * 0.25), up * 0.5, 0.45, 0.4);
        }
        int spokes = 4 + r.nextInt(3);
        for (int i = 0; i < spokes; i++) {
            a += Math.PI * 2 / spokes + (r.nextDouble() - 0.5) * 0.4;
            double l = rx * (0.55 + r.nextDouble() * 0.15), e = (r.nextDouble() - 0.3) * 0.6;
            spoke(bp, x, y, z, x + Math.cos(a) * Math.cos(e) * l, y + Math.sin(e) * l * 0.6, z + Math.sin(a) * Math.cos(e) * l);
        }
        bp.cloud(seed, x, y, z, rx, up, up * 0.6, 0.5, 0.35);
    }

    private static void spoke(Blueprint bp, double ax, double ay, double az, double bx, double by, double bz) {
        bp.line((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az), (int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz),
                Blueprint.WOOD);
    }

    /** The trunk's centre line: a slow sway to one side as it rises. */
    record Trunk(int h, double angle, double by) {
        double x(double y) {
            return Math.cos(angle) * by * Math.sin(Math.PI * 0.5 * Math.max(0, y) / h);
        }

        double z(double y) {
            return Math.sin(angle) * by * Math.sin(Math.PI * 0.5 * Math.max(0, y) / h);
        }
    }
}
