package dev.awaken.world.fauna.client;

import dev.awaken.Awaken;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The awakening pulse: a soft signal-blue vignette that swells and fades over less than two seconds, with one ring
 * travelling outward. Vanilla's Distortion Effects slider is the reduced-motion switch: below half there is no ring,
 * the vignette dims with it, and at zero nothing is drawn.
 */
final class RevealPulse {
    private RevealPulse() {}

    private static final Identifier VIGNETTE = Awaken.id("textures/gui/fauna_pulse.png");
    private static final Identifier RING = Awaken.id("textures/gui/fauna_ring.png");
    private static final long DURATION_MS = 1800;

    private static long start = -1;
    private static float strength;

    static void start(int s) {
        start = Util.getMillis();
        strength = Mth.clamp(s / 100.0F, 0.0F, 1.0F);
    }

    static void stop() {
        start = -1;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (start < 0) return;
        Minecraft mc = Minecraft.getInstance();
        long t = Util.getMillis() - start;
        if (t > DURATION_MS || mc.player == null) {
            start = -1;
            return;
        }
        float distortion = mc.options.screenEffectScale().get().floatValue();
        if (distortion <= 0.01F) return;
        float k = t / (float) DURATION_MS;
        // Quick swell, long soft fade.
        float swell = k < 0.15F ? k / 0.15F : 1.0F - (k - 0.15F) / 0.85F;
        swell = swell * swell * (3.0F - 2.0F * swell);
        int w = g.guiWidth(), h = g.guiHeight();
        int a = (int) (swell * 0.6F * strength * (0.4F + 0.6F * distortion) * 255.0F);
        if (a > 2) g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, w, h, 64, 64, 64, 64, ARGB.color(a, 0xFFFFFF));
        if (distortion < 0.5F) return;
        int size = (int) ((0.2F + k * 1.3F) * Math.max(w, h)) * 2;
        int ra = (int) ((1.0F - k) * (1.0F - k) * 0.5F * strength * 255.0F);
        if (ra > 2) g.blit(RenderPipelines.GUI_TEXTURED, RING, (w - size) / 2, (h - size) / 2, 0.0F, 0.0F, size, size, 64, 64, 64, 64, ARGB.color(ra, 0xFFFFFF));
    }
}
