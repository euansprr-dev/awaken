package dev.awaken.terrain.hero;

import net.minecraft.util.RandomSource;

/**
 * The Lumen Spire (docs/modules/lands.md, "Lumen Forest"): a slender ash-white trunk twisted like rope, 40-52 tall,
 * two small tiers on the way up, and on top a broad flat crown of teal pads on radiating limbs, hung with a curtain of
 * glowing vines that grow longer toward the rim. At night the curtain is the brightest thing in the forest.
 */
final class LumenSpire {
    private LumenSpire() {}

    static final int EXTENT = 22, MIN_Y = -3, MAX_Y = 62;

    static Blueprint build(long seed) {
        RandomSource r = RandomSource.create(seed);
        Blueprint bp = new Blueprint(EXTENT, MIN_Y, MAX_Y);
        int h = 40 + r.nextInt(13); // 40-52: three times the Lumen canopy, so it reads from far terrain
        double r0 = 2.0 + r.nextDouble() * 0.7;
        double twist = 0.18 + r.nextDouble() * 0.08, phase = r.nextDouble() * Math.PI * 2;
        ElderColossus.Trunk trunk = new ElderColossus.Trunk(h, r.nextDouble() * Math.PI * 2, 0.5 + r.nextDouble());

        // the trunk: three lobes winding round each other as it rises
        for (int y = MIN_Y; y <= h; y++) {
            double t = Math.max(0, y) / (double) h;
            double rad = r0 * (1 - 0.55 * Math.pow(t, 1.2)) + (y < 6 ? Math.pow((6 - Math.max(0, y)) / 6.0, 2) * 1.8 : 0);
            double cx = trunk.x(y), cz = trunk.z(y);
            int span = (int) Math.ceil(rad * 1.3) + 1;
            for (int x = (int) Math.floor(cx) - span; x <= (int) Math.ceil(cx) + span; x++) {
                for (int z = (int) Math.floor(cz) - span; z <= (int) Math.ceil(cz) + span; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d2 = dx * dx + dz * dz;
                    if (d2 > rad * rad * (1 + 0.24) * (1 + 0.24)) continue; // outside even the widest flute: skip the trig
                    double reff = rad * (1 + 0.24 * Math.sin(3 * Math.atan2(dz, dx) + phase + y * twist));
                    if (dx * dx + dz * dz > reff * reff) continue;
                    bp.set(x, y, z, Blueprint.LOG);
                    if (y == MIN_Y) bp.ground(x, y, z);
                }
            }
        }

        int roots = 4 + r.nextInt(3);
        double ra = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < roots; k++) {
            double a = ra + k * Math.PI * 2 / roots + (r.nextDouble() - 0.5) * 0.6;
            Roots.fin(bp, a, (r.nextDouble() - 0.5) * 0.8, r0 * 0.6, r0 + 3 + r.nextInt(5), 3 + r.nextInt(4), 1.2, 0.5, seed + k);
        }

        // two small tiers on the way up
        double a = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 2; k++) {
            a += Math.PI * (0.8 + r.nextDouble() * 0.4);
            int hy = (int) (h * (k == 0 ? 0.5 : 0.7));
            double len = 4 + r.nextInt(3), e = 0.1 + r.nextDouble() * 0.15;
            double sx = trunk.x(hy), sz = trunk.z(hy);
            double ex = sx + Math.cos(a) * len, ey = hy + Math.sin(e) * len, ez = sz + Math.sin(a) * len;
            bp.tube(sx, hy, sz, ex, ey, ez, 0.7, 0.45, Blueprint.WOOD);
            pad(bp, r, seed + 13L * k, ex, ey, ez, 3.5 + r.nextDouble(), 1.3);
        }

        // the crown: slender limbs radiating from the top, rising then drooping under their pads, one pad over the middle
        double tx = trunk.x(h), tz = trunk.z(h);
        int limbs = 6 + r.nextInt(3);
        a = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < limbs; k++) {
            a += Math.PI * 2 / limbs + (r.nextDouble() - 0.5) * 0.3;
            double len = 8 + r.nextInt(5);
            double sy = h - 2 - r.nextInt(3);
            double mx = tx + Math.cos(a) * len * 0.5, my = sy + 3.2, mz = tz + Math.sin(a) * len * 0.5;
            double ex = tx + Math.cos(a) * len, ey = sy - 0.5, ez = tz + Math.sin(a) * len;
            bp.tube(tx, sy, tz, mx, my, mz, 0.75, 0.5, Blueprint.WOOD);
            bp.tube(mx, my, mz, ex, ey, ez, 0.5, 0.4, Blueprint.WOOD);
            pad(bp, r, seed + 37L * k, mx, my, mz, 3.2 + r.nextDouble(), 1.3);
            pad(bp, r, seed + 37L * k + 5, ex, ey, ez, 4.5 + r.nextDouble() * 1.2, 1.4);
        }
        bp.tube(tx, h - 1, tz, tx, h + 1, tz, 1.0, 0.8, Blueprint.WOOD);
        pad(bp, r, seed + 999, tx, h + 1, tz, 6.0 + r.nextDouble(), 1.8);
        return bp;
    }

    /** A flat pad of leaves round a short twig cross centred on the branch end (wood keeps every leaf of it within reach). */
    static void pad(Blueprint bp, RandomSource r, long seed, double x, double y, double z, double rx, double up) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        int arm = (int) Math.max(1, rx * 0.6);
        bp.line(ix - arm, iy, iz, ix + arm, iy, iz, Blueprint.WOOD);
        bp.line(ix, iy, iz - arm, ix, iy, iz + arm, Blueprint.WOOD);
        bp.cloud(seed, x, y, z, rx, up, up * 0.7, 0.0, 0.3, Blueprint.LEAF);
    }

    /** The curtain: vines hang from the crown's underside, longer toward its rim; shorter from the tiers. */
    static void vines(Blueprint bp, long seed, int h) {
        bp.hangVines(seed, 0.24, 2, 7, r2 -> r2 > 110 ? 5 : r2 > 50 ? 2 : 0);
    }
}
