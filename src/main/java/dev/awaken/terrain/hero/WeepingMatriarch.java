package dev.awaken.terrain.hero;

import net.minecraft.util.RandomSource;

/**
 * The Weeping Matriarch (docs/modules/lands.md, "Mirror Marsh"): the marsh's mother tree, standing in its own black
 * water. A pale gnarled trunk 4-6 across, leaning, 22-30 tall, lifted on a cage of prop roots that arch out of the
 * water all round it; five to seven great limbs arching out 12-17 blocks and bowing at their ends, each under a broad
 * low dome of silver leaves, and one dome over the trunk; from every dome a curtain of tendrils falls toward the water,
 * longest at the rim. From far off the crown reads as one weeping dome 40 across over its reflection.
 */
final class WeepingMatriarch {
    private WeepingMatriarch() {}

    static final int EXTENT = 28, MIN_Y = -5, MAX_Y = 44;
    /**
     * The thinnest tube the tree uses. Every cell within 0.87 (half a cell's diagonal) of a tube's axis is joined face to
     * face to the axis's own cells, so no root or limb leaves a block touching the rest only at an edge.
     */
    private static final double THINNEST = 0.9;

    static Blueprint build(long seed) {
        RandomSource r = RandomSource.create(seed);
        Blueprint bp = new Blueprint(EXTENT, MIN_Y, MAX_Y);
        int h = 22 + r.nextInt(9);
        double r0 = 2.2 + r.nextDouble() * 0.8; // 4-6 across
        double phase = r.nextDouble() * Math.PI * 2;
        ElderColossus.Trunk trunk = new ElderColossus.Trunk(h, r.nextDouble() * Math.PI * 2, 1.5 + r.nextDouble() * 1.5);

        // the trunk: two gnarled lobes twisting slowly, swelling where the prop roots leave it
        for (int y = MIN_Y; y <= h; y++) {
            double rad = trunk.radius(r0, y) + (y < 5 ? Math.pow((5 - Math.max(0, y)) / 5.0, 2) * 1.2 : 0);
            double cx = trunk.x(y), cz = trunk.z(y);
            int span = (int) Math.ceil(rad * 1.25) + 1;
            for (int x = (int) Math.floor(cx) - span; x <= (int) Math.ceil(cx) + span; x++) {
                for (int z = (int) Math.floor(cz) - span; z <= (int) Math.ceil(cz) + span; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                    double reff = rad * (1 + 0.18 * Math.sin(2 * Math.atan2(dz, dx) + phase + y * 0.12));
                    if (dx * dx + dz * dz > reff * reff) continue;
                    bp.set(x, y, z, Blueprint.LOG);
                    if (y == MIN_Y) bp.ground(x, y, z);
                }
            }
        }

        // the prop roots: arches leaving the trunk a few blocks up and coming down into the water all round it
        int props = 6 + r.nextInt(4);
        double pa = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < props; k++) {
            double a = pa + k * Math.PI * 2 / props + (r.nextDouble() - 0.5) * 0.4;
            double d = r0 + 4 + r.nextDouble() * 4, y0 = 2 + r.nextDouble() * 3;
            double sx = trunk.x(y0) + Math.cos(a) * r0 * 0.7, sz = trunk.z(y0) + Math.sin(a) * r0 * 0.7;
            double mx = Math.cos(a) * d * 0.55, mz = Math.sin(a) * d * 0.55, my = y0 + 1.2 + r.nextDouble();
            double ex = Math.cos(a) * d, ez = Math.sin(a) * d;
            bp.tube(sx, y0, sz, mx, my, mz, 1.1, THINNEST, Blueprint.WOOD);
            bp.tube(mx, my, mz, ex, MIN_Y, ez, THINNEST, THINNEST, Blueprint.WOOD);
            for (int x = (int) Math.floor(ex) - 1; x <= (int) Math.floor(ex) + 1; x++) {
                for (int z = (int) Math.floor(ez) - 1; z <= (int) Math.floor(ez) + 1; z++) {
                    if (Blueprint.wood(bp.get(x, MIN_Y, z))) bp.ground(x, MIN_Y, z);
                }
            }
        }

        // the great limbs: arching out and bowing at the ends, a broad low dome over each
        int limbs = 5 + r.nextInt(3);
        double la = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < limbs; k++) {
            double a = la + k * Math.PI * 2 / limbs + (r.nextDouble() - 0.5) * 0.35;
            double sy = h * (0.55 + r.nextDouble() * 0.25), len = 12 + r.nextInt(6);
            double sx = trunk.x(sy), sz = trunk.z(sy);
            double mx = sx + Math.cos(a) * len * 0.5, my = sy + 4 + r.nextDouble() * 2, mz = sz + Math.sin(a) * len * 0.5;
            double ex = sx + Math.cos(a) * len, ey = sy + 1 - r.nextDouble() * 2, ez = sz + Math.sin(a) * len;
            bp.tube(sx, sy, sz, mx, my, mz, 1.2, 0.95, Blueprint.WOOD);
            bp.tube(mx, my, mz, ex, ey, ez, 0.95, THINNEST, Blueprint.WOOD);
            dome(bp, seed + 31L * k, mx, my + 1, mz, 4 + r.nextDouble());
            dome(bp, seed + 31L * k + 7, ex, ey + 1.5, ez, 5.2 + r.nextDouble() * 1.3);
        }
        // and one over the trunk
        double tx = trunk.x(h), tz = trunk.z(h);
        bp.tube(tx, h - 2, tz, tx, h + 1, tz, 1.2, THINNEST, Blueprint.WOOD);
        dome(bp, seed + 999, tx, h + 1.5, tz, 6 + r.nextDouble());
        return bp;
    }

    /** A low dome of leaves over a twig cross (the twigs keep every leaf of it within reach of wood). */
    private static void dome(Blueprint bp, long seed, double x, double y, double z, double rx) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        int arm = (int) Math.max(1, rx * 0.65);
        bp.line(ix - arm, iy, iz, ix + arm, iy, iz, Blueprint.WOOD);
        bp.line(ix, iy, iz - arm, ix, iy, iz + arm, Blueprint.WOOD);
        bp.cloud(seed, x, y, z, rx, 2.2, 1.3, 0.45, 0.35, Blueprint.LEAF);
    }

    /** The curtain: tendrils from the domes' undersides, 4-10 long and up to eight longer toward the rim. */
    static void tendrils(Blueprint bp, long seed) {
        bp.hangVines(seed, 0.3, 4, 10, r2 -> r2 > 220 ? 8 : r2 > 110 ? 4 : 0);
    }
}
