package dev.awaken.farsight;

/**
 * Far Sight's clipmap layout (SPEC §16): {@link #RINGS} nested square windows of {@link #SIZE}×{@link #SIZE} cells
 * centred on the camera. Ring k's cells are {@code 2 << k} blocks wide (2 to 128), so each ring reaches twice as far as
 * the one inside it (ring 6: about 16 km). Every window snaps to whole patches of {@link #PATCH}×{@link #PATCH} cells,
 * the unit heights are predicted and uploaded in. Pure integer maths, the same on every side.
 */
public final class Rings {
    private Rings() {}

    public static final int RINGS = 7;
    /** Cells along a window's side; a power of two, so a world cell maps to its texel with a mask (toroidal). */
    public static final int SIZE = 256;
    public static final int MASK = SIZE - 1;
    public static final int PATCH = 16;
    public static final int PATCHES = SIZE / PATCH;
    /** Rings within about a kilometre take the generator's exact base height; farther ones its surface estimate. */
    public static final int EXACT_RINGS = 3;

    public static int cellSize(int ring) {
        return 2 << ring;
    }

    public static int patchBlocks(int ring) {
        return PATCH * cellSize(ring);
    }

    /** Half a window's width in blocks: how far ring {@code ring} reaches from its centre. */
    public static int reach(int ring) {
        return SIZE / 2 * cellSize(ring);
    }

    /** The window's first patch (ring patch coordinates) that centres it nearest block coordinate {@code camera}. */
    public static int windowPatch(double camera, int ring) {
        int p = patchBlocks(ring);
        return (int) Math.floor((camera + p / 2.0) / p) - PATCHES / 2;
    }

    /**
     * Whether a window at {@code current} should move for a camera at {@code camera}: only once the camera is three
     * quarters of a patch past the window's centre, so hovering on a patch boundary never shuffles a strip back and forth.
     */
    public static boolean shouldMove(int current, double camera, int ring) {
        int p = patchBlocks(ring);
        double centre = (current + PATCHES / 2.0) * p;
        return Math.abs(camera - centre) > p * 0.75;
    }

    /** Packs a patch coordinate pair into one long (slot bookkeeping, shared with the workers). */
    public static long key(int px, int pz) {
        return (long) px << 32 | (pz & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    /** The slot a patch lives in: windows are toroidal, so a patch keeps its slot until it leaves the window. */
    public static int slot(int px, int pz) {
        return (pz & (PATCHES - 1)) * PATCHES + (px & (PATCHES - 1));
    }
}
