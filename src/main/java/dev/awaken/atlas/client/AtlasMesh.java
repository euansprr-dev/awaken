package dev.awaken.atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import dev.awaken.atlas.Explored;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The diorama mesh (SPEC §16.8), in slices of 64×64 columns, one GPU vertex buffer each: POSITION_COLOR quads with the
 * light baked in (a low west sun with cast shadows, slope shade, darker side faces, cut edges down to a base like a
 * diorama block). Three levels of detail: a column is 1, 4 or 16 blocks, so a slice covers 64, 256 or 1024 blocks and
 * the quads on screen stay about the same at every zoom. Only explored columns are meshed.
 * <p>
 * Memory is bounded (spike S5's finding: ~100 B per explored column): only a window of slices round the view is kept
 * per level, slices of a level not drawn for a while are dropped, and the whole mesh stays under {@link #GPU_LIMIT}.
 * Slices are built on one worker thread from snapshots; the main thread only uploads finished buffers, before any
 * render pass opens.
 */
final class AtlasMesh {
    static final int S = 64, SHIFT = 6;
    static final int Y_REF = 64, BASE_Y = 44;
    static final int LODS = 3;
    /** Blocks per column at each level, and its shift. */
    static final int[] STEP = {1, 4, 16}, STEP_SHIFT = {0, 2, 4};
    /** Slices kept round the view centre at each level (7 → a 15×15 window: 960, 3840, 15360 blocks across). */
    static final int WINDOW = 7;
    static final long GPU_LIMIT = 96L << 20;
    /** A level not drawn for this long lets its slices go. */
    static final long IDLE_NANOS = 20_000_000_000L;
    static final int IN_FLIGHT = 2;

    static final class Slice {
        final int lod, sx, sz;
        GpuBuffer buffer;
        int quads;
        long bytes;
        boolean dirty = true, building;
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

    private record Done(Slice slice, ByteBuffer data, int quads, long nanos) {}

    @SuppressWarnings("unchecked")
    final Long2ObjectOpenHashMap<Slice>[] slices = new Long2ObjectOpenHashMap[]{new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>()};
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
    private int snapshotVersion = -1;
    private final int[] windowCx = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE}, windowCz = new int[LODS];
    private int generation;

    // stats
    final long[] buildNanos = new long[1024];
    long builds, totalQuads, totalBytes;

    AtlasMesh(ColumnStore store) {
        this.store = store;
    }

    /** The level to draw at this many GUI pixels per block. */
    static int lodFor(float pxPerBlock) {
        return pxPerBlock >= 0.9f ? 0 : pxPerBlock >= 0.22f ? 1 : 2;
    }

    static long key(int sx, int sz) {
        return Explored.key(sx, sz);
    }

    // ---- what changed -------------------------------------------------------------------------------------------------

    /** Blocks in [bx, bx + size) × [bz, bz + size) changed (a cell explored, a chunk sampled): rebuild what draws them. */
    void changed(int bx, int bz, int size) {
        for (int lod = 0; lod < LODS; lod++) {
            int shift = SHIFT + STEP_SHIFT[lod], pad = Builder.PADW << STEP_SHIFT[lod];
            int sx0 = (bx - (1 << STEP_SHIFT[lod])) >> shift, sx1 = (bx + size + pad) >> shift;
            int sz0 = (bz - (1 << STEP_SHIFT[lod])) >> shift, sz1 = (bz + size + (1 << STEP_SHIFT[lod])) >> shift;
            for (int sz = sz0; sz <= sz1; sz++)
                for (int sx = sx0; sx <= sx1; sx++) {
                    Slice s = slices[lod].get(key(sx, sz));
                    if (s != null) s.dirty = true;
                }
        }
    }

    /** Everything is stale (a new dimension, a cleared cache). */
    void clear() {
        generation++;
        for (var map : slices) {
            for (Slice s : map.values()) close(s);
            map.clear();
        }
        totalQuads = totalBytes = 0;
        windowCx[0] = windowCx[1] = windowCx[2] = Integer.MIN_VALUE;
        Done d;
        while ((d = done.poll()) != null) MemoryUtil.memFree(d.data);
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
        int gen = generation;
        worker.execute(() -> {
            long t0 = System.nanoTime();
            ByteBuffer data = null;
            int quads = 0;
            try {
                quads = builder.build(store, ex, s.lod, s.x0(), s.z0());
                if (quads > 0) data = builder.copyOut();
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.debug("[atlas] slice build failed: {}", e.toString());
            }
            if (gen == generation) done.add(new Done(s, data, quads, System.nanoTime() - t0));
            else if (data != null) MemoryUtil.memFree(data);
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
            if (d.quads > 0 && d.data != null) {
                s.buffer = RenderSystem.getDevice().createBuffer(() -> "Atlas slice", GpuBuffer.USAGE_VERTEX, d.data);
                s.quads = d.quads;
                s.bytes = d.data.remaining();
                totalQuads += s.quads;
                totalBytes += s.bytes;
                if (!had) s.builtAt = now;
            }
            if (d.data != null) MemoryUtil.memFree(d.data);
            buildNanos[(int) (builds & 1023)] = d.nanos;
            builds++;
        }
    }

    private void close(Slice s) {
        if (s.buffer != null) {
            s.buffer.close();
            s.buffer = null;
        }
        totalQuads -= s.quads;
        totalBytes -= s.bytes;
        s.quads = 0;
        s.bytes = 0;
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
        static final int PADW = 48; // shadow casters up to 48 columns west of a slice
        /** Sun from the west, 40° up: a shadow line drops tan(40°) = 0.84 blocks per block east. */
        static final float SUN_DROP = 0.84f;
        private static final int GW = PADW + S + 1, GH = S + 2, VERTEX = 16;
        private final short[] gh = new short[GW * GH];
        private final int[] gc = new int[GW * GH];
        private final byte[] gi = new byte[GW * GH];
        private final boolean[] gd = new boolean[GW * GH];
        private final int[] top = new int[S * S];
        private long vb = MemoryUtil.nmemAlloc(1 << 20);
        private long vbCap = 1 << 20, vbPos;
        private int step;

        private static int g(int x, int z) {
            return (z + 1) * GW + (x + PADW);
        }

        ByteBuffer copyOut() {
            ByteBuffer out = MemoryUtil.memAlloc((int) vbPos);
            MemoryUtil.memCopy(vb, MemoryUtil.memAddress(out), vbPos);
            return out;
        }

        int build(ColumnStore store, Explored explored, int lod, int x0, int z0) {
            int sh = STEP_SHIFT[lod];
            step = 1 << sh;
            int half = step >> 1;
            // 1. gather the columns this level draws (the middle column of each step × step square)
            boolean any = false;
            ColumnStore.Tile tile = null;
            int lastTx = Integer.MIN_VALUE, lastTz = Integer.MIN_VALUE;
            for (int z = -1; z <= S; z++) {
                int wz = z0 + (z << sh) + half;
                for (int x = -PADW; x <= S; x++) {
                    int wx = x0 + (x << sh) + half, gi0 = g(x, z);
                    int tx = wx >> ColumnStore.TILE_SHIFT, tz = wz >> ColumnStore.TILE_SHIFT;
                    if (tx != lastTx || tz != lastTz) {
                        lastTx = tx;
                        lastTz = tz;
                        tile = store.peek(tx, tz);
                    }
                    int i = ColumnStore.index(wx, wz);
                    boolean d = tile != null && (tile.color[i] >>> 24) != 0 && explored.explored(wx >> 4, wz >> 4);
                    gd[gi0] = d;
                    if (d) {
                        gh[gi0] = tile.height[i];
                        gc[gi0] = tile.color[i];
                        gi[gi0] = tile.info[i];
                        if (x >= 0 && x < S && z >= 0 && z < S) any = true;
                    }
                }
            }
            vbPos = 0;
            if (!any) return 0;
            int quads = 0;
            float drop = SUN_DROP * step;
            // 2. top colours: map colour × slope shade × cast shadow from the west sun
            for (int z = 0; z < S; z++) {
                float level = -1e9f;
                for (int x = -PADW; x < S; x++) {
                    int gg = g(x, z);
                    if (x >= 0 && gd[gg]) {
                        int h = gh[gg], c = gc[gg], info = gi[gg], depth = info & 15, kind = (info >> 4) & 7;
                        float shade = 1f;
                        if (depth > 0) {
                            shade = Math.max(0.55f, 1.02f - depth * 0.035f);
                        } else {
                            int w = g(x - 1, z), n = g(x, z - 1);
                            if (gd[w]) shade += clamp((h - gh[w]) * 0.06f / step, -0.16f, 0.16f);
                            if (gd[n]) shade += clamp((h - gh[n]) * 0.04f / step, -0.10f, 0.10f);
                            // a little per-block grain on natural ground so it reads as voxels, not paint
                            if (kind == ColumnStore.K_SOIL || kind == ColumnStore.K_LEAVES || kind == ColumnStore.K_SAND)
                                shade += ((hash(x0 + (x << sh), z0 + (z << sh)) & 7) - 3.5f) * 0.008f;
                        }
                        float shadow = clamp((level - h) * 0.5f, 0f, 1f);
                        shade *= 1f - 0.30f * shadow;
                        top[z * S + x] = scale(c, shade);
                    }
                    if (gd[gg]) level = Math.max(level, gh[gg]);
                    level -= drop;
                }
            }
            // 3. top faces, merged along x where height and colour repeat
            for (int z = 0; z < S; z++) {
                int x = 0;
                while (x < S) {
                    int gg = g(x, z);
                    if (!gd[gg]) {
                        x++;
                        continue;
                    }
                    int h = gh[gg], c = top[z * S + x], run = 1;
                    while (x + run < S && gd[gg + run] && gh[gg + run] == h && top[z * S + x + run] == c) run++;
                    float y = h + 1 - Y_REF;
                    quad(x, y, z, x + run, y, z, x + run, y, z + 1, x, y, z + 1, c, c);
                    quads++;
                    x += run;
                }
            }
            // 4. side faces down to the lower neighbour, or to the base at the explored edge (the diorama cut)
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    int gg = g(x, z);
                    if (!gd[gg]) continue;
                    int h = gh[gg], c = top[z * S + x], kind = (gi[gg] >> 4) & 7;
                    quads += side(gg + 1, h, c, kind, x + 1, z, x + 1, z + 1, 0.62f);  // east
                    quads += side(gg - 1, h, c, kind, x, z + 1, x, z, 0.94f);          // west, sunlit
                    quads += side(gg + GW, h, c, kind, x + 1, z + 1, x, z + 1, 0.80f); // south
                    quads += side(gg - GW, h, c, kind, x, z, x + 1, z, 0.70f);         // north
                }
            }
            return quads;
        }

        /** One vertical face between column g's top and its neighbour n; returns the quads written. */
        private int side(int n, int h, int topColor, int kind, int ax, int az, int bx, int bz, float dir) {
            boolean edge = !gd[n];
            int low = edge ? BASE_Y - 1 : gh[n];
            if (low >= h) return 0;
            float yTop = h + 1 - Y_REF, yLow = low + 1 - Y_REF;
            int lit = scale(topColor, dir);
            if (!edge && h - low <= 2) {
                quad(ax, yTop, az, bx, yTop, bz, bx, yLow, bz, ax, yLow, az, lit, scale(lit, 0.84f));
                return 1;
            }
            // a one-block lip in the surface colour, then the ground under it, darkening downwards
            float yLip = yTop - 1;
            quad(ax, yTop, az, bx, yTop, bz, bx, yLip, bz, ax, yLip, az, lit, scale(lit, 0.9f));
            int under = scale(under(kind, topColor), dir);
            int bottom = edge ? scale(under, 0.42f) : scale(under, 0.72f);
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

        /** A quad in column units (scaled to blocks here): the first two corners take colour a, the last two colour b. */
        private void quad(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                          float x4, float y4, float z4, int a, int b) {
            if (vbPos + 4L * VERTEX > vbCap) {
                vbCap *= 2;
                vb = MemoryUtil.nmemRealloc(vb, vbCap);
            }
            long p = vb + vbPos;
            float s = step;
            vertex(p, x1 * s, y1, z1 * s, a);
            vertex(p + VERTEX, x2 * s, y2, z2 * s, a);
            vertex(p + 2 * VERTEX, x3 * s, y3, z3 * s, b);
            vertex(p + 3 * VERTEX, x4 * s, y4, z4 * s, b);
            vbPos += 4L * VERTEX;
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

    static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((argb & 255) * f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static int hash(int x, int z) {
        int h = x * 0x27d4eb2d ^ z * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        return h ^ (h >>> 13);
    }
}
