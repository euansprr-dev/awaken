package dev.awaken.holo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.core.AwakenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;

/**
 * Replays each visible {@link HoloPanel}'s canvas into the level's always-on-top phase (drawn after the world, its
 * translucency and particles, before Lens; walls never cut a panel). One order per layer keeps them in sequence:
 * halo, glass and fills, sprites, items, text (vanilla's see-through text), then the additive top glow (edges, the
 * scan line, typing carets). Submit records are built once per panel and reused; only text lines whose inputs
 * changed get a new record.
 */
final class HoloRender {
    private HoloRender() {}

    static final int ORDER = 9100;
    private static final int FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT;
    private static final OnTopCollector ON_TOP = new OnTopCollector();
    /**
     * Item normals as the GUI lights them. The level pass keeps its own lighting (two world-fixed lights), so instead of
     * switching the lights we turn the normals: vanilla lights GUI items with the level lights rotated by a fixed pose
     * (Lighting: ITEMS_FLAT, ITEMS_3D), and dot(R·L, n) = dot(L, Rᵀ·n), so normals through Rᵀ (and the GUI's y flip)
     * light exactly like an inventory slot. (In the Nether the second light differs, so 3D items shade a little off.)
     */
    private static final org.joml.Matrix3f FLAT_NORMALS = new org.joml.Matrix3f(new Matrix4f()
            .rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0))).transpose().scale(1, -1, 1);
    private static final org.joml.Matrix3f BLOCK_NORMALS = new org.joml.Matrix3f(new Matrix4f()
            .scaling(1.0F, -1.0F, 1.0F).rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
            .rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F)).transpose().scale(1, -1, 1);

    /** Per-panel render caches. */
    static final class Draw {
        final HoloPanel p;
        final PoseStack stack = new PoseStack();
        CustomFeatureRenderer.Submit halo, glass, top, sprites;
        final TextFeatureRenderer.Submit[] text = new TextFeatureRenderer.Submit[HoloCanvas.MAX_TEXT];
        final PrefixText[] seq = new PrefixText[HoloCanvas.MAX_TEXT];
        final float[] key = new float[HoloCanvas.MAX_TEXT * 3];
        final int[] keyColour = new int[HoloCanvas.MAX_TEXT];
        final boolean[] keyShadow = new boolean[HoloCanvas.MAX_TEXT];
        final Matrix4f[] scaled = new Matrix4f[HoloCanvas.MAX_TEXT];
        final ItemStackRenderState[] items = new ItemStackRenderState[HoloCanvas.MAX_ITEMS];
        // typing carets for this frame (x, y) in units
        final float[] carets = new float[64 * 2];
        int caretCount;
        RenderType spriteType;

        Draw(HoloPanel p) {
            this.p = p;
        }
    }

    static void submit(SubmitCustomGeometryEvent e) {
        if (!HoloPipelines.ok()) return;
        SubmitNodeCollector c = e.getSubmitNodeCollector();
        for (Draw d : HoloClient.draws()) {
            HoloPanel p = d.p;
            if (!p.drawn || !visible(p)) continue;
            submitPanel(c, d);
        }
    }

    static boolean visible(HoloPanel p) {
        return p.anchor == HoloPanel.Anchor.VIEW || p.timeline.active();
    }

    private static void submitPanel(SubmitNodeCollector c, Draw d) {
        HoloPanel p = d.p;
        int base = ORDER + p.layer * 8;
        d.stack.last().pose().set(p.pose);
        if (d.glass == null) {
            d.halo = new CustomFeatureRenderer.Submit(d.stack.last(), HoloPipelines.glow(), (pose, vc) -> halo(d, pose, vc));
            d.glass = new CustomFeatureRenderer.Submit(d.stack.last(), HoloPipelines.glass(), (pose, vc) -> glass(d, pose, vc));
            d.top = new CustomFeatureRenderer.Submit(d.stack.last(), HoloPipelines.glow(), (pose, vc) -> top(d, pose, vc));
        }
        c.order(base).submitSpecial(OnTopCollector.ON_TOP, d.halo);
        c.order(base + 1).submitSpecial(OnTopCollector.ON_TOP, d.glass);
        HoloCanvas cv = p.canvas;
        float content = p.glassAlpha * p.fade;
        if (content > 0.02f) {
            if (cv.spriteCount > 0) {
                if (d.sprites == null) {
                    TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
                    d.spriteType = RenderTypes.textSeeThrough(atlas.location());
                    d.sprites = new CustomFeatureRenderer.Submit(d.stack.last(), d.spriteType, (pose, vc) -> sprites(d, pose, vc));
                }
                c.order(base + 2).submitSpecial(OnTopCollector.ON_TOP, d.sprites);
            }
            items(c, d, base + 3);
            text(c, d, base + 4);
        } else {
            d.caretCount = 0;
        }
        c.order(base + 5).submitSpecial(OnTopCollector.ON_TOP, d.top);
    }

    // ---- glass ----------------------------------------------------------------------------------------------------

    private static void glass(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        Matrix4f m = pose.pose();
        float ga = p.glassAlpha * p.fade;
        if (ga <= 0.004f) return;
        int bg = (Math.round(255 * ga) << 24) | (p.accent & 0xFFFFFF);
        quad(vc, m, 0, 0, p.w, p.h, 0, 0, p.w, p.h, bg, bg, p.w, p.h);
        HoloCanvas cv = p.canvas;
        for (int i = 0; i < cv.fills; i++) {
            int o = i * 4;
            int top = mulAlpha(cv.fillTop[i], ga), bottom = mulAlpha(cv.fillBottom[i], ga);
            quad(vc, m, cv.fill[o], cv.fill[o + 1], cv.fill[o + 2], cv.fill[o + 3], 0, 0, 0, 0, top, bottom, 0, 0);
        }
    }

    // ---- glow -----------------------------------------------------------------------------------------------------

    private static void halo(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        float a = 0.30f * p.glassAlpha * p.fade + 0.35f * p.edgeAlpha * Math.max(0, p.band - 1);
        if (a <= 0.004f) return;
        float mgn = 16;
        int col = mulAlpha(p.accent | 0xFF000000, a);
        quad(vc, pose.pose(), -mgn, -mgn, p.w + mgn, p.h + mgn, -mgn, -mgn, p.w + mgn, p.h + mgn, col, col, p.w, p.h);
    }

    private static void top(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        Matrix4f m = pose.pose();
        float w = p.w, h = p.h;
        // edges: bright while the frame draws itself, settling to a faint rim
        float settle = 1f - p.glassAlpha;
        float ea = p.edgeAlpha * (0.16f + 0.55f * settle);
        if (ea > 0.004f) {
            int col = mulAlpha(p.accent | 0xFF000000, ea);
            float t = 2.2f;
            line(vc, m, 0, -t, w, t, col);
            line(vc, m, 0, h - t, w, h + t, col);
            lineV(vc, m, -t, 0, t, h, col);
            lineV(vc, m, w - t, 0, w + t, h, col);
        }
        // corner brackets just outside the frame: the System's mark, as on the flat panels
        float ba = p.edgeAlpha * (0.42f + 0.4f * settle);
        if (ba > 0.004f) {
            int col = mulAlpha(p.accent | 0xFF000000, ba);
            float o = 3.5f, len = Math.min(18f, Math.min(w, h) / 4f), t = 2.4f;
            line(vc, m, -o, -o - t, -o + len, -o + t, col);
            lineV(vc, m, -o - t, -o, -o + t, -o + len, col);
            line(vc, m, w + o - len, -o - t, w + o, -o + t, col);
            lineV(vc, m, w + o - t, -o, w + o + t, -o + len, col);
            line(vc, m, -o, h + o - t, -o + len, h + o + t, col);
            lineV(vc, m, -o - t, h + o - len, -o + t, h + o, col);
            line(vc, m, w + o - len, h + o - t, w + o, h + o + t, col);
            lineV(vc, m, w + o - t, h + o - len, w + o + t, h + o, col);
        }
        // corner glints while it unfolds
        float glint = p.edgeAlpha * settle;
        if (glint > 0.01f) {
            int col = mulAlpha(0xFFCFEAFF, 0.6f * glint);
            float r = 9;
            blob(vc, m, 0, 0, r, col);
            blob(vc, m, w, 0, r, col);
            blob(vc, m, 0, h, r, col);
            blob(vc, m, w, h, r, col);
        }
        // the scan line: a bright core and a fading wake above it
        if (p.scan >= 0) {
            float y = Math.min(1f, p.scan) * h;
            float fadeOut = p.scan > 1 ? Math.max(0, 1 - (p.scan - 1) / 0.15f) : 1f;
            int core = mulAlpha(0xFFBFE3FF, 0.95f * fadeOut);
            quad(vc, m, -6, y - 4, w + 6, y + 4, -1, -1, 1, 1, core, core, -2, 0);
            int wakeTop = p.accent & 0xFFFFFF, wakeBottom = mulAlpha(p.accent | 0xFF000000, 0.10f * fadeOut);
            float wy = Math.max(0, y - 46);
            quad(vc, m, 0, wy, w, y, 0, 0, 0, 0, wakeTop, wakeBottom, 0, 0);
        }
        // a press: a ring of light opening where the click landed
        if (p.flashMs >= 0 && p.flashMs < 260) {
            float k = p.flashMs / 260f;
            float r = 4 + 18 * k;
            blob(vc, m, p.flashX, p.flashY, r, mulAlpha(p.accent | 0xFF000000, 0.55f * (1 - k) * (1 - k)));
        }
        // typing carets
        int caret = mulAlpha(p.accent | 0xFF000000, 0.85f);
        for (int i = 0; i < d.caretCount; i++) {
            float x = d.carets[i * 2], y = d.carets[i * 2 + 1];
            quad(vc, m, x + 0.5f, y - 0.5f, x + 3.5f, y + 8.5f, 0, 0, 0, 0, caret, caret, 0, 0);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int col) {
        quad(vc, m, x0, y0, x1, y1, -0.8f, -1, 0.8f, 1, col, col, -2, 0);
    }

    /** A vertical line: the line shape runs across UV0.y, so swap the axes. */
    private static void lineV(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int col) {
        vc.addVertex(m, x0, y0, 0).setUv(-0.8f, -1).setColor(col).setUv2(-2, 0);
        vc.addVertex(m, x0, y1, 0).setUv(0.8f, -1).setColor(col).setUv2(-2, 0);
        vc.addVertex(m, x1, y1, 0).setUv(0.8f, 1).setColor(col).setUv2(-2, 0);
        vc.addVertex(m, x1, y0, 0).setUv(-0.8f, 1).setColor(col).setUv2(-2, 0);
    }

    private static void blob(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, int col) {
        quad(vc, m, cx - r, cy - r, cx + r, cy + r, -1, -1, 1, 1, col, col, -1, 0);
    }

    /** An axis-aligned quad in panel units; top/bottom colours make vertical gradients. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
            int top, int bottom, int s0, int s1) {
        vc.addVertex(m, x0, y0, 0).setUv(u0, v0).setColor(top).setUv2(s0, s1);
        vc.addVertex(m, x0, y1, 0).setUv(u0, v1).setColor(bottom).setUv2(s0, s1);
        vc.addVertex(m, x1, y1, 0).setUv(u1, v1).setColor(bottom).setUv2(s0, s1);
        vc.addVertex(m, x1, y0, 0).setUv(u1, v0).setColor(top).setUv2(s0, s1);
    }

    static int mulAlpha(int argb, float k) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ---- sprites ----------------------------------------------------------------------------------------------------

    private static void sprites(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        HoloCanvas cv = p.canvas;
        Matrix4f m = pose.pose();
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        float ga = p.glassAlpha * p.fade;
        for (int i = 0; i < cv.spriteCount; i++) {
            TextureAtlasSprite s = atlas.getSprite(cv.sprites[i]);
            int o = i * 4;
            float x0 = cv.spriteRect[o], y0 = cv.spriteRect[o + 1], x1 = x0 + cv.spriteRect[o + 2], y1 = y0 + cv.spriteRect[o + 3];
            int col = mulAlpha(cv.spriteColour[i], ga);
            vc.addVertex(m, x0, y0, 0).setUv(s.getU0(), s.getV0()).setColor(col);
            vc.addVertex(m, x0, y1, 0).setUv(s.getU0(), s.getV1()).setColor(col);
            vc.addVertex(m, x1, y1, 0).setUv(s.getU1(), s.getV1()).setColor(col);
            vc.addVertex(m, x1, y0, 0).setUv(s.getU1(), s.getV0()).setColor(col);
        }
    }

    // ---- items ------------------------------------------------------------------------------------------------------

    private static void items(SubmitNodeCollector c, Draw d, int order) {
        HoloCanvas cv = d.p.canvas;
        if (cv.itemCount == 0) return;
        Minecraft mc = Minecraft.getInstance();
        OnTopCollector out = ON_TOP.into(c, order);
        PoseStack ps = d.stack;
        for (int i = 0; i < cv.itemCount; i++) {
            ItemStackRenderState st = d.items[i];
            if (st == null) st = d.items[i] = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(st, cv.items[i], ItemDisplayContext.GUI, mc.level, mc.player, 0);
            if (st.isEmpty()) continue;
            ps.pushPose();
            // GUI item space (y flipped, 16 units), flattened in depth and lifted off the glass so the glass's depth
            // never hides its back faces
            ps.translate(cv.itemXY[i * 2] + 8, cv.itemXY[i * 2 + 1] + 8, 10);
            ps.scale(16, -16, 4);
            ps.last().normal().set(st.usesBlockLight() ? BLOCK_NORMALS : FLAT_NORMALS);
            st.submit(ps, out, FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }
    }

    // ---- text -------------------------------------------------------------------------------------------------------

    private static void text(SubmitNodeCollector c, Draw d, int order) {
        HoloPanel p = d.p;
        HoloCanvas cv = p.canvas;
        Font font = Minecraft.getInstance().font;
        float typed = p.typeMs;
        // Signal's typing speed ×4 in characters per millisecond, at least fast enough that every line is done by the
        // end of the timeline; the lines start one after another across the first 90 ms
        float rate = (float) Math.max(0.5, signalSpeed()) * 4f / 50f;
        float stagger = cv.texts > 1 ? Math.min(12f, 90f / (cv.texts - 1)) : 0f;
        float window = HoloTimeline.OPEN_MS - 300f;
        d.caretCount = 0;
        var out = c.order(order);
        float ga = p.glassAlpha * p.fade;
        for (int i = 0; i < cv.texts; i++) {
            PrefixText seq = d.seq[i];
            if (seq == null) seq = d.seq[i] = new PrefixText();
            int limit = Integer.MAX_VALUE;
            seq.set(cv.text[i], Integer.MAX_VALUE);
            if (typed != Float.MAX_VALUE) {
                float start = i * stagger;
                float since = typed - start;
                if (since <= 0) continue;
                float r = Math.max(rate, seq.length() / Math.max(20f, window - start));
                limit = (int) (since * r);
            }
            seq.limit = limit;
            float x = cv.textXY[i * 2], y = cv.textXY[i * 2 + 1];
            int colour = mulAlpha(cv.textColour[i], ga);
            if ((colour >>> 24) < 26) continue; // the text shader drops alpha under 0.1
            boolean shadow = cv.textShadow[i];
            float scale = cv.textScale[i];
            int k = i * 3;
            TextFeatureRenderer.Submit s = d.text[i];
            boolean same = s != null && d.key[k] == x && d.key[k + 1] == y && d.keyColour[i] == colour
                    && d.key[k + 2] == scale && d.keyShadow[i] == shadow;
            if (!same) {
                Matrix4f pose = p.pose;
                float tx = x, ty = y;
                if (scale != 1f) {
                    Matrix4f sm = d.scaled[i];
                    if (sm == null) sm = d.scaled[i] = new Matrix4f();
                    pose = sm;
                    tx = 0;
                    ty = 0;
                }
                s = new TextFeatureRenderer.Submit(pose, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT,
                        new TextFeatureRenderer.Content.Text(tx, ty, seq, shadow, colour, 0, 0));
                d.text[i] = s;
                d.key[k] = x;
                d.key[k + 1] = y;
                d.key[k + 2] = scale;
                d.keyColour[i] = colour;
                d.keyShadow[i] = shadow;
            }
            if (scale != 1f) d.scaled[i].set(p.pose).translate(x, y, 0).scale(scale, scale, 1);
            out.submitSpecial(OnTopCollector.ON_TOP, s);
            if (limit != Integer.MAX_VALUE && limit < seq.length() && d.caretCount < 64) {
                d.carets[d.caretCount * 2] = x + font.width(seq) * scale;
                d.carets[d.caretCount * 2 + 1] = y;
                d.caretCount++;
            }
        }
    }

    private static double signalSpeed() {
        try {
            return AwakenConfig.Client.SPEC.isLoaded() ? AwakenConfig.Client.SIGNAL_TYPE_SPEED.get() : 2.0;
        } catch (RuntimeException e) {
            return 2.0;
        }
    }
}
