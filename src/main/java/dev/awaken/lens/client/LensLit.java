package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The lit air (SPEC §22 "Steady Light", the sun's rays): how much of the sun reaches every block of air near the camera,
 * as a texture the light pass reads along each view ray. Where the heightfield can only say "this air is above the
 * ground", this knows the air under a canopy from the air in its gaps, the far side of a trunk, a doorway's beam: the
 * rays of the sun standing in the air.
 *
 * <p>Nothing of it is worked out on the GPU. The sun travels in the world's x-y plane, so every z-slice of the block map
 * (LensBlocks) is its own flat problem: a worker thread sweeps each slice once from the sun's side, every cell taking
 * its light from the two cells the ray came through, cut by what the cell holds (a block stops it, leaves pass a part,
 * some leaf blocks more than others so a canopy's shade is dappled). The land beyond the map is the light pass's to
 * judge, from the volume's edge on. A whole volume is two million cells and takes a few milliseconds, about once a second and sooner
 * while you move; a frame pays only its texture reads, the same in a forest as on a plain.
 *
 * <p>The texture is one byte a cell: 128 slices of 128 x 128 (x across, y up), sixteen slices across and eight down.
 * It is addressed from the volume's own corner, which the frame carries (LensFrame.lit), so a volume is always whole.
 */
final class LensLit {
    static final int SIZE = LensBlocks.SIZE, ACROSS = 16, WIDTH = SIZE * ACROSS, HEIGHT = SIZE * (SIZE / ACROSS);
    // a new volume this often at rest, and no sooner than this while the camera has moved a few blocks
    private static final int EVERY = 20, EVERY_MOVING = 5, MOVED = 5;
    private static final int IDLE = 0, WORKING = 1, READY = 2;

    final Texture texture = new Texture();
    private final ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT);
    private final int[] cells = new int[LensBlocks.CELLS];
    private final byte[] pass = new byte[SIZE * SIZE * SIZE];
    private final float[] edge = new float[SIZE];
    private final float[] a = new float[SIZE + 2], b = new float[SIZE + 2];
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Awaken Lens lit air");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile int state = IDLE;
    private volatile long lastNanos;
    private long uploadNanos;
    private int sweeps;
    // the volume the texture holds now, and the one being worked
    private int originX, originY, originZ, jobX, jobY, jobZ;
    private float jobLx, jobLy;
    private boolean valid;
    private int since = EVERY;

    /** Whether the texture holds a volume. */
    boolean valid() {
        return valid;
    }

    int originX() {
        return originX;
    }

    int originY() {
        return originY;
    }

    int originZ() {
        return originZ;
    }

    /** The last sweep's and the last upload's time in milliseconds, and how many volumes there have been (dev report). */
    String report() {
        return String.format(java.util.Locale.ROOT, "sweep %.2f ms on its worker, upload %.2f ms, %d volumes", lastNanos / 1.0e6, uploadNanos / 1.0e6, sweeps);
    }

    /** Nothing here is true any more (another world, no sun): the next tick starts fresh. */
    void forget() {
        valid = false;
        since = EVERY;
    }

    /**
     * Once a client tick, after the block map's own: takes up a finished volume, and starts the next when it is due.
     * {@code lx, ly, lz} point toward the light (the sun, or the moon by night).
     */
    void tick(LensBlocks blocks, float lx, float ly, float lz, int camX, int camZ) {
        if (state == READY) {
            long t0 = System.nanoTime();
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), pixels, 0, 0, 0, 0, WIDTH, HEIGHT);
            uploadNanos = System.nanoTime() - t0;
            dev.awaken.turbo.pixels.Pixels.mark(1); // measurement only (the GPU timing's trace)
            sweeps++;
            originX = jobX;
            originY = jobY;
            originZ = jobZ;
            valid = true;
            state = IDLE;
        }
        since++;
        if (state != IDLE) return;
        // the sun's plane is x-y; a light that leaves it (no world of ours has one) gets no volume
        float flat = (float) Math.sqrt(lx * lx + ly * ly);
        if (ly < 0.02f || Math.abs(lz) > 0.08f || flat < 0.5f) {
            valid = false;
            return;
        }
        boolean moved = Math.abs(camX - (originX + SIZE / 2)) > MOVED || Math.abs(camZ - (originZ + SIZE / 2)) > MOVED;
        if (valid && since < (moved ? EVERY_MOVING : EVERY)) return;
        since = 0;
        blocks.snapshot(cells);
        jobX = blocks.originX();
        jobY = blocks.minY();
        jobZ = blocks.originZ();
        jobLx = lx / flat;
        jobLy = ly / flat;
        state = WORKING;
        dev.awaken.turbo.pixels.Pixels.mark(2);
        worker.execute(this::work);
    }

    private void work() {
        try {
            long t0 = System.nanoTime();
            passes();
            sweep();
            lastNanos = System.nanoTime() - t0;
            state = READY;
        } catch (RuntimeException e) {
            Awaken.LOG.warn("[lens] the lit air's sweep failed; the sun's rays fall back to the heightfield", e);
            state = IDLE;
        }
    }

    // what each cell lets through, [z][x][y]: 0 a block, 255 open air; leaves a part, and one leaf block in three most of
    // it (the canopy's gaps: its shade is dappled in whole blocks, as its texture is in pixels)
    private void passes() {
        for (int z = 0; z < SIZE; z++) {
            int wz = jobZ + z;
            for (int x = 0; x < SIZE; x++) {
                int wx = jobX + x, slot = ((wz >> 4) & 7) * LensBlocks.CHUNKS + ((wx >> 4) & 7), inner = (wz & 15) * 16 + (wx & 15);
                int at = (z * SIZE + x) * SIZE;
                for (int k = 0; k < LensBlocks.SLOTS; k++) {
                    int sy = (jobY >> 4) + k, bits = cells[(slot * LensBlocks.SLOTS + (sy & 7)) * 256 + inner];
                    for (int j = 0; j < 16; j++, at++) {
                        // (a section not read yet is open: it never darkens)
                        int kind = bits == LensBlocks.UNKNOWN ? 0 : (bits >>> (j * 2)) & 3;
                        pass[at] = switch (kind) {
                            case 0 -> (byte) 255;
                            case 2 -> (byte) (hash(wx, (sy << 4) + j, wz) < 85 ? 232 : 84);
                            case 3 -> (byte) 110;
                            default -> 0;
                        };
                    }
                }
            }
        }
    }

    private static int hash(int x, int y, int z) {
        int h = x * 0x9E3779B1 ^ y * 0x85EBCA77 ^ z * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        return (h >>> 16) & 255;
    }

    private void sweep() {
        float lx = jobLx, ly = jobLy, ax = Math.abs(lx);
        int sx = lx >= 0f ? 1 : -1;
        // beyond the map the air is lit: the volume holds only what its own blocks do. The land out there is the light
        // pass's to judge, by the same sun map that shades the ground (a far ridge's shade kept here and not there lay
        // on open grass as a patchwork under a low sun)
        java.util.Arrays.fill(edge, -4096f);
        long base = MemoryUtil.memAddress(pixels);
        boolean low = ax >= ly;
        float f = low ? ly / Math.max(ax, 1.0e-4f) : ax / ly;
        for (int z = 0; z < SIZE; z++) {
            float shade = edge[z];
            long tile = base + (long) (z >> 4) * SIZE * WIDTH + (long) (z & 15) * SIZE;
            float[] prev = a, cur = b;
            if (low) {
                // a low sun: column by column from its side, each cell lit by the two cells beside it one column sunward
                for (int y = 0; y <= SIZE; y++) prev[y] = y + 0.5f > shade ? 1f : 0f;
                for (int i = 0; i < SIZE; i++) {
                    int x = sx > 0 ? SIZE - 1 - i : i, at = (z * SIZE + x) * SIZE;
                    for (int y = 0; y < SIZE; y++) {
                        float v = (prev[y] + (prev[y + 1] - prev[y]) * f) * ((pass[at + y] & 255) * (1f / 255f));
                        cur[y] = v;
                        MemoryUtil.memPutByte(tile + (long) y * WIDTH + x, (byte) (v * 255f + 0.5f));
                    }
                    cur[SIZE] = 1f;
                    float[] t = prev;
                    prev = cur;
                    cur = t;
                }
            } else {
                // a high sun: row by row from the top, each cell lit by the cell above and the one beside that, sunward
                // (index x + 1; the cell outside the map on the sun's side is the land's shade out there)
                for (int x = 0; x < SIZE + 2; x++) prev[x] = 1f;
                for (int y = SIZE - 1; y >= 0; y--) {
                    prev[sx > 0 ? SIZE + 1 : 0] = y + 1.5f > shade ? 1f : 0f;
                    for (int x = 0; x < SIZE; x++) {
                        float v = (prev[x + 1] + (prev[x + 1 + sx] - prev[x + 1]) * f) * ((pass[(z * SIZE + x) * SIZE + y] & 255) * (1f / 255f));
                        cur[x + 1] = v;
                        MemoryUtil.memPutByte(tile + (long) y * WIDTH + x, (byte) (v * 255f + 0.5f));
                    }
                    float[] t = prev;
                    prev = cur;
                    cur = t;
                }
            }
        }
    }

    /**
     * How much of the sun reaches a point by the volume the texture holds, 0..1; -1 outside it (the dust that only shows
     * in a sunbeam). Read while a sweep may be writing: a cell is then last second's or this one's, both true enough.
     */
    float at(double x, double y, double z) {
        if (!valid) return -1f;
        int ix = (int) Math.floor(x) - originX, iy = (int) Math.floor(y) - originY, iz = (int) Math.floor(z) - originZ;
        if ((ix | iy | iz) < 0 || ix >= SIZE || iy >= SIZE || iz >= SIZE) return -1f;
        return (pixels.get(((iz >> 4) * SIZE + iy) * WIDTH + (iz & 15) * SIZE + ix) & 255) * (1f / 255f);
    }

    /** The texture the chains read as {@code awaken:lens_lit}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens lit air", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.R8_UNORM, WIDTH, HEIGHT, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            // lit everywhere until the first volume lands
            ByteBuffer one = MemoryUtil.memAlloc(WIDTH * HEIGHT);
            MemoryUtil.memSet(one, 255);
            device.createCommandEncoder().writeToTexture(texture, one, 0, 0, 0, 0, WIDTH, HEIGHT);
            MemoryUtil.memFree(one);
        }
    }
}
