package dev.awaken.powers.client.fx;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.powers.fx.FxBudget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * Screen feel for the local player: a coloured flash, anime-style impact frames (a short client post effect),
 * screen-space speed lines, an FOV punch and a directional camera kick. All of it follows the camera-shake setting,
 * the "screen flashes" setting and the power-effects level (off at minimal, halved at reduced).
 */
final class FxScreen {
    private FxScreen() {}

    private static final Identifier LAYER = Awaken.id("power_fx_screen");
    /** Four impact-frame strengths, strongest first; swapped tick by tick (post uniforms are fixed per effect). */
    private static final List<Identifier> IMPACT = List.of(Awaken.id("power_fx_impact_3"), Awaken.id("power_fx_impact_2"),
            Awaken.id("power_fx_impact_1"), Awaken.id("power_fx_impact_0"));

    private static int flashColour;
    private static float flash, flashDecay;
    private static int impactTicks;
    private static Identifier activeImpact;
    private static float fov, fovDecay;
    private static float kickPitch, kickYaw, kickRoll;
    private static int speedTicks, speedTotal;

    static void register(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, FxScreen::render);
    }

    private static float scale() {
        return PowerFxConfig.flashes() ? FxBudget.screen(PowerFxConfig.level()) : 0f;
    }

    /** A full-screen tint that fades out; strength 0..1. */
    static void flash(int argb, float strength, int ticks) {
        float s = strength * scale();
        if (s <= 0) return;
        if (s >= flash) flashColour = argb;
        flash = Math.max(flash, Math.min(0.75f, s));
        flashDecay = flash / Math.max(1, ticks);
    }

    /** An impact frame: a few ticks of high-contrast, desaturated, radially smeared screen. */
    static void impactFrame(int ticks) {
        if (scale() < 0.75f) return;
        impactTicks = Math.max(impactTicks, Math.min(4, ticks));
    }

    /** FOV punch: positive widens (speed), negative zooms in (a big release). */
    static void fovPunch(float amount, int ticks) {
        if (!AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        float s = amount * Math.max(0.4f, FxBudget.screen(PowerFxConfig.level()));
        if (Math.abs(s) >= Math.abs(fov)) fov = s;
        fovDecay = Math.abs(fov) / Math.max(1, ticks);
    }

    /** A directional kick of the view (degrees), springing back. */
    static void kick(float pitch, float yaw, float roll) {
        if (!AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        float s = FxBudget.screen(PowerFxConfig.level());
        if (s <= 0) s = 0.3f;
        kickPitch += pitch * s;
        kickYaw += yaw * s;
        kickRoll += roll * s;
    }

    static void speedLines(int ticks) {
        if (scale() <= 0) return;
        speedTicks = Math.max(speedTicks, ticks);
        speedTotal = Math.max(speedTicks, 1);
    }

    static void tick() {
        flash = Math.max(0, flash - flashDecay);
        if (fov > 0) fov = Math.max(0, fov - fovDecay);
        else if (fov < 0) fov = Math.min(0, fov + fovDecay);
        kickPitch *= 0.62f;
        kickYaw *= 0.62f;
        kickRoll *= 0.62f;
        if (speedTicks > 0) speedTicks--;
        updateImpact();
    }

    private static void updateImpact() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        List<Identifier> effects = p.getActivePostEffects();
        Identifier want = impactTicks > 0 ? IMPACT.get(Math.max(0, 4 - impactTicks)) : null;
        if (activeImpact != null && activeImpact != want) effects.remove(activeImpact);
        if (want != null && !effects.contains(want)) effects.add(want);
        activeImpact = want;
        if (impactTicks > 0) impactTicks--;
    }

    static void reset() {
        flash = 0;
        impactTicks = 0;
        fov = 0;
        kickPitch = kickYaw = kickRoll = 0;
        speedTicks = 0;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && activeImpact != null) p.getActivePostEffects().remove(activeImpact);
        activeImpact = null;
    }

    static void onFov(ViewportEvent.ComputeFov e) {
        if (fov != 0) e.setFOV(e.getFOV() * (1 + fov));
    }

    static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (Math.abs(kickPitch) + Math.abs(kickYaw) + Math.abs(kickRoll) < 0.01f) return;
        float k = 1 - (float) e.getPartialTick() * 0.38f;
        e.setPitch(e.getPitch() + kickPitch * k);
        e.setYaw(e.getYaw() + kickYaw * k);
        e.setRoll(e.getRoll() + kickRoll * k);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (flash > 0.005f) {
            int a = Mth.clamp((int) (flash * 255), 0, 255);
            g.fill(0, 0, w, h, (a << 24) | (flashColour & 0xFFFFFF));
        }
        if (speedTicks > 0) speedLines(g, w, h, speedTicks / (float) speedTotal);
    }

    /** Thin radial streaks at the screen edges, a manga speed burst. */
    private static void speedLines(GuiGraphicsExtractor g, int w, int h, float strength) {
        int count = 18;
        float cxs = w / 2f, cys = h / 2f;
        float reach = Math.max(w, h) * 0.75f;
        long seed = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        for (int i = 0; i < count; i++) {
            int hash = (int) (i * 2654435761L + seed * 40503L);
            float ang = (i + ((hash >>> 8) & 255) / 512f) / count * Mth.TWO_PI;
            float inner = reach * (0.55f + ((hash >>> 16) & 255) / 1200f);
            int len = (int) (reach * (0.25f + ((hash >>> 4) & 15) / 60f));
            int a = (int) (90 * strength) + 20;
            g.pose().pushMatrix();
            g.pose().translate(cxs + Mth.cos(ang) * inner, cys + Mth.sin(ang) * inner);
            g.pose().rotate(ang);
            g.fill(0, 0, len, 1, (a << 24) | 0xFFFFFF);
            g.pose().popMatrix();
        }
    }
}
