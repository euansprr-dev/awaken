package dev.awaken.atlas.client;

import dev.awaken.atlas.Explored;

/**
 * What one slice of the diorama shows (SPEC §16.8), read from the whole square under each column, never one block of
 * it. A coarse column stands half way between its square's mean height and its top, so a wall or a clump of trees still
 * rises out of a field. Its top is painted from the slice's texture: a texel a block (two or four blocks at the two
 * coarsest levels), each lit on its own from the real heights under it (the west sun's cast shadows, slope shade,
 * ambient occlusion, a little grain on natural ground, the zones' washes and borders). A road, a stream or a roof a block
 * wide keeps its colour at every zoom, with its weight in the texel, and the mip chain carries it further out.
 * <p>
 * Worker thread, one instance. Plain arrays and no client classes, so a GameTest can read it on the server.
 */
public final class SliceSampler {
    static final int S = 64, LODS = 5;
    /** Texels across a slice's texture at most: a block a texel up to 4-block columns, then 2 and 4 blocks. */
    static final int MAX_TEX = 256;
    /** Texels read west of the slice for the shadows cast into it, and round the other sides for slopes and borders. */
    static final int PAD_W = 64, PAD = 5;
    /** Sun from the west, 40° up: a shadow line drops tan(40°) = 0.84 blocks per block east. */
    static final float SUN_DROP = 0.84f;
    static final float GRAIN = 0.009f;
    static final int CW = S + 2;

    /**
     * The level to draw at this many screen pixels per block: the coarsest whose column still covers 3.6 pixels (where
     * the finest level always began on a Retina screen), so a column is 3.6 to 7 pixels wide at every zoom, never more
     * columns on screen than before, and each step only halves; the texture carries the detail below a column.
     */
    static int lodFor(float physicalPxPerBlock) {
        float p = physicalPxPerBlock;
        return p >= 3.6f ? 0 : p >= 1.8f ? 1 : p >= 0.9f ? 2 : p >= 0.45f ? 3 : 4;
    }

    static int texels(int lod) {
        return Math.min(S << lod, MAX_TEX);
    }

    static int blocksPerTexel(int lod) {
        return (S << lod) / texels(lod);
    }

    private static final int GW_MAX = PAD_W + MAX_TEX + PAD, GH_MAX = MAX_TEX + 2 * PAD;
    // the texel grid: x in [-PAD_W, T + PAD), z in [-PAD, T + PAD)
    private final float[] hMean = new float[GW_MAX * GH_MAX];
    private final short[] hMax = new short[GW_MAX * GH_MAX];
    private final int[] colour = new int[GW_MAX * GH_MAX];
    private final byte[] info = new byte[GW_MAX * GH_MAX];
    private final boolean[] drawn = new boolean[GW_MAX * GH_MAX];
    private final int[] zone = new int[GW_MAX * GH_MAX];
    private int gw;

    // ---- the result -------------------------------------------------------------------------------------------------
    /** Blocks a column, texels across the texture, blocks a texel, texels a column. */
    int step, tex, bpt, cpt;
    /** The slice's lit top colours, ARGB, opaque everywhere (texels off the known land take their nearest neighbour's). */
    final int[] lit = new int[MAX_TEX * MAX_TEX];
    /** Columns -1..S each way ({@link #c}): height, drawn, material class. */
    final short[] ch = new short[CW * CW];
    final boolean[] cd = new boolean[CW * CW];
    final byte[] ck = new byte[CW * CW];
    /** The slice's own columns (z * S + x): the lit top colour across the column (for its sides), and whether it's lit. */
    final int[] cc = new int[S * S];
    final boolean[] cl = new boolean[S * S];

    static int c(int x, int z) {
        return (z + 1) * CW + x + 1;
    }

    private int g(int x, int z) {
        return (z + PAD) * gw + x + PAD_W;
    }

