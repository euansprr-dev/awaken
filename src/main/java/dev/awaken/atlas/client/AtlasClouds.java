package dev.awaken.atlas.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.atlas.Explored;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

/**
 * Clouds over everything the player hasn't explored (SPEC §16.8), never a black fog of war. Each cloud cell carries one
 * or two soft puffs from a sheet of three generated sprites (sunlit crown, blue-grey belly), billboarded on the CPU
 * every frame, sorted far to near, the whole blanket drifting on one slow wind and breathing a little, each puff casting
 * a soft shadow on known land. The cells follow the zoom continuously ({@link CloudLattice}): zooming out, puffs glide
 * together and merge into bigger ones, zooming in they split, so the blanket keeps its density on screen and never
 * changes all at once. At every zoom the bank reaches over the last stretch of known land, and a flat cloud floor
 * ({@link #sea}) lies under it all, so the land's edge goes under the clouds and never shows a dark gap. Cells that just
 * cleared thin out over a second, and a bell's reveal ripples out from the tower.
 */
final class AtlasClouds {
    /** Cells either side of the centre at a lattice's own zoom (the blanket's rim is 0.84 R cells out). */
    static final int R = 20;
    /** The cover fields reach this many cells either side: a lattice half way to its parent spans twice its rim. */
    static final int FR = 37, W = 2 * FR + 3;
    static final int MAX_PUFFS = 8192;
    /** Puffs, and a shadow under each puff that hangs over explored land. */
    static final int MAX_QUADS = MAX_PUFFS * 2;
    static final int VERTEX = 24;                     // POSITION_TEX_COLOR
    static final int VARIANTS = 3;
    private static final float FADE_NANOS = 1.0e9f;

    private GpuTexture texture;
    private GpuTextureView view;

    /** One level's cover: 1 where a cell holds no known land, 0 where it holds any; rebuilt when the record or centre moves. */
    private static final class Field {
        final float[] unknown = new float[W * W];
        final long[] reveal = new long[W * W];
        int version = -1, cx = Integer.MIN_VALUE, cz;
    }

    private final Field[] fields = new Field[CloudLattice.LEVELS];
    {
        for (int k = 0; k < fields.length; k++) fields[k] = new Field();
    }
    /** The wind's offset in blocks, added up frame by frame at the zoom's own speed, so a zoom never jumps the blanket. */
    private double windX, windZ;
    private float lastTime = Float.NaN;
    private final double[] at = new double[3];

    // per-frame scratch
    private final float[] px = new float[MAX_PUFFS], py = new float[MAX_PUFFS], pz = new float[MAX_PUFFS];
    private final float[] ps = new float[MAX_PUFFS], pa = new float[MAX_PUFFS];
    private final long[] order = new long[MAX_PUFFS];
    private final byte[] look = new byte[MAX_PUFFS];
    private final Vector3f v = new Vector3f();
    private final float[] sx = new float[MAX_PUFFS], sy = new float[MAX_PUFFS], sz = new float[MAX_PUFFS], sw = new float[MAX_PUFFS], sa = new float[MAX_PUFFS];
    private int shadowCount;

    GpuTextureView texture() {
        if (view == null) {
            GpuDevice device = RenderSystem.getDevice();
            try (NativeImage img = sheet()) {
                texture = device.createTexture(() -> "Atlas cloud puffs", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        GpuFormat.RGBA8_UNORM, img.getWidth(), img.getHeight(), 1, 1);
                view = device.createTextureView(texture);
                device.createCommandEncoder().writeToTexture(texture, img);
            }
        }
        return view;
    }

