package dev.awaken.terrain.hero;

import net.minecraft.util.RandomSource;

/**
 * The Elder Colossus (docs/modules/lands.md, "Elderwood"): a fluted pale trunk 6-9 across that swells to twice that at
 * the ground and runs out into buttress roots arching 8-17 blocks like walls, 60-85 tall, under one great dome of
 * billowing gold-green masses carried on six to eight great limbs and a few leaders: a broad umbrella, a landmark from
 * far terrain. The dome is open round the trunk's head and pierced by light shafts between the limbs, so the sky still
 * reaches the bark under it (a closed dome leaves the trunk in light 0-6: a black pole), and its upper masses turn gold.
 */
final class ElderColossus {
    private ElderColossus() {}

    static final int EXTENT = 48, MIN_Y = -3, MAX_Y = 112;

    static Blueprint build(long seed) {
        RandomSource r = RandomSource.create(seed);
        Blueprint bp = new Blueprint(EXTENT, MIN_Y, MAX_Y);
        int h = 60 + r.nextInt(26);
        double r0 = 3.4 + r.nextDouble() * 1.0;
        double flutePhase = r.nextDouble() * Math.PI * 2;
        int lobes = 6 + r.nextInt(3);
        Trunk trunk = new Trunk(h, r.nextDouble() * Math.PI * 2, 0.6 + r.nextDouble() * 1.2);

        // the trunk: bark all round (no end grain on its ledges), fluted, swelling to the ground into the roots
        for (int y = MIN_Y; y <= h; y++) {
            double t = Math.max(0, y) / (double) h;
            double low = Math.max(0, 16 - Math.max(0, y)) / 16.0;
            double rad = r0 * (1 - 0.42 * Math.pow(t, 1.3)) + low * low * 4.8;
            double flute = 0.1 + 0.16 * low;
            double cx = trunk.x(y), cz = trunk.z(y);
            double lim = rad * (1 + flute), inside = rad * (1 - flute);
            double phase = flutePhase + y * 0.04, sp = Math.sin(phase), cp = Math.cos(phase);
            int span = (int) Math.ceil(lim) + 1;
            for (int x = (int) Math.floor(cx) - span; x <= (int) Math.ceil(cx) + span; x++) {
                for (int z = (int) Math.floor(cz) - span; z <= (int) Math.ceil(cz) + span; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d2 = dx * dx + dz * dz;
                    if (d2 > lim * lim) continue;
                    if (d2 > inside * inside) {
                        // the flutes: sin(lobes * angle + phase) by complex powers (no atan2, no sin per cell)
                        double d = Math.sqrt(d2), c = dx / d, sn = dz / d, cr = 1, ci = 0;
                        for (int k = 0; k < lobes; k++) {
                            double nr = cr * c - ci * sn;
                            ci = cr * sn + ci * c;
                            cr = nr;
                        }
                        double reff = rad * (1 + flute * (ci * cp + cr * sp));
                        if (d2 > reff * reff) continue;
                    }
                    bp.set(x, y, z, Blueprint.WOOD);
                    if (y == MIN_Y) bp.ground(x, y, z);
                }
            }
        }

        // buttress roots: tall fins between the flutes, arching down to the ground
        int roots = 6 + r.nextInt(3);
        double ra = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < roots; k++) {
            double a = ra + k * Math.PI * 2 / roots + (r.nextDouble() - 0.5) * 0.4;
            Roots.fin(bp, a, (r.nextDouble() - 0.5) * 0.6, r0 * 0.8, r0 + 6 + r.nextInt(8), 9 + r.nextInt(7), 2.0, 0.6, seed + k);
        }

        // the dome: centre and size
        double domeY = h * 0.78, dome = 24 + r.nextDouble() * 6;
        int limbs = 6 + r.nextInt(3);
        double a = r.nextDouble() * Math.PI * 2;
        double[] limbAngles = new double[limbs];
        for (int k = 0; k < limbs; k++) {
            a += 2.39996 + (r.nextDouble() - 0.5) * 0.4;
            limbAngles[k] = a;
            int hy = (int) (h * (0.46 + 0.3 * k / (double) Math.max(1, limbs - 1))) + r.nextInt(3) - 1;
            double sx = trunk.x(hy), sz = trunk.z(hy);
            // out to the dome's shell, rising as it goes
            double reach = dome * (0.7 + r.nextDouble() * 0.22);
            double ty = domeY - dome * 0.22 + r.nextDouble() * dome * 0.22;
            double ex = sx + Math.cos(a) * reach, ez = sz + Math.sin(a) * reach;
            double mx = sx + Math.cos(a) * reach * 0.55, mz = sz + Math.sin(a) * reach * 0.55, my = hy + (ty - hy) * 0.45;
            double rad = 2.2 - 0.12 * k;
            bp.tube(sx, hy, sz, mx, my, mz, rad, rad * 0.65, Blueprint.WOOD);
            bp.tube(mx, my, mz, ex, ty, ez, rad * 0.65, 0.7, Blueprint.WOOD);
            mass(bp, r, seed + 31L * k, ex, ty, ez, 1.5, 8.5 + r.nextDouble() * 2.0, 4.2 + r.nextDouble() * 1.2, domeY, dome);
            // two boughs from the limb's middle out to the shell on either side
            for (int side = -1; side <= 1; side += 2) {
                double sa = a + side * (0.45 + r.nextDouble() * 0.35);
                double sr = dome * (0.55 + r.nextDouble() * 0.3);
                double bx = sx + Math.cos(sa) * sr, bz = sz + Math.sin(sa) * sr, by = domeY - dome * 0.24 + r.nextDouble() * dome * 0.3;
                bp.tube(mx, my, mz, bx, by, bz, 1.0, 0.6, Blueprint.WOOD);
                mass(bp, r, seed + 31L * k + 7 + side, bx, by, bz, 1.0, 6.5 + r.nextDouble() * 1.5, 4.0 + r.nextDouble(), domeY, dome);
            }
        }

