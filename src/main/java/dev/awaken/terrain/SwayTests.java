package dev.awaken.terrain;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Swaying stays smooth (SPEC 15, "Swaying plants"): the clock never rewinds when a lagging server pulls the game's
 * time back, the sway is the same on both sides of every wrap (the clock's 2048 s, the world positions' 4096 blocks),
 * the gust at a place does not move when the camera does, and the corner texels carry the wind at 16 bits.
 */
final class SwayTests {
    private SwayTests() {}

    /** Marker codes (mode x 8 + level - 1): a short grass tip, a tall grass top, a strand's end, kelp, a wall vine, a pad. */
    private static final int[] CODES = {PlantSway.MODE_ROOTED * 8 + 2, PlantSway.MODE_ROOTED * 8 + 7, PlantSway.MODE_HANGING * 8 + 6,
            PlantSway.MODE_WATER * 8 + 5, PlantSway.MODE_WALL_Z * 8 + 3, PlantSway.FLOAT_CODE};

    static void register() {
        AwakenTests.register("terrain_sway_continuous", 20, h -> {
            clock(h);
            wraps(h);
            grid(h);
            h.succeed();
        });
    }

    /** A server stuck at 15 ticks a second syncs the client's game time every 20 of its ticks; the client ticks at 20. */
    private static void clock(GameTestHelper h) {
        SwayClock clock = new SwayClock();
        double frame = 1.0 / 60.0, real = 0, server = 1000, clientTicks = server, lastSync = 0, prev = Double.NaN, maxErr = 0;
        int rawRewinds = 0;
        double lastRaw = Double.NaN;
        for (int i = 0; i < 60 * 60; i++) {
            real += frame;
            server += frame * 15.0;          // the server's own count
            clientTicks += frame * 20.0;     // the client ticks on regardless
            if (real - lastSync >= 20.0 / 15.0) { // the server's time packet: snap the client to it
                clientTicks = server;
                lastSync = real;
            }
            double raw = clientTicks / 20.0;
            if (!Double.isNaN(lastRaw) && raw < lastRaw) rawRewinds++;
            lastRaw = raw;
            double t = clock.advance(raw, frame, false);
            if (!Double.isNaN(prev)) {
                double step = t - prev;
                h.assertTrue(step > 0, "the clock never stands still or rewinds while playing: step " + step + " at frame " + i);
                h.assertTrue(step <= frame * (1 + SwayClock.MAX_PACE_CHANGE) + 1e-9, "and never lurches ahead: step " + step);
            }
            prev = t;
            if (i > 600) maxErr = Math.max(maxErr, Math.abs(t - server / 20.0));
        }
        h.assertTrue(rawRewinds > 20, "the raw game clock rewinds at every sync in this setting: " + rawRewinds);
        h.assertTrue(maxErr < 1.0, "the smooth clock stays within a second of the server's time: " + maxErr);
        double held = clock.seconds();
        h.assertTrue(clock.advance(held + 0.5, frame, true) == held, "paused, it holds");
        double jumped = clock.advance(held + 30, frame, false);
        h.assertTrue(jumped == held + 30, "a real jump in time (a new world) is followed at once: " + jumped);
    }

    /** The same sway on both sides of the clock's wrap and of the world positions' wrap. */
    private static void wraps(GameTestHelper h) {
        SwayMath.Frame f = frame(1234.5F, 4093.2F, 70.6F, 17.3F);
        SwayMath.Frame g = frame(1234.5F, 4093.2F, 70.6F, 17.3F);
        float[] a = new float[4], b = new float[4];
        float[][] points = {{4095.9F, 71F, 18.4F}, {3.4F, 71F, 4092.6F}, {4090.1F, 69.5F, 4095.5F}};
        for (float[] p : points) {
            for (int code : CODES) {
                f.t = 0F;
                g.t = 2048F;
                SwayMath.plantOffset(f, code, p[0], p[1], p[2], 5F, a);
                SwayMath.plantOffset(g, code, p[0], p[1], p[2], 5F, b);
                h.assertTrue(close(a, b, 3e-4F), "plant " + code + " sways the same at the clock's wrap: " + diff(a, b));
                // the camera crossing x = 0 or z = 0 (or any 4096): world positions and the camera shift by 4096
                f.t = g.t = 777.25F;
                SwayMath.plantOffset(f, code, p[0], p[1], p[2], 5F, a);
                shift(g, -4096F, 0F);
                SwayMath.plantOffset(g, code, p[0] - 4096F, p[1], p[2], 5F, b);
                h.assertTrue(close(a, b, 3e-3F), "plant " + code + " sways the same across the 4096 wrap in x: " + diff(a, b));
                shift(g, 4096F, -4096F);
                SwayMath.plantOffset(g, code, p[0], p[1], p[2] - 4096F, 5F, b);
                h.assertTrue(close(a, b, 3e-3F), "plant " + code + " sways the same across the 4096 wrap in z: " + diff(a, b));
                shift(g, 0F, 4096F);
            }
            f.t = 0F;
            g.t = 2048F;
            SwayMath.leafOffset(f, p[0], p[1], p[2], a);
            SwayMath.leafOffset(g, p[0], p[1], p[2], b);
            h.assertTrue(close(a, b, 3e-4F), "a leaf sways the same at the clock's wrap: " + diff(a, b));
            f.t = g.t = 777.25F;
            SwayMath.leafOffset(f, p[0], p[1], p[2], a);
            shift(g, -4096F, -4096F);
            SwayMath.leafOffset(g, p[0] - 4096F, p[1], p[2] - 4096F, b);
            h.assertTrue(close(a, b, 3e-3F), "a leaf sways the same across the 4096 wrap: " + diff(a, b));
            shift(g, 4096F, 4096F);
        }
    }

