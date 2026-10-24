package dev.awaken.holo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.core.AwakenConfig;
import dev.awaken.holo.ui.HoloTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
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
        final PoseStack stack = new PoseStack(), view = new PoseStack();
        CustomFeatureRenderer.Submit halo, glass, top, sprites, beam;
        final TextFeatureRenderer.Submit[] text = new TextFeatureRenderer.Submit[HoloCanvas.MAX_TEXT];
        // each line's glow: GLOW_TAPS faint offset copies under it
        final TextFeatureRenderer.Submit[] glow = new TextFeatureRenderer.Submit[HoloCanvas.MAX_TEXT * GLOW_TAPS];
        final PrefixText[] seq = new PrefixText[HoloCanvas.MAX_TEXT];
        final float[] key = new float[HoloCanvas.MAX_TEXT * 3];
        final int[] keyColour = new int[HoloCanvas.MAX_TEXT];
        final boolean[] keyShadow = new boolean[HoloCanvas.MAX_TEXT];
        final Matrix4f[] scaled = new Matrix4f[HoloCanvas.MAX_TEXT];
        final ItemStackRenderState[] items = new ItemStackRenderState[HoloCanvas.MAX_ITEMS];
        // typing carets for this frame (x, y) in units
        final float[] carets = new float[64 * 2];
        int caretCount;
        // headers (x, y, width) for their glow
        final float[] headers = new float[48 * 3];
        int headerCount;
        RenderType spriteType;
        // external textures: one submit per slot, rebuilt only when the slot's texture changes
        final Identifier[] texOf = new Identifier[HoloCanvas.MAX_TEXTURES];
        final CustomFeatureRenderer.Submit[] tex = new CustomFeatureRenderer.Submit[HoloCanvas.MAX_TEXTURES];

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
            if (HoloOthers.peekSelf && p.thirdPerson) continue; // the autopilot is looking at what others see
            // in third person the beams come from your character's wrist, as the render layer found it this frame
            if (p.thirdPerson && p.anchor == HoloPanel.Anchor.WRIST && HoloOthers.selfWrist(p.beamFrom)) {
                // (camera-relative world → view space)
                ViewMath.WORLD_TO_PANEL_VIEW.transformPosition(p.beamFrom);
            }
            submitPanel(c, d);
        }
    }

    static boolean visible(HoloPanel p) {
        return p.anchor == HoloPanel.Anchor.VIEW || p.timeline.active();
    }

    private static RenderPhaseKey<SubmitNode> key = OnTopCollector.MAIN;

    private static void submitPanel(SubmitNodeCollector c, Draw d) {
        HoloPanel p = d.p;
        key = OnTopCollector.phase();
        int base = ORDER + p.layer * 8;
        d.stack.last().pose().set(p.pose);
        if (d.glass == null) {
            d.halo = new CustomFeatureRenderer.Submit(d.stack.last(), HoloPipelines.glow(), (pose, vc) -> halo(d, pose, vc));
            d.glass = new CustomFeatureRenderer.Submit(d.stack.last(), HoloPipelines.glass(), (pose, vc) -> glass(d, pose, vc));
            d.top = new CustomFeatureRenderer.Submit(d.stack.last(), HoloPipelines.glow(), (pose, vc) -> top(d, pose, vc));
        }
        if (p.beam > 0.01f) {
            d.view.last().pose().set(ViewMath.VIEW_TO_WORLD).mul(ViewMath.UNBOB);
            if (d.beam == null) d.beam = new CustomFeatureRenderer.Submit(d.view.last(), HoloPipelines.glow(), (pose, vc) -> beam(d, pose, vc));
            c.order(base).submitSpecial(key, d.beam);
        }
        c.order(base).submitSpecial(key, d.halo);
        c.order(base + 1).submitSpecial(key, d.glass);
        HoloCanvas cv = p.canvas;
        float content = p.glassAlpha * p.fade;
        if (content > 0.02f) {
            if (cv.spriteCount > 0) {
                if (d.sprites == null) {
                    TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
                    d.spriteType = RenderTypes.textSeeThrough(atlas.location());
                    d.sprites = new CustomFeatureRenderer.Submit(d.stack.last(), d.spriteType, (pose, vc) -> sprites(d, pose, vc));
                }
                c.order(base + 2).submitSpecial(key, d.sprites);
            }
            for (int i = 0; i < cv.textureCount; i++) {
                Identifier id = cv.textures[i];
                if (d.tex[i] == null || !id.equals(d.texOf[i])) {
                    final int slot = i;
                    d.texOf[i] = id;
                    d.tex[i] = new CustomFeatureRenderer.Submit(d.stack.last(), RenderTypes.textSeeThrough(id), (pose, vc) -> texture(d, slot, pose, vc));
                }
                c.order(base + 2).submitSpecial(key, d.tex[i]);
            }
            items(c, d, base + 3);
            text(c, d, base + 4); // glow copies at base + 4, the text at base + 5
        } else {
            d.caretCount = 0;
        }
        c.order(base + 6).submitSpecial(key, d.top);
    }

    // ---- glass ----------------------------------------------------------------------------------------------------

    private static void glass(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        Matrix4f m = pose.pose();
        float ga = p.glassAlpha * p.fade;
        if (ga <= 0.004f) return;
        int bg = (Math.round(255 * ga) << 24) | (p.accent & 0xFFFFFF);
        quad(vc, m, 0, 0, p.w, p.h, 0, 0, p.w, p.h, bg, bg, p.w, p.h);
        // one even tint over the whole content area, feathered out toward the rim (no per-line boxes), so the light
        // text reads over any sky, grass or night while the edges stay light
        float in = 3f, f = 14f;
        softQuad(vc, m, in + f, in + f, p.w - in - f, p.h - in - f, f, mulAlpha(0xFF040912, 0.38f * ga), 0x00040912);
        HoloCanvas cv = p.canvas;
        for (int i = 0; i < cv.fills; i++) {
            int o = i * 4;
            int top = mulAlpha(cv.fillTop[i], ga), bottom = mulAlpha(cv.fillBottom[i], ga);
            quad(vc, m, cv.fill[o], cv.fill[o + 1], cv.fill[o + 2], cv.fill[o + 3], 0, 0, 0, 0, top, bottom, 0, 0);
        }
    }

    /** A flat rectangle whose outer ring of {@code f} units fades to clear (a 3×3 grid; glass pipeline, flat mode). */
    private static void softQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float f, int inner, int clear) {
        float[] xs = GX, ys = GY;
        xs[0] = x0 - f;
        xs[1] = x0;
        xs[2] = x1;
        xs[3] = x1 + f;
        ys[0] = y0 - f;
        ys[1] = y0;
        ys[2] = y1;
        ys[3] = y1 + f;
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                vc.addVertex(m, xs[i], ys[j], 0).setUv(0, 0).setColor(grid(i, j, inner, clear)).setUv2(0, 0);
                vc.addVertex(m, xs[i], ys[j + 1], 0).setUv(0, 0).setColor(grid(i, j + 1, inner, clear)).setUv2(0, 0);
                vc.addVertex(m, xs[i + 1], ys[j + 1], 0).setUv(0, 0).setColor(grid(i + 1, j + 1, inner, clear)).setUv2(0, 0);
                vc.addVertex(m, xs[i + 1], ys[j], 0).setUv(0, 0).setColor(grid(i + 1, j, inner, clear)).setUv2(0, 0);
            }
        }
    }

    private static final float[] GX = new float[4], GY = new float[4];

    private static int grid(int i, int j, int inner, int clear) {
        return (i == 1 || i == 2) && (j == 1 || j == 2) ? inner : clear;
    }

    // ---- glow -----------------------------------------------------------------------------------------------------

    private static void halo(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        float a = 0.42f * p.glassAlpha * p.fade + 0.35f * p.edgeAlpha * Math.max(0, p.band - 1);
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
        float ea = p.edgeAlpha * (0.30f + 0.45f * settle);
        // line thickness in units, held constant on screen while the panel is squashed by its unfold
        float kx = 1f / Math.max(0.05f, p.sx), ky = 1f / Math.max(0.05f, p.sy);
        if (ea > 0.004f) {
            int col = mulAlpha(p.accent | 0xFF000000, ea);
            float tx = 2.2f * kx, ty = 2.2f * ky;
            line(vc, m, 0, -ty, w, ty, col);
            line(vc, m, 0, h - ty, w, h + ty, col);
            lineV(vc, m, -tx, 0, tx, h, col);
            lineV(vc, m, w - tx, 0, w + tx, h, col);
        }
        // corner brackets just outside the frame: the System's mark, as on the flat panels
        float ba = p.edgeAlpha * (0.42f + 0.4f * settle);
        if (ba > 0.004f) {
            int col = mulAlpha(p.accent | 0xFF000000, ba);
            float len = Math.min(18f, Math.min(w, h) / 4f);
            float ox = 3.5f * kx, oy = 3.5f * ky, lx = len * Math.min(kx, 3f), ly = len * Math.min(ky, 3f), tx = 2.4f * kx, ty = 2.4f * ky;
            line(vc, m, -ox, -oy - ty, -ox + lx, -oy + ty, col);
            lineV(vc, m, -ox - tx, -oy, -ox + tx, -oy + ly, col);
            line(vc, m, w + ox - lx, -oy - ty, w + ox, -oy + ty, col);
            lineV(vc, m, w + ox - tx, -oy, w + ox + tx, -oy + ly, col);
            line(vc, m, -ox, h + oy - ty, -ox + lx, h + oy + ty, col);
            lineV(vc, m, -ox - tx, h + oy - ly, -ox + tx, h + oy, col);
            line(vc, m, w + ox - lx, h + oy - ty, w + ox, h + oy + ty, col);
            lineV(vc, m, w + ox - tx, h + oy - ly, w + ox + tx, h + oy, col);
        }
        // corner glints while it unfolds
        float glint = p.edgeAlpha * settle;
        if (glint > 0.01f) {
            int col = mulAlpha(0xFFCFEAFF, 0.6f * glint);
            float rx = 9 * kx, ry = 9 * ky;
            blob(vc, m, 0, 0, rx, ry, col);
            blob(vc, m, w, 0, rx, ry, col);
            blob(vc, m, 0, h, rx, ry, col);
            blob(vc, m, w, h, rx, ry, col);
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
        // a soft glow behind the System-blue headers
        int hg = mulAlpha(p.accent | 0xFF000000, 0.30f * p.glassAlpha * p.fade);
        for (int i = 0; i < d.headerCount; i++) {
            float x = d.headers[i * 3], y = d.headers[i * 3 + 1], w0 = d.headers[i * 3 + 2];
            quad(vc, m, x - 6, y - 4, x + w0 + 6, y + 12, -1, -1, 1, 1, hg, hg, -1, 0);
        }
        // typing carets
        int caret = mulAlpha(p.accent | 0xFF000000, 0.85f);
        for (int i = 0; i < d.caretCount; i++) {
            float x = d.carets[i * 2], y = d.carets[i * 2 + 1];
            quad(vc, m, x + 0.5f, y - 0.5f, x + 3.5f, y + 8.5f, 0, 0, 0, 0, caret, caret, 0, 0);
        }
    }

    private static final org.joml.Vector3f BA = new org.joml.Vector3f(), BS = new org.joml.Vector3f(), BM = new org.joml.Vector3f();

    /** A ribbon of light from the wrist to the rising panel, turned to face the eye, widening as it goes. */
    private static void beam(Draw d, PoseStack.Pose pose, VertexConsumer vc) {
        HoloPanel p = d.p;
        Matrix4f m = pose.pose();
        var a = p.beamFrom;
        float k = p.beam, boost = p.beamBoost;
        // the projection cone: a faint sheet of light from the wrist to the panel's bottom edge, fading as it goes
        int coneNear = mulAlpha(p.accent | 0xFF000000, 0.10f * k + 0.10f * boost), coneFar = p.accent & 0xFFFFFF;
        vc.addVertex(m, a.x, a.y, a.z).setUv(0, 0).setColor(coneNear).setUv2(0, 0);
        vc.addVertex(m, p.cornerL.x, p.cornerL.y, p.cornerL.z).setUv(0, 0).setColor(coneFar).setUv2(0, 0);
        vc.addVertex(m, p.cornerR.x, p.cornerR.y, p.cornerR.z).setUv(0, 0).setColor(coneFar).setUv2(0, 0);
        vc.addVertex(m, a.x, a.y, a.z).setUv(0, 0).setColor(coneNear).setUv2(0, 0);
        // two beams to the lower corners, wider and brighter while the panel opens
        // widths in metres at the panel's distance; a far origin (your character's wrist in third person) widens to match
        float far = Math.max(1f, a.length() / 0.6f);
        float w0 = 0.0028f * (1 + boost) * far, w1 = 0.009f * (1 + 2.2f * boost);
        int core = mulAlpha(0xFFCFEAFF, Math.min(1f, 0.55f * k + 0.45f * boost)), edge = mulAlpha(p.accent | 0xFF000000, Math.min(1f, 0.35f * k + 0.4f * boost));
        ribbon(vc, m, a, p.cornerL, w0, w1, core, edge);
        ribbon(vc, m, a, p.cornerR, w0, w1, core, edge);
        // a few scan dots travelling up each beam
        float sec = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
        for (int b = 0; b < 2; b++) {
            var c = b == 0 ? p.cornerL : p.cornerR;
            for (int j = 0; j < 3; j++) {
                float f = (sec / 2.4f + j / 3f + b * 0.17f) % 1f;
                float alpha = (float) Math.sin(f * Math.PI) * (0.55f * k + 0.3f * boost);
                if (alpha < 0.02f) continue;
                BM.set(a).lerp(c, f);
                float r = (0.0035f + 0.003f * f) * (far * (1 - f) + f);
                int col = mulAlpha(0xFFE6F4FF, Math.min(1f, alpha));
                vc.addVertex(m, BM.x - r, BM.y - r, BM.z).setUv(-1, -1).setColor(col).setUv2(-1, 0);
                vc.addVertex(m, BM.x - r, BM.y + r, BM.z).setUv(-1, 1).setColor(col).setUv2(-1, 0);
                vc.addVertex(m, BM.x + r, BM.y + r, BM.z).setUv(1, 1).setColor(col).setUv2(-1, 0);
                vc.addVertex(m, BM.x + r, BM.y - r, BM.z).setUv(1, -1).setColor(col).setUv2(-1, 0);
            }
        }
    }

    /** A ribbon of light from {@code a} to {@code b}, turned to face the eye (view space), widening as it goes. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, org.joml.Vector3f a, org.joml.Vector3f b, float w0, float w1, int c0, int c1) {
        BA.set(b).sub(a);
        BM.set(a).add(b).mul(0.5f);
        BS.set(BA).cross(BM);
        if (BS.lengthSquared() < 1e-12f) return;
        BS.normalize();
        vc.addVertex(m, a.x - BS.x * w0, a.y - BS.y * w0, a.z - BS.z * w0).setUv(-0.8f, -1).setColor(c0).setUv2(-2, 0);
        vc.addVertex(m, b.x - BS.x * w1, b.y - BS.y * w1, b.z - BS.z * w1).setUv(0.8f, -1).setColor(c1).setUv2(-2, 0);
        vc.addVertex(m, b.x + BS.x * w1, b.y + BS.y * w1, b.z + BS.z * w1).setUv(0.8f, 1).setColor(c1).setUv2(-2, 0);
        vc.addVertex(m, a.x + BS.x * w0, a.y + BS.y * w0, a.z + BS.z * w0).setUv(-0.8f, 1).setColor(c0).setUv2(-2, 0);
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
        blob(vc, m, cx, cy, r, r, col);
    }

    private static void blob(VertexConsumer vc, Matrix4f m, float cx, float cy, float rx, float ry, int col) {
        quad(vc, m, cx - rx, cy - ry, cx + rx, cy + ry, -1, -1, 1, 1, col, col, -1, 0);
    }

    /** An axis-aligned quad in panel units; top/bottom colours make vertical gradients. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
            int top, int bottom, int s0, int s1) {
        vc.addVertex(m, x0, y0, 0).setUv(u0, v0).setColor(top).setUv2(s0, s1);
        vc.addVertex(m, x0, y1, 0).setUv(u0, v1).setColor(bottom).setUv2(s0, s1);
        vc.addVertex(m, x1, y1, 0).setUv(u1, v1).setColor(bottom).setUv2(s0, s1);
        vc.addVertex(m, x1, y0, 0).setUv(u1, v0).setColor(top).setUv2(s0, s1);
    }

    /**
     * The flat palette, lit for a hologram: body text white, labels a pale blue, the System-blue headers brighter (they
     * also get a glow). Other colours (amber, warnings, rank colours) keep their hue.
     */
    static int holoColour(int argb) {
        int rgb = argb & 0xFFFFFF, a = argb & 0xFF000000;
        if (rgb == dev.awaken.signal.client.Glass.TEXT || rgb == dev.awaken.signal.client.Glass.TITLE) return a | 0xEAF9FF; // cyan-white
        if (rgb == dev.awaken.signal.client.Glass.LABEL) return a | 0xC6D8EE;
        if (rgb == dev.awaken.signal.client.Glass.EDGE) return a | 0xA6DCFF;
        return argb;
    }

    static boolean isHeader(int argb) {
        return (argb & 0xFFFFFF) == dev.awaken.signal.client.Glass.EDGE;
    }

    static int mulAlpha(int argb, float k) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ---- sprites ----------------------------------------------------------------------------------------------------

    /** One external texture's quad (UV0 its own 0..1 UVs; see-through text type, so it needs no lightmap). */
    private static void texture(Draw d, int i, PoseStack.Pose pose, VertexConsumer vc) {
        HoloCanvas cv = d.p.canvas;
        if (i >= cv.textureCount) return;
        Matrix4f m = pose.pose();
        int o = i * 4;
        float x0 = cv.textureRect[o], y0 = cv.textureRect[o + 1], x1 = x0 + cv.textureRect[o + 2], y1 = y0 + cv.textureRect[o + 3];
        float u0 = cv.textureUv[o], v0 = cv.textureUv[o + 1], u1 = cv.textureUv[o + 2], v1 = cv.textureUv[o + 3];
        int col = mulAlpha(cv.textureColour[i], d.p.glassAlpha * d.p.fade);
        vc.addVertex(m, x0, y0, 0).setUv(u0, v0).setColor(col);
        vc.addVertex(m, x0, y1, 0).setUv(u0, v1).setColor(col);
        vc.addVertex(m, x1, y1, 0).setUv(u1, v1).setColor(col);
        vc.addVertex(m, x1, y0, 0).setUv(u1, v0).setColor(col);
    }

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
            float sz = cv.itemSize[i];
            ps.translate(cv.itemXY[i * 2] + sz / 2, cv.itemXY[i * 2 + 1] + sz / 2, 10 * sz / 16);
            ps.scale(sz, -sz, sz / 4);
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
        d.headerCount = 0;
        var glowOut = c.order(order);
        var out = c.order(order + 1);
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
            int colour = mulAlpha(holoColour(cv.textColour[i]), ga);
            if ((colour >>> 24) < 26) continue; // the text shader drops alpha under 0.1
            boolean header = isHeader(cv.textColour[i]);
            boolean shadow = false; // emitted light, not print: a soft glow under it instead of vanilla's hard shadow
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
                // the glow: faint cyan copies a little off each way, so every stroke gets a soft halo
                int glowColour = mulAlpha(header ? HEADER_GLOW : TEXT_GLOW, (colour >>> 24) / 255f);
                for (int g = 0; g < GLOW_TAPS; g++) {
                    d.glow[i * GLOW_TAPS + g] = new TextFeatureRenderer.Submit(pose, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                            FULL_BRIGHT, new TextFeatureRenderer.Content.Text(tx + GLOW_DX[g] / scale, ty + GLOW_DY[g] / scale, seq,
                            false, glowColour, 0, 0));
                }
                d.key[k] = x;
                d.key[k + 1] = y;
                d.key[k + 2] = scale;
                d.keyColour[i] = colour;
                d.keyShadow[i] = shadow;
            }
            if (scale != 1f) d.scaled[i].set(p.pose).translate(x, y, 0).scale(scale, scale, 1);
            if (glowOn && ((colour >>> 24) * GLOW_ALPHA) >= 26) { // under 0.1 the text shader drops it anyway
                for (int g = 0; g < GLOW_TAPS; g++) glowOut.submitSpecial(key, d.glow[i * GLOW_TAPS + g]);
            }
            out.submitSpecial(key, s);
            if (header && d.headerCount < 48) {
                d.headers[d.headerCount * 3] = x;
                d.headers[d.headerCount * 3 + 1] = y;
                d.headers[d.headerCount * 3 + 2] = font.width(seq) * scale;
                d.headerCount++;
            }
            if (limit != Integer.MAX_VALUE && limit < seq.length() && d.caretCount < 64) {
                d.carets[d.caretCount * 2] = x + font.width(seq) * scale;
                d.carets[d.caretCount * 2 + 1] = y;
                d.caretCount++;
            }
        }
    }

    // the text glow: four taps half a unit off diagonally (≈2–3 framebuffer pixels), faint cyan; headers a little bluer
    static final int GLOW_TAPS = 4;
    /** Autopilot A/B for frame times ({@code holo glow on|off}). */
    static boolean glowOn = true;
    private static final float[] GLOW_DX = {-0.5f, 0.5f, -0.5f, 0.5f}, GLOW_DY = {-0.5f, -0.5f, 0.5f, 0.5f};
    private static final float GLOW_ALPHA = 0.20f;
    private static final int TEXT_GLOW = (Math.round(255 * GLOW_ALPHA) << 24) | 0x7FDCFF;
    private static final int HEADER_GLOW = (Math.round(255 * GLOW_ALPHA) << 24) | 0x5CC4FF;

    private static double signalSpeed() {
        try {
            return AwakenConfig.Client.SPEC.isLoaded() ? AwakenConfig.Client.SIGNAL_TYPE_SPEED.get() : 2.0;
        } catch (RuntimeException e) {
            return 2.0;
        }
    }
}
