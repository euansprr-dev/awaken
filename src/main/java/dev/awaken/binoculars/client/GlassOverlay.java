package dev.awaken.binoculars.client;

import dev.awaken.Awaken;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.List;

/**
 * What you see through them (SPEC §39.4): our lens edge (twin, single or none), the readout on the glass, and the focus
 * pull's softness as stepped blur chains in the player's active post effects (like the screen moments). The edge stays
 * under F1; the readout doesn't.
 */
final class GlassOverlay {
    private GlassOverlay() {}

    static final Identifier LAYER = Awaken.id("binoculars");
    private static final Identifier TWIN = Awaken.id("textures/gui/binoculars/edge_twin.png");
    private static final Identifier SINGLE = Awaken.id("textures/gui/binoculars/edge_single.png");
    /** The textures' sizes (tools/art/specs/binoculars.py). */
    private static final int TWIN_W = 1024, SINGLE_W = 640, TEX_H = 640;
    /** The focus pull, strongest first (assets/awaken/post_effect/binoculars_focus_N.json). */
    private static final Identifier[] FOCUS = {Awaken.id("binoculars_focus_1"), Awaken.id("binoculars_focus_2"), Awaken.id("binoculars_focus_3")};
    private static Identifier activeFocus;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        float a = Glass.edge();
        Minecraft mc = Minecraft.getInstance();
        if (a <= 0.003F || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        GlassConfig.Edge edge = GlassConfig.edge();
        if (edge != GlassConfig.Edge.OFF) {
            boolean twin = edge == GlassConfig.Edge.TWIN;
            // the fields a touch taller than the screen, as through real eyecups; black past the sides
            int th = Math.round(h * 1.04F), tw = Math.round(th * (twin ? TWIN_W : SINGLE_W) / (float) TEX_H);
            int x = (w - tw) / 2, y = (h - th) / 2;
            int col = ARGB.color(Math.round(a * 255), 0xFFFFFF);
            g.blit(RenderPipelines.GUI_TEXTURED, twin ? TWIN : SINGLE, x, y, 0F, 0F, tw, th, twin ? TWIN_W : SINGLE_W, TEX_H,
                    twin ? TWIN_W : SINGLE_W, TEX_H, col);
            int black = ARGB.color(Math.round(a * 255), 0x000000);
            if (x > 0) {
                g.fill(0, 0, x, h, black);
                g.fill(x + tw, 0, w, h, black);
            }
        }
        if (!GlassConfig.readout() || mc.gui.hud.isHidden()) return;
        LocalPlayer p = mc.player;
        String s = Rangefinder.words(Glass.stepMag(), p.getYRot());
        Font font = mc.font;
        int tx = (w - font.width(s)) / 2, ty = Math.round(h * 0.86F);
        int ta = Math.round(a * 0.85F * 255);
        g.fill(tx - 5, ty - 3, tx + font.width(s) + 5, ty + 11, ARGB.color(Math.round(a * 0.28F * 255), 0x000000));
        g.text(font, s, tx, ty, ARGB.color(ta, 0xF1E6CF), false);
    }

    /** Once a frame: the blur chain for how soft the focus is now (none once clear). */
    static void focus() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            activeFocus = null;
            return;
        }
        float f = Glass.focus();
        Identifier want = f > 0.66F ? FOCUS[0] : f > 0.33F ? FOCUS[1] : f > 0.04F ? FOCUS[2] : null;
        if (want == activeFocus) return;
        List<Identifier> effects = p.getActivePostEffects();
        if (activeFocus != null) effects.remove(activeFocus);
        if (want != null && !effects.contains(want)) effects.add(want);
        activeFocus = want;
    }

    static void reset() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && activeFocus != null) p.getActivePostEffects().remove(activeFocus);
        activeFocus = null;
    }
}
