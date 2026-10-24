package dev.awaken.terrain.hero;

import java.util.Arrays;

/**
 * A hero tree as a dense voxel grid round its base (x and z from the trunk's centre, y from the ground at the centre),
 * built once from the structure's seed and kept while the chunks it spans generate. Building it never looks at the
 * world: every random choice comes from the seed, so every chunk sees the same tree. The world only decides, column
 * by column, where the ground is ({@link #foot}: trunk and root columns reach down to it) and what may be replaced.
 *
 * <p>Cost: the work follows the cells written, not the grid. Writes keep each column's used range and a list of the
 * wood and leaf cells, so settling the leaves walks only those; canopy masses fill whole spans of a row and only test
 * the ragged rim cell by cell.
 */
public final class Blueprint {
    // cell codes (states per tree: HeroTree#state)
    public static final byte EMPTY = 0, LOG = 1, LOG_X = 2, LOG_Z = 3, WOOD = 4, LEAF = 5, VINE = 6, VINE_TIP = 7, MOSS = 8, LEAF2 = 9;
    /**
     * Leaves after {@link #settleLeaves()}: distance 1..6 to wood ({@code LEAF_D1 + d - 1}) or too far (persistent);
     * the second leaf tone the same from {@link #LEAF2_D1}.
     */
    public static final byte LEAF_D1 = 10, LEAF_PERSISTENT = 16, LEAF2_D1 = 17, LEAF2_PERSISTENT = 23, CODES = 24;
    public static final int NO_FOOT = Short.MIN_VALUE;

    public final int extent, minY, maxY, sx, sy;
    final byte[] cells;
    /** Per column: the lowest trunk or root cell, which reaches down to the ground at placement (NO_FOOT elsewhere). */
    final short[] foot;
    /** Per column: the lowest and highest cell ever written (lowest > highest when the column is empty). */
    final short[] colLo, colHi;
    /** Every cell written as wood, and as leaf (may hold repeats and cells since overwritten: read the cell again). */
    private int[] woodCells = new int[4096], leafCells = new int[16384];
    private int woodCount, leafCount;

    public Blueprint(int extent, int minY, int maxY) {
        this.extent = extent;
        this.minY = minY;
        this.maxY = maxY;
        this.sx = 2 * extent + 1;
        this.sy = maxY - minY + 1;
        this.cells = new byte[sx * sx * sy];
        this.foot = new short[sx * sx];
        this.colLo = new short[sx * sx];
        this.colHi = new short[sx * sx];
        Arrays.fill(foot, (short) NO_FOOT);
        Arrays.fill(colLo, Short.MAX_VALUE);
        Arrays.fill(colHi, Short.MIN_VALUE);
    }

    public boolean inside(int x, int y, int z) {
        return x >= -extent && x <= extent && z >= -extent && z <= extent && y >= minY && y <= maxY;
    }

    int index(int x, int y, int z) {
        return ((y - minY) * sx + (x + extent)) * sx + (z + extent);
    }

    int column(int x, int z) {
        return (x + extent) * sx + (z + extent);
    }

    public byte get(int x, int y, int z) {
        return inside(x, y, z) ? cells[index(x, y, z)] : EMPTY;
    }

    public void set(int x, int y, int z, byte code) {
        if (inside(x, y, z)) write(index(x, y, z), column(x, z), y, code);
    }

    /** Sets a cell only where nothing is yet (leaves never overwrite wood). */
    public void fill(int x, int y, int z, byte code) {
        if (inside(x, y, z)) {
            int i = index(x, y, z);
            if (cells[i] == EMPTY) write(i, column(x, z), y, code);
        }
    }

    private void write(int i, int col, int y, byte code) {
        cells[i] = code;
        if (y < colLo[col]) colLo[col] = (short) y;
        if (y > colHi[col]) colHi[col] = (short) y;
        if (wood(code)) {
            if (woodCount == woodCells.length) woodCells = Arrays.copyOf(woodCells, woodCount * 2);
            woodCells[woodCount++] = i;
        } else if (code == LEAF || code == LEAF2) {
            if (leafCount == leafCells.length) leafCells = Arrays.copyOf(leafCells, leafCount * 2);
            leafCells[leafCount++] = i;
        }
    }

    static boolean wood(byte c) {
        return c >= LOG && c <= WOOD;
    }

    static boolean leaf(byte c) {
        return c == LEAF || c == LEAF2 || (c >= LEAF_D1 && c <= LEAF2_PERSISTENT);
    }

    /** Marks a trunk or root column: from its lowest cell the wood continues down to the ground. */
    public void ground(int x, int y, int z) {
        if (x < -extent || x > extent || z < -extent || z > extent) return;
        int c = column(x, z);
        if (foot[c] == NO_FOOT || y < foot[c]) foot[c] = (short) Math.max(minY, y);
    }

    // ---- shapes --------------------------------------------------------------------------------------------------

