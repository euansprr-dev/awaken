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
 * every frame, sorted far to near, drifting slowly. A cell is 16 blocks at the finest level and 64 or 256 when zoomed
 * out, so the blanket keeps the same density on screen. The explored edge gets a soft spill of cloud; cells that just
 * cleared thin out over a second, and a bell's reveal ripples out from the tower.
 */
final class AtlasClouds {
    static final int R = 20, N = 2 * R + 1;           // cells either side of the centre cell
    static final int MAX_PUFFS = N * N * 2;
    /** Puffs, and a shadow under each puff that hangs over explored land. */
    static final int MAX_QUADS = MAX_PUFFS * 2;
    static final int VERTEX = 24;                     // POSITION_TEX_COLOR
    static final int VARIANTS = 3;
    private static final float FADE_NANOS = 1.0e9f;

    private GpuTexture texture;
    private GpuTextureView view;

    // the cached fog field, rebuilt when the explored record, the centre cell or the level changes
    private final float[] unexplored = new float[(N + 2) * (N + 2)];
    private final long[] revealNanos = new long[(N + 2) * (N + 2)];
    private int cachedVersion = -1, cachedCx = Integer.MIN_VALUE, cachedCz, cachedLod = -1;

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

    /** How much of a cloud cell (16·2^shift blocks) is unexplored: 0..1. */
    private static float unexploredFraction(Explored e, int cellX, int cellZ, int shift) {
        if (shift == 0) return e.explored(cellX, cellZ) ? 0f : 1f;
        int span = 1 << shift; // exact: every cell counted, a word of bits a row (a road a cell wide still shows)
        return 1f - e.countIn(cellX << shift, cellZ << shift, span) / (float) (span * span);
    }

    private void refresh(Explored explored, Long2LongOpenHashMap revealedAt, int ccx, int ccz, int lod) {
        if (explored.version == cachedVersion && ccx == cachedCx && ccz == cachedCz && lod == cachedLod) return;
        cachedVersion = explored.version;
        cachedCx = ccx;
        cachedCz = ccz;
        cachedLod = lod;
        int W = N + 2, shift = AtlasMesh.STEP_SHIFT[lod];
        for (int j = 0; j < W; j++)
            for (int i = 0; i < W; i++) {
                int cx = ccx + i - R - 1, cz = ccz + j - R - 1, k = j * W + i;
                unexplored[k] = unexploredFraction(explored, cx, cz, shift);
                revealNanos[k] = shift == 0 && unexplored[k] == 0f ? revealedAt.getOrDefault(Explored.key(cx, cz), 0L) : 0L;
            }
    }

