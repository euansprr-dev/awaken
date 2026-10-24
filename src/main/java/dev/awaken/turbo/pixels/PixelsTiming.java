package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import dev.awaken.Awaken;

import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Measurement only (autopilot {@code pixels gpu on}): GPU time of the world pass (level, hand and post effects, at
 * render scale) and of the upscale, from timestamp queries written around them. Frame times on a busy Mac measure the
 * CPU; these measure what Pixels actually changes. Off unless asked for; it then allocates a little per frame.
 */
final class PixelsTiming {
    private PixelsTiming() {}

    /** Stamps per frame: before the world, after the post effects, after the upscale. */
    static final int BEFORE_WORLD = 0, AFTER_WORLD = 1, AFTER_UPSCALE = 2;
    // results come back a few frames late on a busy GPU: read each slot RING - 1 frames after writing it
    private static final int STAMPS = 3, RING = 8;
    private static boolean on;
    private static GpuQueryPool[] pools;
    // which stamps each slot holds this time round, as bits
    private static final int[] written = new int[RING];
    private static int slot;
    private static float periodNs = 1f;
    // samples since the last report, in milliseconds
    private static final float[] WORLD = new float[1 << 14], UPSCALE = new float[1 << 14];
    private static int count;

    static void enable(boolean enable) {
        if (enable == on) return;
        on = enable;
        if (on) {
            pools = new GpuQueryPool[RING];
            for (int i = 0; i < RING; i++) pools[i] = RenderSystem.getDevice().createTimestampQueryPool(STAMPS);
            periodNs = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
            Arrays.fill(written, 0);
            count = 0;
        } else if (pools != null) {
            for (GpuQueryPool p : pools) p.close();
            pools = null;
        }
    }

    static void stamp(int which) {
        if (!on) return;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pools[slot], which);
        written[slot] |= 1 << which;
    }

    /** After every frame: read the oldest slot (written RING - 1 frames ago), then move on to it. */
    static void frameEnd() {
        if (!on) return;
        int next = (slot + 1) % RING;
        if (written[next] == (1 << STAMPS) - 1 && count < WORLD.length) {
            OptionalLong[] v = pools[next].getValues(0, STAMPS);
            if (v[0].isPresent() && v[1].isPresent() && v[2].isPresent()) {
                WORLD[count] = (v[1].getAsLong() - v[0].getAsLong()) * periodNs / 1e6f;
                UPSCALE[count] = (v[2].getAsLong() - v[1].getAsLong()) * periodNs / 1e6f;
                count++;
            }
        }
        written[next] = 0;
        slot = next;
    }

    /** Logs the GPU times since the last report and starts over. */
    static String report(String label) {
        if (!on) return "gpu timing is off";
        if (count == 0) return label + ": no GPU samples";
        float[] w = Arrays.copyOf(WORLD, count), u = Arrays.copyOf(UPSCALE, count);
        Arrays.sort(w);
        Arrays.sort(u);
        String line = String.format(Locale.ROOT, "%s samples=%d world_p50=%.2fms world_p95=%.2fms world_p99=%.2fms upscale_p50=%.2fms upscale_p99=%.2fms",
                label, count, pct(w, 0.5), pct(w, 0.95), pct(w, 0.99), pct(u, 0.5), pct(u, 0.99));
        Awaken.LOG.info("[pixels] gpu {}", line);
        count = 0;
        return line;
    }

    static void reset() {
        count = 0;
    }

    private static float pct(float[] sorted, double q) {
        return sorted[Math.min(sorted.length - 1, (int) (sorted.length * q))];
    }
}
