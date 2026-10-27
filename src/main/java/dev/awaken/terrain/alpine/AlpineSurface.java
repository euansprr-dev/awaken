package dev.awaken.terrain.alpine;

/**
 * What alpine ground is made of (SPEC §15, Alpine), as one rule shared by the chunk generator's surface condition
 * ({@link AlpineCondition}) and Far Sight's far shading, so far mountains wear the snow and rock the near ones will:
 * <ul>
 *   <li>snow above a snow line that wanders by a few blocks, drops on north-facing slopes and lifts on south ones, and
 *       never on slopes steeper than about 51°, where it slides off;</li>
 *   <li>bare rock on faces steeper than about 61°, and scree between, where loose stone collects under cliffs;</li>
 *   <li>erosion only on high ground: the lowlands and valley floors keep their shape.</li>
 * </ul>
 * Pure maths, no game types: callers pass slope as rise per block and aspect as the ground's slope toward +z.
 */
public final class AlpineSurface {
    private AlpineSurface() {}

    /** Mean snow line (y) and how far it wanders, drops on north faces, and lifts on south faces. */
    public static final float SNOW_LINE = 186f, SNOW_WANDER = 9f, NORTH_DROP = 22f, SOUTH_LIFT = 14f;
    /** Slopes (rise per block): snow holds up to the first, scree from the second, bare rock from the third. */
    public static final float SNOW_SLOPE = 1.25f, SCREE_SLOPE = 1.15f, ROCK_SLOPE = 1.5f;
    /** The Soft Land (SPEC §31, P3): scree and rock reach lower down every steep slope (they were 1.45 and 1.8, cliffs only), and
     *  from this slope the soil starts to show loose ground in patches: coarse dirt and gravel. */
    public static final float LOOSE_FROM = 0.7f;
    /** Ground heights (y) over which erosion fades in: none on lowland, full on mountains. */
    public static final float ERODE_FROM = 74f, ERODE_FULL = 140f;

    /** The treeline sits this far under the snow line; trees need slopes gentler than {@link #TREE_SLOPE}. */
    public static final float TREELINE_GAP = 14f, TREE_SLOPE = 1.1f;

    /**
     * Mountain forests: spruce the pack plants in mountain biomes (windswept hills thick, meadows sparse), as the tree
     * density (0..255) Far Sight's far forests add for them, matched to the placed features' counts.
     */
    public static int mountainForest(String archetype) {
        return switch (archetype) {
            case "windswept_hills" -> 190;
            case "windswept_forest" -> 60;
            case "meadow" -> 60;
            default -> 0;
        };
    }

    /** Whether a tree may stand on ground at (x, y, z) sloping (gx, gz): below the treeline, on soil-holding slopes. */
    public static boolean treeAllowed(float x, float y, float z, float gx, float gz) {
        return Math.sqrt(gx * gx + gz * gz) <= TREE_SLOPE && y <= snowLine(x, z, gx, gz) - TREELINE_GAP;
    }

    /** How strongly erosion works on ground standing at {@code y}: 0 below {@link #ERODE_FROM}, 1 from {@link #ERODE_FULL}. */
    public static float erosionStrength(float y) {
        return Erosion.smooth(ERODE_FROM, ERODE_FULL, y);
    }

    /**
     * The snow line at (x, z) for ground sloping ({@code gx}, {@code gz}) (rise per block): lower on slopes that face
     * north (rising toward +z, so their face looks toward -z), higher on those facing south.
     */
    public static float snowLine(float x, float z, float gx, float gz) {
        float slope = (float) Math.sqrt(gx * gx + gz * gz);
        float wander = SNOW_WANDER * (valueNoise(x, z, 160f, 77) * 2f - 1f);
        float facing = slope < 1e-4f ? 0f : gz / slope * Erosion.smooth(0.08f, 0.45f, slope);
        float aspect = facing > 0f ? -NORTH_DROP * facing : -SOUTH_LIFT * facing;
        return SNOW_LINE + wander + aspect;
    }

    /**
     * Whether ground at height y with that slope holds snow: the snow line broken into drifts and bare patches over a
     * band of about 20 blocks ({@link #patches}, the same noise Far Sight's shader lays), never one hard line;
     * {@code jitter} (0..1) feathers the steepness limit per block.
     */
    public static boolean snow(float x, float y, float z, float gx, float gz, float jitter) {
        float slope = (float) Math.sqrt(gx * gx + gz * gz);
        if (slope > SNOW_SLOPE + 0.3f * (jitter - 0.5f)) return false;
        return y + patches(x, z) >= snowLine(x, z, gx, gz);
    }

