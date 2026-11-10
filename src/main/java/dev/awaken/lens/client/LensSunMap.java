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
 * The sun map (SPEC §22; "Steady Light", the trim): for each column of the heightfield's ring, the height above which
 * the sun reaches it. The light pass's mist reads it to know whether a point of far air is lit.
 *
 * <p>It was a pass of the chain, marched on the GPU for every column every frame, though the sun moves a third of a
 * degree a second and the land hardly at all. The sun travels in the world's x-y plane, so each row of the ring is its
 * own problem with an exact answer in one sweep from the sun's side: a column's height is its own top or the height
 * handed on from the column sunward, less the light's drop over a block. A worker thread sweeps the whole ring (a
 * million columns, a few milliseconds) about twice a second from the heightfield's mirror; the result goes up an
 * eighth of the texture a tick, so no tick carries more than half a megabyte.
 *
 * <p>One cell a block on every tier (the GPU's pass had two-block cells on Cinematic), reaching the whole trusted
 * window (the GPU's march gave out after 24 steps). The texture is RGBA8 1024x1024 by world block modulo 1024: r/g the
 * height without leaves, b/a with leaves, as lens_frame.glsl's packHeight packs them; zero (below anything) where
 * there is no answer.
 */
final class LensSunMap {
    static final int SIZE = LensHeightfield.SIZE, ROWS_PER_TICK = SIZE / 8, EVERY = 8;
    private static final int IDLE = 0, WORKING = 1, READY = 2;

    final Texture texture = new Texture();
    // the sweep being worked, and the last whole one (what the rolling upload sends)
    private ByteBuffer work = MemoryUtil.memCalloc(SIZE * SIZE * 4), whole = MemoryUtil.memCalloc(SIZE * SIZE * 4);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Awaken Lens sun map");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile int state = IDLE;
    private volatile long lastNanos;
    private int since = EVERY, row, sweeps;
    private boolean have;

    /** The last sweep's time and how many there have been (dev report). */
    String report() {
        return String.format(java.util.Locale.ROOT, "sun map sweep %.2f ms on its worker, %d sweeps", lastNanos / 1.0e6, sweeps);
    }

    /**
     * Once a client tick where there is a sun: takes up a finished sweep, sends the next rows of the last whole one, and
     * starts the next sweep when it is due. {@code lx, ly, lz} point toward the light (the sun, or the moon by night).
     */
    void tick(LensHeightfield height, float lx, float ly, float lz, int camX, int camZ) {
        if (state == READY) {
            ByteBuffer t = whole;
            whole = work;
            work = t;
            have = true;
            sweeps++;
            state = IDLE;
        }
        if (have) {
            // an eighth of the ring a tick, round and round: the whole of a sweep is up within eight ticks of its end
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(),
                    MemoryUtil.memSlice(whole, row * SIZE * 4, ROWS_PER_TICK * SIZE * 4), 0, 0, 0, row, SIZE, ROWS_PER_TICK);
            row = (row + ROWS_PER_TICK) % SIZE;
        }
        if (++since < EVERY || state != IDLE) return;
        since = 0;
        ByteBuffer into = work;
        float maxTop = height.maxTop();
        state = WORKING;
        worker.execute(() -> {
            try {
                long t0 = System.nanoTime();
                sweep(height, into, lx, ly, lz, camX, camZ, maxTop);
                lastNanos = System.nanoTime() - t0;
                state = READY;
            } catch (RuntimeException e) {
                Awaken.LOG.warn("[lens] the sun map's sweep failed; the far mist takes the air as lit", e);
                state = IDLE;
            }
        });
    }

    private static void sweep(LensHeightfield height, ByteBuffer into, float lx, float ly, float lz, int camX, int camZ, float maxTop) {
        long base = MemoryUtil.memAddress(into);
        MemoryUtil.memSet(base, 0, (long) SIZE * SIZE * 4);
        // no light from above, or a light out of the x-y plane (no world of ours has one): no answer, all air lit
        if (ly <= 0.02f || Math.abs(lz) > 0.08f) return;
        int reach = (int) LensHeightfield.TRUSTED, sx = lx >= 0f ? 1 : -1;
        // the light's drop over one block toward the sun (straight overhead: nothing is handed on)
        float drop = ly / Math.max(Math.abs(lx), 1.0e-4f);
        for (int dz = -reach; dz <= reach; dz++) {
            int z = (camZ + dz) & (SIZE - 1);
            long rowAt = base + (long) z * SIZE * 4;
            float solid = -4096f, all = -4096f;
            for (int i = 0; i <= 2 * reach; i++) {
                int x = (camX + sx * (reach - i)) & (SIZE - 1);
                solid = Math.max(height.solidAt(x, z), solid - drop);
                all = Math.max(height.topAt(x, z), all - drop);
                int qs = pack(solid), qa = pack(Math.max(all, solid));
                long at = rowAt + (long) x * 4;
                MemoryUtil.memPutByte(at, (byte) (qs >> 8));
                MemoryUtil.memPutByte(at + 1, (byte) qs);
                MemoryUtil.memPutByte(at + 2, (byte) (qa >> 8));
                MemoryUtil.memPutByte(at + 3, (byte) qa);
            }
        }
    }

    // lens_frame.glsl's packHeight: 16 bits over -256..768
    private static int pack(float h) {
        float v = (h + 256f) / 1024f;
        return v <= 0f ? 0 : v >= 1f ? 65535 : (int) (v * 65535f + 0.5f);
    }

    /** The texture the chains read as {@code awaken:lens_sunmap}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens sun map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            // all air lit until the first sweep lands
            ByteBuffer zero = MemoryUtil.memCalloc(SIZE * SIZE * 4);
            device.createCommandEncoder().writeToTexture(texture, zero, 0, 0, 0, 0, SIZE, SIZE);
            MemoryUtil.memFree(zero);
        }
    }
}