        // the dome's top: leaders from the trunk's head to masses high on the shell
        double tx = trunk.x(h), tz = trunk.z(h);
        int leaders = 4 + r.nextInt(2);
        double la = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < leaders; k++) {
            la += Math.PI * 2 / leaders + (r.nextDouble() - 0.5) * 0.5;
            double lr = dome * (0.42 + r.nextDouble() * 0.18);
            double ex = tx + Math.cos(la) * lr, ez = tz + Math.sin(la) * lr, ey = domeY + dome * (0.12 + r.nextDouble() * 0.13);
            bp.tube(tx, h - 8, tz, ex, ey, ez, 1.6, 0.7, Blueprint.WOOD);
            mass(bp, r, seed + 101L * k, ex, ey, ez, 1.0, 7.5 + r.nextDouble() * 1.5, 4.5, domeY, dome);
        }
        // an open chimney round the trunk's head (no mass over the trunk) and light shafts between the limbs: the sky reaches
        // the bark all the way down (sky light beside the trunk 12-15; under a closed dome 0-6, and the trunk read black)
        bp.shaft(tx, tz, trunk.radius(r0, h) + 4.5, (int) (h * 0.35));
        for (int k = 0; k < 4; k++) {
            double sa = gap(limbAngles, k * Math.PI / 2 + r.nextDouble() * 0.6);
            double d = 6 + r.nextDouble() * 3;
            bp.shaft(Math.cos(sa) * d, Math.sin(sa) * d, 2.6, (int) (h * 0.35));
        }
        return bp;
    }

    /** The angle near {@code want} that lies furthest from every limb (between two of them). */
    private static double gap(double[] limbs, double want) {
        double best = want, bestScore = -1;
        for (int i = 0; i < 24; i++) {
            double cand = want + (i - 12) * Math.PI / 24;
            double near = Math.PI;
            for (double l : limbs) {
                double diff = Math.abs(((cand - l) % (Math.PI * 2) + Math.PI * 3) % (Math.PI * 2) - Math.PI);
                near = Math.min(near, diff);
            }
            double score = near - Math.abs(i - 12) * 0.02;
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    /**
     * A billowing mass {@code lift} above the branch end at (bx, by, bz), joined to it by a short stem: one big hollow
     * ellipsoid and two or three puffs round its shoulders, twig spokes from its centre into each. Masses high on the
     * dome are gold more often (the warm crown top); the rest gold-green.
     */
    static void mass(Blueprint bp, RandomSource r, long seed, double bx, double by, double bz, double lift, double rx, double up,
                     double domeY, double dome) {
        double x = bx, y = by + lift, z = bz;
        spoke(bp, bx, by, bz, x, y, z);
        double high = Math.max(0, Math.min(1, (y - (domeY - dome * 0.1)) / (dome * 0.5)));
        byte tone = r.nextDouble() < 0.12 + 0.6 * high ? Blueprint.LEAF2 : Blueprint.LEAF;
        int puffs = 2 + r.nextInt(2);
        double a = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < puffs; i++) {
            a += Math.PI * 2 / puffs + (r.nextDouble() - 0.5) * 0.8;
            double d = rx * (0.5 + r.nextDouble() * 0.2), pr = rx * (0.55 + r.nextDouble() * 0.15);
            double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d, py = y + (r.nextDouble() - 0.3) * up * 0.7;
            spoke(bp, x, y, z, px, py, pz);
            bp.cloud(seed + 17L * i, px, py, pz, pr, up * (0.65 + r.nextDouble() * 0.25), up * 0.5, 0.6, 0.4, tone);
        }
        for (int i = 0; i < 4; i++) {
            a += Math.PI / 2 + (r.nextDouble() - 0.5) * 0.4;
            double l = rx * 0.6, e = (r.nextDouble() - 0.3) * 0.5;
            spoke(bp, x, y, z, x + Math.cos(a) * Math.cos(e) * l, y + Math.sin(e) * l * 0.6, z + Math.sin(a) * Math.cos(e) * l);
        }
        bp.cloud(seed, x, y, z, rx, up, up * 0.6, 0.62, 0.35, tone);
    }

    private static void spoke(Blueprint bp, double ax, double ay, double az, double bx, double by, double bz) {
        bp.line((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az), (int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz),
                Blueprint.WOOD);
    }

    /** The trunk's centre line: a slow sway to one side as it rises. */
    record Trunk(int h, double angle, double by) {
        /** The trunk's radius at height y (without its flutes). */
        double radius(double r0, double y) {
            return r0 * (1 - 0.42 * Math.pow(Math.max(0, y) / h, 1.3));
        }

        double x(double y) {
            return Math.cos(angle) * by * Math.sin(Math.PI * 0.5 * Math.max(0, y) / h);
        }

        double z(double y) {
            return Math.sin(angle) * by * Math.sin(Math.PI * 0.5 * Math.max(0, y) / h);
        }
    }
}
