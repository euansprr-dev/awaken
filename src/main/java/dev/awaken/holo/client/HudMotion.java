package dev.awaken.holo.client;

import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.Set;

/**
 * The calm HUD (docs/proposals/holo-ui.md §8, tier 1): Awaken's own GUI layers drift a few pixels against a sharp
 * turn and settle in about 200 ms. Driven only by how fast the camera turns (a {@link Spring}'s trailing offset),
 * never constant. Off / Subtle (default, ~2 GUI px) / Full (4 px), scaled by vanilla's Screen Effect Scale and off
 * with View Bobbing off. Vanilla's layers (hotbar, hearts, crosshair) are never moved, and neither are Awaken's
 * full-screen overlays (screen flashes, the reveal vignette, the Veil's flash and its meditation ring around the
 * crosshair). The translate is snapped to framebuffer pixels so text never shimmers. Tier-1 layers that {@link CalmHud}
 * fades are drawn from here, after the translate, through its fading graphics.
 */
final class HudMotion {
    private HudMotion() {}

    /** Full-screen or crosshair-locked layers that must stay put. */
    private static final Set<Identifier> STILL = Set.of(Awaken.id("power_fx_screen"), Awaken.id("fauna_reveal_pulse"), Awaken.id("veil"));
    /** GUI pixels of drift per degree of trailing offset before the soft limit. */
    private static final float PX_PER_DEG = 0.34f;

    private static final Spring SPRING = new Spring(0.20f, 12f);
    private static float dx, dy;
    private static int pushed;

    /** Once a frame, from the camera's angles. */
    static void update(float camYaw, float camPitch, float dt) {
        Minecraft mc = Minecraft.getInstance();
        SPRING.follow(camYaw, camPitch, dt, false);
        float max = switch (HoloConfig.hudMotion) {
            case OFF -> 0f;
            case SUBTLE -> 2f;
            case FULL -> 4f;
        };
        if (!mc.options.bobView().get()) max = 0;
        max *= mc.options.screenEffectScale().get().floatValue();
        if (max <= 0.01f) {
            dx = dy = 0;
            return;
        }
        // soft limit: proportional for small turns, never past max
        float sx = SPRING.yaw() * PX_PER_DEG, sy = SPRING.pitch() * PX_PER_DEG;
        dx = max * (float) Math.tanh(sx / max);
        dy = max * (float) Math.tanh(sy / max);
        // snap to framebuffer pixels
        float scale = (float) mc.getWindow().getGuiScale();
        if (scale > 0) {
            dx = Math.round(dx * scale) / scale;
            dy = Math.round(dy * scale) / scale;
        }
    }

    /** Current offset in GUI pixels (for the tour's log). */
    static float dx() {
        return dx;
    }

    static float dy() {
        return dy;
    }

    static void pre(RenderGuiLayerEvent.Pre e) {
        Identifier id = e.getName();
        if (!Awaken.MODID.equals(id.getNamespace()) || STILL.contains(id)) return;
        if (dx != 0 || dy != 0) {
            e.getGuiGraphics().pose().pushMatrix();
            e.getGuiGraphics().pose().translate(dx, dy);
            pushed++;
        }
        // tier 1 fades when idle: drawn here through the fading graphics (last listener, so nothing after us is skipped)
        if (CalmHud.TIER_1.contains(id)) {
            float a = CalmHud.alpha();
            if (a < 0.995f) CalmHud.drawFaded(e, a);
        }
    }

    static void post(RenderGuiLayerEvent.Post e) {
        if (pushed == 0) return;
        Identifier id = e.getName();
        if (!Awaken.MODID.equals(id.getNamespace()) || STILL.contains(id)) return;
        e.getGuiGraphics().pose().popMatrix();
        pushed--;
    }

    /** A layer cancelled after we pushed never posts: pop what is left so the next frame starts clean. */
    static void endFrame(RenderGuiEvent.Post e) {
        while (pushed > 0) {
            e.getGuiGraphics().pose().popMatrix();
            pushed--;
        }
    }
}
