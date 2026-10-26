package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.farsight.FarColumns;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import dev.awaken.farsight.FarStore;
import dev.awaken.farsight.FarTrees;
import dev.awaken.farsight.FarWorkers;
import dev.awaken.farsight.Rings;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The rings' data on the client (SPEC §20, Far Sight 2): each ring's window, the patch every slot holds, and its
 * atlases of {@code 256 × (256·7)} texels, one 256×256 block per ring, addressed toroidally (a world grid corner's texel
 * is its coordinate masked to 255, so a moving window only rewrites the strip that came into view). A texel is the
 * ring's grid corner {@code (i · cell, j · cell)}, so a coarse ring's corners are every other corner of the finer ring:
 * <ul>
 *   <li>height (R16): the ground's top to a sixteenth of a block ({@link FarColumns#packHeight}), 0 unknown;</li>
 *   <li>colour (RGBA8): the ground's colour, flags in alpha;</li>
 *   <li>info (RGBA8): water depth, canopy height × 4, tree density;</li>
 *   <li>crown (RGBA8): the trees' crown colour and shape;</li>
 *   <li>the same four for the generated layer (what the real chunks seen there show: {@link FarStore}), which wins
 *       where it has a texel;</li>
 *   <li>shade (RGBA8, baked on the GPU by {@link FarGpu}): the normal's x and z, valley shade, sun.</li>
 * </ul>
 * A slot whose patch leaves its window is zeroed at once, so the shaders fall back to the coarser ring there until the
 * new patch arrives. Render thread only, except {@link #wanted}, which the workers call.
 */
final class FarField implements AutoCloseable {
    static final int WIDTH = Rings.SIZE, HEIGHT = Rings.SIZE * Rings.RINGS;
    private static final long NONE = Long.MIN_VALUE;
    private static final int PATCH_CELLS = Rings.PATCH * Rings.PATCH, SLOTS = Rings.PATCHES * Rings.PATCHES;

    final GpuTexture height, colour, info, crown, genHeight, genColour, genInfo, genCrown, shade;
    final GpuTextureView heightView, colourView, infoView, crownView, genHeightView, genColourView, genInfoView, genCrownView, shadeView;

    /** Each ring's window: its first patch; {@link #valid} false until placed. */
    final int[] winX = new int[Rings.RINGS], winZ = new int[Rings.RINGS];
    final boolean[] valid = new boolean[Rings.RINGS];
    /** The patch each slot expects (read by the workers), whether it was asked for, and whether it has arrived. */
    private final AtomicLongArray[] expect = new AtomicLongArray[Rings.RINGS];
    private final boolean[][] asked = new boolean[Rings.RINGS][SLOTS];
    final boolean[][] ready = new boolean[Rings.RINGS][SLOTS];
    /** Lowest and highest top per slot (whole blocks + {@link FarColumns#HEIGHT_BASE}): predicted, and the generated layer's. */
    final short[][] lo = new short[Rings.RINGS][SLOTS], hi = new short[Rings.RINGS][SLOTS], genHi = new short[Rings.RINGS][SLOTS];
    /** Slots still to arrive per ring (hidden ones excluded). */
    final int[] missing = new int[Rings.RINGS];
    /** Generated-layer jobs per slot: 0 none queued, 1 queued, 2 queued and asked again since (run it once more). */
    private final byte[][] genQueued = new byte[Rings.RINGS][SLOTS];
    /** Each ring's window centre and hidden radius as last placed, for generated-layer refreshes. */
    private final double[] centreX = new double[Rings.RINGS], centreZ = new double[Rings.RINGS], hiddenR = new double[Rings.RINGS];
    /** Rings whose heights changed since the last bake. */
    int dirty;
    /** Generated-layer jobs asked for, and patches of it uploaded with something in them (status). */
    int genAsked, genUploaded;
    volatile int epoch;

    /**
     * The far forests ({@link FarTrees}): per ring 1-4 slot, two runs of {@link FarTrees#MAX} trees (predicted, then the
     * real chunks'), 16 bytes each, in one texel buffer the tree shader reads; and how many each run holds.
     */
    final GpuBuffer trees;
    final int[][] treeCount = new int[Rings.RINGS][SLOTS * 2];
    static final int TREE_BYTES = 16, TREE_RINGS = FarTrees.LAST_RING - FarTrees.FIRST_RING + 1;
    private final ByteBuffer treeStaging = MemoryUtil.memAlloc(FarTrees.MAX * TREE_BYTES).order(ByteOrder.LITTLE_ENDIAN);

    /** A tree run's index in the buffer (runs of {@link FarTrees#MAX} trees). */
    static int treeRun(int ring, int slot, boolean generated) {
        return ((ring - FarTrees.FIRST_RING) * SLOTS + slot) * 2 + (generated ? 1 : 0);
    }

    private final ByteBuffer zeros, staging;
    private final FarWorkers.Wanted wanted = this::wanted;

    FarField() {
        var device = RenderSystem.getDevice();
        int copy = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
        height = device.createTexture(() -> "awaken far sight heights", copy, GpuFormat.R16_UNORM, WIDTH, HEIGHT, 1, 1);
        colour = device.createTexture(() -> "awaken far sight colours", copy, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        info = device.createTexture(() -> "awaken far sight info", copy, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        crown = device.createTexture(() -> "awaken far sight crowns", copy, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        genHeight = device.createTexture(() -> "awaken far sight generated heights", copy, GpuFormat.R16_UNORM, WIDTH, HEIGHT, 1, 1);
        genColour = device.createTexture(() -> "awaken far sight generated colours", copy, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        genInfo = device.createTexture(() -> "awaken far sight generated info", copy, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        genCrown = device.createTexture(() -> "awaken far sight generated crowns", copy, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        shade = device.createTexture(() -> "awaken far sight shade", copy | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        heightView = device.createTextureView(height);
        colourView = device.createTextureView(colour);
        infoView = device.createTextureView(info);
        crownView = device.createTextureView(crown);
        genHeightView = device.createTextureView(genHeight);
        genColourView = device.createTextureView(genColour);
        genInfoView = device.createTextureView(genInfo);
        genCrownView = device.createTextureView(genCrown);
        shadeView = device.createTextureView(shade);
        trees = device.createBuffer(() -> "awaken far sight trees", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER,
                (long) TREE_RINGS * SLOTS * 2 * FarTrees.MAX * TREE_BYTES);
        for (int r = 0; r < Rings.RINGS; r++) {
            expect[r] = new AtomicLongArray(SLOTS);
            for (int i = 0; i < SLOTS; i++) expect[r].set(i, NONE);
        }
        zeros = MemoryUtil.memCalloc(WIDTH * Rings.SIZE * 4);
        staging = MemoryUtil.memAlloc(PATCH_CELLS * 4).order(ByteOrder.LITTLE_ENDIAN);
        clearAll();
    }

    /** Workers: is patch (px, pz) of ring {@code ring} still in its window for this epoch? */
    boolean wanted(int ring, int px, int pz, int epoch) {
        return epoch == this.epoch && expect[ring].get(Rings.slot(px, pz)) == Rings.key(px, pz);
    }

    /** Forgets every window and texel (a new world or dimension, far view off). */
    void reset() {
        epoch++;
        for (int r = 0; r < Rings.RINGS; r++) {
            valid[r] = false;
            missing[r] = 0;
            for (int i = 0; i < SLOTS; i++) {
                expect[r].set(i, NONE);
                ready[r][i] = false;
                asked[r][i] = false;
                genQueued[r][i] = 0;
                genHi[r][i] = 0;
                treeCount[r][i * 2] = treeCount[r][i * 2 + 1] = 0;
            }
        }
        clearAll();
        dirty = 0;
    }

    private void clearAll() {
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        for (int r = 0; r < Rings.RINGS; r++) {
            enc.writeToTexture(height, zeros, 0, 0, 0, r * Rings.SIZE, WIDTH, Rings.SIZE);
            enc.writeToTexture(genHeight, zeros, 0, 0, 0, r * Rings.SIZE, WIDTH, Rings.SIZE);
            enc.writeToTexture(shade, zeros, 0, 0, 0, r * Rings.SIZE, WIDTH, Rings.SIZE);
        }
    }

    /**
     * Places or moves ring {@code ring}'s window around the camera. Slots whose patch left are zeroed and handed the
     * patch that came in; patches the real chunks will always hide (within {@code hiddenWithin} blocks of the window's
     * centre, wherever the camera sits in it) are asked for only once they could show.
     */
    void place(int ring, double camX, double camZ, double hiddenWithin, ServerLevel level, @Nullable FarStore store, FarWorkers workers) {
        int nx = Rings.windowPatch(camX, ring), nz = Rings.windowPatch(camZ, ring);
        boolean moved = !valid[ring];
        if (valid[ring]) {
            boolean moveX = nx != winX[ring] && Rings.shouldMove(winX[ring], camX, ring);
            boolean moveZ = nz != winZ[ring] && Rings.shouldMove(winZ[ring], camZ, ring);
            if (!moveX) nx = winX[ring];
            if (!moveZ) nz = winZ[ring];
            moved = moveX || moveZ;
        }
        int pb = Rings.patchBlocks(ring);
        double hidden = hiddenWithin - pb * 1.1;
        // a window that didn't move still asks for patches that just came out from under the real chunks (the hidden
        // radius shrinks when the render distance does), but only every few frames: it's a walk over 256 slots
        if (!moved && hidden == hiddenR[ring] && (frame++ & 15) != ring) return;
        winX[ring] = nx;
        winZ[ring] = nz;
        valid[ring] = true;
        double cx = (nx + Rings.PATCHES / 2.0) * pb, cz = (nz + Rings.PATCHES / 2.0) * pb;
        centreX[ring] = cx;
        centreZ[ring] = cz;
        hiddenR[ring] = hidden;
        CommandEncoder enc = null;
        int missingNow = 0;
        for (int j = 0; j < Rings.PATCHES; j++) {
            for (int i = 0; i < Rings.PATCHES; i++) {
                int px = nx + i, pz = nz + j, slot = Rings.slot(px, pz);
                long key = Rings.key(px, pz);
                boolean isHidden = isHidden(px, pz, pb, cx, cz, hidden) || covered(ring, px, pz, pb);
                if (expect[ring].get(slot) != key) {
                    // a slot that held another patch is zeroed now; a fresh one already is (reset clears the atlases)
                    if (expect[ring].getAndSet(slot, key) != NONE) {
                        if (enc == null) enc = RenderSystem.getDevice().createCommandEncoder();
                        int x = (px * Rings.PATCH) & Rings.MASK, y = ring * Rings.SIZE + ((pz * Rings.PATCH) & Rings.MASK);
                        enc.writeToTexture(height, zeros, 0, 0, x, y, Rings.PATCH, Rings.PATCH);
                        enc.writeToTexture(genHeight, zeros, 0, 0, x, y, Rings.PATCH, Rings.PATCH);
                    }
                    ready[ring][slot] = false;
                    asked[ring][slot] = false;
                    genQueued[ring][slot] = 0;
                    genHi[ring][slot] = 0;
                    treeCount[ring][slot * 2] = treeCount[ring][slot * 2 + 1] = 0;
                }
                if (ready[ring][slot] || isHidden) continue;
                missingNow++;
                if (asked[ring][slot]) continue;
                asked[ring][slot] = true;
                // coarsest ring first (the horizon in a second), then nearest the camera
                float priority = priority(ring, px, pz, camX, camZ);
                workers.submit(level, ring, px, pz, epoch, priority, wanted);
                if (store != null && store.mayHave((px * Rings.PATCH - 1) * Rings.cellSize(ring), (pz * Rings.PATCH - 1) * Rings.cellSize(ring),
                        (px + 1) * pb, (pz + 1) * pb)) {
                    genQueued[ring][slot] = 1;
                    genAsked++;
                    workers.submitGenerated(level, ring, px, pz, epoch, priority - 0.5f, wanted);
                }
            }
        }
        missing[ring] = missingNow;
        if (moved) dirty |= 1 << ring;
    }

    private int frame;
    /** Where the camera will be in a moment, less where it is (blocks): the queue ranks patches from there. */
    double leadX, leadZ;

    /**
     * Whether the next finer ring draws all of this patch: inside its window, clear of the outer 16 cells where it eases
     * into this ring's surface. Such a patch would only be a fallback, and the horizon ring (never skipped) already is
     * one; asked for, these came first (they lie nearest) and the rings' visible bands waited behind them (Euan's tour,
     * 2026-10-26: rings 1-5 still empty a minute after a jump, see-through ground outside Carillon).
     */
    private boolean covered(int ring, int px, int pz, int pb) {
        int f = ring - 1;
        if (ring == 0 || ring == Rings.RINGS - 1 || !valid[f]) return false;
        long fb = Rings.patchBlocks(f), band = 16L * Rings.cellSize(f);
        long x0 = winX[f] * fb + band, x1 = (winX[f] + Rings.PATCHES) * fb - band;
        long z0 = winZ[f] * fb + band, z1 = (winZ[f] + Rings.PATCHES) * fb - band;
        return (long) px * pb >= x0 && (long) (px + 1) * pb <= x1 && (long) pz * pb >= z0 && (long) (pz + 1) * pb <= z1;
    }

    /**
     * The horizon ring first (the whole horizon in a second), then every other ring's patches by how far they are from the
     * camera, nearest first: the near far land is the most seen, and with the coarser rings first it waited behind every
     * patch they queued as the camera moved (in the opening's flight ring 0 never arrived).
     */
    private float priority(int ring, int px, int pz, double camX, double camZ) {
        int pb = Rings.patchBlocks(ring);
        // from where the camera is heading (FarSightClient's lead), so in fast flight the land ahead comes first
        double dx = (px + 0.5) * pb - (camX + leadX), dz = (pz + 0.5) * pb - (camZ + leadZ);
        float blocks = (float) Math.sqrt(dx * dx + dz * dz);
        return (ring == Rings.RINGS - 1 ? 0f : 1.0e6f) + blocks;
    }

    /**
     * A real chunk was (re)seen: every ring's patch whose corners average it reads the generated layer again, if it is
     * in its window and not under the real chunks.
     */
    void chunkChanged(int chunkX, int chunkZ, double camX, double camZ, ServerLevel level, FarWorkers workers) {
        for (int r = 0; r < Rings.RINGS; r++) {
            if (!valid[r]) continue;
            int pb = Rings.patchBlocks(r), half = Rings.cellSize(r) / 2;
            // a corner averages the half cell either side of it: the chunk can reach the patch on either side of an edge
            int x0 = Math.floorDiv(chunkX * 16 - half, pb), x1 = Math.floorDiv(chunkX * 16 + 15 + half, pb);
            int z0 = Math.floorDiv(chunkZ * 16 - half, pb), z1 = Math.floorDiv(chunkZ * 16 + 15 + half, pb);
            for (int pz = z0; pz <= z1; pz++) {
                for (int px = x0; px <= x1; px++) {
                    int slot = Rings.slot(px, pz);
                    if (expect[r].get(slot) != Rings.key(px, pz) || isHidden(px, pz, pb, centreX[r], centreZ[r], hiddenR[r])) continue;
                    if (genQueued[r][slot] != 0) {
                        genQueued[r][slot] = 2;
                        continue;
                    }
                    genQueued[r][slot] = 1;
                    genAsked++;
                    workers.submitGenerated(level, r, px, pz, epoch, priority(r, px, pz, camX, camZ) - 0.5f, wanted);
                }
            }
        }
    }

    /** Queues a generated patch once more (it was asked for again while its last job ran). */
    void regenerate(int ring, int px, int pz, double camX, double camZ, ServerLevel level, FarWorkers workers) {
        int slot = Rings.slot(px, pz);
        if (expect[ring].get(slot) != Rings.key(px, pz)) return;
        genQueued[ring][slot] = 1;
        genAsked++;
        workers.submitGenerated(level, ring, px, pz, epoch, priority(ring, px, pz, camX, camZ) - 0.5f, wanted);
    }

    private static boolean isHidden(int px, int pz, int pb, double cx, double cz, double hidden) {
        if (hidden <= 0) return false;
        // the patch's farthest corner from the window's centre
        double fx = Math.max(Math.abs(px * pb - cx), Math.abs((px + 1) * pb - cx));
        double fz = Math.max(Math.abs(pz * pb - cz), Math.abs((pz + 1) * pb - cz));
        return fx * fx + fz * fz < hidden * hidden;
    }

    /**
     * Uploads one finished patch if it is still wanted; true when it was. A generated one asked for again while it ran
     * comes back as {@code again}: the caller queues it once more. A failed job ({@code range == -2}) releases its slot
     * so it is asked for again.
     */
    boolean accept(FarWorkers.Patch p) {
        if (!wanted(p.ring, p.px, p.pz, p.epoch)) return false;
        int slot = Rings.slot(p.px, p.pz);
        int x = (p.px * Rings.PATCH) & Rings.MASK, y = p.ring * Rings.SIZE + ((p.pz * Rings.PATCH) & Rings.MASK);
        if (p.generated) {
            boolean more = genQueued[p.ring][slot] == 2;
            genQueued[p.ring][slot] = 0;
            again = more;
            if (p.range < 0) return true;
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            uploadHeights(enc, genHeight, p.height, x, y);
            upload(enc, genColour, p.colour, x, y);
            upload(enc, genInfo, p.info, x, y);
            upload(enc, genCrown, p.crown, x, y);
            uploadTrees(enc, p, slot, true);
            genUploaded++;
            // a built-up patch can stand above its prediction: culling assumes the higher of the two
            genHi[p.ring][slot] = (short) (p.range & 0xFFFF);
            dirty |= 1 << p.ring;
            return true;
        }
        again = false;
        if (p.range == -2) {
            asked[p.ring][slot] = false;
            return false;
        }
        if (ready[p.ring][slot]) return false;
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        uploadHeights(enc, height, p.height, x, y);
        upload(enc, colour, p.colour, x, y);
        upload(enc, info, p.info, x, y);
        upload(enc, crown, p.crown, x, y);
        uploadTrees(enc, p, slot, false);
        ready[p.ring][slot] = true;
        lo[p.ring][slot] = (short) (p.range >>> 16);
        hi[p.ring][slot] = (short) (p.range & 0xFFFF);
        if (missing[p.ring] > 0) missing[p.ring]--;
        dirty |= 1 << p.ring;
        return true;
    }

    /** Set by {@link #accept} for a generated patch that was asked for again while its job ran. */
    boolean again;

    private void upload(CommandEncoder enc, GpuTexture into, int[] texels, int x, int y) {
        staging.clear();
        for (int t : texels) staging.putInt(t);
        staging.flip();
        enc.writeToTexture(into, staging, 0, 0, x, y, Rings.PATCH, Rings.PATCH);
    }

    private void uploadTrees(CommandEncoder enc, FarWorkers.Patch p, int slot, boolean generated) {
        if (!FarTrees.plants(p.ring)) return;
        treeCount[p.ring][slot * 2 + (generated ? 1 : 0)] = p.treeCount;
        if (p.treeCount == 0) return;
        treeStaging.clear();
        for (int i = 0; i < p.treeCount * FarTrees.INTS; i++) treeStaging.putInt(p.trees[i]);
        treeStaging.flip();
        long at = (long) treeRun(p.ring, slot, generated) * FarTrees.MAX * TREE_BYTES;
        enc.writeToBuffer(trees.slice(at, (long) p.treeCount * TREE_BYTES), treeStaging);
    }

    private void uploadHeights(CommandEncoder enc, GpuTexture into, int[] texels, int x, int y) {
        staging.clear();
        for (int t : texels) staging.putShort((short) t);
        staging.flip();
        enc.writeToTexture(into, staging, 0, 0, x, y, Rings.PATCH, Rings.PATCH);
    }

    /** Whether slot (px, pz) of the ring holds its patch's predicted heights. */
    boolean has(int ring, int px, int pz) {
        int slot = Rings.slot(px, pz);
        return ready[ring][slot] && expect[ring].get(slot) == Rings.key(px, pz);
    }

    /** The slot's highest top (whole blocks + base), the generated layer's if that stands higher. */
    int top(int ring, int slot) {
        return Math.max(hi[ring][slot] & 0xFFFF, genHi[ring][slot] & 0xFFFF);
    }

    int memoryBytes() {
        return WIDTH * HEIGHT * (2 * 2 + 4 * 7) + (int) trees.size();
    }

    @Override
    public void close() {
        for (GpuTextureView v : new GpuTextureView[]{heightView, colourView, infoView, crownView, genHeightView, genColourView, genInfoView, genCrownView, shadeView}) v.close();
        for (GpuTexture t : new GpuTexture[]{height, colour, info, crown, genHeight, genColour, genInfo, genCrown, shade}) t.close();
        trees.close();
        MemoryUtil.memFree(zeros);
        MemoryUtil.memFree(staging);
        MemoryUtil.memFree(treeStaging);
        Awaken.LOG.info("[farsight] released the ring atlases");
    }
}
