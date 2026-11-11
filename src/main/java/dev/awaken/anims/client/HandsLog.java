package dev.awaken.anims.client;

import dev.awaken.Awaken;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Dev: one line a frame of where the world arms were drawn (the pilot's "handslog on|off", autopilot runs only), so a
 * tour can show that a hand held still is still and that one moving moves without steps (SPEC §65.2). For each arm
 * drawn: its fist and its line in view space as the hand pass got them, its stretch and its distance; and, where the
 * source gave them, the same in the world with how far out the hand is. Off, it costs a field read.
 */
public final class HandsLog {
    private HandsLog() {}

    public static volatile boolean on;
    private static final float[] DRAWN = new float[2 * 8];
    private static final double[] WORLD = new double[2 * 8];
    private static final boolean[] HAS = new boolean[2], HAS_WORLD = new boolean[2];
    private static long since;
    private static String scene = "";

    /** A mark in the log (a pilot verb as it runs), with the log's own clock, so the lines round it can be told apart. */
    public static void mark(String what) {
        if (!on) return;
        long now = System.nanoTime();
        if (since == 0) since = now;
        Awaken.LOG.info("[handslog] mark ms={} {}", (now - since) / 1_000_000L, what);
    }

    /** What is being worked, for the line (a station's kind, a touch's name). */
    public static void scene(String name) {
        if (on) scene = name;
    }

    /** A source's own numbers for an arm (world): the fist, the arm's line back from it, its stretch, how far out it is. */
    public static void arm(int h, double fx, double fy, double fz, double dx, double dy, double dz, double stretch, double out) {
        if (!on) return;
        int j = h * 8;
        WORLD[j] = fx;
        WORLD[j + 1] = fy;
        WORLD[j + 2] = fz;
        WORLD[j + 3] = dx;
        WORLD[j + 4] = dy;
        WORLD[j + 5] = dz;
        WORLD[j + 6] = stretch;
        WORLD[j + 7] = out;
        HAS_WORLD[h] = true;
    }

    /** An arm as the hand pass drew it (view space): the fist, the line back from it (zero for an arm from the corner). */
    static void drawn(int h, float vx, float vy, float vz, float ax, float ay, float az, float stretch, float far) {
        if (!on) return;
        int j = h * 8;
        DRAWN[j] = vx;
        DRAWN[j + 1] = vy;
        DRAWN[j + 2] = vz;
        DRAWN[j + 3] = ax;
        DRAWN[j + 4] = ay;
        DRAWN[j + 5] = az;
        DRAWN[j + 6] = stretch;
        DRAWN[j + 7] = far;
        HAS[h] = true;
    }

    /** The frame's line, if any arm was drawn in it. */
    static void frame(Vec3 eye, Matrix4f view, float fov) {
        if (!on) return;
        long now = System.nanoTime();
        if (since == 0) since = now;
        if (HAS[0] || HAS[1]) {
            StringBuilder b = new StringBuilder(220);
            b.append(String.format(Locale.ROOT, "[handslog] ms=%d scene=%s eye=%.4f,%.4f,%.4f fov=%.3f look=%.5f,%.5f,%.5f", (now - since) / 1_000_000L, scene,
                    eye.x, eye.y, eye.z, fov, -view.m02(), -view.m12(), -view.m22()));
            for (int h = 0; h < 2; h++) {
                if (!HAS[h]) continue;
                int j = h * 8;
                b.append(String.format(Locale.ROOT, " | %s view=%.5f,%.5f,%.5f line=%.4f,%.4f,%.4f stretch=%.4f far=%.4f", h == 0 ? "R" : "L",
                        DRAWN[j], DRAWN[j + 1], DRAWN[j + 2], DRAWN[j + 3], DRAWN[j + 4], DRAWN[j + 5], DRAWN[j + 6], DRAWN[j + 7]));
                if (HAS_WORLD[h]) {
                    b.append(String.format(Locale.ROOT, " world=%.4f,%.4f,%.4f out=%.4f", WORLD[j], WORLD[j + 1], WORLD[j + 2], WORLD[j + 7]));
                }
            }
            Awaken.LOG.info(b.toString());
        }
        HAS[0] = HAS[1] = HAS_WORLD[0] = HAS_WORLD[1] = false;
    }
}
