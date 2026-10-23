package dev.awaken.powers.client;

import dev.awaken.core.AwakenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Combat feel: camera shake (respects the cameraShake client setting) and hit-stop, a 40-80 ms freeze-frame on the
 * struck entity's render (position, animation and age held, with a small tremble) so heavy hits land.
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

    /** Render-state modifier for every entity: outline marks and hit-stop. */
    static void modify(Entity entity, EntityRenderState state) {
        int mark = ClientPowers.markColour(entity.getId());
        if (mark != 0) state.outlineColor = mark;
        if (ClientPowers.FROZEN.isEmpty()) return;
        ClientPowers.Freeze f = ClientPowers.FROZEN.get(entity.getId());
        if (f == null) return;
        if (System.nanoTime() > f.untilNanos) {
            ClientPowers.FROZEN.remove(entity.getId());
            return;
        }
        LivingEntityRenderState living = state instanceof LivingEntityRenderState l ? l : null;
        if (!f.captured) {
            f.captured = true;
            f.x = state.x;
            f.y = state.y;
            f.z = state.z;
            f.age = state.ageInTicks;
            if (living != null) {
                f.walkPos = living.walkAnimationPos;
                f.walkSpeed = living.walkAnimationSpeed;
            }
        }
        double tremble = ((System.nanoTime() / 16_000_000L) % 2 == 0 ? 1 : -1) * 0.04;
        state.x = f.x + tremble;
        state.y = f.y;
        state.z = f.z - tremble;
        state.ageInTicks = f.age;
        if (living != null) {
            living.walkAnimationPos = f.walkPos;
            living.walkAnimationSpeed = f.walkSpeed;
        }
    }
}
