package dev.awaken.ground;

/**
 * The foot rule of the soft steps (SPEC §31, P1; measured in docs/terrain-immersion/proof/SoftBench.java): a column takes a
 * half-block slab on top of its ground when one of its four neighbours is exactly one block higher, none is lower, and none
 * of the eight is two or more higher (a cliff stays a cliff). It only ever adds; it reads heights and nothing else, so every
 * chunk decides each column the same way whatever order the world is made in. Pure maths on a grid of ground heights.
 */
public final class SoftRule {
    private SoftRule() {}

    /**
     * Whether the column at (i, j) of the row-major height grid {@code h} (width {@code w}, ground heights as the y of the top
     * block) takes a slab. The caller keeps one column of margin: i and j are never on the grid's edge.
     */
    public static boolean foot(int[] h, int w, int i, int j) {
        int k = j * w + i, c = h[k];
        int e = h[k + 1] - c, west = h[k - 1] - c, s = h[k + w] - c, n = h[k - w] - c;
        if (e < 0 || west < 0 || s < 0 || n < 0) return false;                 // something lower: raising would make a bigger step
        if (e > 1 || west > 1 || s > 1 || n > 1) return false;                 // a cliff beside it
        if (e != 1 && west != 1 && s != 1 && n != 1) return false;             // nothing a block higher: no step to soften
        return h[k + w + 1] - c <= 1 && h[k + w - 1] - c <= 1 && h[k - w + 1] - c <= 1 && h[k - w - 1] - c <= 1;
    }

    /** Which plant stands in a turf slab ({@link TurfSlabBlock#TUFT}) at a column: 0 none, else short, tall, fern, flower, bush. */
    public static int tuft(long seed, int x, int z) {
        long k = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + z * 0x85157AF5L;
        k ^= k >>> 29;
        k *= 0xBF58476D1CE4E5B9L;
        k ^= k >>> 32;
        double a = (k & 0xFFFFF) / (double) 0x100000, b = (k >>> 20 & 0xFFFFF) / (double) 0x100000;
        if (a >= 0.45) return 0;
        return b < 0.45 ? 1 : b < 0.62 ? 2 : b < 0.80 ? 3 : b < 0.92 ? 4 : 5;
    }
}
