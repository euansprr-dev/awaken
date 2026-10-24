package dev.awaken.city;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * One chunk's (or one small region's) worth of city, painted before anything touches the world. Cells hold null where
 * the city leaves the world alone. Heights are relative to the street: ry 0 is the paving's top block.
 * Painting addresses cells in plan coordinates; whatever falls outside the buffer or the height band is dropped
 * (that is the clipping that lets every chunk paint only its share of a piece).
 */
final class CityBuffer {
    static final int YMIN = -16, YMAX = 152, H = YMAX - YMIN + 1;
    final int w, d;
    final BlockState[] cells;
    /** Columns whose ground the writer must carry down to solid rock (the plate, where terrain did not flatten it). */
    final boolean[] plate;
    /** Plan coordinates of the buffer's north-west cell. */
    int px0, pz0;

    CityBuffer(int w, int d) {
        this.w = w;
        this.d = d;
        cells = new BlockState[w * d * H];
        plate = new boolean[w * d];
    }

    private static final ThreadLocal<CityBuffer> LOCAL = ThreadLocal.withInitial(() -> new CityBuffer(16, 16));

    /** The worldgen thread's chunk buffer. */
    static CityBuffer chunk() {
        return LOCAL.get();
    }

    void reset(int px0, int pz0) {
        Arrays.fill(cells, null);
        Arrays.fill(plate, false);
        this.px0 = px0;
        this.pz0 = pz0;
    }

    int index(int lx, int ry, int lz) {
        return ((ry - YMIN) * d + lz) * w + lx;
    }

    void set(int px, int ry, int pz, BlockState s) {
        int lx = px - px0, lz = pz - pz0;
        if (lx < 0 || lz < 0 || lx >= w || lz >= d || ry < YMIN || ry > YMAX) return;
        cells[index(lx, ry, lz)] = s;
    }

    /** Sets a run of rows [ry0, ry1] in one column. */
    void column(int px, int ry0, int ry1, int pz, BlockState s) {
        int lx = px - px0, lz = pz - pz0;
        if (lx < 0 || lz < 0 || lx >= w || lz >= d) return;
        int a = Math.max(YMIN, ry0), b = Math.min(YMAX, ry1);
        for (int ry = a; ry <= b; ry++) cells[index(lx, ry, lz)] = s;
    }

    BlockState get(int px, int ry, int pz) {
        int lx = px - px0, lz = pz - pz0;
        if (lx < 0 || lz < 0 || lx >= w || lz >= d || ry < YMIN || ry > YMAX) return null;
        return cells[index(lx, ry, lz)];
    }

    boolean contains(int px, int pz) {
        int lx = px - px0, lz = pz - pz0;
        return lx >= 0 && lz >= 0 && lx < w && lz < d;
    }

    void plate(int px, int pz) {
        int lx = px - px0, lz = pz - pz0;
        if (lx < 0 || lz < 0 || lx >= w || lz >= d) return;
        plate[lz * w + lx] = true;
    }

    int x1() {
        return px0 + w - 1;
    }

    int z1() {
        return pz0 + d - 1;
    }
}
