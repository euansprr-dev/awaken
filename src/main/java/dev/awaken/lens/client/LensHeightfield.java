package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Lens 2's sun shadows without a second render of the world (SPEC §22): the loaded terrain's height around the camera,
 * from the client chunks' own heightmaps, which the light pass marches toward the sun. A 1024×1024 RG16F texture,
 * addressed by world block coordinate modulo 1024 (a ring buffer, so moving never shifts it): r the top of everything
 * that blocks motion (leaves included), g the same without leaves (a roof). Mips 1-4 hold the maximum of their 2×2, so
 * far steps of the march test a whole cell at once. Where the top is water, g sits at the block's base + 0.75 instead of
 * its top (exactly representable in half floats to y 512): the shaders read that fraction as "water here" (Lens 2's
 * reflections, L3) and lose nothing as an occluder. Built from a 64×64-chunk window centred on the camera's chunk,
 * nearest chunks first and a few each tick; block changes show within a few seconds (a rolling rescan). Uploads go up
 * as whole-width strips of the window, straight from an off-heap mirror, so nothing is allocated per tick.
 */
final class LensHeightfield {
    static final int SIZE = 1024, CHUNKS = SIZE / 16, LEVELS = 5;
    /** No data yet (or never loaded): below anything, so it never shadows. */
    static final float NONE = -2048f;
    /** Blocks from the camera the shader may trust: the window's half-width less the camera's own chunk. */
    static final float TRUSTED = (CHUNKS / 2 - 1) * 16f;
    private static final int READS_PER_TICK = 40, RESCANS_PER_TICK = 24, NEAR_EVERY = 5, NEAR_RADIUS = 2, UPLOAD_ROWS_PER_TICK = 8;

    final Texture texture;
    // per level, the whole window as the texture holds it (row-major RG16F) and one view per chunk row for its upload
    private final ByteBuffer[] mirror = new ByteBuffer[LEVELS];
    private final ByteBuffer[][] rowSlices = new ByteBuffer[LEVELS][CHUNKS];
    // per slot (chunk x & 63, z & 63): which chunk it holds, whether it waits for a read, its content's hash and top
    private final long[] owner = new long[CHUNKS * CHUNKS];
    private final boolean[] pending = new boolean[CHUNKS * CHUNKS];
    private final long[] hash = new long[CHUNKS * CHUNKS];
    private final short[] top = new short[CHUNKS * CHUNKS];
    private final boolean[] dirtyRow = new boolean[CHUNKS];
    // slot offsets from the centre, nearest first (Chebyshev rings)
    private static final int[] ORDER = ringOrder();
    private final float[] all = new float[256], solid = new float[256];
    private int centreX = Integer.MIN_VALUE, centreZ, rescan, clock, maxTop = -64, nextRow;
    private boolean maxDirty;
    private ClientLevel level;

