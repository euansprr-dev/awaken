package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The far block map (SPEC §22 "Shadows beyond the block map"): what stops the sun out to some 120 blocks from the
 * camera, in three dimensions, for a walk that carries on where the block map's ends.
 *
 * <p>Why: the block map (LensBlocks) is exact and 128 blocks across, so a walk toward the sun leaves it within 48 to
 * 79 blocks, and under a morning sun 30 degrees up that is short of a tall tree's crown for any ground on the sun's
 * side of the camera. Past it only the heightfield answered, which holds one top a column: a crown as a depth, a
 * jungle giant's side boughs not at all. Its answer and the map's differed, so a tall tree's shadow changed with where
 * you stood (Euan, playing, 11 Nov: "on one side of the tree the shadow is perfect, then if I go to the other side,
 * the shadow is gone"). This map has every layer of leaves and every bough, coarser.
 *
 * <p>256 blocks across and 256 high, a ring like the block map's. A cell is two blocks by one by two: half the map's
 * detail across, all of it in height, which is what keeps a ray low over the ground from being stopped by the ground
 * beside it. Two bits a cell: open; solid (two and more of its four blocks stop the sun); leaves (any, and nothing
 * solid); part (one block of four: a post, a thin trunk, shaded soft). One RGBA8 texel is sixteen cells of one column,
 * one 16-block section high, as in the block map; a texel of all ones is a section not read yet. Texel (x, y) is cell
 * x mod 128 and (cell z mod 128) * 16 + section mod 16.
 *
 * <p>Read like the block map: a far section (32 by 16 by 32 blocks, one section of four chunks) when it enters the
 * window or in its turn of a slow round, a few a tick within half a tick's budget, nearest first. A section wholly
 * under its chunk's lowest ground is solid without a look.
 */
final class LensBlocksFar {
    /** Cells across and sections high; a far chunk is sixteen cells (32 blocks) across. */
    static final int SIZE = 128, CHUNKS = SIZE / 16, SLOTS = 16;
    static final int UNKNOWN = 0xFFFFFFFF, CELLS = CHUNKS * CHUNKS * SLOTS * 256;
    // sections read a tick when the window moves, sections looked at again a tick, chunk slots uploaded a tick
    private static final int READS_PER_TICK = 4, RESCANS_PER_TICK = 1, UPLOADS_PER_TICK = 8;
    private static final int OPEN = 0, SOLID = 1, LEAVES = 2, PART = 3;

    final Texture texture = new Texture();
    private final LensBlocks kinds;
    // the texture's mirror: [far chunk slot][section slot][z][x], one int a column of sixteen cells
    private final int[] cells = new int[CELLS];
    private final int[] heldX = new int[CHUNKS * CHUNKS], heldZ = new int[CHUNKS * CHUNKS];
    private final int[] heldY = new int[CHUNKS * CHUNKS * SLOTS];
    private final boolean[] known = new boolean[CHUNKS * CHUNKS * SLOTS];
    private final boolean[] dirty = new boolean[CHUNKS * CHUNKS];
    private final ByteBuffer staging = MemoryUtil.memAlloc(16 * 16 * SLOTS * 4);
    private final int[] scratch = new int[256];
    // a read's count of blocks that stop the sun and of leaves in each cell: [y][z][x]
    private final byte[] solidIn = new byte[4096], leavesIn = new byte[4096];
    private final LevelChunk[] four = new LevelChunk[4];
    private final int[] order = ringOrder();
    private final boolean[] sent = new boolean[CHUNKS * CHUNKS];
    private int minSectionY, centreX, centreZ, rescanAt, unsent = CHUNKS * CHUNKS;
    private boolean started;
    private ClientLevel held;

    /** {@code kinds}: the block map, whose reading of a block (what it does to the sun) this shares. */
    LensBlocksFar(LensBlocks kinds) {
        this.kinds = kinds;
        Arrays.fill(cells, UNKNOWN);
        Arrays.fill(heldX, Integer.MIN_VALUE);
        Arrays.fill(heldY, Integer.MIN_VALUE);
        Arrays.fill(dirty, true);
    }

    /** Whether the whole texture has gone up at least once (until then it holds whatever its memory did). */
    boolean ready() {
        return unsent <= 0;
    }

    /** The window's lowest block on each axis (multiples of 32, 16 and 32). */
    int originX() {
        return (centreX - CHUNKS / 2) << 5;
    }

    int minY() {
        return minSectionY << 4;
    }

    int originZ() {
        return (centreZ - CHUNKS / 2) << 5;
    }

    /** Once a client tick: the window follows the camera, and a few sections are read, looked at again and uploaded. */
    void tick(ClientLevel level, int camX, int camY, int camZ) {
        if (level != held) {
            // another world: nothing read in the last one is true here
            held = level;
            Arrays.fill(known, false);
            Arrays.fill(cells, UNKNOWN);
            Arrays.fill(dirty, true);
        }
        int ccx = camX >> 5, ccz = camZ >> 5;
        centreX = ccx;
        centreZ = ccz;
        // the ground is what casts and catches: six sections under the camera's, nine over it
        minSectionY = (camY >> 4) - 6;
        for (int dz = -CHUNKS / 2; dz < CHUNKS / 2; dz++) {
            for (int dx = -CHUNKS / 2; dx < CHUNKS / 2; dx++) {
                int cx = ccx + dx, cz = ccz + dz, slot = (cz & 7) * CHUNKS + (cx & 7);
                boolean moved = heldX[slot] != cx || heldZ[slot] != cz;
                heldX[slot] = cx;
                heldZ[slot] = cz;
                for (int k = 0; k < SLOTS; k++) {
                    int sy = minSectionY + ((k - minSectionY) & 15), at = slot * SLOTS + k;
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
        long until = System.nanoTime() + LensClient.tickBudget() / 2;
        int reads = 0;
        scan:
        for (int i = 0; i < order.length && reads < READS_PER_TICK; i++) {
            int cx = ccx + (short) (order[i] >> 16), cz = ccz + (short) order[i], slot = (cz & 7) * CHUNKS + (cx & 7);
            boolean loaded = false;
            for (int k = 0; k < SLOTS; k++) {
                if (known[slot * SLOTS + k]) continue;
                if (reads > 0 && System.nanoTime() > until) break scan;
                if (!loaded) {
                    if (!load(level, slot)) break;
                    loaded = true;
                }
                if (read(level, slot, k)) reads++;
            }
        }
        for (int i = 0; i < RESCANS_PER_TICK && System.nanoTime() < until; i++) {
            rescanAt = (rescanAt + 1) % (CHUNKS * CHUNKS * SLOTS);
            int slot = rescanAt / SLOTS;
            if (known[rescanAt] && load(level, slot)) read(level, slot, rescanAt % SLOTS);
        }
        Arrays.fill(four, null);
        upload();
    }

    /** A far chunk's four chunks into {@link #four}; false when any is not loaded yet. */
    private boolean load(ClientLevel level, int slot) {
        for (int q = 0; q < 4; q++) {
            if (level.getChunkSource().getChunk(heldX[slot] * 2 + (q & 1), heldZ[slot] * 2 + (q >> 1), ChunkStatus.FULL, false) instanceof LevelChunk c) {
                four[q] = c;
            } else {
                return false;
            }
        }
        return true;
    }

    /** Reads one far section from {@link #four} into the mirror; true when it took a real walk of blocks. */
    private boolean read(ClientLevel level, int slot, int k) {
        int at = slot * SLOTS + k, sy = heldY[at], index = sy - level.getMinSectionY();
        boolean walked = false, any = false;
        Arrays.fill(solidIn, (byte) 0);
        Arrays.fill(leavesIn, (byte) 0);
        for (int q = 0; q < 4; q++) {
            LevelChunk chunk = four[q];
            if (index < 0 || index >= chunk.getSectionsCount()) continue;
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir()) continue;
            any = true;
            int ox = (q & 1) * 8, oz = (q >> 1) * 8;
            if ((sy << 4) + 15 < lowest(chunk) - 1) {
                // wholly under the chunk's lowest ground: no ray of the sun comes here, and none need be read
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 8; z++) Arrays.fill(solidIn, (y * 16 + oz + z) * 16 + ox, (y * 16 + oz + z) * 16 + ox + 8, (byte) 4);
                }
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    int row = (y * 16 + oz + (z >> 1)) * 16 + ox;
                    for (int x = 0; x < 16; x++) {
                        int kind = kinds.kindOf(section.getBlockState(x, y, z));
                        if (kind == OPEN) continue;
                        // (the block map's kinds: 1 stops the sun, 2 leaves, 3 a block's lower half, which counts)
                        if (kind == 2) leavesIn[row + (x >> 1)]++;
                        else solidIn[row + (x >> 1)]++;
                    }
                }
            }
            walked = true;
        }
        if (!any) {
            Arrays.fill(scratch, 0);
        } else {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int bits = 0;
                    for (int y = 0; y < 16; y++) {
                        int i = (y * 16 + z) * 16 + x, solid = solidIn[i];
                        bits |= (solid >= 2 ? SOLID : solid == 1 ? PART : leavesIn[i] > 0 ? LEAVES : OPEN) << (y * 2);
                    }
                    // (sixteen parts in a column would read as "not read yet": its top is solid instead)
                    scratch[z * 16 + x] = bits == UNKNOWN ? bits & 0x7FFFFFFF : bits;
                }
            }
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

    /** The lowest ground of a chunk (the first free y over its solid top, the least of its columns). */
    private static int lowest(LevelChunk chunk) {
        int low = Integer.MAX_VALUE;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) low = Math.min(low, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        }
        return low + 1;
    }

    private void upload() {
        int sent = 0;
        for (int slot = 0; slot < dirty.length && sent < UPLOADS_PER_TICK; slot++) {
            if (!dirty[slot]) continue;
            dirty[slot] = false;
            sent++;
            if (!this.sent[slot]) {
                this.sent[slot] = true;
                unsent--;
            }
            staging.clear();
            // texel (x, z * 16 + k): rows run z by z, each z sixteen rows of sixteen columns
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

    /** The texture the chains read as {@code awaken:lens_blocks_far}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens far block map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, SIZE, SIZE * SLOTS, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