    /** Reads the slice at level {@code lod} whose corner is block (x0, z0); false when none of it is known land. */
    boolean sample(ColumnStore store, Explored explored, ZoneMap.View zones, int lod, int x0, int z0) {
        step = 1 << lod;
        tex = texels(lod);
        bpt = blocksPerTexel(lod);
        cpt = step / bpt;
        int T = tex, b = bpt;
        gw = PAD_W + T + PAD;
        // 1. every texel from the whole of its b × b blocks: mean and top height, mean colour, the highest block's class
        boolean any = false;
        ColumnStore.Tile tile = null;
        int lastTx = Integer.MIN_VALUE, lastTz = Integer.MIN_VALUE, lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        boolean cellKnown = false;
        for (int z = -PAD; z < T + PAD; z++) {
            int wz = z0 + z * b;
            for (int x = -PAD_W; x < T + PAD; x++) {
                int wx = x0 + x * b, k = g(x, z);
                int cx = wx >> 4, cz = wz >> 4;
                if (cx != lastCx || cz != lastCz) {
                    lastCx = cx;
                    lastCz = cz;
                    cellKnown = explored.explored(cx, cz);
                }
                drawn[k] = false;
                if (!cellKnown) continue;
                int tx = wx >> ColumnStore.TILE_SHIFT, tz = wz >> ColumnStore.TILE_SHIFT;
                if (tx != lastTx || tz != lastTz) {
                    lastTx = tx;
                    lastTz = tz;
                    tile = store.peek(tx, tz);
                }
                if (tile == null) continue;
                int n = 0, top = Integer.MIN_VALUE, hSum = 0, r = 0, gr = 0, bl = 0, depthSum = 0, wet = 0, topInfo = 0;
                boolean lamp = false;
                for (int dz = 0; dz < b; dz++) {
                    int row = ColumnStore.index(wx, wz + dz);
                    for (int dx = 0; dx < b; dx++) {
                        int i = row + dx, argb = tile.color[i];
                        if ((argb >>> 24) == 0) continue;
                        int h = tile.height[i], in = tile.info[i];
                        n++;
                        hSum += h;
                        r += (argb >> 16) & 255;
                        gr += (argb >> 8) & 255;
                        bl += argb & 255;
                        if (h > top) {
                            top = h;
                            topInfo = in;
                        }
                        if (((in >> 4) & 7) == ColumnStore.K_WATER) {
                            wet++;
                            depthSum += in & 15;
                        }
                        lamp |= (in & ColumnStore.LIT) != 0;
                    }
                }
                if (n == 0) continue;
                drawn[k] = true;
                hMean[k] = hSum / (float) n;
                hMax[k] = (short) top;
                colour[k] = 0xFF000000 | (r / n) << 16 | (gr / n) << 8 | (bl / n);
                int kind = (topInfo >> 4) & 7, depth = kind == ColumnStore.K_WATER && wet > 0 ? depthSum / wet : 0;
                info[k] = (byte) (depth | kind << 4 | (lamp ? ColumnStore.LIT : 0));
                zone[k] = zones == null || x < -3 ? 0 : zones.zone(wx + (b >> 1), wz + (b >> 1));
                if (x >= 0 && x < T && z >= 0 && z < T) any = true;
            }
        }
        if (!any) return false;
        // 2. light each texel: map colour × slope shade × occlusion × the west sun's cast shadow, then the zone
        int edge = Math.max(1, Math.min(2, (step >> 1) / b)); // a border about 2 px wide each side at any zoom
        float drop = SUN_DROP * b;
        for (int z = 0; z < T; z++) {
            float level = -1e9f;
            for (int x = -PAD_W; x < T; x++) {
                int k = g(x, z);
                if (x >= 0 && drawn[k]) {
                    float h = hMean[k];
                    int in = info[k], depth = in & 15, kind = (in >> 4) & 7;
                    float shade = 1f;
                    if (depth > 0) {
                        shade = Math.max(0.55f, 1.02f - depth * 0.035f);
                    } else {
                        int w = k - 1, n = k - gw;
                        if (drawn[w]) shade += clamp((h - hMean[w]) * 0.06f / b, -0.16f, 0.16f);
                        if (drawn[n]) shade += clamp((h - hMean[n]) * 0.04f / b, -0.10f, 0.10f);
                        // a little per-block grain on natural ground so it reads as voxels, not paint
                        if (kind == ColumnStore.K_SOIL || kind == ColumnStore.K_LEAVES || kind == ColumnStore.K_SAND)
                            shade += ((hash(x0 + x * b, z0 + z * b) & 7) - 3.5f) * GRAIN;
                    }
                    // ambient occlusion: ground hemmed in by taller neighbours (a forest floor, a lane between houses)
                    // sits darker, so dense canopy reads as clumps instead of noise
                    int hemmed = 0;
                    if (drawn[k + 1] && hMean[k + 1] - h >= 2) hemmed++;
                    if (drawn[k - 1] && hMean[k - 1] - h >= 2) hemmed++;
                    if (drawn[k + gw] && hMean[k + gw] - h >= 2) hemmed++;
                    if (drawn[k - gw] && hMean[k - gw] - h >= 2) hemmed++;
                    shade -= hemmed * 0.045f;
                    float shadow = clamp((level - h) * 0.5f, 0f, 1f);
                    shade *= 1f - 0.30f * shadow;
                    lit[z * T + x] = zoned(scale(colour[k], shade), k, edge, zones, x0 + x * b + (b >> 1), z0 + z * b + (b >> 1));
                }
                if (drawn[k]) level = Math.max(level, hMax[k]);
                level -= drop;
            }
        }
        fillGaps(T);
        // 3. the columns: drawn where any texel is, half way between the square's mean and its top
        for (int cz = -1; cz <= S; cz++) {
            for (int cx = -1; cx <= S; cx++) {
                int ci = c(cx, cz), n = 0, top = Integer.MIN_VALUE, kind = 0;
                float sum = 0f;
                for (int dz = 0; dz < cpt; dz++)
                    for (int dx = 0; dx < cpt; dx++) {
                        int k = g(cx * cpt + dx, cz * cpt + dz);
                        if (!drawn[k]) continue;
                        n++;
                        sum += hMean[k];
                        if (hMax[k] > top) {
                            top = hMax[k];
                            kind = (info[k] >> 4) & 7;
                        }
                    }
                cd[ci] = n > 0;
                if (n == 0) continue;
                ch[ci] = (short) Math.floor(0.5f * (sum / n) + 0.5f * top + 0.5f);
                ck[ci] = (byte) kind;
                if (cx < 0 || cz < 0 || cx >= S || cz >= S) continue;
                int r = 0, gr = 0, bl = 0, m = 0;
                boolean lamp = false;
                for (int dz = 0; dz < cpt; dz++)
                    for (int dx = 0; dx < cpt; dx++) {
                        int tx = cx * cpt + dx, tz = cz * cpt + dz, k = g(tx, tz);
                        if (!drawn[k]) continue;
                        int v = lit[tz * T + tx];
                        r += (v >> 16) & 255;
                        gr += (v >> 8) & 255;
                        bl += v & 255;
                        m++;
                        lamp |= (info[k] & ColumnStore.LIT) != 0;
                    }
                cc[cz * S + cx] = 0xFF000000 | (r / m) << 16 | (gr / m) << 8 | (bl / m);
                cl[cz * S + cx] = lamp;
            }
        }
        return true;
    }

