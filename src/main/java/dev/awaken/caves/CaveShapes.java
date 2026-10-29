package dev.awaken.caves;

import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * The regions' cave shapes (SPEC §42.3), as densities: negative is open cave, about a quarter of a unit per block from
 * the wall, so the router's interpolation between cell corners finds smooth walls. Sampled only on the 4 × 8 × 4 cell
 * corners, so nothing narrower than about five blocks or lower than about six survives; every shape here is at least
 * that. The noises are the world's own (the pack's {@code awaken:caves/shape_a}, {@code shape_b} at about 128 blocks and
 * {@code shape_flat} at about 256; normalised, so about a third is one standard deviation), so a shape runs across chunk
 * borders whole. Everything 2D is worked out once a column ({@link Column}): a corner costs a few multiplications.
 */
final class CaveShapes {
    private CaveShapes() {}

    /** One column's shape values, worked out the first time its region asks. */
    static final class Column {
        boolean tubes, tilted;
        float across, centre, half, tilt;
        int layer = Integer.MIN_VALUE;
        float foot;

        void reset() {
            tubes = tilted = false;
            layer = Integer.MIN_VALUE;
        }
    }

    /**
     * The shape's density at a corner; {@code under} is how far below the seal it is, in blocks (the Shallows hang their
     * galleries from the ground, not from a fixed y).
     */
    static float density(CaveRegion.Shape shape, Column c, Noise a, Noise b, Noise flat, int x, int y, int z, float under) {
        return switch (shape) {
            case TUBES -> tubes(c, a, b, flat, x, y, z, under);
            case TERRACES -> terraces(c, a, flat, x, y, z);
            case NONE -> 1f;
        };
    }

    /** The Rootways' channels: half-widths in noise units, at the narrowest and added in a hall. */
    static final float CHANNEL = 0.035f, HALL = 0.06f;

    /**
     * The Rootways: winding galleries hung under the seal, two crossing families of channels (the zero lines of two 2D
     * noises), 5 to 9 blocks wide and 5 to 8 high, opening into low wide halls where the flat noise rises. Never above
     * {@link CaveRegion#MAX_Y} less a gallery, so a gallery under a hill isn't cut by the cap.
     */
    static float tubes(Column c, Noise a, Noise b, Noise flat, int x, int y, int z, float under) {
        if (!c.tubes) {
            float hall = Math.clamp((flat.get(x * 0.5, z * 0.5) - 0.2f) * 4f, 0f, 1f);
            float w = CHANNEL + HALL * hall;
            // across: the nearer channel of the two families, in quarter-units per block at their usual slope
            c.across = Math.min(Math.abs(a.get(x, z)) - w, Math.abs(b.get(x + 3000.0, z - 3000.0)) - w) * 9f;
            // a gallery's middle 7 to 11 blocks under the seal, taller in a hall
            c.centre = 9f + 2f * flat.get(x + 1500.0, z + 1500.0);
            c.half = 2.75f + 1.25f * hall;
            c.tubes = true;
        }
        // its middle as a y: hung from the seal, held under the cap
        float mid = Math.min(y + under - c.centre, CaveRegion.MAX_Y - 3 - c.half);
        float upDown = (Math.abs(y - mid) - c.half) * 0.25f;
        return Math.max(c.across, upDown);
    }

    /** The terraces' layers, in blocks: a gallery {@link #GALLERY} high in every {@link #LAYER}. */
    static final int LAYER = 16, GALLERY = 9;
    /** How much of a layer opens: where its footprint noise is above this (about 30% of each layer). */
    static final float FOOT = 0.17f;

    /**
     * The Ochre Deep: stacked galleries, one every {@link #LAYER} blocks, each with its own footprint (a slice of a 3D
     * noise, so neighbouring layers differ), floors gently tilted. Where one layer's footprint runs on past the one
     * above or below you get the ledges and drops between terraces.
     */
    static float terraces(Column c, Noise a, Noise flat, int x, int y, int z) {
        if (!c.tilted) {
            c.tilt = 4f * flat.get(x - 1500.0, z + 700.0);
            c.tilted = true;
        }
        float t = y + 64 + c.tilt;
        int k = Math.floorDiv((int) Math.floor(t), LAYER);
        float in = t - k * LAYER;
        float upDown = in < GALLERY ? (Math.abs(in - GALLERY / 2f) - GALLERY / 2f) * 0.25f : Math.min(in - GALLERY, LAYER - in) * 0.25f;
        if (c.layer != k) {
            c.foot = a.get(x * 0.8, k * 97.0, z * 0.8);
            c.layer = k;
        }
        float across = (FOOT - c.foot) * 6f;
        return Math.max(across, upDown);
    }
}
