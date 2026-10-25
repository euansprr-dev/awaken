package dev.awaken.powers.client;

import dev.awaken.core.AwakenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * A power's combat feel: camera shake (respects the cameraShake client setting) and the marks' outlines. The hit-stop
 * freeze is every blow's one freeze, core.api.CombatFeedback (installed by Arms).
 */
final class CombatFeel {
    private CombatFeel() {}

    private static int ticks;
    private static int total;
    private static float strength;

    static void kick(float degrees, int duration) {
        if (!AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        strength = Math.min(8f, Math.max(strength * (ticks > 0 ? 1 : 0), degrees));
        ticks = Math.max(ticks, duration);
        total = Math.max(ticks, 1);
    }

    static void tick() {
        if (ticks > 0 && --ticks == 0) strength = 0;
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (ticks <= 0 || strength <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = (float) ((ticks - e.getPartialTick()) / (double) total);
        float amp = strength * Math.max(0, Math.min(1, t)) * Math.max(0, Math.min(1, t));
        double time = (mc.level.getGameTime() + e.getPartialTick()) * 1.7;
        e.setYaw(e.getYaw() + amp * (float) Math.sin(time * 3.1));
        e.setPitch(e.getPitch() + amp * (float) Math.sin(time * 4.7 + 1.3));
        e.setRoll(e.getRoll() + amp * 0.6f * (float) Math.sin(time * 6.3 + 2.1));
    }

    /** Render-state modifier for every entity: a power's mark outlines it. */
    static void modify(Entity entity, EntityRenderState state) {
        int mark = ClientPowers.markColour(entity.getId());
        if (mark != 0) state.outlineColor = mark;
    }
}
