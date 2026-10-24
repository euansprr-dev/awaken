package dev.awaken.story.client;

import dev.awaken.core.AwakenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The ritual's camera: drawn gently toward the throne when the doors close, held on the Seated One's face while its
 * eyes open, on its palm while the light drains into it; a slow push-in; shakes from the bell, the eyes, the strikes.
 * Shake and FOV punches follow the core {@code cameraShake} setting; the pulls are the scene's and always play.
 */
final class RitualCamera {
    private RitualCamera() {}

    private static float shake, shakeDecay;
    private static float fovPunch, fovDecay;
    private static float kickYaw, kickPitch;
    /** The pull: how strongly the view is drawn to {@link #target} (eased each tick toward {@link #pullWant}). */
    private static float pull, pullO, pullWant;
    private static Vec3 target = Vec3.ZERO;
    private static float zoom, zoomO, zoomWant;

    static void reset() {
        shake = fovPunch = kickYaw = kickPitch = pull = pullO = pullWant = zoom = zoomO = zoomWant = 0;
    }

    static boolean shakeOn() {
        try {
            return AwakenConfig.Client.CAMERA_SHAKE.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void shake(float deg, int ticks) {
        if (!shakeOn()) return;
        if (deg > shake) {
            shake = deg;
            shakeDecay = deg / Math.max(1, ticks);
        }
    }

    /** A shake that falls off with distance from where it happened. */
    static void shakeNear(Vec3 at, float deg, int ticks, double range) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = p.position().distanceTo(at);
        if (d < range) shake(deg * (float) (1 - d / range), ticks);
    }

    static void fovPunch(float amount, int ticks) {
        if (!shakeOn()) return;
        fovPunch = amount;
        fovDecay = Math.abs(amount) / Math.max(1, ticks);
    }

    /** The view jerks away from a blow that came from {@code from}. */
    static void kick(Vec3 from, float deg) {
        var p = Minecraft.getInstance().player;
        if (p == null || !shakeOn()) return;
        Vec3 d = p.position().subtract(from);
        float yawTo = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float rel = Mth.wrapDegrees(yawTo - p.getYRot());
        kickYaw += Mth.clamp(rel, -90, 90) / 90F * deg;
        kickPitch -= deg * 0.5F;
    }

    static void tick() {
        shake = Math.max(0, shake - shakeDecay);
        if (fovPunch > 0) fovPunch = Math.max(0, fovPunch - fovDecay);
        else if (fovPunch < 0) fovPunch = Math.min(0, fovPunch + fovDecay);
        kickYaw *= 0.6F;
        kickPitch *= 0.6F;
        // where the scene wants the camera this tick
        pullWant = 0;
        zoomWant = 0;
        int beat = RitualClient.beat;
        float age = RitualClient.beatAge(0);
        if (beat == RitualClient.DOORS) {
            // drawn toward the throne while the candles light toward it, closing in a little as the line reaches the god
            target = RitualFx.eyes(0).add(0, -5, 0);
            pullWant = age < 40 ? 0 : age < 90 ? 0.3F * (age - 40) / 50F : age < 165 ? 0.3F : 0.3F * Math.max(0, 1 - (age - 165) / 25F);
            zoomWant = age > 90 && age < 175 ? -0.18F * Math.min(1, (age - 90) / 60F) : 0;
        } else if (beat == RitualClient.WAKING) {
            // the face, framed: a slow push-in until the eyes fill the frame as they open, held, then let go
            target = RitualFx.eyes(0);
            pullWant = age < 96 ? Math.min(0.95F, age / 24F) : Math.max(0, 0.95F - (age - 96) / 30F);
            zoomWant = age < 96 ? -0.6F * smooth(Math.min(1, age / 64F)) : 0;
        } else if (beat == RitualClient.END) {
            target = RitualFx.palm(0);
            pullWant = Math.min(0.9F, age / 40F);
            zoomWant = -0.16F * Math.min(1, age / 220F);
        } else if (beat == RitualClient.JUDGED) {
            target = RitualClient.words;
            pullWant = 0.12F * Math.min(1, age / 15F);
        }
        pullO = pull;
        pull += (pullWant - pull) * 0.12F;
        zoomO = zoom;
        zoom += (zoomWant - zoom) * 0.08F;
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    static void angles(ViewportEvent.ComputeCameraAngles e) {
        float pt = e.getPartialTick();
        float w = Mth.lerp(pt, pullO, pull);
        if (w > 0.001F && !target.equals(Vec3.ZERO)) {
            Vec3 cam = e.getCamera().position();
            Vec3 d = target.subtract(cam);
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float pitch = Math.max(-55F, (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
            float ease = w * w * (3 - 2 * w);
            e.setYaw(e.getYaw() + Mth.wrapDegrees(yaw - e.getYaw()) * ease);
            e.setPitch(e.getPitch() + (pitch - e.getPitch()) * ease);
        }
        if (shake > 0.001F || Math.abs(kickYaw) > 0.01F) {
            double t = (RitualClient.ticks + pt) * 0.9;
            e.setYaw(e.getYaw() + shake * (float) Math.sin(t * 3.1) * 0.6F + kickYaw);
            e.setPitch(e.getPitch() + shake * (float) Math.sin(t * 4.7 + 1.3) * 0.6F + kickPitch);
            e.setRoll(e.getRoll() + shake * 0.35F * (float) Math.sin(t * 6.3 + 2.1));
        }
    }

    static void fov(ViewportEvent.ComputeFov e) {
        float z = Mth.lerp((float) e.getPartialTick(), zoomO, zoom) + fovPunch;
        if (Math.abs(z) > 0.0005F) e.setFOV(e.getFOV() * (1 + z));
    }
}
