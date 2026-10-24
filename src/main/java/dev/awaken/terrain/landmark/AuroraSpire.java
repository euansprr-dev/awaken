package dev.awaken.terrain.landmark;

import dev.awaken.terrain.frost.FrostSpiresFeature;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;

/**
 * An Aurora Spire (docs/modules/lands.md, "Frost Spires"): where an old gate froze, the cold grew into crystal. A
 * frostspar needle 42-58 tall stands from the middle of a frozen tarn 17-27 across, four to six lesser needles leaning
 * out round its foot, every big one branching into side crystals at about 55° as frost does on a pane, sheaves of small
 * spires round the tarn's rim and slivers in the ice. At night the aurora's colours glow in the frostspar's seams, so
 * the spire is the brightest thing in the snow for hundreds of blocks.
 * <p>
 * The plan is the shape from the seed alone; {@link LandmarkPiece} freezes the tarn column by column (relative to the
 * centre's ground: the site search keeps it level) and places the voxels.
 */
final class AuroraSpire {
    private AuroraSpire() {}

    // palette codes (Landmark.AURORA_SPIRE.state)
    static final byte SPAR = 1, CLUSTER = 2;

    /** The tarn and the crystals on it, from the seed. */
    static final class Plan {
        final long seed;
        /** The frozen tarn's radius; the hoarfrost ring round it is {@link #RING} wide. */
        final double tarn;
        final int height;
        final Voxels voxels;

        Plan(long seed, double tarn, int height, Voxels voxels) {
            this.seed = seed;
            this.tarn = tarn;
            this.height = height;
            this.voxels = voxels;
        }

        /** How far the frozen ground reaches from the centre. */
        int groundReach() {
            return (int) Math.ceil(tarn + RING) + 1;
        }
    }

    static final double RING = 3.5;
    /** The angle between successive crystals round the foot (the golden angle: no two line up). */
    private static final double GOLDEN = 2.39996;

    static Plan plan(long seed) {
        RandomSource r = RandomSource.create(seed);
        double tarn = 8.5 + r.nextInt(5) + r.nextDouble(); // 17-27 across
        int h = 42 + r.nextInt(17);
        int extent = (int) Math.ceil(tarn + RING) + 14; // the lesser needles and their branches lean out past the rim
        Voxels v = new Voxels(extent, -3, h + 3);

        // the great needle, near upright from the tarn's middle, branching on the way up
        double a = r.nextDouble() * Math.PI * 2, tilt = r.nextDouble() * 0.06;
        double r0 = 3.4 + r.nextDouble() * 0.8;
        crystal(v, r, 0.5, 0.5, a, tilt, h, r0, 3 + r.nextInt(2));

        // the lesser needles round its foot, leaning out
        int lesser = 4 + r.nextInt(3);
        for (int k = 0; k < lesser; k++) {
            double ka = a + k * GOLDEN + (r.nextDouble() - 0.5) * 0.5, d = 2.5 + r.nextDouble() * 1.5;
            int len = 16 + r.nextInt(19);
            crystal(v, r, 0.5 + Math.cos(ka) * d, 0.5 + Math.sin(ka) * d, ka, 0.12 + r.nextDouble() * 0.18, len,
                    1.5 + len * 0.03 + r.nextDouble() * 0.5, 2 + r.nextInt(2));
        }

        // sheaves of small spires round the rim, and slivers on the ice
        int rim = 6 + r.nextInt(5);
        double ra = r.nextDouble() * Math.PI * 2;
        for (int k = 0; k < rim; k++) {
            double ka = ra + k * Math.PI * 2 / rim + (r.nextDouble() - 0.5) * 0.4, d = tarn - 0.5 + r.nextDouble() * 2.5;
            FrostSpiresFeature.sheaf(v, Math.cos(ka) * d, -1, Math.sin(ka) * d, r, 2 + r.nextInt(3), UniformInt.of(4, 9 + r.nextInt(3)), 0.5, SPAR);
        }
        for (int x = -(int) tarn; x <= (int) tarn; x++) {
            for (int z = -(int) tarn; z <= (int) tarn; z++) {
                if (x * x + z * z < tarn * tarn && Voxels.hash(seed + 7, x, 0, z) < 0.035) v.fill(x, 0, z, CLUSTER);
            }
        }
        v.seal();
        return new Plan(seed, tarn, h, v);
    }

    /**
     * One big crystal: a needle from (x, z) on the ice leaning {@code tilt} toward angle {@code a}, and {@code branches}
     * side crystals off it at about 55°, spread round it and up its middle.
     */
    private static void crystal(Voxels v, RandomSource r, double x, double z, double a, double tilt, int len, double r0, int branches) {
        double dx = Math.sin(tilt) * Math.cos(a), dy = Math.cos(tilt), dz = Math.sin(tilt) * Math.sin(a);
        v.needle(x, -1, z, dx, dy, dz, len, r0, true, SPAR);
        double ba0 = r.nextDouble() * Math.PI * 2;
        for (int b = 0; b < branches; b++) {
            double f = 0.25 + 0.5 * (b + r.nextDouble()) / branches;
            double ba = ba0 + b * GOLDEN, bt = Math.min(1.2, tilt + 0.9 + r.nextDouble() * 0.15);
            double blen = len * (0.22 + r.nextDouble() * 0.12);
            v.needle(x + dx * len * f, -1 + dy * len * f, z + dz * len * f, Math.sin(bt) * Math.cos(ba), Math.cos(bt), Math.sin(bt) * Math.sin(ba),
                    blen, Math.max(0.5, r0 * (1 - f) * 0.55), false, SPAR);
        }
    }
}
