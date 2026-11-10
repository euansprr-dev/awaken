package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The block map (SPEC §22 "Steady Light", the Sun Pass): which blocks near the camera stop the sun, as a small texture
 * the light pass walks toward the light. Where the heightfield holds one top per column, this holds every block, so a
 * shadow has an underside: light comes in under a canopy, through a doorway and under an arch, and what casts is the
 * block itself, edge for edge.
 *
 * <p>128 blocks across and 128 high, a ring addressed by world block modulo 128 on every axis. One RGBA8 texel is one
 * column of one 16-block section: sixteen levels, two bits each (0 open, 1 a block that stops the sun, 2 leaves, 3 a
 * block's lower half); a texel of all ones is a section not read yet. A chunk's eight sections sit side by side in the
 * texture (x = block x mod 128, y = (block z mod 128) * 8 + section mod 8), so a chunk uploads as one piece.
 *
 * <p>Nothing here is drawn and nothing is redone per frame: a section is read when it enters the window or changes
 * (a few each tick, nearest first), and a frame's cost is the walk the light pass makes for the pixels that need it.
 */
final class LensBlocks {
    static final int SIZE = 128, CHUNKS = SIZE / 16, SLOTS = 8;
    /** Within this many blocks of the camera (on x and z) every section has been read once the window has settled. */
    static final float TRUSTED = 46f;
    static final int UNKNOWN = 0xFFFFFFFF, CELLS = CHUNKS * CHUNKS * SLOTS * 256;
    // sections read a tick when the window moves, sections looked at again a tick for changes, chunks uploaded a tick,
    // and of the 27 sections round the camera those looked at again a tick
    private static final int READS_PER_TICK = 14, RESCANS_PER_TICK = 6, UPLOADS_PER_TICK = 6, NEAR_PER_TICK = 3;
    /** What a tick may spend reading sections before it leaves the rest for the next one. */
    private static final long BUDGET_NANOS = 500_000L;
    private static final byte OPEN = 0, SOLID = 1, LEAVES = 2, LOW = 3, UNSET = -1;

    final Texture texture = new Texture();
    // the texture's mirror: [chunk slot][section slot][z][x], one int a column
    private final int[] cells = new int[CELLS];
    // what each chunk slot and each section slot holds now, and whether it has been read
    private final int[] heldX = new int[CHUNKS * CHUNKS], heldZ = new int[CHUNKS * CHUNKS];
    private final int[] heldY = new int[CHUNKS * CHUNKS * SLOTS];
    private final boolean[] known = new boolean[CHUNKS * CHUNKS * SLOTS];
    private final boolean[] dirty = new boolean[CHUNKS * CHUNKS];
    private final ByteBuffer staging = MemoryUtil.memAlloc(16 * 16 * SLOTS * 4);
    private final int[] scratch = new int[256];
    private final int[] order = ringOrder();
    private byte[] kinds = new byte[0];
    private int minSectionY, centreX, centreZ;
    private int rescanAt, nearAt;
    private boolean started;

    LensBlocks() {
        Arrays.fill(cells, UNKNOWN);
        Arrays.fill(heldX, Integer.MIN_VALUE);
        Arrays.fill(heldY, Integer.MIN_VALUE);
        Arrays.fill(dirty, true);
    }

    /** The window's lowest block y (a multiple of 16): the shader's frame value. */
    int minY() {
        return minSectionY << 4;
    }

    /** The window's lowest block x and z (the lit air's corner: LensLit). */
    int originX() {
        return (centreX - CHUNKS / 2) << 4;
    }

    int originZ() {
        return (centreZ - CHUNKS / 2) << 4;
    }

    /** The whole map as it stands, for a worker to read at its leisure (LensLit's sweep). */
    void snapshot(int[] into) {
        System.arraycopy(cells, 0, into, 0, CELLS);
    }

    /** Once a client tick: the window follows the camera, and a few sections are read, looked at again and uploaded. */
    void tick(ClientLevel level, int camX, int camY, int camZ) {
        int ccx = camX >> 4, ccz = camZ >> 4;
        centreX = ccx;
        centreZ = ccz;
        minSectionY = (camY >> 4) - 3;
        // what each slot should hold now; anything else is forgotten
        for (int dz = -CHUNKS / 2; dz < CHUNKS / 2; dz++) {
            for (int dx = -CHUNKS / 2; dx < CHUNKS / 2; dx++) {
                int cx = ccx + dx, cz = ccz + dz, slot = (cz & 7) * CHUNKS + (cx & 7);
                boolean moved = heldX[slot] != cx || heldZ[slot] != cz;
                heldX[slot] = cx;
                heldZ[slot] = cz;
                for (int k = 0; k < SLOTS; k++) {
                    int sy = minSectionY + ((k - minSectionY) & 7), at = slot * SLOTS + k;
                    if (moved || heldY[at] != sy) {
                        heldY[at] = sy;
                        if (known[at] || !started) {
                            known[at] = false;
                            Arrays.fill(cells, at * 256, at * 256 + 256, UNKNOWN);
                            dirty[slot] = true;
                        }
                    }
                }
            }
        }
        started = true;
        // a tick's reading has a time limit as well as a count (a tick that took 2 ms and more here was a late frame in
        // flight); what waits is read on the next tick
        long until = System.nanoTime() + BUDGET_NANOS;
        // read what isn't known yet, nearest the camera first
        int reads = 0;
        scan:
        for (int i = 0; i < order.length && reads < READS_PER_TICK; i++) {
            int cx = ccx + (short) (order[i] >> 16), cz = ccz + (short) order[i], slot = (cz & 7) * CHUNKS + (cx & 7);
            LevelChunk chunk = null;
            for (int k = 0; k < SLOTS; k++) {
                int at = slot * SLOTS + k;
                if (known[at]) continue;
                if (reads > 0 && System.nanoTime() > until) break scan;
                if (chunk == null) {
                    if (level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk c) chunk = c;
                    else break;
                }
                if (read(level, chunk, slot, k)) reads++;
            }
        }
        // look again at what is known, for blocks placed and broken. The 27 sections round the camera in turn, three a
        // tick, so each is looked at about twice a second and no tick takes them all (a change near you shows within
        // half a second); then a few of the rest in turn (one farther off shows within a few seconds)
        for (int i = 0; i < NEAR_PER_TICK; i++) {
            nearAt = (nearAt + 1) % 27;
            int slot = ((ccz + nearAt / 9 - 1) & 7) * CHUNKS + ((ccx + nearAt / 3 % 3 - 1) & 7);
            rescan(level, slot, ((camY >> 4) + nearAt % 3 - 1) & 7);
        }
        for (int i = 0; i < RESCANS_PER_TICK && System.nanoTime() < until; i++) {
            rescanAt = (rescanAt + 1) % (CHUNKS * CHUNKS * SLOTS);
            rescan(level, rescanAt / SLOTS, rescanAt % SLOTS);
        }
        upload();
    }

    private void rescan(ClientLevel level, int slot, int k) {
        if (!known[slot * SLOTS + k]) return;
        if (level.getChunkSource().getChunk(heldX[slot], heldZ[slot], ChunkStatus.FULL, false) instanceof LevelChunk chunk) read(level, chunk, slot, k);
    }

    /** Reads one section into the mirror; true when it took a real walk (an open section costs nothing). */
    private boolean read(ClientLevel level, LevelChunk chunk, int slot, int k) {
        int at = slot * SLOTS + k, sy = heldY[at], index = sy - level.getMinSectionY();
        boolean walked = false;
        if (index < 0 || index >= chunk.getSectionsCount() || chunk.getSection(index).hasOnlyAir()) {
            Arrays.fill(scratch, 0);
        } else {
            LevelChunkSection section = chunk.getSection(index);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int bits = 0;
                    for (int y = 0; y < 16; y++) bits |= kind(section.getBlockState(x, y, z)) << (y * 2);
                    scratch[z * 16 + x] = bits;
                }
            }
            walked = true;
        }
        int base = at * 256;
        boolean changed = !known[at];
        for (int i = 0; i < 256 && !changed; i++) changed = cells[base + i] != scratch[i];
        if (changed) {
            System.arraycopy(scratch, 0, cells, base, 256);
            dirty[slot] = true;
        }
        known[at] = true;
        return walked;
    }

    /** What a block does to the sun: nothing, stops it, leaves, or stops it across its lower half. */
    private int kind(BlockState state) {
        int id = Block.getId(state);
        if (id >= kinds.length) {
            int old = kinds.length;
            kinds = Arrays.copyOf(kinds, Math.max(id + 1, Block.BLOCK_STATE_REGISTRY.size()));
            Arrays.fill(kinds, old, kinds.length, UNSET);
        }
        byte k = kinds[id];
        if (k == UNSET) kinds[id] = k = classify(state);
        return k;
    }

    private static byte classify(BlockState state) {
        if (state.isAir()) return OPEN;
        if (state.is(BlockTags.LEAVES)) return LEAVES;
        if (state.isSolidRender()) return SOLID;
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) return OPEN;
        // glass and ice fill their block and let the sun through
        if (Block.isShapeFullBlock(shape)) return state.canOcclude() ? SOLID : OPEN;
        if (state.getBlock() instanceof StairBlock) return SOLID;
        AABB b = shape.bounds();
        if (b.minX > 0.01 || b.maxX < 0.99 || b.minZ > 0.01 || b.maxZ < 0.99 || b.minY > 0.01 || !state.canOcclude()) return OPEN;
        // a slab, a path or farmland: a whole footprint, half a block or nearly a whole one high
        return b.maxY >= 0.9 ? SOLID : (b.maxY >= 0.4 && b.maxY <= 0.51 ? LOW : OPEN);
    }

    private void upload() {
        int sent = 0;
        for (int slot = 0; slot < dirty.length && sent < UPLOADS_PER_TICK; slot++) {
            if (!dirty[slot]) continue;
            dirty[slot] = false;
            sent++;
            staging.clear();
            // texel (x, z * 8 + k): rows run z by z, each z eight rows of sixteen columns
            for (int z = 0; z < 16; z++) {
                for (int k = 0; k < SLOTS; k++) {
                    int base = (slot * SLOTS + k) * 256 + z * 16;
                    for (int x = 0; x < 16; x++) staging.putInt(cells[base + x]);
                }
            }
            staging.flip();
            int tx = (slot % CHUNKS) * 16, ty = (slot / CHUNKS) * 16 * SLOTS;
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), staging, 0, 0, tx, ty, 16, 16 * SLOTS);
        }
    }

    void close() {
        texture.close();
        MemoryUtil.memFree(staging);
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

    /** The texture the chains read as {@code awaken:lens_blocks}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens block map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, SIZE, SIZE * SLOTS, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