    LensHeightfield() {
        for (int k = 0; k < LEVELS; k++) {
            int w = SIZE >> k, rows = 16 >> k;
            mirror[k] = MemoryUtil.memAlloc(w * w * 4);
            for (int r = 0; r < CHUNKS; r++) rowSlices[k][r] = MemoryUtil.memSlice(mirror[k], r * rows * w * 4, rows * w * 4);
        }
        texture = new Texture();
        clearAll();
        for (int k = 0; k < LEVELS; k++) {
            int w = SIZE >> k;
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), mirror[k], k, 0, 0, 0, w, w);
        }
    }

    /** The highest surface in the window (the march stops once the ray is above it). */
    int maxTop() {
        if (maxDirty) {
            int m = -64;
            for (int i = 0; i < top.length; i++) m = Math.max(m, top[i]);
            maxTop = m;
            maxDirty = false;
        }
        return maxTop;
    }

    /**
     * Whether a point near the camera is in sunlight by the CPU mirror (Lens 2's dust only shows in sunlit air): a short
     * march toward the light over level 0, leaves counting as shade.
     */
    boolean sunlit(double x, double y, double z, org.joml.Vector3fc l) {
        if (l.y() <= 0.02f) return false;
        int top = maxTop();
        double t = 0.4;
        for (int i = 0; i < 20; i++) {
            double qy = y + l.y() * t;
            if (qy > top) return true;
            int ix = net.minecraft.util.Mth.floor(x + l.x() * t) & (SIZE - 1), iz = net.minecraft.util.Mth.floor(z + l.z() * t) & (SIZE - 1);
            if (qy < Float.float16ToFloat(mirror[0].getShort((iz * SIZE + ix) * 4))) return false;
            t += 0.5 + t * 0.25;
        }
        return true;
    }

    /** Once a client tick while Lens 2 draws in a place with a sun: follow the camera, read chunks, upload changes. */
    void tick(ClientLevel lvl, int camChunkX, int camChunkZ) {
        if (lvl != level) {
            level = lvl;
            centreX = Integer.MIN_VALUE;
            clearAll();
            Arrays.fill(dirtyRow, true);
        }
        if (camChunkX != centreX || camChunkZ != centreZ) recentre(camChunkX, camChunkZ);
        int reads = 0;
        // waiting slots, nearest first
        for (int i = 0; i < ORDER.length && reads < READS_PER_TICK; i++) {
            int slot = slotAt(ORDER[i]);
            if (pending[slot]) {
                pending[slot] = false;
                if (read(slot)) reads++;
            }
        }
        // the camera's neighbourhood often (blocks broken and placed nearby), the rest of the window in turn
        if (++clock % NEAR_EVERY == 0) {
            int nearSlots = (2 * NEAR_RADIUS + 1) * (2 * NEAR_RADIUS + 1);
            for (int i = 0; i < nearSlots; i++) read(slotAt(ORDER[i]));
        }
        for (int n = 0; n < RESCANS_PER_TICK; n++) {
            rescan = (rescan + 1) % ORDER.length;
            read(slotAt(ORDER[rescan]));
        }
        upload();
    }

    /** A chunk arrived from the server: read it on the next tick if it's in the window. */
    void loaded(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int slot = (pos.x() & (CHUNKS - 1)) + (pos.z() & (CHUNKS - 1)) * CHUNKS;
        if (owner[slot] == ChunkPos.pack(pos.x(), pos.z())) pending[slot] = true;
    }

    void close() {
        texture.close();
        for (int k = 0; k < LEVELS; k++) MemoryUtil.memFree(mirror[k]);
    }

    private int slotAt(int packedOffset) {
        int cx = centreX + (short) (packedOffset >> 16), cz = centreZ + (short) packedOffset;
        return (cx & (CHUNKS - 1)) + (cz & (CHUNKS - 1)) * CHUNKS;
    }

    private void recentre(int cx, int cz) {
        centreX = cx;
        centreZ = cz;
        int half = CHUNKS / 2;
        for (int sz = 0; sz < CHUNKS; sz++) {
            for (int sx = 0; sx < CHUNKS; sx++) {
                // the one chunk in [centre - 32, centre + 31] that lands on this slot
                int ox = cx - half + Math.floorMod(sx - (cx - half), CHUNKS), oz = cz - half + Math.floorMod(sz - (cz - half), CHUNKS);
                long want = ChunkPos.pack(ox, oz);
                int slot = sx + sz * CHUNKS;
                if (owner[slot] != want) {
                    owner[slot] = want;
                    clearSlot(slot);
                    pending[slot] = true;
                }
            }
        }
    }

    /** Reads one slot's chunk if it's loaded; marks its strip for upload when the heights changed. */
    private boolean read(int slot) {
        long key = owner[slot];
        if (level == null || key == Long.MIN_VALUE) return false;
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty()) return false;
        long h = 1469598103934665603L;
        int high = level.getMinY();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int a = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
                int s = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                all[x + z * 16] = a;
                // open water on top: its column's solid top drops to base + 0.75, the shaders' water mark
                boolean water = a == s && chunk.getFluidState(x, s - 1, z).getType().is(net.minecraft.tags.FluidTags.WATER);
                solid[x + z * 16] = water ? s - 0.25f : s;
                high = Math.max(high, a);
                h = (h ^ (a * 4099L + s * 2L + (water ? 1L : 0L))) * 1099511628211L;
            }
        }
        if (h == hash[slot]) return true;
        hash[slot] = h;
        write(slot, all, solid);
        if (top[slot] != high) {
            top[slot] = (short) high;
            maxDirty = true;
        }
        return true;
    }

    private void clearSlot(int slot) {
        Arrays.fill(all, NONE);
        Arrays.fill(solid, NONE);
        write(slot, all, solid);
        hash[slot] = 0;
        top[slot] = (short) -64;
        maxDirty = true;
    }

    private void clearAll() {
        Arrays.fill(owner, Long.MIN_VALUE);
        Arrays.fill(pending, false);
        Arrays.fill(hash, 0);
        Arrays.fill(top, (short) -64);
        short none = Float.floatToFloat16(NONE);
        int v = (none & 0xFFFF) | (none & 0xFFFF) << 16;
        for (int k = 0; k < LEVELS; k++) {
            ByteBuffer m = mirror[k];
            for (int i = 0; i < m.capacity(); i += 4) m.putInt(i, v);
        }
        maxDirty = true;
    }

    /** One chunk's 16×16 heights into level 0 at its slot, then its share of each max level. */
    private void write(int slot, float[] a, float[] s) {
        int sx = slot & (CHUNKS - 1), sz = slot / CHUNKS;
        for (int k = 0; k < LEVELS; k++) {
            int n = 16 >> k, w = SIZE >> k, step = 1 << k;
            ByteBuffer m = mirror[k];
            for (int z = 0; z < n; z++) {
                for (int x = 0; x < n; x++) {
                    float ma = NONE, ms = NONE;
                    for (int dz = 0; dz < step; dz++) {
                        for (int dx = 0; dx < step; dx++) {
                            int i = (x * step + dx) + (z * step + dz) * 16;
                            ma = Math.max(ma, a[i]);
                            ms = Math.max(ms, s[i]);
                        }
                    }
                    int at = ((sz * n + z) * w + sx * n + x) * 4;
                    m.putShort(at, Float.floatToFloat16(ma));
                    m.putShort(at + 2, Float.floatToFloat16(ms));
                }
            }
        }
        dirtyRow[sz] = true;
    }

    private void upload() {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        int done = 0;
        // round the ring from where the last tick stopped, so a busy row can't keep the others waiting
        for (int i = 0; i < CHUNKS && done < UPLOAD_ROWS_PER_TICK; i++) {
            int r = (nextRow + i) & (CHUNKS - 1);
            if (!dirtyRow[r]) continue;
            nextRow = r + 1;
            dirtyRow[r] = false;
            done++;
            for (int k = 0; k < LEVELS; k++) {
                int w = SIZE >> k, rows = 16 >> k;
                encoder.writeToTexture(texture.getTexture(), rowSlices[k][r], k, 0, 0, r * rows, w, rows);
            }
        }
    }

    private static int[] ringOrder() {
        int half = CHUNKS / 2;
        int[] out = new int[CHUNKS * CHUNKS];
        int n = 0;
        for (int ring = 0; ring <= half; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || dx < -half || dx >= half || dz < -half || dz >= half) continue;
                    out[n++] = (dx & 0xFFFF) << 16 | (dz & 0xFFFF);
                }
            }
        }
        return out;
    }

    /** The texture the chains read as {@code awaken:lens_height}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens heightfield", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RG16_FLOAT, SIZE, SIZE, 1, LEVELS);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
