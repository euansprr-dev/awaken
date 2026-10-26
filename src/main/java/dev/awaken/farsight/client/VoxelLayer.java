package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
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
 * mask (256×256 chunks, toroidal) tells the far terrain and far trees where City Sight stands, so they step aside.
 */
final class VoxelLayer implements AutoCloseable {
    static final int PAGE_QUADS = 1 << 18, MAX_SLOTS = 1 << 16, DRAW_QUADS = 1 << 16, MAX_COMMANDS = 8192, COMMAND_BYTES = 20;
    /** A level's blocks give way to the next coarser level once they look this many pixels or fewer. */
    static final float PIXELS = 2.0f;
    /** Chunks are kept this far (blocks) and drawn in the coverage mask this far; the city's are kept at any distance. */
    static final float KEEP = 1900f, COVER = 2000f;
    /** At most this many quads kept (256 MB of pages): past it, the farthest chunks go first. */
    static final long MAX_QUADS = 16L << 20;
    /** The cross-fade between two levels, as a share of the nearer one's reach. */
    static final float BAND = 0.15f;

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
        int cx, cz, minY, maxY;
        final int[] page = new int[VoxelMesher.LEVELS], first = new int[VoxelMesher.LEVELS], count = new int[VoxelMesher.LEVELS];
        /** Per level, each face's first quad within the level's run (down, up, north, south, west, east, end). */
        final int[][] starts = new int[VoxelMesher.LEVELS][7];
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
    private final MappableRingBuffer commands = new MappableRingBuffer(() -> "awaken city sight draws",
            GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_INDIRECT_PARAMETERS, MAX_COMMANDS * COMMAND_BYTES);
    private final MappableRingBuffer params = new MappableRingBuffer(() -> "awaken city sight params", 130, 32);
    private final GpuTexture coverage;
    final GpuTextureView coverageView;
    private final ByteBuffer coverageBytes = MemoryUtil.memCalloc(256 * 256);
    private boolean coverageDirty = true;
    private final ByteBuffer staging = MemoryUtil.memAlloc(PAGE_QUADS * 16).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer one = MemoryUtil.memAlloc(16).order(ByteOrder.LITTLE_ENDIAN);
    final VoxelLooks looks = new VoxelLooks();
    /** Each worker's mesher (it reuses its arrays), reading this layer's looks. */
    final ThreadLocal<VoxelMesher> mesher = ThreadLocal.withInitial(() -> new VoxelMesher(looks));
    /** Each slot's last meshed snapshot's hash (workers write, so a chunk taken again unchanged isn't meshed again). */
    final long[] hashes = new long[MAX_SLOTS];
    /** Time spent meshing and chunks meshed (status). */
    final java.util.concurrent.atomic.AtomicLong meshNanos = new java.util.concurrent.atomic.AtomicLong(), meshed = new java.util.concurrent.atomic.AtomicLong();
    /** Meshing jobs on the workers. */
    final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
    final ConcurrentLinkedQueue<VoxelMesh> finished = new ConcurrentLinkedQueue<>();
    private final boolean indirect = "Vulkan".equalsIgnoreCase(RenderSystem.getDevice().getDeviceInfo().backendName());
    private final int levelMinY;

    // this frame's draws per page: page (+ EDGED for those needing the discarding pipeline), quad count, first quad
    private final IntArrayList drawPage = new IntArrayList(), drawQuads = new IntArrayList(), drawFirst = new IntArrayList();
    /**
     * A draw near the render distance's edge or in a level's cross-fade, drawn by the pipeline that discards (Sodium's
     * sections, the dither). The rest are drawn without discard, so the GPU's own hidden-surface removal works (Apple
     * GPUs shade every fragment of a shader that may discard: the city's hidden walls cost as much as its visible ones).
     */
    static final int EDGED = 1 << 20;
    int quadsDrawn, chunksDrawn;
    private final float[] reach = new float[VoxelMesher.LEVELS];

