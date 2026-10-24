package dev.awaken.holo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.holo.ui.HoloTimeline;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * The System's cards as projected glass in the GUI pass (docs/modules/holo.md "System cards"): the K panel's own
 * shaders ({@code holo_glass}, {@code holo_glow}) on GUI pipelines, so a toast, a choice card, a story line, the Now
 * line and the first-power choice share the panel's glass, hairline edge, corner brackets, static scan lines and
 * shimmer, with one even tint for contrast and text as light (no shadow, a faint cyan halo). A card scans in from the
 * top behind a bright line and glitches out in three bands that slip apart and fade; with reduced motion both are a
 * plain fade. Installed into {@link HudLook}; drawn only while {@code statusStyle = HOLO} and the GUI pipelines
 * compiled.
 */
final class HoloGuiLook implements HudLook.Look {
    static final HoloGuiLook INSTANCE = new HoloGuiLook();

    private HoloGuiLook() {}

    private static final int TINT = 0x040912;
    private static final float TINT_ALPHA = 0.40f;
    private static final int GLOW_RGB = 0x7FDCFF;
    private static final float GLOW_ALPHA = 0.30f;
    /** The interference band: one slow pass through the card every {@code DRIFT_S} seconds, {@code DRIFT_H} units tall. */
    private static final float DRIFT_S = 6.5f, DRIFT_H = 5f;
    private static final float[] DX = {-0.5f, 0.5f, -0.5f, 0.5f}, DY = {-0.5f, -0.5f, 0.5f, 0.5f};
    // the glitch's three bands (fractions of the height) and which way each slips
    private static final float[] BAND = {0f, 0.38f, 0.71f, 1f};
    private static final float[] SLIP = {-1f, 1.4f, -0.6f};

    @Override
    public boolean enabled() {
        return HoloConfig.statusStyle == HoloConfig.StatusStyle.HOLO && HoloPipelines.guiOk();
    }

