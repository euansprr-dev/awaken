package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.farsight.FarColumns;
import dev.awaken.farsight.FarStore;
import dev.awaken.farsight.FarWorkers;
import dev.awaken.farsight.Rings;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The rings' data on the client (SPEC §20): each ring's window, the patch every slot holds, and three RGBA8 atlases of
 * {@code 256 × (256·7)} texels, one 256×256 block per ring, addressed toroidally (a world cell's texel is its
 * coordinate masked to 255, so a moving window only rewrites the strip that came into view):
 * <ul>
 *   <li>height: r, g the drawn top (+{@link FarColumns#HEIGHT_OFFSET}), b flags, a 255 known / 0 unknown;</li>
 *   <li>colour: r, g, b the ground or water colour;</li>
 *   <li>shade: r, g the smoothed normal's x and z, b valley shade (baked on the GPU by {@link FarGpu});</li>
 *   <li>generated height and colour, laid out the same: what the real chunks seen there show
 *       ({@link dev.awaken.farsight.FarStore}). Where a cell has it, it wins over the prediction.</li>
 * </ul>
 * A slot whose patch leaves its window is zeroed at once, so the shaders fall back to the coarser ring there until the
 * new patch arrives. Render thread only, except {@link #wanted}, which the workers call.
 */
final class FarField implements AutoCloseable {
    static final int WIDTH = Rings.SIZE, HEIGHT = Rings.SIZE * Rings.RINGS;
    private static final long NONE = Long.MIN_VALUE;
    private static final int TEXEL_BYTES = 4, PATCH_CELLS = Rings.PATCH * Rings.PATCH;

    final GpuTexture height, colour, shade, genHeight, genColour;
    final GpuTextureView heightView, colourView, shadeView, genHeightView, genColourView;

    /** Each ring's window: its first patch; {@link #valid} false until placed. */
    final int[] winX = new int[Rings.RINGS], winZ = new int[Rings.RINGS];
    final boolean[] valid = new boolean[Rings.RINGS];
    /** The patch each slot expects (read by the workers), and whether it has arrived. */
    private final AtomicLongArray[] expect = new AtomicLongArray[Rings.RINGS];
    final boolean[][] ready = new boolean[Rings.RINGS][Rings.PATCHES * Rings.PATCHES];
    /** Lowest and highest drawn top per slot (offset blocks), for culling. */
    final short[][] lo = new short[Rings.RINGS][Rings.PATCHES * Rings.PATCHES], hi = new short[Rings.RINGS][Rings.PATCHES * Rings.PATCHES];
    /** Slots still to arrive per ring (hidden ones excluded), and when each ring first had none left. */
    final int[] missing = new int[Rings.RINGS];
    /** Generated-layer jobs per slot: 0 none queued, 1 queued, 2 queued and asked again since (run it once more). */
    private final byte[][] genQueued = new byte[Rings.RINGS][Rings.PATCHES * Rings.PATCHES];
    /** Each ring's window centre and hidden radius as last placed, for generated-layer refreshes. */
    private final double[] centreX = new double[Rings.RINGS], centreZ = new double[Rings.RINGS], hiddenR = new double[Rings.RINGS];
    /** Rings whose heights changed since the last bake. */
    int dirty;
    /** Generated-layer jobs asked for, and patches of it uploaded with something in them (status). */
    int genAsked, genUploaded;
    volatile int epoch;

    private final ByteBuffer zeros, staging;
    private final FarWorkers.Wanted wanted = this::wanted;

    FarField() {
        var device = RenderSystem.getDevice();
        // copy-dst: written in strips; texture-binding: read by the shaders; the shade atlas is also drawn into
        height = device.createTexture(() -> "awaken far sight heights", 5, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        colour = device.createTexture(() -> "awaken far sight colours", 5, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        shade = device.createTexture(() -> "awaken far sight shade", 13, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        heightView = device.createTextureView(height);
        colourView = device.createTextureView(colour);
        shadeView = device.createTextureView(shade);
        genHeight = device.createTexture(() -> "awaken far sight generated heights", 5, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        genColour = device.createTexture(() -> "awaken far sight generated colours", 5, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        genHeightView = device.createTextureView(genHeight);
        genColourView = device.createTextureView(genColour);
        for (int r = 0; r < Rings.RINGS; r++) {
            expect[r] = new AtomicLongArray(Rings.PATCHES * Rings.PATCHES);
            for (int i = 0; i < Rings.PATCHES * Rings.PATCHES; i++) expect[r].set(i, NONE);
        }
        zeros = MemoryUtil.memCalloc(WIDTH * Rings.SIZE * TEXEL_BYTES);
        staging = MemoryUtil.memAlloc(PATCH_CELLS * TEXEL_BYTES).order(ByteOrder.LITTLE_ENDIAN);
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
            for (int i = 0; i < Rings.PATCHES * Rings.PATCHES; i++) {
                expect[r].set(i, NONE);
                ready[r][i] = false;
                genQueued[r][i] = 0;
            }
        }
        clearAll();
        dirty = 0;
    }

    private void clearAll() {
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        for (int r = 0; r < Rings.RINGS; r++) {
            enc.writeToTexture(height, zeros, 0, 0, 0, r * Rings.SIZE, WIDTH, Rings.SIZE);
            enc.writeToTexture(shade, zeros, 0, 0, 0, r * Rings.SIZE, WIDTH, Rings.SIZE);
            enc.writeToTexture(genHeight, zeros, 0, 0, 0, r * Rings.SIZE, WIDTH, Rings.SIZE);
        }
    }

    /**
     * Places or moves ring {@code ring}'s window around the camera. Slots whose patch left are zeroed and handed the
     * patch that came in; patches the real chunks will always hide (within {@code hiddenWithin} blocks of the window's
     * centre, wherever the camera sits in it) are never asked for.
     */
    void place(int ring, double camX, double camZ, double hiddenWithin, ServerLevel level, @Nullable FarStore store, FarWorkers workers) {
        int nx = Rings.windowPatch(camX, ring), nz = Rings.windowPatch(camZ, ring);
        if (valid[ring]) {
            boolean moveX = nx != winX[ring] && Rings.shouldMove(winX[ring], camX, ring);
            boolean moveZ = nz != winZ[ring] && Rings.shouldMove(winZ[ring], camZ, ring);
            if (!moveX && !moveZ) return;
            if (!moveX) nx = winX[ring];
            if (!moveZ) nz = winZ[ring];
        }
        winX[ring] = nx;
        winZ[ring] = nz;
        valid[ring] = true;
        int pb = Rings.patchBlocks(ring);
        double cx = (nx + Rings.PATCHES / 2.0) * pb, cz = (nz + Rings.PATCHES / 2.0) * pb;
        // the camera stays within three quarters of a patch of the centre (per axis) before the window moves
        double hidden = hiddenWithin - pb * 1.1;
        centreX[ring] = cx;
        centreZ[ring] = cz;
        hiddenR[ring] = hidden;
        CommandEncoder enc = null;
        int missingNow = 0;
        for (int j = 0; j < Rings.PATCHES; j++) {
            for (int i = 0; i < Rings.PATCHES; i++) {
                int px = nx + i, pz = nz + j, slot = Rings.slot(px, pz);
                long key = Rings.key(px, pz);
                if (expect[ring].get(slot) == key) {
                    if (!ready[ring][slot] && !isHidden(px, pz, pb, cx, cz, hidden)) missingNow++;
                    continue;
                }
                // a slot that held another patch is zeroed now; a fresh one already is (reset clears the atlases)
                if (expect[ring].getAndSet(slot, key) != NONE) {
                    if (enc == null) enc = RenderSystem.getDevice().createCommandEncoder();
                    int x = (px * Rings.PATCH) & Rings.MASK, y = ring * Rings.SIZE + ((pz * Rings.PATCH) & Rings.MASK);
                    enc.writeToTexture(height, zeros, 0, 0, x, y, Rings.PATCH, Rings.PATCH);
                    enc.writeToTexture(genHeight, zeros, 0, 0, x, y, Rings.PATCH, Rings.PATCH);
                }
                ready[ring][slot] = false;
                genQueued[ring][slot] = 0;
                if (isHidden(px, pz, pb, cx, cz, hidden)) continue;
                missingNow++;
                // coarsest ring first (the horizon in a second), then nearest the camera
                float priority = priority(ring, px, pz, camX, camZ);
                workers.submit(level, ring, px, pz, epoch, priority, wanted);
                if (store != null && store.mayHave(px * pb, pz * pb, (px + 1) * pb - 1, (pz + 1) * pb - 1)) {
                    genQueued[ring][slot] = 1;
                    genAsked++;
                    workers.submitGenerated(level, ring, px, pz, epoch, priority - 0.5f, wanted);
                }
            }
        }
        missing[ring] = missingNow;
        dirty |= 1 << ring;
    }

    private static float priority(int ring, int px, int pz, double camX, double camZ) {
        int pb = Rings.patchBlocks(ring);
        double dx = (px + 0.5) * pb - camX, dz = (pz + 0.5) * pb - camZ;
        return (Rings.RINGS - 1 - ring) * 1.0e6f + (float) Math.sqrt(dx * dx + dz * dz) / Rings.cellSize(ring);
    }

    /**
     * A real chunk was (re)seen: every ring's patch over it reads the generated layer again, if it is in its window and
     * not under the real chunks.
     */
    void chunkChanged(int chunkX, int chunkZ, double camX, double camZ, ServerLevel level, FarWorkers workers) {
        for (int r = 0; r < Rings.RINGS; r++) {
            if (!valid[r]) continue;
            int pb = Rings.patchBlocks(r);
            int px = Math.floorDiv(chunkX * 16, pb), pz = Math.floorDiv(chunkZ * 16, pb), slot = Rings.slot(px, pz);
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
     * comes back as {@code again}: the caller queues it once more.
     */
    boolean accept(FarWorkers.Patch p) {
        if (!wanted(p.ring, p.px, p.pz, p.epoch)) return false;
        int slot = Rings.slot(p.px, p.pz);
        if (p.generated) {
            boolean more = genQueued[p.ring][slot] == 2;
            genQueued[p.ring][slot] = 0;
            again = more;
            if (p.range < 0) return true;
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            int x = (p.px * Rings.PATCH) & Rings.MASK, y = p.ring * Rings.SIZE + ((p.pz * Rings.PATCH) & Rings.MASK);
            upload(enc, genHeight, p.height, x, y);
            upload(enc, genColour, p.colour, x, y);
            genUploaded++;
            // a built-up patch can stand above its prediction: widen what culling assumes
            hi[p.ring][slot] = (short) Math.max(hi[p.ring][slot] & 0xFFFF, p.range & 0xFFFF);
            dirty |= 1 << p.ring;
            return true;
        }
        again = false;
        if (ready[p.ring][slot]) return false;
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        int x = (p.px * Rings.PATCH) & Rings.MASK, y = p.ring * Rings.SIZE + ((p.pz * Rings.PATCH) & Rings.MASK);
        upload(enc, height, p.height, x, y);
        upload(enc, colour, p.colour, x, y);
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

    /** Whether slot (px, pz) of the ring holds its patch's real heights. */
    boolean has(int ring, int px, int pz) {
        int slot = Rings.slot(px, pz);
        return ready[ring][slot] && expect[ring].get(slot) == Rings.key(px, pz);
    }

    int memoryBytes() {
        return 5 * WIDTH * HEIGHT * TEXEL_BYTES;
    }

    @Override
    public void close() {
        heightView.close();
        colourView.close();
        shadeView.close();
        genHeightView.close();
        genColourView.close();
        height.close();
        colour.close();
        genHeight.close();
        genColour.close();
        shade.close();
        MemoryUtil.memFree(zeros);
        MemoryUtil.memFree(staging);
        Awaken.LOG.info("[farsight] released the ring atlases");
    }
}