    /** Three puffs side by side, each 128×96: overlapping soft blobs, a warm sunlit crown, a blue-grey belly. */
    private static NativeImage sheet() {
        int w = 128, h = 96;
        NativeImage img = new NativeImage(w * VARIANTS, h, false);
        float[][][] shapes = {
                {{64, 54, 40, 0.95f}, {40, 60, 28, 0.9f}, {90, 60, 30, 0.9f}, {60, 38, 26, 0.85f}, {82, 44, 20, 0.8f}},
                {{56, 56, 34, 0.95f}, {86, 52, 36, 0.92f}, {34, 64, 22, 0.85f}, {72, 34, 24, 0.85f}},
                {{64, 58, 42, 0.95f}, {32, 62, 24, 0.85f}, {98, 64, 24, 0.85f}, {50, 40, 20, 0.8f}, {78, 34, 22, 0.85f}, {64, 28, 14, 0.7f}},
        };
        for (int k = 0; k < VARIANTS; k++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float keep = 1f; // product of (1 - alpha) over the blobs: "over" compositing
                    float crown = 0f;
                    for (float[] b : shapes[k]) {
                        float dx = x + 0.5f - b[0], dy = y + 0.5f - (b[1] - b[2] * 0.08f);
                        float d = (float) Math.sqrt(dx * dx + dy * dy) / b[2];
                        float a = d < 0.6f ? b[3] * (1f - d / 0.6f * 0.25f) : d < 1f ? b[3] * 0.75f * (1f - (d - 0.6f) / 0.4f) : 0f;
                        keep *= 1f - a;
                        // sunlit rim: the upper-left edge of each blob, where the low west sun catches it
                        if (d > 0.55f && d < 1f && dy < 0 && dx < b[2] * 0.3f) crown = Math.max(crown, (1f - Math.abs(d - 0.8f) / 0.25f) * 0.5f);
                    }
                    float alpha = 1f - keep;
                    float under = smooth((y - 34f) / 58f) * 0.55f;
                    float r = 255 * (1 - under) + 70 * under, g = 255 * (1 - under) + 95 * under, bl = 255 * (1 - under) + 135 * under;
                    r += 20 * crown;
                    g += 10 * crown;
                    bl -= 18 * crown;
                    img.setPixel(k * w + x, y, ((int) (alpha * 255) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl));
                }
            }
        }
        return img;
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** The wind, blocks a second at the finest level (scaled with the cloud cell when zoomed out): slow, from the west. */
    static final float WIND_X = 1.8f, WIND_Z = 0.55f;

    /** Level k's cover round centre cell (ccx, ccz): a cell is cloud when none of it is known (counted exactly). */
    private static void refresh(Field f, Explored explored, Long2LongOpenHashMap revealedAt, int k, int ccx, int ccz) {
        if (explored.version == f.version && ccx == f.cx && ccz == f.cz) return;
        f.version = explored.version;
        f.cx = ccx;
        f.cz = ccz;
        int span = 1 << k;
        for (int j = 0; j < W; j++)
            for (int i = 0; i < W; i++) {
                int cx = ccx + i - FR - 1, cz = ccz + j - FR - 1, n = j * W + i;
                boolean known = k == 0 ? explored.explored(cx, cz) : explored.countIn(cx << k, cz << k, span) > 0;
                f.unknown[n] = known ? 0f : 1f;
                f.reveal[n] = k == 0 && known ? revealedAt.getOrDefault(Explored.key(cx, cz), 0L) : 0L;
            }
    }

    /**
     * How unexplored the land under a point is at one level, 0..1, blended between cell centres (so a drifting puff
     * thins smoothly, never in a step), with freshly cleared cells still fading.
     */
    private static float cover(Field f, double x, double z, int cell, long now) {
        double fx = x / cell - 0.5 - (f.cx - FR - 1), fz = z / cell - 0.5 - (f.cz - FR - 1);
        int i0 = (int) Math.floor(fx), j0 = (int) Math.floor(fz);
        float tx = (float) (fx - i0), tz = (float) (fz - j0);
        float c00 = u(f, i0, j0, now), c10 = u(f, i0 + 1, j0, now), c01 = u(f, i0, j0 + 1, now), c11 = u(f, i0 + 1, j0 + 1, now);
        return (c00 * (1 - tx) + c10 * tx) * (1 - tz) + (c01 * (1 - tx) + c11 * tx) * tz;
    }

    private static float u(Field f, int i, int j, long now) {
        int k = Math.clamp(j, 0, W - 1) * W + Math.clamp(i, 0, W - 1);
        float u = f.unknown[k];
        if (f.reveal[k] != 0L) u = Math.max(u, 1f - (now - f.reveal[k]) / FADE_NANOS);
        return u;
    }

    /** The blanket's rim, blocks from the centre, at the zoom's continuous level: it thins out over {@link #rimSoft}. */
    static float rim(float level) {
        return R * 16f * (float) Math.pow(2, level) * 0.62f;
    }

    static float rimSoft(float level) {
        return R * 16f * (float) Math.pow(2, level) * 0.22f;
    }

    /** How much of the blanket is left this far from the centre: whole inside the rim, gone past its soft edge. */
    static float rimFade(float dist, float level) {
        return 1f - smooth((dist - rim(level)) / rimSoft(level));
    }

    static final int SEA_SEGMENTS = 64, SEA_BANDS = 5, SEA_QUADS = SEA_SEGMENTS * SEA_BANDS;
    private static final float[] SEA_RING = {0f, 1f, 1.25f, 1.5f, 1.75f, 2f}; // 0, the rim, then quarters of its soft edge

    /**
     * (SPEC §16.8) The cloud floor: a flat disc of the sea's colour ({@link SliceSampler#SEA}) under the land, as wide as
     * the blanket and thinning with it, so wherever no land is drawn (unknown ground, a gap between puffs, under the
     * land's edge) the table shows cloud, never a dark void. Written as POSITION_COLOR quads (16 bytes a vertex) at the
     * view centre's height; returns the quad count.
     */
    int sea(long dst, Matrix4f base, float level) {
        float rim = rim(level), soft = rimSoft(level);
        int r = (SliceSampler.SEA >> 16) & 255, g = (SliceSampler.SEA >> 8) & 255, b = SliceSampler.SEA & 255;
        long p = dst;
        for (int band = 0; band < SEA_BANDS; band++) {
            float r0 = ring(band, rim, soft), r1 = ring(band + 1, rim, soft);
            int a0 = Math.round(255 * rimFade(r0, level)), a1 = Math.round(255 * rimFade(r1, level));
            for (int k = 0; k < SEA_SEGMENTS; k++) {
                double t0 = k * Math.PI * 2 / SEA_SEGMENTS, t1 = (k + 1) * Math.PI * 2 / SEA_SEGMENTS;
                float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
                p = seaVtx(p, base, r0 * c0, r0 * s0, r, g, b, a0);
                p = seaVtx(p, base, r1 * c0, r1 * s0, r, g, b, a1);
                p = seaVtx(p, base, r1 * c1, r1 * s1, r, g, b, a1);
                p = seaVtx(p, base, r0 * c1, r0 * s1, r, g, b, a0);
            }
        }
        return SEA_QUADS;
    }

    private static float ring(int i, float rim, float soft) {
        return i == 0 ? 0f : rim + (SEA_RING[i] - 1f) * soft;
    }

    private long seaVtx(long p, Matrix4f base, float x, float z, int r, int g, int b, int a) {
        base.transformPosition(x, 0f, z, v);
        MemoryUtil.memPutFloat(p, v.x);
        MemoryUtil.memPutFloat(p + 4, v.y);
        MemoryUtil.memPutFloat(p + 8, v.z);
        MemoryUtil.memPutByte(p + 12, (byte) r);
        MemoryUtil.memPutByte(p + 13, (byte) g);
        MemoryUtil.memPutByte(p + 14, (byte) b);
        MemoryUtil.memPutByte(p + 15, (byte) a);
        return p + 16;
    }

    /**
     * Writes this frame's shadows and puffs as view-space quads into {@code dst} (an address), shadows first, puffs far
     * to near; returns the quad count. {@code base} maps centre-relative world blocks to view pixels; {@code level} is
     * the zoom's continuous level ({@link CloudLattice#level}).
     */
    int write(long dst, Explored explored, Long2LongOpenHashMap revealedAt, ColumnStore store, Matrix4f base, float pxPerBlock, float tiltDeg,
              float level, double cx, double cy, double cz, float time, float night, long now) {
        shadowCount = 0;
        int lv = Math.min((int) level, CloudLattice.LEVELS - 1);
        float t = lv == CloudLattice.LEVELS - 1 ? 0f : level - lv;
        float scaleNow = (float) Math.pow(2, level); // the zoom's own cell, in finest cells
        // the wind: one offset for every lattice, so a puff and its parent drift together, and a zoom never jumps it
        float dt = Float.isNaN(lastTime) ? 0f : Math.clamp(time - lastTime, 0f, 0.1f);
        lastTime = time;
        windX += WIND_X * scaleNow * dt;
        windZ += WIND_Z * scaleNow * dt;
        int cell = CloudLattice.cell(lv);
        Field fine = fields[lv], coarse = fields[Math.min(lv + 1, CloudLattice.LEVELS - 1)];
        refresh(fine, explored, revealedAt, lv, Math.floorDiv((int) Math.floor(cx), cell), Math.floorDiv((int) Math.floor(cz), cell));
        if (t > 0f)
            refresh(coarse, explored, revealedAt, lv + 1, Math.floorDiv((int) Math.floor(cx), 2 * cell), Math.floorDiv((int) Math.floor(cz), 2 * cell));
        int n = 0;
        // each puff is placed where its cell's ground is on screen and lifted ~30 blocks only in depth: drawn at real
        // height, the tilt would slide the blanket north of the cells it covers and open dark gaps on the north rim
        float liftPerScale = pxPerBlock * (float) Math.sin(Math.toRadians(tiltDeg));
        float rim = rim(level), rimSoft = rimSoft(level);
        int reach = (int) Math.ceil((rim + rimSoft) / cell) + 1;
        int li0 = (int) Math.floor((cx - windX) / cell), lj0 = (int) Math.floor((cz - windZ) / cell);
        for (int j = -reach; j <= reach; j++) {
            for (int i = -reach; i <= reach; i++) {
                int li = li0 + i, lj = lj0 + j;
                float left = CloudLattice.place(li, lj, lv, t, windX, windZ, at);
                if (left <= 0.001f) continue;
                double pxw = at[0], pzw = at[1];
                float s = (float) at[2];
                float wx = (float) (pxw - cx), wz = (float) (pzw - cz);
                float dist = (float) Math.sqrt(wx * wx + wz * wz);
                float edge = rimFade(dist, level); // the blanket thins out at the table's rim
                if (edge <= 0f) continue;
                // its cover where it is now, blended from its own lattice's to its parent's over the step
                float f = cover(fine, pxw, pzw, cell, now);
                if (t > 0f) f += (cover(coarse, pxw, pzw, 2 * cell, now) - f) * t;
                float a = CloudLattice.opacity(f) * edge * left;
                if (a <= 0.01f) continue;
                int hsh = CloudLattice.hash(li, lj, lv);
                float r1 = (hsh & 255) / 255f, r2 = ((hsh >> 8) & 255) / 255f, r3 = ((hsh >> 16) & 255) / 255f, ph = r3 * 6.28f;
                int variant = (hsh >>> 24) % VARIANTS;
                boolean flip = (hsh & 0x800000) != 0;
                // a slow breath, each puff on its own long phase
                float breathe = 1f + 0.05f * (float) Math.sin(time * 0.3f + ph);
                float size = (34f + 26f * r1) * (0.6f + 0.4f * a) * s * breathe;
                // its shadow, on explored ground only: a little east and south of it (the sun is low in the west)
                float shx = wx + 10f * s, shz = wz + 4f * s;
                double gx = cx + shx, gz = cz + shz;
                if (shadowCount < MAX_PUFFS && explored.explored((int) Math.floor(gx) >> 4, (int) Math.floor(gz) >> 4)) {
                    base.transformPosition(shx, (float) (store.groundY(gx, gz, cy) - cy), shz, v);
                    sx[shadowCount] = v.x;
                    sy[shadowCount] = v.y;
                    sz[shadowCount] = v.z + 8f * pxPerBlock;
                    sw[shadowCount] = size * 1.15f;
                    sa[shadowCount] = a * (1f - night * 0.6f);
                    shadowCount++;
                }
                int tone = (hsh >>> 4) & 7;
                float lift = liftPerScale * s;
                n = add(n, base, wx, 0f, wz, (30f + (r2 - 0.5f) * 6f) * lift, size, a, variant, flip, tone);
                if (r3 > 0.45f) // a smaller cap on top for volume, nudged up the screen, a touch brighter
                    n = add(n, base, wx + (r2 - 0.5f) * 10f * s, (5f + r1 * 3f) * s, wz - 3f * s, (37f + r1 * 4f) * lift, size * 0.62f, a * 0.95f,
                            (variant + 1) % VARIANTS, !flip, Math.max(0, tone - 3));
            }
        }
        for (int k = 0; k < n; k++) {
            int bits = Float.floatToIntBits(pz[k]);
            int sortable = bits ^ ((bits >> 31) & 0x7FFFFFFF);
            order[k] = ((long) sortable << 32) | k;
        }
        Arrays.sort(order, 0, n);
        // night: moonlit blue-grey, a little thinner so the lit city shows through the rim
        float tr = 1f + (0.62f - 1f) * night, tg = 1f + (0.70f - 1f) * night, tb = 1f + (0.86f - 1f) * night;
        int r = (int) (tr * 255), g = (int) (tg * 255), b = (int) (tb * 255);
        float du = 1f / VARIANTS;
        float tone0 = 1f;
        long p = dst;
        float squash = 0.75f * (float) Math.sin(Math.toRadians(tiltDeg));
        for (int k = 0; k < shadowCount; k++) { // lying on the ground: as flat as the table is tilted
            float hw = sw[k] * pxPerBlock * 0.5f, hh = hw * squash;
            int alpha = Math.min(255, (int) (sa[k] * 70));
            p = vtx(p, sx[k] - hw, sy[k] - hh, sz[k], 0, 0, 12, 22, 44, alpha);
            p = vtx(p, sx[k] + hw, sy[k] - hh, sz[k], du, 0, 12, 22, 44, alpha);
            p = vtx(p, sx[k] + hw, sy[k] + hh, sz[k], du, 1, 12, 22, 44, alpha);
            p = vtx(p, sx[k] - hw, sy[k] + hh, sz[k], 0, 1, 12, 22, 44, alpha);
        }
        for (int o = 0; o < n; o++) {
            int k = (int) order[o];
            float hw = ps[k] * pxPerBlock * 0.5f, hh = hw * 0.75f, x = px[k], y = py[k], z = pz[k];
            int alpha = Math.min(255, (int) (pa[k] * 238));
            int vv = look[k] & 3;
            // a little tone per puff (lower and bigger ones greyer), so the blanket has depth instead of one white
            float tone = tone0 - ((look[k] >> 3) & 7) * 0.022f;
            int rr = (int) (r * tone), gg = (int) (g * tone), bb2 = (int) (b * Math.min(1f, tone + 0.03f));
            float u0 = vv * du, u1 = u0 + du;
            if ((look[k] & 4) != 0) {
                float swap = u0;
                u0 = u1;
                u1 = swap;
            }
            p = vtx(p, x - hw, y - hh, z, u0, 0, rr, gg, bb2, alpha);
            p = vtx(p, x + hw, y - hh, z, u1, 0, rr, gg, bb2, alpha);
            p = vtx(p, x + hw, y + hh, z, u1, 1, rr, gg, bb2, alpha);
            p = vtx(p, x - hw, y + hh, z, u0, 1, rr, gg, bb2, alpha);
        }
        return n + shadowCount;
    }

    /** How many of the last {@link #write}'s quads were shadows (they come first). */
    int shadows() {
        return shadowCount;
    }

    /**
     * {@code tone} 0..7 comes from the cell's hash, never from where the puff is drawn: taken from its drifting position
     * it changed every redraw and the whole blanket flickered.
     */
    private int add(int n, Matrix4f base, float x, float y, float z, float depthLift, float size, float a, int variant, boolean flip, int tone) {
        if (n >= MAX_PUFFS) return n;
        base.transformPosition(x, y, z, v);
        px[n] = v.x;
        py[n] = v.y;
        pz[n] = v.z + depthLift;
        ps[n] = size;
        pa[n] = a;
        look[n] = (byte) (variant | (flip ? 4 : 0) | (tone << 3));
        return n + 1;
    }

    private static long vtx(long p, float x, float y, float z, float u, float vv, int r, int g, int b, int a) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, z);
        MemoryUtil.memPutFloat(p + 12, u);
        MemoryUtil.memPutFloat(p + 16, vv);
        MemoryUtil.memPutByte(p + 20, (byte) r);
        MemoryUtil.memPutByte(p + 21, (byte) g);
        MemoryUtil.memPutByte(p + 22, (byte) b);
        MemoryUtil.memPutByte(p + 23, (byte) a);
        return p + VERTEX;
    }

    void close() {
        if (view != null) view.close();
        if (texture != null) texture.close();
        view = null;
        texture = null;
    }
}