    /** The gust grid is fixed in the world, and the texels carry the wind at 16 bits. */
    private static void grid(GameTestHelper h) {
        Wind.Field wind = new Wind.Field().setTicks(77L, 123_456.25, 0F, 0F);
        float worldX = 1040.5F, worldZ = 790.25F;
        float first = Float.NaN;
        ByteBuffer buf = ByteBuffer.allocate(SwayMath.TEXELS * 4).order(ByteOrder.LITTLE_ENDIAN);
        SwayMath.Frame back = new SwayMath.Frame();
        for (int step = 0; step < 40; step++) {
            double camX = 1000 + step * 0.37, camZ = 760 + step * 0.21; // walking
            SwayMath.Frame f = new SwayMath.Frame();
            f.camX = (float) camX;
            f.camZ = (float) camZ;
            f.dirX = wind.dirX;
            f.dirZ = wind.dirZ;
            f.profileOff = SwayMath.profileOffset(camX, camZ, wind.dirX, wind.dirZ);
            for (int i = 0; i < SwayMath.PROFILE; i++) {
                float along = SwayMath.sampleAlong(i, f.profileOff);
                f.gust[i] = wind.gust(camX + wind.dirX * along, camZ + wind.dirZ * along);
            }
            buf.clear();
            SwayMath.encode(f, buf);
            SwayMath.decode(buf, back);
            float g = SwayMath.gustAt(back, worldX, worldZ);
            if (Float.isNaN(first)) first = g;
            h.assertTrue(Math.abs(g - first) < 2e-4F, "the gust at a place stays put while the camera walks: " + first + " -> " + g);
            h.assertTrue(Math.abs(back.dirX - f.dirX) < 4e-5F && Math.abs(back.dirZ - f.dirZ) < 4e-5F, "direction survives at 16 bits");
        }
    }

    private static SwayMath.Frame frame(float t, float camX, float camY, float camZ) {
        SwayMath.Frame f = new SwayMath.Frame();
        f.t = t;
        f.camX = camX;
        f.camY = camY;
        f.camZ = camZ;
        f.dirX = 0.8F;
        f.dirZ = 0.6F;
        f.steady = 0.45F;
        f.leafAmp = 0.11F;
        f.air = 0.2F;
        f.water = 0.16F;
        f.sheen = 0.1F;
        f.push = 0.32F;
        f.feetX = camX;
        f.feetY = camY - 1.6F;
        f.feetZ = camZ;
        for (int i = 0; i < SwayMath.PROFILE; i++) f.gust[i] = 0.5F + 0.5F * (float) Math.sin(i * 0.3);
        return f;
    }

    private static void shift(SwayMath.Frame f, float dx, float dz) {
        f.camX += dx;
        f.camZ += dz;
        f.feetX += dx;
        f.feetZ += dz;
    }

    private static boolean close(float[] a, float[] b, float tol) {
        for (int i = 0; i < 4; i++) if (Math.abs(a[i] - b[i]) > tol) return false;
        return true;
    }

    private static String diff(float[] a, float[] b) {
        return String.format(java.util.Locale.ROOT, "(%.5f %.5f %.5f) vs (%.5f %.5f %.5f)", a[0], a[1], a[2], b[0], b[1], b[2]);
    }
}
