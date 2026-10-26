package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.farsight.voxel.VoxelMesh;
import dev.awaken.farsight.voxel.VoxelMesher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.ChunkPos;
import org.joml.FrustumIntersection;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * City Sight on the GPU (SPEC §20): real chunks' blocks drawn by Far Sight wherever Sodium doesn't draw them, at
 * their real textures. Each chunk's quads at every level ({@link VoxelMesher}) live in pages of one texel buffer each;
 * the vertex shader pulls them by index. A chunk table gives each slot's origin, a look table each look's atlas
 * rectangle. Each frame every chunk picks its level by how big its blocks look (a block under {@link #PIXELS} pixels
 * goes to the next level), the levels cross-fading over a band, and chunks Sodium draws whole are skipped. A coverage
 * mask (256×256 chunks, toroidal) tells the far terrain and far trees where City Sight stands, so they step aside: the
 * chunks it draws this frame, never just the ones it holds (a chunk held but not drawn left the far land stepping
 * aside for nothing, a hole to the sky: Euan, 2026-10-26, the city seen from the Commons).
 */
final class VoxelLayer implements AutoCloseable {
    static final int PAGE_QUADS = 1 << 18, MAX_SLOTS = 1 << 16, DRAW_QUADS = 1 << 16, MAX_COMMANDS = 32768, COMMAND_BYTES = 20;
    /** A level's blocks give way to the next coarser level once they look this many pixels or fewer. */
    static final float PIXELS = 2.0f;
    /** Chunks are kept this far (blocks) and drawn in the coverage mask this far; the city's are kept at any distance. */
    static final float KEEP = 1900f, COVER = 2000f;
    /** At most this many quads kept (256 MB of pages): past it, the farthest chunks go first. */
    static final long MAX_QUADS = 16L << 20;
    /**
     * A chunk new to City Sight dissolves in over this long (ms) while the far terrain and far trees it replaces
     * dissolve out in the same dither, so wherever the prediction differed from the real blocks it changes gently,
     * never pops (Euan: gradual, imperceptible change).
     */
    static final long FADE_MS = 1000L;
    private final IntArrayList fading = new IntArrayList();
    /** The cross-fade between two levels, as a share of the nearer one's reach. */
    static final float BAND = 0.15f;
    /**
     * Thin things and plants (the detail run, SPEC §20 C) draw while a block spans at least this many pixels: a fence's
     * post (a quarter block) a pixel, a tuft of grass four. Past it they're under a pixel, as City Sight always took them.
     */
    static final float DETAIL_PIXELS = 4f;

    private static final class Page {
        final GpuBuffer buffer;
        int used;
        final List<int[]> free = new ArrayList<>(); // {first, count}

        Page(int index) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "awaken city sight quads " + index,
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, (long) PAGE_QUADS * 16);
        }
    }

    private static final class Chunk {
        int cx, cz, minY, maxY, groundY;
        /** When it first came up (ms), and how far its dissolve in has gone (0..255). */
        long appeared;
        int fade;
        final int[] page = new int[VoxelMesher.LEVELS], first = new int[VoxelMesher.LEVELS], count = new int[VoxelMesher.LEVELS];
        /** Per level, each face's first quad within the level's run (down, up, north, south, west, east, detail, end). */
        final int[][] starts = new int[VoxelMesher.LEVELS][8];
    }

    private final List<Page> pages = new ArrayList<>();
    private final Chunk[] chunks = new Chunk[MAX_SLOTS];
    private final IntArrayList live = new IntArrayList();
    private final Long2IntOpenHashMap slotOf = new Long2IntOpenHashMap();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet pinned = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private long quadsKept;
    private double lastCamX, lastCamZ;
    private final IntArrayList freeSlots = new IntArrayList();
    private int nextSlot;
    private final GpuBuffer chunkTable, lookTable, indices;
    private final MappableRingBuffer params = new MappableRingBuffer(() -> "awaken city sight params", 130, 32);
    private final GpuTexture coverage;
    final GpuTextureView coverageView;
    private final ByteBuffer coverageBytes = MemoryUtil.memCalloc(256 * 256);
    /** This frame's coverage (the chunks drawn, at their dissolve's share) and the one on the GPU. */
    private final byte[] cover = new byte[256 * 256], coverShown = new byte[256 * 256];
    private boolean coverageDirty = true, cappedLogged;
    private final ByteBuffer staging = MemoryUtil.memAlloc(PAGE_QUADS * 16).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer one = MemoryUtil.memAlloc(16).order(ByteOrder.LITTLE_ENDIAN);
    final VoxelLooks looks = new VoxelLooks();
    /** Each worker's mesher (it reuses its arrays), reading this layer's looks. */
    final ThreadLocal<VoxelMesher> mesher = ThreadLocal.withInitial(() -> new VoxelMesher(looks));
    /** Each slot's last meshed snapshot's hash (workers write, so a chunk taken again unchanged isn't meshed again). */
    final long[] hashes = new long[MAX_SLOTS];
    /** Time spent meshing and chunks meshed (status). */
    final java.util.concurrent.atomic.AtomicLong meshNanos = new java.util.concurrent.atomic.AtomicLong(), meshed = new java.util.concurrent.atomic.AtomicLong(),
            empty = new java.util.concurrent.atomic.AtomicLong();
    /** Meshing jobs on the workers. */
    final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
    final ConcurrentLinkedQueue<VoxelMesh> finished = new ConcurrentLinkedQueue<>();
    private final boolean indirect = "Vulkan".equalsIgnoreCase(RenderSystem.getDevice().getDeviceInfo().backendName());
    private final int levelMinY;

    /**
     * One view's draws: the screen's, or a cached horizon slice's (FarHorizon). Per draw its page (+ EDGED for those
     * needing the discarding pipeline), quad count and first quad, and the view's own indirect commands.
     */
    private static final class Plan {
        final IntArrayList page = new IntArrayList(), quads = new IntArrayList(), first = new IntArrayList();
        /** Quads drawn, those by the discarding pipeline (edges, fades, cut-outs), and the detail run's (status). */
        int quadsDrawn, chunksDrawn, edgedQuads, detailQuads;
        final MappableRingBuffer commands;
        boolean used;

        Plan(String label) {
            commands = new MappableRingBuffer(() -> label, GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_INDIRECT_PARAMETERS, MAX_COMMANDS * COMMAND_BYTES);
        }

        void clear() {
            page.clear();
            quads.clear();
            first.clear();
            quadsDrawn = 0;
            chunksDrawn = 0;
            edgedQuads = 0;
            detailQuads = 0;
        }
    }

    private final Plan screen = new Plan("awaken city sight draws");
    private final Plan[] slices = new Plan[FarHorizon.MAX_RENDERS];
    /**
     * A draw near the render distance's edge or in a level's cross-fade, drawn by the pipeline that discards (Sodium's
     * sections, the dither). The rest are drawn without discard, so the GPU's own hidden-surface removal works (Apple
     * GPUs shade every fragment of a shader that may discard: the city's hidden walls cost as much as its visible ones).
     */
    static final int EDGED = 1 << 20;
    int quadsDrawn, chunksDrawn;
    /** Chunks whose drawn blocks changed since the cached horizon last heard (a mesh up, a dissolve's step, one let go). */
    final it.unimi.dsi.fastutil.longs.LongArrayList changed = new it.unimi.dsi.fastutil.longs.LongArrayList();
    private final float[] reach = new float[VoxelMesher.LEVELS];
    /** How far the detail run draws this frame (blocks). */
    private float detailReach;

    VoxelLayer(int levelMinY) {
        this.levelMinY = levelMinY;
        for (int k = 0; k < slices.length; k++) slices[k] = new Plan("awaken city sight horizon draws " + k);
        var device = RenderSystem.getDevice();
        chunkTable = device.createBuffer(() -> "awaken city sight chunks", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, (long) MAX_SLOTS * 16);
        lookTable = device.createBuffer(() -> "awaken city sight looks", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, (long) VoxelLooks.MAX * 32);
        ByteBuffer idx = MemoryUtil.memAlloc(DRAW_QUADS * 6 * 4).order(ByteOrder.nativeOrder());
        for (int q = 0; q < DRAW_QUADS; q++) {
            int v = q * 4;
            idx.putInt(v).putInt(v + 1).putInt(v + 2).putInt(v + 2).putInt(v + 3).putInt(v);
        }
        idx.flip();
        indices = device.createBuffer(() -> "awaken city sight quad indices", GpuBuffer.USAGE_INDEX, idx);
        MemoryUtil.memFree(idx);
        coverage = device.createTexture(() -> "awaken city sight coverage", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.R8_UNORM, 256, 256, 1, 1);
        coverageView = device.createTextureView(coverage);
    }

    /** A slot for chunk (cx, cz): its own if it has one, else a new one (or -1 when all are taken). */
    int slot(int cx, int cz) {
        long key = ChunkPos.pack(cx, cz);
        if (slotOf.containsKey(key)) return slotOf.get(key);
        int s = !freeSlots.isEmpty() ? freeSlots.removeInt(freeSlots.size() - 1) : nextSlot < MAX_SLOTS ? nextSlot++ : -1;
        if (s < 0) return -1;
        slotOf.put(key, s);
        return s;
    }

    boolean has(int cx, int cz) {
        int s = slotOf.getOrDefault(ChunkPos.pack(cx, cz), -1);
        return s >= 0 && chunks[s] != null;
    }

    int resident() {
        return live.size();
    }

    /** Uploads finished meshes, up to about {@code budget} bytes, and the looks met since. */
    void upload(int budget) {
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        int n = looks.count();
        if (n > looks.uploaded) {
            ByteBuffer b = MemoryUtil.memAlloc((n - looks.uploaded) * 32).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = looks.uploaded; i < n; i++) for (int k = 0; k < 8; k++) b.putFloat(looks.table[i * 8 + k]);
            b.flip();
            enc.writeToBuffer(lookTable.slice((long) looks.uploaded * 32, (long) (n - looks.uploaded) * 32), b);
            MemoryUtil.memFree(b);
            looks.uploaded = n;
        }
        int sent = 0;
        VoxelMesh m;
        while (sent < budget && (m = finished.poll()) != null) {
            sent += put(enc, m);
        }
        // the dissolves under way: each chunk's share in the chunk table (City Sight's dither) and the coverage mask (the
        // far terrain's, its complement)
        if (!fading.isEmpty()) {
            long now = net.minecraft.util.Util.getMillis();
            for (int i = fading.size() - 1; i >= 0; i--) {
                int s = fading.getInt(i);
                Chunk c = chunks[s];
                if (c == null) {
                    fading.removeInt(i);
                    continue;
                }
                int f = (int) Math.min(255L, (now - c.appeared) * 255L / FADE_MS);
                if (f == c.fade) continue;
                c.fade = f;
                changed.add(ChunkPos.pack(c.cx, c.cz));
                one.clear();
                one.putInt(c.cx * 16).putInt(levelMinY).putInt(c.cz * 16).putInt(f).flip();
                enc.writeToBuffer(chunkTable.slice((long) s * 16, 16), one);
                if (f == 255) fading.removeInt(i);
            }
        }
    }

    /**
     * After {@link #plan}: the coverage mask for this frame, the chunks the plan draws (each at its dissolve's share),
     * uploaded when it changed. Before the far terrain draws.
     */
    void cover() {
        if (!coverageDirty && java.util.Arrays.equals(cover, coverShown)) return;
        coverageDirty = false;
        System.arraycopy(cover, 0, coverShown, 0, cover.length);
        coverageBytes.clear();
        coverageBytes.put(cover).flip();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(coverage, coverageBytes, 0, 0, 0, 0, 256, 256);
    }

    private int put(CommandEncoder enc, VoxelMesh m) {
        int s = m.slot();
        if (s < 0 || s >= MAX_SLOTS || slotOf.getOrDefault(ChunkPos.pack(m.chunkX(), m.chunkZ()), -1) != s) return 0;
        Chunk c = chunks[s];
        if (c != null) release(c);
        else {
            c = new Chunk();
            chunks[s] = c;
            live.add(s);
            c.appeared = net.minecraft.util.Util.getMillis();
            c.fade = 0;
            fading.add(s);
        }
        c.cx = m.chunkX();
        c.cz = m.chunkZ();
        changed.add(ChunkPos.pack(c.cx, c.cz));
        c.minY = m.minY();
        c.maxY = m.maxY();
        c.groundY = m.groundY();
        int bytes = 0;
        for (int l = 0; l < VoxelMesher.LEVELS; l++) {
            int[] q = m.quads()[l];
            int count = q.length / VoxelMesher.INTS;
            c.count[l] = count;
            quadsKept += count;
            System.arraycopy(m.starts()[l], 0, c.starts[l], 0, 8);
            if (count == 0) continue;
            int[] at = allocate(count);
            c.page[l] = at[0];
            c.first[l] = at[1];
            // one bulk copy (an int at a time was ~1.5M calls a frame at the upload budget)
            staging.clear();
            staging.asIntBuffer().put(q);
            staging.limit(q.length * 4);
            enc.writeToBuffer(pages.get(at[0]).buffer.slice((long) at[1] * 16, (long) count * 16), staging);
            bytes += count * 16;
        }
        one.clear();
        one.putInt(m.chunkX() * 16).putInt(levelMinY).putInt(m.chunkZ() * 16).putInt(c.fade).flip();
        enc.writeToBuffer(chunkTable.slice((long) s * 16, 16), one);
        return bytes + 16;
    }

    private int[] allocate(int count) {
        if (count > PAGE_QUADS) count = PAGE_QUADS;
        for (int p = 0; p < pages.size(); p++) {
            Page page = pages.get(p);
            for (int i = 0; i < page.free.size(); i++) {
                int[] f = page.free.get(i);
                if (f[1] >= count) {
                    int first = f[0];
                    f[0] += count;
                    f[1] -= count;
                    if (f[1] == 0) page.free.remove(i);
                    return new int[]{p, first};
                }
            }
            if (PAGE_QUADS - page.used >= count) {
                int first = page.used;
                page.used += count;
                return new int[]{p, first};
            }
        }
        pages.add(new Page(pages.size()));
        Page page = pages.getLast();
        page.used = count;
        return new int[]{pages.size() - 1, 0};
    }

    private void release(Chunk c) {
        for (int l = 0; l < VoxelMesher.LEVELS; l++) {
            if (c.count[l] > 0) pages.get(c.page[l]).free.add(new int[]{c.first[l], c.count[l]});
            quadsKept -= c.count[l];
            c.count[l] = 0;
        }
    }

    /** The city's chunks: kept at any distance (the city is drawn from its real blocks however far away). */
    void pin(long[] keys) {
        pinned.clear();
        for (long k : keys) pinned.add(k);
    }

    /**
     * About once a second: drops chunks past {@link #KEEP} (never the city's), the farthest first while over
     * {@link #MAX_QUADS}, and redraws the coverage mask for the chunks within {@link #COVER} (it is toroidal, 4 km
     * across: a chunk farther off would stand in for one near).
     */
    void tend(double camX, double camZ, java.util.function.LongConsumer forgotten) {
        lastCamX = camX;
        lastCamZ = camZ;
        int n = live.size();
        long[] order = new long[n];
        for (int i = 0; i < n; i++) {
            Chunk c = chunks[live.getInt(i)];
            double dx = c.cx * 16 + 8 - camX, dz = c.cz * 16 + 8 - camZ;
            order[i] = (long) Math.min(Integer.MAX_VALUE, (long) Math.sqrt(dx * dx + dz * dz)) << 32 | live.getInt(i);
        }
        java.util.Arrays.sort(order);
        for (int i = n - 1; i >= 0; i--) {
            int s = (int) order[i];
            Chunk c = chunks[s];
            long key = ChunkPos.pack(c.cx, c.cz);
            if (pinned.contains(key)) continue;
            if ((order[i] >>> 32) <= KEEP && quadsKept <= MAX_QUADS) break;
            release(c);
            chunks[s] = null;
            live.rem(s);
            changed.add(key);
            slotOf.remove(key);
            freeSlots.add(s);
            hashes[s] = 0;
            forgotten.accept(key);
        }
    }

    /** Forgets every chunk (a new world, a resource reload). */
    void clear() {
        for (int s : live) {
            Chunk c = chunks[s];
            if (c != null) release(c);
            chunks[s] = null;
        }
        live.clear();
        slotOf.clear();
        freeSlots.clear();
        java.util.Arrays.fill(hashes, 0L);
        quadsKept = 0;
        nextSlot = 0;
        for (Page p : pages) {
            p.used = 0;
            p.free.clear();
        }
        finished.clear();
        fading.clear();
        java.util.Arrays.fill(cover, (byte) 0);
        coverageDirty = true;
    }

    /**
     * This frame's draws: every chunk at the level its blocks' size picks (and the next one too inside a cross-fade),
     * in view, not drawn whole by Sodium, and not drawn by the cached horizon this frame.
     */
    void plan(FarRenderer renderer, double camX, double camY, double camZ, float pixelsPerBlockAt1, int realWithin, boolean on, float haze,
            FarHorizon horizon) {
        screen.clear();
        java.util.Arrays.fill(cover, (byte) 0);
        planned = on;
        if (!on) return;
        // a level-l cell (2^l blocks) looks PIXELS pixels at this distance, more where the air has dimmed it (detail
        // follows the air: a cell's error counts for what shows through the haze, so hazier distance needs less of it)
        for (int l = 0; l < VoxelMesher.LEVELS; l++) {
            if (l == VoxelMesher.LEVELS - 1) {
                reach[l] = Float.MAX_VALUE;
                continue;
            }
            float d = (1 << l) * pixelsPerBlockAt1 / PIXELS;
            for (int k = 0; k < 3; k++) d = (1 << l) * pixelsPerBlockAt1 / PIXELS * (float) Math.exp(-haze * d);
            reach[l] = d;
        }
        float dr = pixelsPerBlockAt1 / DETAIL_PIXELS;
        for (int k = 0; k < 3; k++) dr = pixelsPerBlockAt1 / DETAIL_PIXELS * (float) Math.exp(-haze * dr);
        detailReach = dr;
        try (var view = params.currentBuffer().map(false, true)) {
            ByteBuffer d = view.data();
            d.putFloat(0, reach[0]).putFloat(4, reach[1]).putFloat(8, reach[2]).putFloat(12, BAND);
            d.putFloat(16, levelMinY).putFloat(20, FarRenderer.SECTIONS).putFloat(24, detailReach).putFloat(28, 0f);
        }
        collect(screen, renderer, null, horizon, camX, camY, camZ, realWithin);
        quadsDrawn = screen.quadsDrawn;
        chunksDrawn = screen.chunksDrawn;
    }


    private boolean planned;

    /** A horizon slice's draws (after the screen's plan, whose levels it shares): the chunks in the cached zone in its frustum. */
    void planSlice(int k, FarRenderer renderer, FrustumIntersection frustum, FarHorizon horizon, double camX, double camY, double camZ, int realWithin) {
        slices[k].clear();
        if (planned) collect(slices[k], renderer, frustum, horizon, camX, camY, camZ, realWithin);
    }

    /**
     * Gathers a view's draws: the screen's ({@code slice} null: in its frustum, less what the horizon serves, and the
     * coverage mask for every chunk drawn from here, in view or not, so the far land steps aside the same way in any
     * view the horizon draws), or a horizon slice's (in its frustum, the cached zone only).
     */
    private void collect(Plan p, FarRenderer renderer, FrustumIntersection slice, FarHorizon horizon, double camX, double camY, double camZ,
            int realWithin) {
        boolean capped = false;
        for (int i = 0; i < live.size(); i++) {
            Chunk c = chunks[live.getInt(i)];
            float ax = (float) (c.cx * 16 - camX), az = (float) (c.cz * 16 - camZ), bx = ax + 16, bz = az + 16;
            float ay = (float) (c.minY - camY), by = (float) (c.maxY - camY);
            float nx = Math.max(0, Math.max(ax, -bx)), nz = Math.max(0, Math.max(az, -bz));
            float near = (float) Math.sqrt(nx * nx + nz * nz);
            float fx = Math.max(Math.abs(ax), Math.abs(bx)), fz = Math.max(Math.abs(az), Math.abs(bz));
            float far = (float) Math.sqrt(fx * fx + fz * fz);
            // Sodium draws all of it: inside its distance (with a block's margin, as it culls) and every section it can be
            // seen in built, from the chunk's lowest ground up (not the sealed ones under it, which Sodium never builds:
            // counting those drew most chunks for 2 ms more at the Roost; nor a timer: on a busy machine Sodium builds
            // late, and land it hadn't built stood as a hole). The chunks drawn hide per section (the section mask)
            if (far < realWithin - 2 && Math.max(Math.abs(ay), Math.abs(by)) < realWithin - 2
                    && renderer.sectionsBuilt(c.cx, c.cz, c.groundY, c.maxY, levelMinY)) continue;
            boolean cached = horizon.cachedCity(ax, az, bx, bz);
            int at = (c.cz & 255) << 8 | (c.cx & 255);
            if (slice == null) {
                if (near < COVER && hasLevel(c, near, far)) cover[at] = (byte) c.fade;
                if (!renderer.inView(ax, ay, az, bx, by, bz)) continue;
                if (cached && horizon.serves(ax, ay, az, bx, by, bz)) continue;
            } else if (!cached || !slice.testAab(ax, ay, az, bx, by, bz)) {
                continue;
            }
            // faces turned away from the camera across the whole chunk aren't drawn: down, up, north, south, west, east
            int faces = (camY < c.maxY ? 1 : 0) | (camY > c.minY ? 2 : 0) | (camZ < c.cz * 16 + 16 ? 4 : 0) | (camZ > c.cz * 16 ? 8 : 0)
                    | (camX < c.cx * 16 + 16 ? 16 : 0) | (camX > c.cx * 16 ? 32 : 0);
            boolean drawn = false, fits = true;
            int mark = p.page.size(), quadsMark = p.quadsDrawn;
            boolean edge = near < realWithin + 2;
            for (int l = 0; l < VoxelMesher.LEVELS; l++) {
                float from = l == 0 ? 0f : reach[l - 1], to = reach[l] * (1f + BAND);
                if (far < from || near > to) continue;
                if (c.count[l] == 0) continue;
                // in a fade: inside the band past the finer level's reach, or past its own
                boolean fading = (l > 0 && near < from * (1f + BAND)) || (l < VoxelMesher.LEVELS - 1 && far > reach[l]) || c.fade < 255;
                int pageFlag = edge || fading ? EDGED : 0;
                int[] st = c.starts[l];
                for (int d = 0; d < 6; ) {
                    if ((faces & 1 << d) == 0 || st[d + 1] == st[d]) {
                        d++;
                        continue;
                    }
                    // a run of visible faces in one range
                    int e = d;
                    while (e < 6 && (faces & 1 << e) != 0) e++;
                    if (st[e] > st[d]) fits &= add(p, c.page[l] | pageFlag, c.first[l] + st[d], st[e] - st[d]);
                    d = e;
                }
                // thin things and plants, while a block shows big enough for them (always the discarding pipeline: cut-outs)
                if (l == 0 && st[7] > st[6] && near < detailReach * (1f + BAND)) {
                    fits &= add(p, c.page[l] | EDGED, c.first[l] + st[6], st[7] - st[6]);
                    p.detailQuads += st[7] - st[6];
                }
                drawn = true;
            }
            if (!fits) {
                // out of room for this frame's draws: the chunk isn't drawn at all (never in part), so the far land
                // stands in for it
                p.page.size(mark);
                p.quads.size(mark);
                p.first.size(mark);
                p.quadsDrawn = quadsMark;
                drawn = false;
                capped = true;
                if (slice == null) cover[at] = 0;
            }
            if (!drawn) continue;
            p.chunksDrawn++;
        }
        if (capped && !cappedLogged) {
            cappedLogged = true;
            dev.awaken.Awaken.LOG.warn("[farsight] city sight: more than {} draws in a frame; the rest fall back to the far land", MAX_COMMANDS);
        }
        sortByPage(p);
    }

    /** Whether the chunk has quads at a level its distance draws (it is drawn wherever it's in view). */
    private boolean hasLevel(Chunk c, float near, float far) {
        for (int l = 0; l < VoxelMesher.LEVELS; l++) {
            float from = l == 0 ? 0f : reach[l - 1], to = reach[l] * (1f + BAND);
            if (far >= from && near <= to && c.count[l] > 0) return true;
        }
        return false;
    }

    /** Adds one range's draws; false (and nothing added past the room left) when this frame's draws are full. */
    private static boolean add(Plan p, int page, int first, int count) {
        for (int at = 0; at < count; at += DRAW_QUADS) {
            if (p.page.size() >= MAX_COMMANDS) return false;
            int n = Math.min(DRAW_QUADS, count - at);
            p.page.add(page);
            p.quads.add(n);
            p.first.add(first + at);
            p.quadsDrawn += n;
            if ((page & EDGED) != 0) p.edgedQuads += n;
        }
        return true;
    }

    /** The screen's quads by the discarding pipeline and the detail run's (status). */
    String quadsStatus() {
        return " edged=" + screen.edgedQuads + " detail=" + screen.detailQuads;
    }

    /** This frame's draw commands on the screen (status). */
    int commands() {
        return screen.page.size();
    }

    /** Draws the screen's planned chunks into the open pass (Far Sight's targets), page by page. */
    void draw(RenderPass pass, GpuBufferSlice projection, GpuBufferSlice frame, GpuTextureView lightmap, GpuTextureView chunkMask) {
        draw(screen, pass, projection, frame, lightmap, chunkMask);
    }

    /** Draws a horizon slice's planned chunks (FarHorizon). */
    void drawSlice(int k, RenderPass pass, GpuBufferSlice projection, GpuBufferSlice frame, GpuTextureView lightmap, GpuTextureView chunkMask) {
        draw(slices[k], pass, projection, frame, lightmap, chunkMask);
    }

    private void draw(Plan plan, RenderPass pass, GpuBufferSlice projection, GpuBufferSlice frame, GpuTextureView lightmap, GpuTextureView chunkMask) {
        if (plan.page.isEmpty()) return;
        GpuBuffer p = params.currentBuffer();
        AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        // the plain draws first (sorted ahead of the edged ones), then the pipeline that discards
        boolean edgedBound = (plan.page.getInt(0) & EDGED) != 0;
        pass.setPipeline(RenderSystem.getCompiledPipeline(edgedBound ? FarPipelines.VOXELS : FarPipelines.VOXELS_PLAIN));
        bindAll(pass, projection, frame, p, atlas, lightmap, chunkMask);
        GpuBuffer cmd = plan.commands.currentBuffer();
        int n = plan.page.size();
        if (indirect) {
            try (var view = cmd.map(false, true)) {
                ByteBuffer d = view.data();
                for (int i = 0; i < n; i++) {
                    int at = i * COMMAND_BYTES;
                    d.putInt(at, plan.quads.getInt(i) * 6).putInt(at + 4, 1).putInt(at + 8, 0).putInt(at + 12, plan.first.getInt(i) * 4).putInt(at + 16, 0);
                }
            }
        }
        // the plan is grouped by page and kind (sortByPage): one run of draws each
        for (int i = 0; i < n; ) {
            int key = plan.page.getInt(i), j = i;
            while (j < n && plan.page.getInt(j) == key) j++;
            if ((key & EDGED) != 0 && !edgedBound) {
                // the same bindings carry over to the second pipeline (same layout)
                pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.VOXELS));
                bindAll(pass, projection, frame, p, atlas, lightmap, chunkMask);
                edgedBound = true;
            }
            int pg = key & (EDGED - 1);
            pass.setUniform("Quads", pages.get(pg).buffer);
            if (indirect) {
                pass.drawIndexedIndirect(cmd.slice((long) i * COMMAND_BYTES, (long) (j - i) * COMMAND_BYTES), j - i);
            } else {
                for (int k = i; k < j; k++) pass.drawIndexed(plan.quads.getInt(k) * 6, 1, 0, plan.first.getInt(k) * 4, 0);
            }
            i = j;
        }
        plan.used = true;
    }

    private void bindAll(RenderPass pass, GpuBufferSlice projection, GpuBufferSlice frame, GpuBuffer p, AbstractTexture atlas, GpuTextureView lightmap,
            GpuTextureView chunkMask) {
        pass.setUniform("Projection", projection);
        pass.setUniform("Fog", RenderSystem.getShaderFog());
        pass.setUniform("FarFrame", frame);
        pass.setUniform("FarVoxel", p);
        pass.setUniform("Chunks", chunkTable);
        pass.setUniform("Looks", lookTable);
        pass.setUniform("AtlasSampler", atlas.getTextureView(), atlas.getSampler());
        pass.setUniform("LightSampler", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        pass.setUniform("SectionSampler", chunkMask, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        pass.setIndexBuffer(indices, IndexType.INT);
    }

    /** After the passes have closed: the ring buffers move on (a fence, which can't be made inside a pass). */
    void endFrame() {
        params.rotate();
        if (screen.used) screen.commands.rotate();
        screen.used = false;
        for (Plan p : slices) {
            if (p.used) p.commands.rotate();
            p.used = false;
        }
    }

    // sortByPage's scratch, sized for the most draws a plan holds (add() stops at MAX_COMMANDS): nothing per frame
    private final long[] sortKeys = new long[MAX_COMMANDS];
    private final int[] sortPage = new int[MAX_COMMANDS], sortQuads = new int[MAX_COMMANDS], sortFirst = new int[MAX_COMMANDS];

    /** Groups the plan's draws by page, so each page's indirect commands are one run. */
    private void sortByPage(Plan plan) {
        IntArrayList drawPage = plan.page, drawQuads = plan.quads, drawFirst = plan.first;
        int n = drawPage.size();
        if (n < 2) return;
        long[] keys = sortKeys;
        for (int i = 0; i < n; i++) keys[i] = (long) drawPage.getInt(i) << 32 | i;
        java.util.Arrays.sort(keys, 0, n);
        int[] p = sortPage, q = sortQuads, f = sortFirst;
        for (int i = 0; i < n; i++) {
            int k = (int) keys[i];
            p[i] = drawPage.getInt(k);
            q[i] = drawQuads.getInt(k);
            f[i] = drawFirst.getInt(k);
        }
        drawPage.clear();
        drawQuads.clear();
        drawFirst.clear();
        for (int i = 0; i < n; i++) {
            drawPage.add(p[i]);
            drawQuads.add(q[i]);
            drawFirst.add(f[i]);
        }
    }

    long memoryBytes() {
        return (long) pages.size() * PAGE_QUADS * 16 + (long) MAX_SLOTS * 16 + (long) VoxelLooks.MAX * 32 + (long) DRAW_QUADS * 24 + 65536;
    }

    @Override
    public void close() {
        for (Page p : pages) p.buffer.close();
        pages.clear();
        chunkTable.close();
        lookTable.close();
        indices.close();
        screen.commands.close();
        for (Plan p : slices) p.commands.close();
        params.close();
        coverageView.close();
        coverage.close();
        MemoryUtil.memFree(coverageBytes);
        MemoryUtil.memFree(staging);
        MemoryUtil.memFree(one);
    }
}