    VoxelLayer(int levelMinY) {
        this.levelMinY = levelMinY;
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
        if (coverageDirty) {
            coverageDirty = false;
            coverageBytes.clear();
            enc.writeToTexture(coverage, coverageBytes, 0, 0, 0, 0, 256, 256);
        }
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
            double dx = m.chunkX() * 16 + 8 - lastCamX, dz = m.chunkZ() * 16 + 8 - lastCamZ;
            if (dx * dx + dz * dz < COVER * COVER) {
                coverageBytes.put((m.chunkZ() & 255) << 8 | (m.chunkX() & 255), (byte) 255);
                coverageDirty = true;
            }
        }
        c.cx = m.chunkX();
        c.cz = m.chunkZ();
        c.minY = m.minY();
        c.maxY = m.maxY();
        int bytes = 0;
        for (int l = 0; l < VoxelMesher.LEVELS; l++) {
            int[] q = m.quads()[l];
            int count = q.length / VoxelMesher.INTS;
            c.count[l] = count;
            quadsKept += count;
            System.arraycopy(m.starts()[l], 0, c.starts[l], 0, 7);
            if (count == 0) continue;
            int[] at = allocate(count);
            c.page[l] = at[0];
            c.first[l] = at[1];
            staging.clear();
            for (int v : q) staging.putInt(v);
            staging.flip();
            enc.writeToBuffer(pages.get(at[0]).buffer.slice((long) at[1] * 16, (long) count * 16), staging);
            bytes += count * 16;
        }
        one.clear();
        one.putInt(m.chunkX() * 16).putInt(levelMinY).putInt(m.chunkZ() * 16).putInt(0).flip();
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
            slotOf.remove(key);
            freeSlots.add(s);
            hashes[s] = 0;
            forgotten.accept(key);
        }
        MemoryUtil.memSet(coverageBytes, 0);
        for (int i = 0; i < live.size(); i++) {
            Chunk c = chunks[live.getInt(i)];
            double dx = c.cx * 16 + 8 - camX, dz = c.cz * 16 + 8 - camZ;
            if (dx * dx + dz * dz < COVER * COVER) coverageBytes.put((c.cz & 255) << 8 | (c.cx & 255), (byte) 255);
        }
        coverageDirty = true;
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
        MemoryUtil.memSet(coverageBytes, 0);
        coverageDirty = true;
    }

    /**
     * This frame's draws: every chunk at the level its blocks' size picks (and the next one too inside a cross-fade),
     * in view, and not drawn whole by Sodium.
     */
    void plan(FarRenderer renderer, double camX, double camY, double camZ, float pixelsPerBlockAt1, int realWithin, boolean on) {
        drawPage.clear();
        drawQuads.clear();
        drawFirst.clear();
        quadsDrawn = 0;
        chunksDrawn = 0;
        if (!on) return;
        // a level-l cell (2^l blocks) looks PIXELS pixels at this distance
        for (int l = 0; l < VoxelMesher.LEVELS; l++) reach[l] = l == VoxelMesher.LEVELS - 1 ? Float.MAX_VALUE : (1 << l) * pixelsPerBlockAt1 / PIXELS;
        for (int i = 0; i < live.size(); i++) {
            Chunk c = chunks[live.getInt(i)];
            float ax = (float) (c.cx * 16 - camX), az = (float) (c.cz * 16 - camZ), bx = ax + 16, bz = az + 16;
            float ay = (float) (c.minY - camY), by = (float) (c.maxY - camY);
            float nx = Math.max(0, Math.max(ax, -bx)), nz = Math.max(0, Math.max(az, -bz));
            float near = (float) Math.sqrt(nx * nx + nz * nz);
            float fx = Math.max(Math.abs(ax), Math.abs(bx)), fz = Math.max(Math.abs(az), Math.abs(bz));
            float far = (float) Math.sqrt(fx * fx + fz * fz);
            // Sodium draws all of it: inside its distance (with a block's margin, as it culls) and shown (loaded, settled
            // or built: sections it hasn't built there are hidden behind others, and drawing them cost 2 ms at the Roost);
            // the chunks drawn hide per section (the section mask)
            if (far < realWithin - 2 && renderer.shown(c.cx, c.cz) && Math.max(Math.abs(ay), Math.abs(by)) < realWithin - 2) continue;
            if (!renderer.inView(ax, ay, az, bx, by, bz)) continue;
            // faces turned away from the camera across the whole chunk aren't drawn: down, up, north, south, west, east
            int faces = (camY < c.maxY ? 1 : 0) | (camY > c.minY ? 2 : 0) | (camZ < c.cz * 16 + 16 ? 4 : 0) | (camZ > c.cz * 16 ? 8 : 0)
                    | (camX < c.cx * 16 + 16 ? 16 : 0) | (camX > c.cx * 16 ? 32 : 0);
            boolean drawn = false;
            boolean edge = near < realWithin + 2;
            for (int l = 0; l < VoxelMesher.LEVELS; l++) {
                float from = l == 0 ? 0f : reach[l - 1], to = reach[l] * (1f + BAND);
                if (far < from || near > to) continue;
                if (c.count[l] == 0) continue;
                // in a fade: inside the band past the finer level's reach, or past its own
                boolean fading = (l > 0 && near < from * (1f + BAND)) || (l < VoxelMesher.LEVELS - 1 && far > reach[l]);
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
                    if (st[e] > st[d]) add(c.page[l] | pageFlag, c.first[l] + st[d], st[e] - st[d]);
                    d = e;
                }
                drawn = true;
            }
            if (drawn) chunksDrawn++;
        }
        sortByPage();
    }

    private void add(int page, int first, int count) {
        for (int at = 0; at < count; at += DRAW_QUADS) {
            if (drawPage.size() >= MAX_COMMANDS) return;
            int n = Math.min(DRAW_QUADS, count - at);
            drawPage.add(page);
            drawQuads.add(n);
            drawFirst.add(first + at);
            quadsDrawn += n;
        }
    }

    boolean any() {
        return !drawPage.isEmpty();
    }

    /** Draws the planned chunks into the open pass (Far Sight's targets), page by page. */
    void draw(RenderPass pass, GpuBuffer frame, GpuTextureView lightmap, GpuTextureView chunkMask) {
        if (drawPage.isEmpty()) return;
        GpuBuffer p = params.currentBuffer();
        try (var view = p.map(false, true)) {
            ByteBuffer d = view.data();
            d.putFloat(0, reach[0]).putFloat(4, reach[1]).putFloat(8, reach[2]).putFloat(12, BAND);
            d.putFloat(16, levelMinY).putFloat(20, FarRenderer.SECTIONS).putFloat(24, 0f).putFloat(28, 0f);
        }
        AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        // the plain draws first (sorted ahead of the edged ones), then the pipeline that discards
        boolean edgedBound = (drawPage.getInt(0) & EDGED) != 0;
        pass.setPipeline(RenderSystem.getCompiledPipeline(edgedBound ? FarPipelines.VOXELS : FarPipelines.VOXELS_PLAIN));
        bindAll(pass, frame, p, atlas, lightmap, chunkMask);
        GpuBuffer cmd = commands.currentBuffer();
        int n = drawPage.size();
        if (indirect) {
            try (var view = cmd.map(false, true)) {
                ByteBuffer d = view.data();
                for (int i = 0; i < n; i++) {
                    int at = i * COMMAND_BYTES;
                    d.putInt(at, drawQuads.getInt(i) * 6).putInt(at + 4, 1).putInt(at + 8, 0).putInt(at + 12, drawFirst.getInt(i) * 4).putInt(at + 16, 0);
                }
            }
        }
        // the plan is grouped by page and kind (sortByPage): one run of draws each
        for (int i = 0; i < n; ) {
            int key = drawPage.getInt(i), j = i;
            while (j < n && drawPage.getInt(j) == key) j++;
            if ((key & EDGED) != 0 && !edgedBound) {
                // the same bindings carry over to the second pipeline (same layout)
                pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.VOXELS));
                bindAll(pass, frame, p, atlas, lightmap, chunkMask);
                edgedBound = true;
            }
            int pg = key & (EDGED - 1);
            pass.setUniform("Quads", pages.get(pg).buffer);
            if (indirect) {
                pass.drawIndexedIndirect(cmd.slice((long) i * COMMAND_BYTES, (long) (j - i) * COMMAND_BYTES), j - i);
            } else {
                for (int k = i; k < j; k++) pass.drawIndexed(drawQuads.getInt(k) * 6, 1, 0, drawFirst.getInt(k) * 4, 0);
            }
            i = j;
        }
        used = true;
    }

    private boolean used;

    private void bindAll(RenderPass pass, GpuBuffer frame, GpuBuffer p, AbstractTexture atlas, GpuTextureView lightmap, GpuTextureView chunkMask) {
        pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
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

    /** After the pass has closed: the ring buffers move on (a fence, which can't be made inside a pass). */
    void endFrame() {
        if (!used) return;
        used = false;
        params.rotate();
        commands.rotate();
    }

    /** Groups the plan's draws by page, so each page's indirect commands are one run. */
    private void sortByPage() {
        int n = drawPage.size();
        if (n < 2) return;
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) keys[i] = (long) drawPage.getInt(i) << 32 | i;
        java.util.Arrays.sort(keys);
        int[] p = new int[n], q = new int[n], f = new int[n];
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
        commands.close();
        params.close();
        coverageView.close();
        coverage.close();
        MemoryUtil.memFree(coverageBytes);
        MemoryUtil.memFree(staging);
        MemoryUtil.memFree(one);
    }
}
