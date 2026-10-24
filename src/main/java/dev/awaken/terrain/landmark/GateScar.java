package dev.awaken.terrain.landmark;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A Gate Scar (docs/modules/lands.md, "Glass Steppe"): where a gate broke long ago, the plain melted to glass. A
 * shallow bowl 30-50 across, black scorched glass at its heart still holding the gate's amber light in its cracks,
 * fused glass round it out to a raised rim, rays of glass splashed out across the grass, tall sunshard spires leaning
 * outward round the rim as if the blast threw them, and in the middle the stumps of the gate itself.
 * <p>
 * The plan is the shape from the seed alone; {@link LandmarkPiece} shapes the ground column by column (the bowl and
 * its glass, relative to the centre's height and the real ground there) and places the voxels.
 */
final class GateScar {
    private GateScar() {}

    // palette codes (Landmark.GATE_SCAR.state)
    static final byte SHARD = 1, FUSED = 2, SCORCHED = 3, TILES = 4, POLISHED = 5, CLUSTER = 6, CHISELED = 7;

    /** The bowl and what lies round it, from the seed. */
    static final class Plan {
        final long seed;
        /** Bowl radius, depth at the centre, rim width and height. */
        final double radius, depth, rimWidth, rimHeight;
        final double[] rayAngle, rayLength, rayWidth;
        final double[] crackAngle;
        final Voxels voxels;

        Plan(long seed, double radius, double depth, double rimWidth, double rimHeight, double[] rayAngle, double[] rayLength,
             double[] rayWidth, double[] crackAngle, Voxels voxels) {
            this.seed = seed;
            this.radius = radius;
            this.depth = depth;
            this.rimWidth = rimWidth;
            this.rimHeight = rimHeight;
            this.rayAngle = rayAngle;
            this.rayLength = rayLength;
            this.rayWidth = rayWidth;
            this.crackAngle = crackAngle;
            this.voxels = voxels;
        }

        /** How far the shaped ground reaches from the centre (rays included). */
        int groundReach() {
            double m = radius + rimWidth;
            for (double l : rayLength) m = Math.max(m, l);
            return (int) Math.ceil(m) + 1;
        }

        /** The bowl's floor relative to the ground at the centre (0 = that ground's top block), inside the rim. */
        double floor(double r) {
            double f = r / radius;
            return -depth * (1 - f * f);
        }

        /**
         * The first air above the shaped ground at r, in voxel y (0 = the first air above the centre's ground): the
         * bowl's floor inside the rim, the rim's crest over ground as high as the centre's beyond it.
         */
        int air(double r) {
            return (int) Math.round(r < radius ? floor(r) : rim(r));
        }

        /** The rim's rise over the natural ground at r (0 inside the bowl and beyond the rim). */
        double rim(double r) {
            if (r < radius || r > radius + rimWidth) return 0;
            double u = (r - radius) / rimWidth; // 0 at the lip, 1 at the outer foot
            return rimHeight * Math.sin(Math.PI * Math.pow(u, 0.6));
        }

        /** 0..1: how much of a ray of splashed glass covers the point (angle a, distance r). */
        double ray(double a, double r) {
            double best = 0;
            for (int i = 0; i < rayAngle.length; i++) {
                if (r < radius || r > rayLength[i]) continue;
                double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(a - rayAngle[i]))) * Math.PI / 180 * r; // blocks across
                double w = rayWidth[i] * (1 - (r - radius) / (rayLength[i] - radius + 1e-6) * 0.75);
                if (d < w) best = Math.max(best, 1 - d / w);
            }
            return best;
        }

        /** On one of the radial cracks of scorched glass that run out from the heart. */
        boolean crack(double a, double r) {
            if (r < radius * 0.25 || r > radius * 0.92) return false;
            for (double c : crackAngle) {
                double wobble = Math.sin(r * 0.7 + c * 5) * 0.05;
                double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(a - c - wobble))) * Math.PI / 180 * r;
                if (d < 0.6) return true;
            }
            return false;
        }
    }

    static Plan plan(long seed) {
        RandomSource r = RandomSource.create(seed);
        double radius = 15 + r.nextInt(10) + r.nextDouble(); // 30-50 across
        double depth = 5 + radius * 0.16 + r.nextDouble() * 1.5;
        double rimWidth = 4 + r.nextDouble() * 2.5, rimHeight = 1.6 + r.nextDouble() * 1.4;
        int rays = 8 + r.nextInt(5);
        double[] ra = new double[rays], rl = new double[rays], rw = new double[rays];
        for (int i = 0; i < rays; i++) {
            ra[i] = (i + r.nextDouble() * 0.6) * Math.PI * 2 / rays;
            rl[i] = radius * (1.35 + r.nextDouble() * 0.6);
            rw[i] = 1.6 + r.nextDouble() * 1.8;
        }
        int cracks = 5 + r.nextInt(4);
        double[] ca = new double[cracks];
        for (int i = 0; i < cracks; i++) ca[i] = (i + r.nextDouble() * 0.7) * Math.PI * 2 / cracks;

        int extent = (int) Math.ceil(radius + rimWidth + 16);
        Voxels v = new Voxels(extent, -(int) Math.ceil(depth) - 3, 40);
        Plan p = new Plan(seed, radius, depth, rimWidth, rimHeight, ra, rl, rw, ca, v);

        // the spires round the rim, leaning outward; three or four of them tall
        int spires = 10 + r.nextInt(7);
        double a0 = r.nextDouble() * Math.PI * 2;
        int tall = 3 + r.nextInt(2);
        for (int i = 0; i < spires; i++) {
            double a = a0 + i * Math.PI * 2 / spires + (r.nextDouble() - 0.5) * 0.35;
            boolean big = i % (spires / tall) == 0;
            double at = radius - 1.5 + r.nextDouble() * 3.5;
            int h = big ? 18 + r.nextInt(11) : 6 + r.nextInt(9);
            double r0 = big ? 2.0 + r.nextDouble() * 0.9 : 1.0 + r.nextDouble() * 0.9;
            double lean = (big ? 0.22 : 0.3) + r.nextDouble() * 0.2;
            double la = a + (r.nextDouble() - 0.5) * 0.5;
            double bx = Math.cos(a) * at, bz = Math.sin(a) * at;
            int by = p.air(at);
            v.prism(bx, by - 1, bz, h + 1, r0, Math.cos(la) * lean, Math.sin(la) * lean, r.nextDouble() * Math.PI, !big && r.nextInt(3) == 0, SHARD);
            // smaller shards at the big ones' feet
            if (big) {
                for (int k = 0; k < 2; k++) {
                    double ba = a + (r.nextDouble() - 0.5) * 0.4, bd = at + (r.nextDouble() - 0.3) * 4;
                    int bh = 3 + r.nextInt(5);
                    v.prism(Math.cos(ba) * bd, p.air(bd) - 1, Math.sin(ba) * bd, bh + 1,
                            0.8 + r.nextDouble() * 0.5, Math.cos(ba) * 0.35, Math.sin(ba) * 0.35, r.nextDouble() * Math.PI, false, SHARD);
                }
            }
        }

        // the gate's stumps in the middle: a broken ring of deepslate pillars, one fallen across the floor
        int stumps = 5 + r.nextInt(3);
        double ring = 4 + r.nextDouble() * 1.5;
        double s0 = r.nextDouble() * Math.PI * 2;
        int fallen = r.nextInt(stumps);
        for (int i = 0; i < stumps; i++) {
            double a = s0 + i * Math.PI * 2 / stumps;
            int x = (int) Math.round(Math.cos(a) * ring), z = (int) Math.round(Math.sin(a) * ring);
            int base = p.air(Math.hypot(x, z));
            if (i == fallen) {
                // lying outward along the floor, the floor rising under it a little: each block stands on it
                for (int k = 0; k < 6; k++) {
                    int fx = (int) Math.round(Math.cos(a) * (ring + 1 + k)), fz = (int) Math.round(Math.sin(a) * (ring + 1 + k));
                    int fy = p.air(Math.hypot(fx, fz));
                    v.set(fx, fy, fz, k % 3 == 1 ? CHISELED : POLISHED);
                    v.stand(fx, fy, fz);
                }
                continue;
            }
            int h = 2 + r.nextInt(5);
            for (int y = 0; y < h; y++) {
                byte b = y == h - 1 && r.nextInt(2) == 0 ? CHISELED : TILES;
                v.set(x, base + y, z, b);
                v.set(x + 1, base + y, z, y < h - 1 || r.nextBoolean() ? b : EMPTY_CODE);
                v.set(x, base + y, z + 1, y < h - 2 ? POLISHED : EMPTY_CODE);
                v.set(x + 1, base + y, z + 1, y < h - 1 ? TILES : EMPTY_CODE);
            }
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) v.stand(x + dx, base, z + dz);
            // sunshard growing out of the stone
            if (r.nextInt(2) == 0) {
                double ga = a + (r.nextDouble() - 0.5);
                v.prism(x + 0.5, base + h, z + 0.5, 3 + r.nextInt(4), 0.7 + r.nextDouble() * 0.4, Math.cos(ga) * 0.3, Math.sin(ga) * 0.3,
                        r.nextDouble(), false, SHARD);
            }
        }
        // the heart: one shard standing where the gate was, a little off true
        double ha = r.nextDouble() * Math.PI * 2;
        v.prism(0.5, p.air(0) - 1, 0.5, 7 + r.nextInt(5), 1.3 + r.nextDouble() * 0.5, Math.cos(ha) * 0.12, Math.sin(ha) * 0.12,
                r.nextDouble(), false, SHARD);
        v.seal();
        return p;
    }

    private static final byte EMPTY_CODE = Voxels.EMPTY;

    /**
     * The surface for one column of the scar: the top block's height relative to the centre's ground (when
     * {@code bowl}), or the rise over the column's own ground (when not), and what it is made of.
     */
    static byte material(Plan p, double a, double r, double hash) {
        double rr = p.radius;
        if (r < rr * 0.27) return SCORCHED;
        if (r < rr * 0.52 && hash < (rr * 0.52 - r) / (rr * 0.25)) return SCORCHED;
        if (r < rr && p.crack(a, r)) return SCORCHED;
        return FUSED;
    }
}