    /** A stable 0..1 value per cell (no RandomSource: the same whatever is built first). */
    public static double hash(long seed, int x, int y, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x8CB92BA72F3D8DD7L + z * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /** A tube from a to b, its radius going from ra to rb; thin ends are kept joined face to face by a walk. */
    public void tube(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, byte code) {
        double r = Math.max(ra, rb);
        int x0 = Math.max(-extent, (int) Math.floor(Math.min(ax, bx) - r - 1)), x1 = Math.min(extent, (int) Math.ceil(Math.max(ax, bx) + r + 1));
        int y0 = Math.max(minY, (int) Math.floor(Math.min(ay, by) - r - 1)), y1 = Math.min(maxY, (int) Math.ceil(Math.max(ay, by) + r + 1));
        int z0 = Math.max(-extent, (int) Math.floor(Math.min(az, bz) - r - 1)), z1 = Math.min(extent, (int) Math.ceil(Math.max(az, bz) + r + 1));
        double dx = bx - ax, dy = by - ay, dz = bz - az, len2 = dx * dx + dy * dy + dz * dz;
        double inv = len2 == 0 ? 0 : 1.0 / len2;
        for (int y = y0; y <= y1; y++) {
            double py = y + 0.5 - ay;
            int layer = (y - minY) * sx;
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5 - ax;
                int rowBase = (layer + x + extent) * sx + extent, colBase = (x + extent) * sx + extent;
                for (int z = z0; z <= z1; z++) {
                    double pz = z + 0.5 - az;
                    double t = Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) * inv));
                    double qx = px - t * dx, qy = py - t * dy, qz = pz - t * dz;
                    double rad = ra + (rb - ra) * t;
                    if (qx * qx + qy * qy + qz * qz <= rad * rad) write(rowBase + z, colBase + z, y, code);
                }
            }
        }
        if (Math.min(ra, rb) < 0.9) line((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az),
                (int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz), code);
    }

    /** Cells from a to b, one axis at a time, so the line stays joined face to face. */
    public void line(int ax, int ay, int az, int bx, int by, int bz, byte code) {
        int x = ax, y = ay, z = az, guard = 0;
        set(x, y, z, code);
        while ((x != bx || y != by || z != bz) && guard++ < 256) {
            int ex = Math.abs(bx - x), ey = Math.abs(by - y), ez = Math.abs(bz - z);
            if (ey >= ex && ey >= ez) y += Integer.signum(by - y);
            else if (ex >= ez) x += Integer.signum(bx - x);
            else z += Integer.signum(bz - z);
            set(x, y, z, code);
        }
    }

    /**
     * A canopy mass of leaves ({@code code}: {@link #LEAF} or {@link #LEAF2}): an ellipsoid (rx across, up above the
     * centre, down below it), its edge broken by the hash, hollow inside {@code hollow} (a share of the radius) so only a
     * shell is built. Rows are filled span by span; only the ragged rim is tested cell by cell.
     */
    public void cloud(long seed, double cx, double cy, double cz, double rx, double up, double down, double hollow, double ragged, byte code) {
        int x0 = Math.max(-extent, (int) Math.floor(cx - rx - 1)), x1 = Math.min(extent, (int) Math.ceil(cx + rx + 1));
        int y0 = Math.max(minY, (int) Math.floor(cy - down - 1)), y1 = Math.min(maxY, (int) Math.ceil(cy + up + 1));
        double h2 = hollow * hollow, inner = 1.0 - ragged * 0.5, outer = 1.0 + ragged * 0.5, irx = 1.0 / rx;
        for (int y = y0; y <= y1; y++) {
            double fy = (y + 0.5 - cy) / ((y + 0.5 >= cy) ? up : down);
            double fy2 = fy * fy;
            if (fy2 > outer) continue;
            int layer = (y - minY) * sx;
            for (int x = x0; x <= x1; x++) {
                double fx = (x + 0.5 - cx) * irx, exy = fx * fx + fy2;
                if (exy > outer) continue;
                // the row's reach: solid out to `in`, ragged from there to `out`, hollow inside `hole` (in z, from cz)
                double out = rx * Math.sqrt(outer - exy), in = exy < inner ? rx * Math.sqrt(inner - exy) : 0;
                double hole = exy < h2 ? rx * Math.sqrt(h2 - exy) : -1;
                int z0 = Math.max(-extent, (int) Math.floor(cz - out - 0.5)), z1 = Math.min(extent, (int) Math.ceil(cz + out - 0.5));
                int rowBase = (layer + x + extent) * sx + extent, colBase = (x + extent) * sx + extent;
                for (int z = z0; z <= z1; z++) {
                    double dz = Math.abs(z + 0.5 - cz);
                    if (dz > out || dz < hole) continue;
                    int i = rowBase + z;
                    if (cells[i] != EMPTY) continue;
                    if (dz > in) {
                        double fz = dz * irx;
                        if (exy + fz * fz > 1.0 + (hash(seed, x, y, z) - 0.5) * ragged) continue;
                    }
                    write(i, colBase + z, y, code);
                }
            }
        }
    }

    /** Clears leaves (never wood) in a vertical cylinder from {@code fromY} up: a shaft for the sky to reach the trunk. */
    public void shaft(double cx, double cz, double r, int fromY) {
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.ceil(cx + r), z0 = (int) Math.floor(cz - r), z1 = (int) Math.ceil(cz + r);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                if (dx * dx + dz * dz > r * r || x < -extent || x > extent || z < -extent || z > extent) continue;
                int col = column(x, z);
                for (int y = Math.max(fromY, colLo[col]); y <= colHi[col]; y++) {
                    int i = index(x, y, z);
                    if (cells[i] == LEAF || cells[i] == LEAF2) cells[i] = EMPTY;
                }
            }
        }
    }

    // ---- finishing -------------------------------------------------------------------------------------------------

    /**
     * Gives every leaf its distance to wood through other leaves (vanilla's decay rule), makes the ones further than
     * six persistent and removes the ones no wood reaches at all (they would float). Walks only the cells written.
     */
    /** Per worker thread: distances for the settling walk (zero between uses: each walk clears what it touched). */
    private static final ThreadLocal<byte[]> DIST = ThreadLocal.withInitial(() -> new byte[0]);

    public void settleLeaves() {
        byte[] dist = DIST.get(); // distance + 1; 0 = not reached
        if (dist.length < cells.length) DIST.set(dist = new byte[cells.length]);
        int[] queue = new int[woodCount + leafCount];
        int head = 0, tail = 0;
        for (int k = 0; k < woodCount; k++) {
            int i = woodCells[k];
            if (wood(cells[i]) && dist[i] == 0) {
                dist[i] = 1;
                queue[tail++] = i;
            }
        }
        int strideY = sx * sx;
        while (head < tail) {
            int i = queue[head++];
            int d = dist[i] + 1;
            if (d >= Byte.MAX_VALUE) continue;
            int z = i % sx, x = (i / sx) % sx, y = i / strideY;
            for (int k = 0; k < 6; k++) {
                int j;
                switch (k) {
                    case 0 -> j = x + 1 < sx ? i + sx : -1;
                    case 1 -> j = x > 0 ? i - sx : -1;
                    case 2 -> j = z + 1 < sx ? i + 1 : -1;
                    case 3 -> j = z > 0 ? i - 1 : -1;
                    case 4 -> j = y + 1 < sy ? i + strideY : -1;
                    default -> j = y > 0 ? i - strideY : -1;
                }
                if (j < 0 || dist[j] != 0 || (cells[j] != LEAF && cells[j] != LEAF2)) continue;
                dist[j] = (byte) d;
                queue[tail++] = j;
            }
        }
        for (int k = 0; k < leafCount; k++) {
            int i = leafCells[k];
            byte c = cells[i];
            if (c != LEAF && c != LEAF2) continue; // overwritten since, or already settled (a repeat)
            int d = dist[i] - 1;
            byte first = c == LEAF ? LEAF_D1 : LEAF2_D1, far = c == LEAF ? LEAF_PERSISTENT : LEAF2_PERSISTENT;
            cells[i] = d < 0 ? EMPTY : d <= 6 ? (byte) (first + d - 1) : far;
        }
        for (int k = 0; k < tail; k++) dist[queue[k]] = 0;
    }

    /** Strands hanging from the underside of leaves: {@code chance} per open leaf, lengths from {@code lo} to {@code hi}. */
    public void hangVines(long seed, double chance, int lo, int hi, java.util.function.IntUnaryOperator longerAt) {
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                int col = column(x, z);
                if (colLo[col] > colHi[col]) continue;
                int bottom = Math.max(minY + 1, colLo[col]); // read once: the vines hung below extend the column
                for (int y = colHi[col]; y >= bottom; y--) {
                    byte c = cells[index(x, y, z)];
                    if (!leaf(c) || get(x, y - 1, z) != EMPTY) continue;
                    if (hash(seed, x, y, z) >= chance) continue;
                    int len = lo + (int) (hash(seed + 1, x, y, z) * (hi - lo + 1)) + longerAt.applyAsInt(x * x + z * z);
                    int k = 0;
                    while (k < len && get(x, y - 1 - k, z) == EMPTY && y - 1 - k > minY) k++;
                    for (int i = 0; i < k; i++) set(x, y - 1 - i, z, i == k - 1 ? VINE_TIP : VINE);
                }
            }
        }
    }

    /** Kept for the callers: the used ranges are tracked as cells are written. Returns the cells in use. */
    public int seal() {
        int used = 0;
        for (int k = 0; k < woodCount; k++) if (cells[woodCells[k]] != EMPTY) used++;
        for (int k = 0; k < leafCount; k++) if (cells[leafCells[k]] != EMPTY) used++;
        return used;
    }

    /** The cells' bounds: minX, minY, minZ, maxX, maxY, maxZ relative to the base. */
    public int[] bounds() {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                int c = column(x, z);
                if (colLo[c] > colHi[c]) continue;
                b[0] = Math.min(b[0], x);
                b[2] = Math.min(b[2], z);
                b[3] = Math.max(b[3], x);
                b[5] = Math.max(b[5], z);
                b[1] = Math.min(b[1], colLo[c]);
                b[4] = Math.max(b[4], colHi[c]);
            }
        }
        return b;
    }
}