    /** The snow edge's patches (blocks, about ±10): value noise on 24- and 7-block lattices, as farsight_terrain.fsh. */
    public static float patches(float x, float z) {
        return (valueNoise(x, z, 24f, 5) - 0.5f) * 14f + (valueNoise(x, z, 7f, 9) - 0.5f) * 6f;
    }

    /** Smooth value noise in 0..1 with the shaders' hash (farsight.glsl farHash), so both sides lay the same patches. */
    static float valueNoise(float x, float z, float cell, int seed) {
        float u = x / cell, v = z / cell;
        int iu = (int) Math.floor(u), iv = (int) Math.floor(v);
        float fu = u - iu, fv = v - iv;
        fu = fu * fu * (3f - 2f * fu);
        fv = fv * fv * (3f - 2f * fv);
        float a = farHash(iu * 3 + seed, iv * 3 + seed), b = farHash((iu + 1) * 3 + seed, iv * 3 + seed);
        float c = farHash(iu * 3 + seed, (iv + 1) * 3 + seed), d = farHash((iu + 1) * 3 + seed, (iv + 1) * 3 + seed);
        float top = a + (b - a) * fu, bottom = c + (d - c) * fu;
        return top + (bottom - top) * fv;
    }

    /** farsight.glsl's farHash: two ints to 0..1. */
    static float farHash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return Integer.toUnsignedLong(h ^ (h >>> 16)) / 4294967296f;
    }

    /**
     * Loose ground on soil that is steepening (slope {@link #LOOSE_FROM} up to the scree's): 0 none (grass stays), 1 coarse dirt,
     * 2 gravel, in patches 7 blocks across that grow with the slope. Near and far agree on the thresholds; the patches are too
     * small to show from afar.
     */
    public static int loose(float x, float z, float gx, float gz, float jitter) {
        float slope = (float) Math.sqrt(gx * gx + gz * gz) + 0.12f * (jitter - 0.5f);
        if (slope < LOOSE_FROM || slope >= SCREE_SLOPE) return 0;
        float p = Erosion.smooth(LOOSE_FROM, SCREE_SLOPE, slope);
        float n = valueNoise(x, z, 7f, 31);
        return n >= 0.80f - 0.25f * p ? 1 : n <= 0.10f + 0.20f * p ? 2 : 0;
    }

    /** A 0..1 noise on 6-block cells: in the scree band, its low end is grass pockets, its high end rock showing through. */
    public static float pocket(float x, float z) {
        return valueNoise(x, z, 6f, 17);
    }

    /**
     * The rock at (x, y, z) in layers: 0 stone, 1 andesite, 2 tuff, 3 granite, 4 calcite (a thin pale band). Three blocks a
     * layer, a cycle of eight, the layers tilted by a slow noise (about 4 blocks over 40) so a cliff has strata that dip, not
     * ruled lines. Pure of position; the rock rule and the tests read it.
     */
    public static int strata(int x, int y, int z) {
        float wobble = (valueNoise(x, z, 40f, 61) - 0.5f) * 8f + (valueNoise(x, z, 11f, 62) - 0.5f) * 2f;
        int yy = (int) Math.floor(y + wobble);
        if (Math.floorMod(Math.floorDiv(yy, 2), 31) == 17) return 4;
        int b = Math.floorMod(Math.floorDiv(yy, 3), 8);
        return b == 1 || b == 7 ? 1 : b == 3 ? 2 : b == 5 ? 3 : 0;
    }

    /**
     * Snow in layers below the snow's edge: how many layers (0..3) a drift lays on the ground at (x, y, z) in the band of about 9
     * blocks under the line (where {@link #snow} has no block yet): thicker toward the line, in patches, never on steep ground.
     */
    public static int driftLayers(float x, float y, float z, float gx, float gz, float jitter) {
        float slope = (float) Math.sqrt(gx * gx + gz * gz);
        if (slope > 1.3f) return 0;
        float d = snowLine(x, z, gx, gz) - (y + patches(x, z));
        if (d <= 0f || d >= 9f) return 0;
        float p = 1f - d / 9f;
        float n = valueNoise(x, z, 5f, 71);
        if (n > p * 0.85f) return 0;
        return Math.clamp(1 + (int) (p * 3f * (0.5f + 0.5f * jitter)), 1, 3);
    }

    /** 0 soil, 1 scree (loose stone), 2 bare rock: by slope, feathered per block by {@code jitter} (0..1). */
    public static int stone(float gx, float gz, float jitter) {
        float slope = (float) Math.sqrt(gx * gx + gz * gz) + 0.25f * (jitter - 0.5f);
        return slope >= ROCK_SLOPE ? 2 : slope >= SCREE_SLOPE ? 1 : 0;
    }


    /** A per-block 0..1 for feathering edges. */
    public static float jitter(int x, int y, int z) {
        return Erosion.unit(Erosion.hash(x, z, y * 31 + 5));
    }
}
