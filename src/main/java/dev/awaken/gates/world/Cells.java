package dev.awaken.gates.world;

/**
 * Geometry of the gate dimension (SPEC section 7). Cell i owns exactly one region file: its origin is
 * ((i mod 64)*1024, (i div 64)*1024) and it uses the 512x512 blocks (32x32 chunks) of region r.(2*(i mod 64)).(2*(i div 64)).
 * The other three regions around it stay empty, so neighbouring cells never share a file. Inside a cell the main dungeon
 * uses slot columns 0..15 and the annex (second dungeon / inner gate) slot columns 16..31. A slot is one chunk.
 */
public final class Cells {
    private Cells() {}

    public static final int PER_ROW = 64;
    public static final int SPACING = 1024;
    public static final int SIZE = 512;
    public static final int SLOT = 16;
    public static final int SLOTS = SIZE / SLOT;          // 32
    public static final int ANNEX_SLOT_X = 16;            // annex starts at slot column 16
    public static final int GRID_ORIGIN = 1;              // floor grids start one slot in from their area edge
    public static final int MAX_CELLS = PER_ROW * 64;     // 4096 cells; z rows beyond that are never used

    public static final int FLOOR1_Y = 200;
    public static final int FLOOR_PITCH = 32;
    /** A floor's band reaches this far below its base... */
    public static final int BAND_BELOW = 4;
    /** ...and this far above (exclusive). Bands of neighbouring floors never overlap. */
    public static final int BAND_ABOVE = 28;

    public static int originX(int cell) {
        return Math.floorMod(cell, PER_ROW) * SPACING;
    }

    public static int originZ(int cell) {
        return Math.floorDiv(cell, PER_ROW) * SPACING;
    }

    public static int regionX(int cell) {
        return originX(cell) >> 9;
    }

    public static int regionZ(int cell) {
        return originZ(cell) >> 9;
    }

    public static String regionFile(int cell) {
        return "r." + regionX(cell) + "." + regionZ(cell) + ".mca";
    }

    /** The cell whose 512x512 area contains this block column, or -1 (gaps between cells, negative coordinates). */
    public static int cellAt(int blockX, int blockZ) {
        if (blockX < 0 || blockZ < 0) return -1;
        int cx = blockX / SPACING, cz = blockZ / SPACING;
        if (cx >= PER_ROW || cz >= 64) return -1;
        if (blockX - cx * SPACING >= SIZE || blockZ - cz * SPACING >= SIZE) return -1;
        return cx + cz * PER_ROW;
    }

    /** Local X inside the cell (0..511) for a column already known to be in the cell. */
    public static int localX(int cell, int blockX) {
        return blockX - originX(cell);
    }

    public static int localZ(int cell, int blockZ) {
        return blockZ - originZ(cell);
    }

    /** Base (walking) Y of main floor f (1..6). */
    public static int floorY(int floor) {
        return FLOOR1_Y - FLOOR_PITCH * (floor - 1);
    }

    /** Which floor band a Y belongs to (1..6), or 0 outside every band. */
    public static int floorAtY(int y) {
        int top = FLOOR1_Y + BAND_ABOVE - 1;
        if (y > top) return 0;
        int f = Math.floorDiv(top - y, FLOOR_PITCH) + 1;
        return f >= 1 && f <= 6 ? f : 0;
    }

    /** Block X of a slot column inside the cell (slot 0..31). */
    public static int slotBlockX(int cell, int slotX) {
        return originX(cell) + slotX * SLOT;
    }

    public static int slotBlockZ(int cell, int slotZ) {
        return originZ(cell) + slotZ * SLOT;
    }
}
