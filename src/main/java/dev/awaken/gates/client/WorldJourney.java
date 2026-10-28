package dev.awaken.gates.client;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.WorldPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The journey down (SPEC §38.7): breaking the Seal to a world floor turns the screen dark at once; motes stream up past
 * you as you fall, the next world's light rises from below and its sound fades in, and once the floor has loaded (at
 * least 1.5 s, at most 6) the dark lifts and you're standing at its best view. Paced messages wait while it's dark.
 */
public final class WorldJourney {
    private WorldJourney() {}

    static final long MIN_MS = 1500, MAX_MS = 6000, SETTLE_MS = 3000, LIFT_MS = 1400;
    private static final int MOTES = 48;
    private static final Object HOLD = new Object();

    private static String world = "";
    private static int glow = 0xFFB060;
    private static long from = -1, lift = -1;

    static void start(WorldPayloads.Journey j) {
        world = j.world();
        glow = j.glow();
        from = Util.getMillis();
        lift = -1;
        dev.awaken.core.api.Notices.client().hold(HOLD, true);
        WorldBed.cue(j.sound());
    }

    static void reset() {
        if (from >= 0) dev.awaken.core.api.Notices.client().hold(HOLD, false);
        from = lift = -1;
    }

    /** True while the screen is (still mostly) dark: the title card waits for the dark to lift. */
    public static boolean dark() {
        if (from < 0) return false;
        return lift < 0 || Util.getMillis() - lift < LIFT_MS / 3;
    }

    static void tick() {
        if (from < 0 || lift >= 0) {
            if (lift >= 0 && Util.getMillis() - lift > LIFT_MS) from = lift = -1;
            return;
        }
        long age = Util.getMillis() - from;
        if (age >= MAX_MS || age >= MIN_MS && ready(age)) {
            lift = Util.getMillis();
            dev.awaken.core.api.Notices.client().hold(HOLD, false);
        }
    }

    /** The floor below has arrived: its world told, the ground under you loaded and (for a while) meshed. */
    private static boolean ready(long age) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != GatesRegistry.GATES) return false;
        WorldPayloads.Arrival a = GateWorldClient.current();
        if (a == null || !a.world().equals(world)) return false;
        if (!mc.level.hasChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4)) return false;
        return age >= SETTLE_MS || mc.levelRenderer.hasRenderedAllSections();
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (from < 0) return;
        long now = Util.getMillis();
        float a = 1;
        if (lift >= 0) {
            float k = Mth.clamp((now - lift) / (float) LIFT_MS, 0, 1);
            a = 1 - k * k * (3 - 2 * k);
            if (a <= 0) return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, argb(a, 0x030305));
        float t = (now - from) / 1000F;
        // the next world's light rising from below, stronger the longer you fall
        float rise = Mth.clamp(t / 2.2F, 0, 1);
        int glowTop = (int) (h * (1 - 0.45F * rise));
        g.fillGradient(0, glowTop, w, h, argb(0, glow), argb(a * 0.22F * rise, glow));
        // motes streaming up past you: the fall
        float speedUp = 0.6F + 0.4F * Mth.clamp(t / 1.2F, 0, 1);
        for (int i = 0; i < MOTES; i++) {
            float hx = hash(i * 3 + 1), hy = hash(i * 3 + 2), hs = hash(i * 3 + 3);
            float speed = (0.35F + 0.9F * hs) * speedUp * h;
            int x = (int) (hx * w);
            float span = h + 24;
            int y = (int) (h + 12 - ((hy * span + t * speed) % span));
            int len = 2 + (int) (hs * 9 * speedUp);
            float ma = a * (0.12F + 0.35F * hs) * Mth.clamp(t * 3, 0, 1);
            int col = hs > 0.8F ? glow : 0xB8B4AC;
            g.fill(x, y, x + 1, y + len, argb(ma, col));
        }
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