    /**
     * Writes this frame's puffs as view-space quads into {@code dst} (an address), far to near; returns the quad count.
     * {@code base} maps centre-relative world blocks to view pixels.
     */
    int write(long dst, Explored explored, Long2LongOpenHashMap revealedAt, ColumnStore store, Matrix4f base, float pxPerBlock, float tiltDeg,
              int lod, double cx, double cy, double cz, float time, float night, long now) {
        shadowCount = 0;
        int shift = AtlasMesh.STEP_SHIFT[lod], cell = 16 << shift;
        int ccx = Math.floorDiv((int) Math.floor(cx), cell), ccz = Math.floorDiv((int) Math.floor(cz), cell);
        refresh(explored, revealedAt, ccx, ccz, lod);
        int W = N + 2, n = 0;
        float s = cell / 16f;
        // each puff is placed where its cell's ground is on screen and lifted ~30 blocks only in depth: drawn at real
        // height, the tilt would slide the blanket north of the cells it covers and open dark gaps on the north rim
        float lift = pxPerBlock * (float) Math.sin(Math.toRadians(tiltDeg)) * s;
        float rim = R * cell * 0.62f, rimSoft = R * cell * 0.22f;
        for (int j = 1; j <= N; j++) {
            for (int i = 1; i <= N; i++) {
                // blurred fog: this cell weighted 4, its 8 neighbours 0.5 each, with fresh reveals still fading out
                float f = 0f;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int k = (j + dj) * W + i + di;
                        float u = unexplored[k];
                        if (revealNanos[k] != 0L) u = Math.max(u, 1f - (now - revealNanos[k]) / FADE_NANOS);
                        f += (di == 0 && dj == 0) ? u * 4f : u * 0.5f;
                    }
                f /= 8f;
                int own = j * W + i;
                float ownU = unexplored[own];
                if (revealNanos[own] != 0L) ownU = Math.max(ownU, 1f - (now - revealNanos[own]) / FADE_NANOS);
                // an unexplored cell is always fully covered; the blur only spills soft cloud over explored edges.
                // Zoomed out a cloud cell is 64 or 256 blocks, so one with any known land in it carries no cloud, and
                // one beside known land carries a small one: the land you know stays in view at every zoom
                boolean beside = false;
                if (lod > 0) {
                    if (ownU < 0.999f) continue;
                    beside = unexplored[own - 1] < 0.999f || unexplored[own + 1] < 0.999f || unexplored[own - W] < 0.999f || unexplored[own + W] < 0.999f;
                    f = 1f;
                }
                if (ownU >= 0.999f) f = Math.max(f, 1f);
                if (f < 0.12f) continue;
                int cellX = ccx + i - R - 1, cellZ = ccz + j - R - 1;
                int hsh = AtlasMesh.hash(cellX * 7 + 3 + lod * 101, cellZ * 13 + 5);
                float r1 = (hsh & 255) / 255f, r2 = ((hsh >> 8) & 255) / 255f, r3 = ((hsh >> 16) & 255) / 255f, ph = r3 * 6.28f;
                float sway = beside ? 0.35f : 1f; // a small cloud beside known land keeps to its own cell
                float wx = (float) ((double) cellX * cell + cell / 2.0 - cx) + ((r1 - 0.5f) * 12f + (float) Math.sin(time * 0.22f + ph) * 6f) * s * sway;
                float wz = (float) ((double) cellZ * cell + cell / 2.0 - cz) + ((r2 - 0.5f) * 12f + (float) Math.cos(time * 0.17f + ph * 1.3f) * 3f) * s * sway;
                float dist = (float) Math.sqrt(wx * wx + wz * wz);
                float edge = 1f - smooth((dist - rim) / rimSoft); // the blanket thins out at the table's rim
                if (edge <= 0f) continue;
                float a = smooth((f - 0.12f) / 0.5f) * edge;
                int variant = (hsh >>> 24) % VARIANTS;
                boolean flip = (hsh & 0x800000) != 0;
                float size = (34f + 26f * r1) * (0.6f + 0.4f * a) * s * (beside ? 0.5f : 1f);
                // its shadow, on explored ground only: a little east and south of it
                float shx = wx + 10f * s, shz = wz + 4f * s;
                double gx = cx + shx, gz = cz + shz;
                if (lod == 0 && shadowCount < MAX_PUFFS && explored.explored((int) Math.floor(gx) >> 4, (int) Math.floor(gz) >> 4)) {
                    base.transformPosition(shx, (float) (store.groundY(gx, gz, cy) - cy), shz, v);
                    sx[shadowCount] = v.x;
                    sy[shadowCount] = v.y;
                    sz[shadowCount] = v.z + 8f * pxPerBlock;
                    sw[shadowCount] = size * 1.15f;
                    sa[shadowCount] = a * (1f - night * 0.6f);
                    shadowCount++;
                }
                n = add(n, base, wx, 0f, wz, (30f + (r2 - 0.5f) * 6f) * lift, size, a, variant, flip);
                if (r3 > 0.45f) // a smaller cap on top for volume, nudged up the screen
                    n = add(n, base, wx + (r2 - 0.5f) * 10f * s, (5f + r1 * 3f) * s, wz - 3f * s, (37f + r1 * 4f) * lift, size * 0.62f, a * 0.95f, (variant + 1) % VARIANTS, !flip);
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
                float t = u0;
                u0 = u1;
                u1 = t;
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

    private int add(int n, Matrix4f base, float x, float y, float z, float depthLift, float size, float a, int variant, boolean flip) {
        if (n >= MAX_PUFFS) return n;
        base.transformPosition(x, y, z, v);
        px[n] = v.x;
        py[n] = v.y;
        pz[n] = v.z + depthLift;
        ps[n] = size;
        pa[n] = a;
        look[n] = (byte) (variant | (flip ? 4 : 0) | ((Float.floatToIntBits(x * 0.37f + z) >>> 7 & 7) << 3));
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