    /**
     * The zone's colour over a texel: a wash of its colour over the land, and where a known texel within {@code edge}
     * belongs to another zone, a bright line of it, so each side of a boundary carries its own colour. A region's colour
     * follows its land level smoothly, blended between cells (SPEC §21.11), read at the texel's middle block.
     */
    private int zoned(int c, int k, int edge, ZoneMap.View zones, int bx, int bz) {
        int zn = zone[k];
        if (zn == 0 || zones == null) return c;
        float level = zn < ZoneMap.DISTRICT ? zones.level(bx, bz) : 0f;
        int zc = level > 0 ? zones.smoothColour(level) : zones.colour(zn);
        if (zc == 0) return c;
        for (int d = 1; d <= edge; d++)
            if (differs(k + d, zn) || differs(k - d, zn) || differs(k + d * gw, zn) || differs(k - d * gw, zn))
                return mix(c, mix(zc, 0xFFFFFFFF, 0.50f), 0.90f);
        return mix(c, zc, level > 0 ? 0.36f : 0.30f);
    }

    private boolean differs(int n, int zn) {
        return drawn[n] && zone[n] != zn && zone[n] != 0;
    }

    /**
     * Texels off the known land take the colour of the nearest known texel in their row (else their column), so the
     * mips never bleed a dark or see-through rim into the land's edge.
     */
    private void fillGaps(int T) {
        boolean[] rowHas = new boolean[T];
        for (int z = 0; z < T; z++) {
            int last = -1;
            for (int x = 0; x < T; x++) {
                if (drawn[g(x, z)]) {
                    if (last < 0) for (int f = 0; f < x; f++) lit[z * T + f] = lit[z * T + x];
                    last = x;
                } else if (last >= 0) lit[z * T + x] = lit[z * T + last];
            }
            rowHas[z] = last >= 0;
        }
        int lastRow = -1;
        for (int z = 0; z < T; z++) {
            if (rowHas[z]) {
                if (lastRow < 0) for (int f = 0; f < z; f++) System.arraycopy(lit, z * T, lit, f * T, T);
                lastRow = z;
            } else if (lastRow >= 0) System.arraycopy(lit, lastRow * T, lit, z * T, T);
        }
    }

