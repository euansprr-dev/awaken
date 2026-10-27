package dev.awaken.atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.atlas.Explored;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The diorama mesh (SPEC §16.8), in slices of 64×64 columns, each a GPU vertex buffer and a texture: the tops are
 * textured quads, merged wherever the height matches, painted from the slice's lit texture ({@link SliceSampler}: a
 * texel a block, lit block by block by a low west sun with cast shadows, slope shade and occlusion); the sides are
 * coloured quads (darker side faces, cut edges down to a base like a diorama block). Five levels of detail: a column is
 * 1, 2, 4, 8 or 16 blocks, so a slice covers 64 to 1024 blocks and a column stays 3.6 to 7 screen pixels at every zoom,
 * while the texture keeps the land's detail down to the pixel. Only explored columns are meshed.
 * <p>
 * Memory is bounded (spike S5's finding: ~100 B per explored column): only a window of slices round the view is kept
 * per level, slices of a level not drawn for a while are dropped, and the whole mesh stays under {@link #GPU_LIMIT}.
 * Slices are built on one worker thread from snapshots; the main thread only uploads finished buffers, before any
 * render pass opens.
 */
final class AtlasMesh {
    static final int S = 64, SHIFT = 6;
    static final int Y_REF = 64;
    /** The diorama's base: the holo grid lies on it and a slice rises from it (lower under a roof: the Nether's lava sea
     *  is at 31). Set per dimension, with the mesh cleared. */
    static volatile int BASE_Y = 44;
    static final int LODS = SliceSampler.LODS;
    /** Blocks per column at each level, and its shift. */
    static final int[] STEP = {1, 2, 4, 8, 16}, STEP_SHIFT = {0, 1, 2, 3, 4};
    /** Slices kept round the view centre at each level (7 → a 15×15 window: 960 blocks across at the finest, 15360 at
     *  the coarsest). */
    static final int WINDOW = 7;
    static final long GPU_LIMIT = 96L << 20;
    /** A level not drawn for this long lets its slices go. */
    static final long IDLE_NANOS = 20_000_000_000L;
    static final int IN_FLIGHT = 2;

    static final class Slice {
        final int lod, sx, sz;
        GpuBuffer buffer;
        GpuTexture texture;
        GpuTextureView view;
        /** The tops come first in the buffer (POSITION_TEX), then the sides (POSITION_COLOR). */
        int quads, tops;
        long bytes;
        boolean dirty = true, building;
        /** Lit columns (x and z in blocks from the slice's corner, the column's top y), for the lights at night. */
        int[] lights = new int[0];
        /** When the slice last changed on screen (for the rise of new land). */
        long builtAt;

        Slice(int lod, int sx, int sz) {
            this.lod = lod;
            this.sx = sx;
            this.sz = sz;
        }

        int blocks() {
            return S << STEP_SHIFT[lod];
        }

        int x0() {
            return sx * blocks();
        }

        int z0() {
            return sz * blocks();
        }
    }

    private record Done(Slice slice, ByteBuffer data, int quads, int tops, ByteBuffer texture, int texels, int[] lights, long nanos) {}

    @SuppressWarnings("unchecked")
    final Long2ObjectOpenHashMap<Slice>[] slices = new Long2ObjectOpenHashMap[LODS];
    {
        for (int l = 0; l < LODS; l++) slices[l] = new Long2ObjectOpenHashMap<>();
    }
    /** The level the table is drawing: only its own new slices rise; slices built ahead for another level don't. */
    int shown = -1;
    private final long[] lastDrawn = new long[LODS];
    private final ColumnStore store;
    private final Queue<Done> done = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Awaken Atlas mesher");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Builder builder = new Builder(); // worker thread only
    private int inFlight;
    private Explored exploredSnapshot = new Explored();
    /** The zones the worker colours by (set by the main thread before each update). */
    volatile ZoneMap.View zones;
    private int snapshotVersion = -1;
    private final int[] windowCx = new int[LODS], windowCz = new int[LODS];
    {
        java.util.Arrays.fill(windowCx, Integer.MIN_VALUE);
    }
    private int generation;

    // stats
    final long[] buildNanos = new long[1024];
    long builds, totalQuads, totalBytes;

    AtlasMesh(ColumnStore store) {
        this.store = store;
    }

    /** The level to draw at this many screen (physical) pixels per block. */
    static int lodFor(float pxPerBlock) {
        return SliceSampler.lodFor(pxPerBlock);
    }

    static long key(int sx, int sz) {
        return Explored.key(sx, sz);
    }

    // ---- what changed -------------------------------------------------------------------------------------------------

    /** Blocks in [bx, bx + size) × [bz, bz + size) changed (a cell explored, a chunk sampled): rebuild what draws them. */
    void changed(int bx, int bz, int size) {
        for (int lod = 0; lod < LODS; lod++) {
            // a slice reads texels PAD_W west of it (the shadows cast into it) and PAD round its other sides
            int shift = SHIFT + STEP_SHIFT[lod], b = SliceSampler.blocksPerTexel(lod);
            int west = SliceSampler.PAD_W * b, round = SliceSampler.PAD * b;
            int sx0 = (bx - round) >> shift, sx1 = (bx + size + west) >> shift;
            int sz0 = (bz - round) >> shift, sz1 = (bz + size + round) >> shift;
            for (int sz = sz0; sz <= sz1; sz++)
                for (int sx = sx0; sx <= sx1; sx++) {
                    Slice s = slices[lod].get(key(sx, sz));
                    if (s != null) s.dirty = true;
                }
        }
    }

    /** Every built slice draws again (the zones' colours changed). */
    void changedAll() {
        for (var map : slices) for (Slice s : map.values()) s.dirty = true;
    }

    /** Everything is stale (a new dimension, a cleared cache). */
    void clear() {
        generation++;
        inFlight = 0; // builds still running belong to the old generation and will never report back
        snapshotVersion = -1;
        for (var map : slices) {
            for (Slice s : map.values()) close(s);
            map.clear();
        }
        totalQuads = totalBytes = 0;
        java.util.Arrays.fill(windowCx, Integer.MIN_VALUE);
        Done d;
        while ((d = done.poll()) != null) free(d);
    }

    // ---- the per-frame step (main thread, outside any render pass) -------------------------------------------------------

    /**
     * Uploads finished slices, keeps the window round (cx, cz) at {@code lod}, and starts builds nearest first.
     * Returns how many slices still wait to be built in the window.
     */
    int update(double cx, double cz, int lod, Explored explored, long now) {
        upload(now);
        if (explored.version != snapshotVersion) { // the worker reads its own copy
            snapshotVersion = explored.version;
            exploredSnapshot = explored.copy();
        }
        lastDrawn[lod] = now;
        int shift = SHIFT + STEP_SHIFT[lod];
        int ccx = (int) Math.floor(cx) >> shift, ccz = (int) Math.floor(cz) >> shift;
        Long2ObjectOpenHashMap<Slice> map = slices[lod];
        if (ccx != windowCx[lod] || ccz != windowCz[lod]) {
            windowCx[lod] = ccx;
            windowCz[lod] = ccz;
            for (int sz = ccz - WINDOW; sz <= ccz + WINDOW; sz++)
                for (int sx = ccx - WINDOW; sx <= ccx + WINDOW; sx++)
                    if (!map.containsKey(key(sx, sz))) map.put(key(sx, sz), new Slice(lod, sx, sz));
            evict(lod, ccx, ccz);
        }
        int waiting = 0;
        while (inFlight < IN_FLIGHT) {
            Slice best = null;
            long bestD = Long.MAX_VALUE;
            for (Slice s : map.values()) {
                if (!s.dirty || s.building) continue;
                long dx = s.sx - ccx, dz = s.sz - ccz, d = dx * dx + dz * dz;
                if (Math.abs(dx) > WINDOW || Math.abs(dz) > WINDOW) continue;
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
            if (best == null) break;
            submit(best);
        }
        for (Slice s : map.values()) if (s.dirty || s.building) waiting++;
        // a level nobody drew for a while lets its slices go
        for (int l = 0; l < LODS; l++) {
            if (l == lod || lastDrawn[l] == 0 || now - lastDrawn[l] < IDLE_NANOS || slices[l].isEmpty()) continue;
            for (Slice s : slices[l].values()) close(s);
            slices[l].clear();
            windowCx[l] = Integer.MIN_VALUE;
        }
        return waiting;
    }

    private void submit(Slice s) {
        s.dirty = false;
        s.building = true;
        inFlight++;
        Explored ex = exploredSnapshot;
        ZoneMap.View zv = zones;
        int gen = generation;
        worker.execute(() -> {
            long t0 = System.nanoTime();
            ByteBuffer data = null, texture = null;
            int quads = 0, tops = 0, texels = 0;
            int[] lights = new int[0];
            try {
                quads = builder.build(store, ex, zv, s.lod, s.x0(), s.z0());
                if (quads > 0) {
                    data = builder.copyOut();
                    texture = builder.textureOut();
                    tops = builder.topQuads();
                    texels = builder.texels();
                    lights = builder.lights();
                }
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.debug("[atlas] slice build failed: {}", e.toString());
            }
            Done d = new Done(s, data, quads, tops, texture, texels, lights, System.nanoTime() - t0);
            if (gen == generation) done.add(d);
            else free(d);
        });
    }

    private void upload(long now) {
        Done d;
        while ((d = done.poll()) != null) {
            inFlight = Math.max(0, inFlight - 1);
            Slice s = d.slice;
            s.building = false;
            boolean had = s.quads > 0;
            close(s);
            if (d.quads > 0 && d.data != null && d.texture != null) {
                var device = RenderSystem.getDevice();
                s.buffer = device.createBuffer(() -> "Atlas slice", GpuBuffer.USAGE_VERTEX, d.data);
                int t = d.texels, mips = Builder.mipLevels(t);
                s.texture = device.createTexture(() -> "Atlas slice tops", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        GpuFormat.RGBA8_UNORM, t, t, 1, mips);
                s.view = device.createTextureView(s.texture);
                CommandEncoder enc = device.createCommandEncoder();
                long at = MemoryUtil.memAddress(d.texture);
                for (int m = 0, w = t; m < mips; m++, w >>= 1) {
                    enc.writeToTexture(s.texture, MemoryUtil.memByteBuffer(at, w * w * 4), m, 0, 0, 0, w, w);
                    at += (long) w * w * 4;
                }
                s.quads = d.quads;
                s.tops = d.tops;
                s.bytes = d.data.remaining() + d.texture.remaining();
                s.lights = d.lights;
                totalQuads += s.quads;
                totalBytes += s.bytes;
                // new land rises where it appears; a level built ahead of a zoom is already there when it's shown
                if (!had && s.lod == shown) s.builtAt = now;
            }
            free(d);
            buildNanos[(int) (builds & 1023)] = d.nanos;
            builds++;
        }
    }

    private static void free(Done d) {
        if (d.data != null) MemoryUtil.memFree(d.data);
        if (d.texture != null) MemoryUtil.memFree(d.texture);
    }

    private void close(Slice s) {
        if (s.buffer != null) {
            s.buffer.close();
            s.buffer = null;
        }
        if (s.view != null) s.view.close();
        if (s.texture != null) s.texture.close();
        s.view = null;
        s.texture = null;
        totalQuads -= s.quads;
        totalBytes -= s.bytes;
        s.quads = s.tops = 0;
        s.bytes = 0;
        s.lights = new int[0];
    }

    /** Drops slices well outside the window, then the farthest while over the memory limit. */
    private void evict(int lod, int ccx, int ccz) {
        Long2ObjectOpenHashMap<Slice> map = slices[lod];
        int keep = WINDOW + WINDOW / 2;
        var it = map.values().iterator();
        while (it.hasNext()) {
            Slice s = it.next();
            if (s.building) continue;
            if (Math.abs(s.sx - ccx) > keep || Math.abs(s.sz - ccz) > keep) {
                close(s);
                it.remove();
            }
        }
        while (totalBytes > GPU_LIMIT) {
            Slice far = null;
            long farD = -1;
            for (var m : slices)
                for (Slice s : m.values()) {
                    if (s.buffer == null) continue;
                    int scx = windowCx[s.lod] == Integer.MIN_VALUE ? s.sx : windowCx[s.lod], scz = windowCz[s.lod];
                    long dx = s.sx - scx, dz = s.sz - scz, d = dx * dx + dz * dz + (s.lod == lod ? 0 : 1_000_000L);
                    if (d > farD) {
                        farD = d;
                        far = s;
                    }
                }
            if (far == null) break;
            close(far);
            slices[far.lod].remove(key(far.sx, far.sz));
        }
    }

    int built(int lod) {
        int n = 0;
        for (Slice s : slices[lod].values()) if (s.quads > 0) n++;
        return n;
    }

    // ---- the builder (worker thread) ------------------------------------------------------------------------------------

    /** Builds one slice into its own scratch. One instance, used only by the worker. */
    static final class Builder {
        static final int TOP_VERTEX = 20, SIDE_VERTEX = 16; // POSITION_TEX, POSITION_COLOR
        private final SliceSampler sampler = new SliceSampler();
        private final boolean[] used = new boolean[S * S];
        private final Scratch tops = new Scratch(), sides = new Scratch();
        private final int[] mip = new int[SliceSampler.MAX_TEX * SliceSampler.MAX_TEX / 4], mip2 = new int[mip.length];
        private int step, topQuads;
        private float uvScale;
        private final it.unimi.dsi.fastutil.ints.IntArrayList lit = new it.unimi.dsi.fastutil.ints.IntArrayList();
        static final int MAX_LIGHTS = 1500;

        /** A growing native buffer of vertices. */
        private static final class Scratch {
            long address = MemoryUtil.nmemAlloc(1 << 20), cap = 1 << 20, pos;

            long reserve(int bytes) {
                if (pos + bytes > cap) {
                    cap = Math.max(cap * 2, pos + bytes);
                    address = MemoryUtil.nmemRealloc(address, cap);
                }
                long p = address + pos;
                pos += bytes;
                return p;
            }
        }

        int[] lights() {
            return lit.toIntArray();
        }

        int topQuads() {
            return topQuads;
        }

        int texels() {
            return sampler.tex;
        }

        /** The vertices: the textured tops first, then the coloured sides. */
        ByteBuffer copyOut() {
            ByteBuffer out = MemoryUtil.memAlloc((int) (tops.pos + sides.pos));
            MemoryUtil.memCopy(tops.address, MemoryUtil.memAddress(out), tops.pos);
            MemoryUtil.memCopy(sides.address, MemoryUtil.memAddress(out) + tops.pos, sides.pos);
            return out;
        }

        static int mipLevels(int texels) {
            return Integer.numberOfTrailingZeros(texels) + 1;
        }

        /** The top texture and its mip chain down to 1×1, RGBA bytes, level after level (each a box filter of the last). */
        ByteBuffer textureOut() {
            int t = sampler.tex;
            long bytes = 0;
            for (int w = t; w >= 1; w >>= 1) bytes += (long) w * w * 4;
            ByteBuffer out = MemoryUtil.memAlloc((int) bytes);
            long p = MemoryUtil.memAddress(out);
            int[] src = sampler.lit;
            p = rgba(p, src, t * t);
            int w = t;
            while (w > 1) {
                int h = w >> 1;
                int[] dst = src == mip ? mip2 : mip;
                for (int z = 0; z < h; z++)
                    for (int x = 0; x < h; x++) {
                        int a = src[2 * z * w + 2 * x], b = src[2 * z * w + 2 * x + 1], c = src[(2 * z + 1) * w + 2 * x], d = src[(2 * z + 1) * w + 2 * x + 1];
                        int r = (((a >> 16) & 255) + ((b >> 16) & 255) + ((c >> 16) & 255) + ((d >> 16) & 255) + 2) >> 2;
                        int g = (((a >> 8) & 255) + ((b >> 8) & 255) + ((c >> 8) & 255) + ((d >> 8) & 255) + 2) >> 2;
                        int bl = ((a & 255) + (b & 255) + (c & 255) + (d & 255) + 2) >> 2;
                        dst[z * h + x] = 0xFF000000 | r << 16 | g << 8 | bl;
                    }
                p = rgba(p, dst, h * h);
                src = dst;
                w = h;
            }
            return out;
        }

        private static long rgba(long p, int[] argb, int n) {
            for (int i = 0; i < n; i++, p += 4) {
                int v = argb[i];
                MemoryUtil.memPutByte(p, (byte) (v >> 16));
                MemoryUtil.memPutByte(p + 1, (byte) (v >> 8));
                MemoryUtil.memPutByte(p + 2, (byte) v);
                MemoryUtil.memPutByte(p + 3, (byte) 255);
            }
            return p;
        }

        /** Builds the slice at {@code lod} with its corner at block (x0, z0); returns the quads (0: no known land in it). */
        int build(ColumnStore store, Explored explored, ZoneMap.View zones, int lod, int x0, int z0) {
            tops.pos = sides.pos = 0;
            topQuads = 0;
            lit.clear();
            if (!sampler.sample(store, explored, zones, lod, x0, z0)) return 0;
            SliceSampler sm = sampler;
            step = 1 << lod;
            uvScale = sm.cpt / (float) sm.tex;
            // tops, merged greedily into rectangles wherever the height matches: their colour is the texture's, block by
            // block, so a field or a roof is one quad however much detail it carries
            java.util.Arrays.fill(used, false);
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    int ci = SliceSampler.c(x, z), k = z * S + x;
                    if (!sm.cd[ci] || used[k]) continue;
                    int h = sm.ch[ci], run = 1;
                    while (x + run < S && !used[k + run] && sm.cd[ci + run] && sm.ch[ci + run] == h) run++;
                    int rows = 1;
                    grow:
                    while (z + rows < S) {
                        int rk = (z + rows) * S + x, rc = SliceSampler.c(x, z + rows);
                        for (int i = 0; i < run; i++)
                            if (used[rk + i] || !sm.cd[rc + i] || sm.ch[rc + i] != h) break grow;
                        rows++;
                    }
                    for (int r = 0; r < rows; r++) java.util.Arrays.fill(used, (z + r) * S + x, (z + r) * S + x + run, true);
                    top(x, z, x + run, z + rows, h + 1 - Y_REF);
                    topQuads++;
                }
            }
            int quads = topQuads;
            // sides down to the lower neighbour, or a short skirt into the cloud floor at the explored edge; lights at night
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    int ci = SliceSampler.c(x, z);
                    if (!sm.cd[ci]) continue;
                    int h = sm.ch[ci], c = sm.cc[z * S + x], kind = sm.ck[ci];
                    quads += side(ci + 1, h, c, kind, x + 1, z, x + 1, z + 1, 0.62f);                  // east
                    quads += side(ci - 1, h, c, kind, x, z + 1, x, z, 0.94f);                          // west, sunlit
                    quads += side(ci + SliceSampler.CW, h, c, kind, x + 1, z + 1, x, z + 1, 0.80f); // south
                    quads += side(ci - SliceSampler.CW, h, c, kind, x, z, x + 1, z, 0.70f);         // north
                    if (sm.cl[z * S + x] && lit.size() < MAX_LIGHTS * 3) {
                        lit.add((x << lod) + (step >> 1));
                        lit.add(h);
                        lit.add((z << lod) + (step >> 1));
                    }
                }
            }
            return quads;
        }

        /**
         * One vertical face between column ci's top and its neighbour n; returns the quads written. Where the neighbour is
         * unknown (the land's edge, under the clouds) the face is a short skirt fading into the cloud floor's colour, never
         * a cliff down to the base (SPEC §16.8).
         */
        private int side(int n, int h, int topColor, int kind, int ax, int az, int bx, int bz, float dir) {
            boolean edge = !sampler.cd[n];
            if (edge) {
                float yTop = h + 1 - Y_REF, yLow = yTop - Math.max(3, 2 * step);
                int face = scale(topColor, dir);
                quad(ax, yTop, az, bx, yTop, bz, bx, yLow, bz, ax, yLow, az, mix(face, SliceSampler.SEA, 0.35f), SliceSampler.SEA);
                return 1;
            }
            int low = sampler.ch[n];
            if (low >= h) return 0;
            float yTop = h + 1 - Y_REF, yLow = low + 1 - Y_REF;
            int face = scale(topColor, dir);
            if (h - low <= 2) {
                quad(ax, yTop, az, bx, yTop, bz, bx, yLow, bz, ax, yLow, az, face, scale(face, 0.84f));
                return 1;
            }
            // a one-block lip in the surface colour, then the ground under it, darkening downwards
            float yLip = yTop - 1;
            quad(ax, yTop, az, bx, yTop, bz, bx, yLip, bz, ax, yLip, az, face, scale(face, 0.9f));
            int under = scale(under(kind, topColor), dir);
            int bottom = scale(under, 0.72f);
            quad(ax, yLip, az, bx, yLip, bz, bx, yLow, bz, ax, yLow, az, under, bottom);
            return 2;
        }

        private static int under(int kind, int topColor) {
            return switch (kind) {
                case ColumnStore.K_SOIL -> 0xFF7A5A3E;
                case ColumnStore.K_SAND -> scale(topColor, 0.86f);
                case ColumnStore.K_WATER -> 0xFF2A4C7A;
                case ColumnStore.K_LEAVES -> scale(topColor, 0.62f);
                case ColumnStore.K_SNOW -> 0xFF8A8F99;
                default -> scale(topColor, 0.9f);
            };
        }

        /** A top from column (x1, z1) to (x2, z2) at height y, its texture coordinates the same corners in texels. */
        private void top(int x1, int z1, int x2, int z2, float y) {
            long p = tops.reserve(4 * TOP_VERTEX);
            float s = step, u = uvScale;
            topVertex(p, x1 * s, y, z1 * s, x1 * u, z1 * u);
            topVertex(p + TOP_VERTEX, x1 * s, y, z2 * s, x1 * u, z2 * u);
            topVertex(p + 2 * TOP_VERTEX, x2 * s, y, z2 * s, x2 * u, z2 * u);
            topVertex(p + 3 * TOP_VERTEX, x2 * s, y, z1 * s, x2 * u, z1 * u);
        }

        private static void topVertex(long p, float x, float y, float z, float u, float v) {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4, y);
            MemoryUtil.memPutFloat(p + 8, z);
            MemoryUtil.memPutFloat(p + 12, u);
            MemoryUtil.memPutFloat(p + 16, v);
        }

        /** A side quad in column units (scaled to blocks here): the first two corners take colour a, the last two colour b. */
        private void quad(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                          float x4, float y4, float z4, int a, int b) {
            long p = sides.reserve(4 * SIDE_VERTEX);
            float s = step;
            vertex(p, x1 * s, y1, z1 * s, a);
            vertex(p + SIDE_VERTEX, x2 * s, y2, z2 * s, a);
            vertex(p + 2 * SIDE_VERTEX, x3 * s, y3, z3 * s, b);
            vertex(p + 3 * SIDE_VERTEX, x4 * s, y4, z4 * s, b);
        }

        private static void vertex(long p, float x, float y, float z, int argb) {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4, y);
            MemoryUtil.memPutFloat(p + 8, z);
            MemoryUtil.memPutByte(p + 12, (byte) (argb >> 16));
            MemoryUtil.memPutByte(p + 13, (byte) (argb >> 8));
            MemoryUtil.memPutByte(p + 14, (byte) argb);
            MemoryUtil.memPutByte(p + 15, (byte) (argb >>> 24));
        }
    }

    static int mix(int a, int b, float t) {
        return SliceSampler.mix(a, b, t);
    }

    static int lighten(int argb) {
        return mix(argb, 0xFFFFFFFF, 0.50f);
    }

    static int scale(int argb, float f) {
        return SliceSampler.scale(argb, f);
    }

    static int hash(int x, int z) {
        return SliceSampler.hash(x, z);
    }
}
