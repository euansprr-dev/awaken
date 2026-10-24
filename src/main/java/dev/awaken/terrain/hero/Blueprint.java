package dev.awaken.terrain.hero;

import java.util.Arrays;

/**
 * A hero tree as a dense voxel grid round its base (x and z from the trunk's centre, y from the ground at the centre),
 * built once from the structure's seed and kept while the chunks it spans generate. Building it never looks at the
 * world: every random choice comes from the seed, so every chunk sees the same tree. The world only decides, column
 * by column, where the ground is ({@link #foot}: trunk and root columns reach down to it) and what may be replaced.
 */
public final class Blueprint {
    // cell codes (states per tree: HeroTree#state)
    public static final byte EMPTY = 0, LOG = 1, LOG_X = 2, LOG_Z = 3, WOOD = 4, LEAF = 5, VINE = 6, VINE_TIP = 7, MOSS = 8;
    /** Leaves after {@link #settleLeaves()}: distance 1..6 to wood ({@code LEAF_D1 + d - 1}), or too far (persistent). */
    public static final byte LEAF_D1 = 10, LEAF_PERSISTENT = 16;
    public static final int NO_FOOT = Short.MIN_VALUE;

    public final int extent, minY, maxY, sx, sy;
    final byte[] cells;
    /** Per column: the lowest trunk or root cell, which reaches down to the ground at placement (NO_FOOT elsewhere). */
    final short[] foot;
    /** Per column: the lowest and highest cell in use (lowest > highest when the column is empty). */
    final short[] colLo, colHi;

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
        if (inside(x, y, z)) cells[index(x, y, z)] = code;
    }

    /** Sets a cell only where nothing is yet (leaves never overwrite wood). */
    public void fill(int x, int y, int z, byte code) {
        if (inside(x, y, z)) {
            int i = index(x, y, z);
            if (cells[i] == EMPTY) cells[i] = code;
        }
    }

    static boolean wood(byte c) {
        return c >= LOG && c <= WOOD;
    }

    static boolean leaf(byte c) {
        return c == LEAF || (c >= LEAF_D1 && c <= LEAF_PERSISTENT);
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
        int x0 = (int) Math.floor(Math.min(ax, bx) - r - 1), x1 = (int) Math.ceil(Math.max(ax, bx) + r + 1);
        int y0 = (int) Math.floor(Math.min(ay, by) - r - 1), y1 = (int) Math.ceil(Math.max(ay, by) + r + 1);
        int z0 = (int) Math.floor(Math.min(az, bz) - r - 1), z1 = (int) Math.ceil(Math.max(az, bz) + r + 1);
        double dx = bx - ax, dy = by - ay, dz = bz - az, len2 = dx * dx + dy * dy + dz * dz;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double px = x + 0.5 - ax, py = y + 0.5 - ay, pz = z + 0.5 - az;
                    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / len2));
                    double qx = px - t * dx, qy = py - t * dy, qz = pz - t * dz;
                    double rad = ra + (rb - ra) * t;
                    if (qx * qx + qy * qy + qz * qz <= rad * rad) set(x, y, z, code);
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
     * A canopy mass: an ellipsoid of leaves (rx across, up above the centre, down below it), its edge broken by the
     * hash, hollow inside {@code hollow} (a share of the radius) so only a shell is built.
     */
    public void cloud(long seed, double cx, double cy, double cz, double rx, double up, double down, double hollow, double ragged) {
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - down - 1), y1 = (int) Math.ceil(cy + up + 1);
        int z0 = (int) Math.floor(cz - rx - 1), z1 = (int) Math.ceil(cz + rx + 1);
        double h2 = hollow * hollow;
        for (int y = y0; y <= y1; y++) {
            double fy = (y + 0.5 - cy) / ((y + 0.5 >= cy) ? up : down);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double fx = (x + 0.5 - cx) / rx, fz = (z + 0.5 - cz) / rx;
                    double e = fx * fx + fy * fy + fz * fz;
                    if (e > 1.0 + (hash(seed, x, y, z) - 0.5) * ragged || e < h2) continue;
                    fill(x, y, z, LEAF);
                }
            }
        }
    }

    // ---- finishing -------------------------------------------------------------------------------------------------

    /**
     * Gives every leaf its distance to wood through other leaves (vanilla's decay rule), makes the ones further than
     * six persistent and removes the ones no wood reaches at all (they would float).
     */
    public void settleLeaves() {
        int n = cells.length;
        int[] queue = new int[n];
        byte[] dist = new byte[n];
        Arrays.fill(dist, Byte.MAX_VALUE);
        int head = 0, tail = 0;
        for (int i = 0; i < n; i++) {
            if (wood(cells[i])) {
                dist[i] = 0;
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
                if (j < 0 || cells[j] != LEAF || dist[j] <= d) continue;
                dist[j] = (byte) d;
                queue[tail++] = j;
            }
        }
        for (int i = 0; i < n; i++) {
            if (cells[i] != LEAF) continue;
            int d = dist[i];
            cells[i] = d == Byte.MAX_VALUE ? EMPTY : d <= 6 ? (byte) (LEAF_D1 + d - 1) : LEAF_PERSISTENT;
        }
    }

    /** Strands hanging from the underside of leaves: {@code chance} per open leaf, lengths from {@code lo} to {@code hi}. */
    public void hangVines(long seed, double chance, int lo, int hi, java.util.function.IntUnaryOperator longerAt) {
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                for (int y = maxY; y > minY; y--) {
                    byte c = get(x, y, z);
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

    /** Records each column's used range, so placement skips empty air quickly; returns the number of cells in use. */
    public int seal() {
        int used = 0;
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                int c = column(x, z);
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int y = minY; y <= maxY; y++) {
                    if (cells[index(x, y, z)] != EMPTY) {
                        lo = Math.min(lo, y);
                        hi = y;
                        used++;
                    }
                }
                colLo[c] = (short) (lo == Integer.MAX_VALUE ? 1 : lo);
                colHi[c] = (short) (lo == Integer.MAX_VALUE ? 0 : hi);
            }
        }
        return used;
    }

    /** The cells' actual bounds (after {@link #seal()}): minX, minY, minZ, maxX, maxY, maxZ relative to the base. */
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