    // ---- colour helpers (shared with the mesh, the zones and the clouds) -------------------------------------------

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((argb & 255) * f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static int hash(int x, int z) {
        int h = x * 0x27d4eb2d ^ z * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        return h ^ (h >>> 13);
    }

    // ---- the test (GameTest atlas_coarse_columns) ---------------------------------------------------------------------

    /**
     * A field with a road a block wide and a wall a block thick, read at the coarsest level: the road keeps its colour in
     * its texel with a quarter's weight, the wall's column still rises, and the finest level is the blocks exactly.
     * Returns null when it holds, else what failed.
     */
    public static String selfTest() {
        if (lodFor(4f) != 0 || lodFor(2f) != 1 || lodFor(1f) != 2 || lodFor(0.5f) != 3 || lodFor(0.2f) != 4)
            return "levels by screen pixels: 4, 2, 1, 0.5, 0.2 px a block → 0..4";
        int grass = 0xFF5FA040, road = 0xFF8A6A3A, stone = 0xFF707070;
        ColumnStore store = new ColumnStore();
        ColumnStore.Tile t = new ColumnStore.Tile(0, 0);
        for (int z = 0; z < 256; z++)
            for (int x = 0; x < 256; x++) {
                int i = ColumnStore.index(x, z);
                boolean wall = z == 100, path = x == 37;
                t.height[i] = (short) (wall ? 74 : 64);
                t.color[i] = wall ? stone : path ? road : grass;
                t.info[i] = (byte) ((wall ? ColumnStore.K_STONE : ColumnStore.K_SOIL) << 4);
            }
        store.adopt(t);
        Explored ex = new Explored();
        for (int cz = 0; cz < 16; cz++) for (int cx = 0; cx < 16; cx++) ex.mark(cx, cz);
        SliceSampler s = new SliceSampler();
        if (!s.sample(store, ex, null, 4, 0, 0)) return "the slice holds known land";
        // the road runs through texel column 9 (blocks 36..39); compare it with its neighbours two texels away, down a run
        // of rows clear of the wall, so the grain averages out
        float onRoad = 0f, off = 0f;
        for (int z = 2; z < 22; z++) {
            onRoad += (s.lit[z * s.tex + 9] >> 16) & 255;
            off += ((s.lit[z * s.tex + 7] >> 16) & 255) * 0.5f + ((s.lit[z * s.tex + 11] >> 16) & 255) * 0.5f;
        }
        float lift = (onRoad - off) / 20f, want = (((road >> 16) & 255) - ((grass >> 16) & 255)) * 0.25f;
        if (Math.abs(lift - want) > 2.5f) return "the road keeps a quarter of its texel: red +" + lift + ", want +" + want;
        int wallCol = c(2, 6); // blocks 32..47 × 96..111: one row of the wall in it
        if (!s.cd[wallCol] || s.ch[wallCol] <= 66 || s.ch[wallCol] >= 74) return "the wall's column rises, was " + s.ch[wallCol];
        if (s.ch[c(2, 2)] != 64) return "a flat field's column stays at its height, was " + s.ch[c(2, 2)];
        if (!s.sample(store, ex, null, 0, 0, 64)) return "the finest slice holds known land";
        if (s.ch[c(37, 100 - 64)] != 74 || s.ch[c(36, 0)] != 64 || s.lit[(100 - 64) * s.tex + 37] == s.lit[0])
            return "the finest level is the blocks exactly";
        return null;
    }
}