    @Override
    public void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float alpha, float reveal, float glitch, int style) {
        if (w <= 0 || h <= 0) return;
        boolean reduced = HoloConfig.reducedMotion;
        if (reduced) {
            alpha *= Math.min(1f, Math.max(0f, reveal)) * (1f - Math.min(1f, Math.max(0f, glitch)));
            reveal = 1f;
            glitch = 0f;
        }
        reveal = Math.min(1f, Math.max(0f, reveal));
        glitch = Math.min(1f, Math.max(0f, glitch));
        alpha *= 1f - glitch * glitch;
        if (alpha <= 0.01f || reveal <= 0f) return;
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        int rgb = accent & 0xFFFFFF;
        // a soft light around the card, as if it were cast: faint at rest (a calm HUD), brighter while it scans in
        int haloCol = argb(rgb, (0.20f + 0.30f * (1f - reveal)) * alpha * reveal);
        float m = 8f;
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, pose, x - m, y - m, x + w + m, y + h * reveal + m,
                -m, -m, w + m, h * reveal + m, haloCol, haloCol, w, Math.max(1, Math.round(h * reveal))));
        float seen = HoloTimeline.easeOut(reveal) * h;
        int glassCol = argb(rgb, alpha);
        int tint = argb(TINT, TINT_ALPHA * alpha);
        if (glitch <= 0f) {
            g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLASS, pose, x, y, x + w, y + seen, 0, 0, w, seen, glassCol, glassCol, w, h));
            if (seen > 2) g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLASS, pose, x + 1, y + 1, x + w - 1, y + seen - 1,
                    0, 0, 0, 0, tint, tint, 0, 0));
        } else {
            // three bands slip sideways and fade (the glass's own UVs travel with each band, so its edges tear)
            float slip = glitch * 3.5f;
            for (int b = 0; b < 3; b++) {
                float y0 = BAND[b] * h, y1 = BAND[b + 1] * h;
                float dx = SLIP[b] * slip;
                g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLASS, pose, x + dx, y + y0, x + w + dx, y + y1, 0, y0, w, y1,
                        glassCol, glassCol, w, h));
                g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLASS, pose, x + 1 + dx, y + Math.max(1, y0), x + w - 1 + dx,
                        y + Math.min(h - 1, y1), 0, 0, 0, 0, tint, tint, 0, 0));
            }
        }
        // the K panel's family: its rim and corner brackets, a slow interference band, a projection edge
        float slip0 = glitch > 0 ? SLIP[0] * glitch * 3.5f : 0f; // the frame rides the top band as it tears
        if ((style & HudLook.FRAME) != 0) frame(g, pose, x + slip0, y, w, h, seen, rgb, alpha, reveal, glitch);
        if ((style & HudLook.DRIFT) != 0 && glitch < 0.5f) drift(g, pose, x, y, w, seen, h, rgb, alpha, reduced);
        if ((style & HudLook.EMITTER) != 0) emitter(g, pose, x + w, y, seen, rgb, alpha, reveal);
        // the scan line at the edge of what has been drawn
        if (reveal < 1f) {
            int line = argb(rgb | 0x303030, 0.9f * alpha * (1f - reveal * 0.5f));
            g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, pose, x - 2, y + seen - 2.5f, x + w + 2, y + seen + 2.5f,
                    -0.9f, -1, 0.9f, 1, line, line, -2, 0));
        }
    }

    /** The rim (the K panel's additive edge lines) and the corner brackets just outside it; the bottom ones once scanned in. */
    private static void frame(GuiGraphicsExtractor g, Matrix3x2f pose, float x, float y, int w, int h, float seen, int rgb, float alpha,
                              float reveal, float glitch) {
        int rim = argb(rgb, (0.34f + 0.40f * (1f - reveal)) * alpha);
        float t = 1.3f;
        boolean whole = seen >= h - 0.5f;
        g.submitGuiElementRenderState(hLine(pose, x, y - t, x + w, y + t, rim));
        if (whole) g.submitGuiElementRenderState(hLine(pose, x, y + h - t, x + w, y + h + t, rim));
        g.submitGuiElementRenderState(vLine(pose, x - t, y, x + t, y + seen, rim));
        g.submitGuiElementRenderState(vLine(pose, x + w - t, y, x + w + t, y + seen, rim));
        float ba = (0.60f + 0.30f * (1f - reveal)) * alpha * (1f - glitch);
        if (ba <= 0.01f) return;
        int b = argb(rgb | 0x202020, ba);
        float len = Math.min(10f, Math.min(w, h) / 3f), o = 2.5f, bt = 1.4f;
        g.submitGuiElementRenderState(hLine(pose, x - o, y - o - bt, x - o + len, y - o + bt, b));
        g.submitGuiElementRenderState(vLine(pose, x - o - bt, y - o, x - o + bt, y - o + len, b));
        g.submitGuiElementRenderState(hLine(pose, x + w + o - len, y - o - bt, x + w + o, y - o + bt, b));
        g.submitGuiElementRenderState(vLine(pose, x + w + o - bt, y - o, x + w + o + bt, y - o + len, b));
        if (!whole) return;
        g.submitGuiElementRenderState(hLine(pose, x - o, y + h + o - bt, x - o + len, y + h + o + bt, b));
        g.submitGuiElementRenderState(vLine(pose, x - o - bt, y + h + o - len, x - o + bt, y + h + o, b));
        g.submitGuiElementRenderState(hLine(pose, x + w + o - len, y + h + o - bt, x + w + o, y + h + o + bt, b));
        g.submitGuiElementRenderState(vLine(pose, x + w + o - bt, y + h + o - len, x + w + o + bt, y + h + o, b));
    }

    /** A soft band of light drifting down through the card (still, a third of the way down, with reduced motion). */
    private static void drift(GuiGraphicsExtractor g, Matrix3x2f pose, int x, int y, int w, float seen, int h, int rgb, float alpha,
                              boolean reduced) {
        long period = (long) (DRIFT_S * 1e9);
        float ph = reduced ? 0.35f : (System.nanoTime() % period) / (float) period;
        float cy = y - DRIFT_H + ph * (h + 2 * DRIFT_H);
        int peak = argb(rgb | 0x303030, 0.20f * alpha), none = rgb & 0xFFFFFF;
        band(g, pose, x + 1, w - 2, cy - DRIFT_H, cy, none, peak, y, y + seen);
        band(g, pose, x + 1, w - 2, cy, cy + DRIFT_H, peak, none, y, y + seen);
        // its bright interference line, and two faint ones trailing it
        for (int i = 0; i < 3; i++) {
            float ly = cy - i * 2.2f;
            if (ly - 0.6f < y || ly + 0.6f > y + seen) continue;
            int lc = argb(rgb | 0x404040, (i == 0 ? 0.30f : 0.12f) * alpha);
            g.submitGuiElementRenderState(hLine(pose, x + 1, ly - 0.6f, x + w - 1, ly + 0.6f, lc));
        }
    }

    /** One half of the band, clipped to [lo, hi], its colours interpolated at the cut. */
    private static void band(GuiGraphicsExtractor g, Matrix3x2f pose, float x, float w, float y0, float y1, int c0, int c1, float lo, float hi) {
        float a = Math.max(y0, lo), b = Math.min(y1, hi);
        if (b - a < 0.05f) return;
        int ca = lerpArgb(c0, c1, (a - y0) / (y1 - y0)), cb = lerpArgb(c0, c1, (b - y0) / (y1 - y0));
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, pose, x, a, x + w, b, 0, 0, 0, 0, ca, cb, 0, 0));
    }

    /** The edge the card is projected from (its screen side): a bright line with a soft skirt, flaring as it arrives. */
    private static void emitter(GuiGraphicsExtractor g, Matrix3x2f pose, float ex, int y, float seen, int rgb, float alpha, float reveal) {
        int col = argb(rgb | 0x404040, (0.40f + 0.55f * (1f - reveal)) * alpha);
        float t = 3.5f + 3f * (1f - reveal);
        g.submitGuiElementRenderState(vLine(pose, ex - t, y - 3, ex + t, y + seen + 3, col));
    }

    private static Quad hLine(Matrix3x2f pose, float x0, float y0, float x1, float y1, int col) {
        return new Quad(HoloPipelines.GUI_GLOW, pose, x0, y0, x1, y1, -0.8f, -1, 0.8f, 1, col, col, -2, 0);
    }

    /** A vertical line: the line shape runs across UV0.y, so the quad's UVs turn with it. */
    private static Quad vLine(Matrix3x2f pose, float x0, float y0, float x1, float y1, int col) {
        return new Quad(HoloPipelines.GUI_GLOW, pose, x0, y0, x1, y1, -0.8f, -1, 0.8f, 1, col, col, -2, 0, true);
    }

    private static int lerpArgb(int a, int b, float t) {
        t = Math.min(1f, Math.max(0f, t));
        int aa = a >>> 24, ab = b >>> 24;
        return (Math.round(aa + (ab - aa) * t) << 24) | (b & 0xFFFFFF | a & 0xFFFFFF) & 0xFFFFFF;
    }

    // ---- a whole panel in the GUI pass (the inventory) ---------------------------------------------------------

    /**
     * A panel drawn in the GUI pass exactly as {@link HoloRender} draws a settled 3D panel, unit for unit (the
     * inventory, whose 3D panel lands on this rectangle and hands over to it): the K panel's glass, one even tint
     * feathered over 14 units toward the rim, its halo, rim and 18-unit corner brackets. {@code alpha} fades all of it.
     */
    void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0.01f || w <= 0 || h <= 0) return;
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        int rgb = Glass.EDGE;
        float m = 16f;
        int halo = argb(rgb, 0.42f * alpha);
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, pose, x - m, y - m, x + w + m, y + h + m, -m, -m, w + m, h + m,
                halo, halo, w, h));
        int glass = argb(rgb, alpha);
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLASS, pose, x, y, x + w, y + h, 0, 0, w, h, glass, glass, w, h));
        float in = 3f, f = 14f;
        g.submitGuiElementRenderState(new Soft(pose, x + in + f, y + in + f, x + w - in - f, y + h - in - f, f,
                argb(0x040912, 0.38f * alpha)));
        // the rim and the corner brackets just outside it (HoloRender.top's settled values)
        int rim = argb(rgb, 0.30f * alpha);
        float t = 2.2f;
        g.submitGuiElementRenderState(hLine(pose, x, y - t, x + w, y + t, rim));
        g.submitGuiElementRenderState(hLine(pose, x, y + h - t, x + w, y + h + t, rim));
        g.submitGuiElementRenderState(vLine(pose, x - t, y, x + t, y + h, rim));
        g.submitGuiElementRenderState(vLine(pose, x + w - t, y, x + w + t, y + h, rim));
        int b = argb(rgb, 0.42f * alpha);
        float len = Math.min(18f, Math.min(w, h) / 4f), o = 3.5f, bt = 2.4f;
        float l = x - o, r = x + w + o, tp = y - o, bm = y + h + o;
        g.submitGuiElementRenderState(hLine(pose, l, tp - bt, l + len, tp + bt, b));
        g.submitGuiElementRenderState(vLine(pose, l - bt, tp, l + bt, tp + len, b));
        g.submitGuiElementRenderState(hLine(pose, r - len, tp - bt, r, tp + bt, b));
        g.submitGuiElementRenderState(vLine(pose, r - bt, tp, r + bt, tp + len, b));
        g.submitGuiElementRenderState(hLine(pose, l, bm - bt, l + len, bm + bt, b));
        g.submitGuiElementRenderState(vLine(pose, l - bt, bm - len, l + bt, bm, b));
        g.submitGuiElementRenderState(hLine(pose, r - len, bm - bt, r, bm + bt, b));
        g.submitGuiElementRenderState(vLine(pose, r - bt, bm - len, r + bt, bm, b));
    }

    /** A soft round light, {@code rx}×{@code ry} about (cx, cy): a projected floor under a figure. */
    void blob(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int accent, float strength) {
        if (strength <= 0.01f) return;
        int col = argb(accent & 0xFFFFFF, strength);
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, new Matrix3x2f(g.pose()), cx - rx, cy - ry, cx + rx, cy + ry,
                -1, -1, 1, 1, col, col, -1, 0));
    }

    @Override
    public void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float strength) {
        if (strength <= 0.01f || w <= 0 || h <= 0) return;
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        int col = argb(accent & 0xFFFFFF, 0.65f * strength);
        float m = 10f;
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, pose, x - m, y - m, x + w + m, y + h + m, -m, -m, w + m, h + m,
                col, col, w, h));
        int fill = argb(accent & 0xFFFFFF, 0.06f * strength);
        g.submitGuiElementRenderState(new Quad(HoloPipelines.GUI_GLOW, pose, x, y, x + w, y + h, 0, 0, 0, 0, fill, fill, 0, 0));
    }

    @Override
    public void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int argb) {
        int a = argb >>> 24;
        int core = (argb & 0xFF000000) | (HoloRender.holoColour(argb) & 0xFFFFFF);
        int glowA = Math.round(a * GLOW_ALPHA);
        if (glowA >= 26) { // under 0.1 the text shader drops it
            int glow = (glowA << 24) | GLOW_RGB;
            for (int i = 0; i < DX.length; i++) {
                g.pose().pushMatrix();
                g.pose().translate(DX[i], DY[i]);
                g.text(font, s, x, y, glow, false);
                g.pose().popMatrix();
            }
        }
        g.text(font, s, x, y, core, false);
    }

    @Override
    public float shake(float glitch) {
        if (HoloConfig.reducedMotion) return 0f;
        return glitch * 2.5f * ((System.nanoTime() / 50_000_000L) % 2 == 0 ? 1f : -1f);
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.round(255 * Math.min(1f, Math.max(0f, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * The feathered tint (HoloRender.softQuad in the GUI pass): flat inside (x0, y0)–(x1, y1), fading to clear over
     * {@code f} units outside it; a 3×3 grid of quads in the glass pipeline's flat mode.
     */
    record Soft(Matrix3x2fc pose, float x0, float y0, float x1, float y1, float f, int inner, @Nullable ScreenRectangle bounds)
            implements GuiElementRenderState {
        Soft(Matrix3x2fc pose, float x0, float y0, float x1, float y1, float f, int inner) {
            this(pose, x0, y0, x1, y1, f, inner, new ScreenRectangle((int) Math.floor(x0 - f), (int) Math.floor(y0 - f),
                    (int) Math.ceil(x1 - x0 + 2 * f) + 1, (int) Math.ceil(y1 - y0 + 2 * f) + 1).transformMaxBounds(pose));
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    vertex(vc, i, j);
                    vertex(vc, i, j + 1);
                    vertex(vc, i + 1, j + 1);
                    vertex(vc, i + 1, j);
                }
            }
        }

        /** Grid corner (i, j), 0..3 each way: the inner four are the flat tint, the outer ring clear. */
        private void vertex(VertexConsumer vc, int i, int j) {
            float x = i == 0 ? x0 - f : i == 1 ? x0 : i == 2 ? x1 : x1 + f;
            float y = j == 0 ? y0 - f : j == 1 ? y0 : j == 2 ? y1 : y1 + f;
            boolean in = (i == 1 || i == 2) && (j == 1 || j == 2);
            vc.addVertexWith2DPose(pose, x, y).setUv(0, 0).setColor(in ? inner : inner & 0xFFFFFF).setUv2(0, 0);
        }

        @Override
        public RenderPipeline pipeline() {
            return HoloPipelines.GUI_GLASS;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public @Nullable ScreenRectangle scissorArea() {
            return null;
        }
    }

    /** One quad in the PARTICLE format the holo shaders read: UV0 = position in card units, UV2 = the card's size or shape. */
    record Quad(RenderPipeline pipeline, Matrix3x2fc pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                int top, int bottom, int s0, int s1, boolean turned, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        Quad(RenderPipeline pipeline, Matrix3x2fc pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
             int top, int bottom, int s0, int s1) {
            this(pipeline, pose, x0, y0, x1, y1, u0, v0, u1, v1, top, bottom, s0, s1, false);
        }

        Quad(RenderPipeline pipeline, Matrix3x2fc pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
             int top, int bottom, int s0, int s1, boolean turned) {
            this(pipeline, pose, x0, y0, x1, y1, u0, v0, u1, v1, top, bottom, s0, s1, turned,
                    new ScreenRectangle((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1)
                            .transformMaxBounds(pose));
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            if (turned) { // UV0.x runs down the quad, UV0.y across it
                vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(top).setUv2(s0, s1);
                vc.addVertexWith2DPose(pose, x0, y1).setUv(u1, v0).setColor(bottom).setUv2(s0, s1);
                vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(bottom).setUv2(s0, s1);
                vc.addVertexWith2DPose(pose, x1, y0).setUv(u0, v1).setColor(top).setUv2(s0, s1);
                return;
            }
            vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(top).setUv2(s0, s1);
            vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(bottom).setUv2(s0, s1);
            vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(bottom).setUv2(s0, s1);
            vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(top).setUv2(s0, s1);
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public @Nullable ScreenRectangle scissorArea() {
            return null;
        }
    }
}
