package dev.awaken.arms.client;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.CombatFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The feel of a blow (SPEC §25), installed as {@link CombatFeedback}: the struck body freezes and trembles for the
 * blow's weight, the striker's hands and view hold for the same beat, and the view kicks along the blow and settles.
 * Visual only: the game's clock never stops. The view kick follows the camera-shake setting.
 */
final class ArmsFeel implements CombatFeedback.Sink {
    static final ArmsFeel INSTANCE = new ArmsFeel();

    private static final class Freeze {
        final long until;
        boolean captured;
        double x, y, z;
        float age, walkPos, walkSpeed;

        Freeze(long until) {
            this.until = until;
        }
    }

    private static final Map<Integer, Freeze> FROZEN = new HashMap<>();
    /** The striker's hold: wall-clock nanos it ends, and how long all holds so far have paused the hands. */
    private static long holdUntil, holdStart;
    private static long pausedTotal;
    // the view kick: a damped return from its start values
    private static float kickYaw, kickPitch, kickRoll;
    private static int kickTicks, kickTotal;

    @Override
    public void hitstop(int entityId, int ms) {
        if (ms <= 0 || entityId < 0) return;
        FROZEN.put(entityId, new Freeze(System.nanoTime() + ms * 1_000_000L));
    }

    @Override
    public void hold(int ms) {
        if (ms <= 0) return;
        long now = System.nanoTime();
        if (now < holdUntil) {
            holdUntil = Math.max(holdUntil, now + ms * 1_000_000L);
            return;
        }
        holdStart = now;
        holdUntil = now + ms * 1_000_000L;
    }

    @Override
    public void kick(float yaw, float pitch, float roll, int ticks) {
        if (!AwakenConfig.Client.CAMERA_SHAKE.get() || ticks <= 0) return;
        kickYaw = yaw;
        kickPitch = pitch;
        kickRoll = roll;
        kickTicks = kickTotal = ticks;
    }

    /** Nanos the hands have been held so far (moves' clocks subtract it so a swing resumes where it froze). */
    static long paused() {
        long now = System.nanoTime();
        return now < holdUntil ? pausedTotal + (now - holdStart) : pausedTotal;
    }

    static boolean holding() {
        return System.nanoTime() < holdUntil;
    }

    static void tick() {
        long now = System.nanoTime();
        if (holdUntil != 0 && now >= holdUntil) {
            pausedTotal += holdUntil - holdStart;
            holdUntil = 0;
        }
        if (kickTicks > 0) kickTicks--;
        if (!FROZEN.isEmpty()) for (Iterator<Freeze> it = FROZEN.values().iterator(); it.hasNext(); ) if (it.next().until < now) it.remove();
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (kickTicks <= 0 || Minecraft.getInstance().level == null) return;
        // eased out, never past where it came from (a critically damped settle)
        float t = (kickTicks - (float) e.getPartialTick()) / kickTotal;
        float w = Math.max(0, t) * Math.max(0, t);
        e.setYaw(e.getYaw() + kickYaw * w);
        e.setPitch(e.getPitch() + kickPitch * w);
        e.setRoll(e.getRoll() + kickRoll * w);
    }

    /** Render-state modifier for every entity: a frozen body holds its pose and trembles. */
    static void modify(Entity entity, EntityRenderState state) {
        if (FROZEN.isEmpty()) return;
        Freeze f = FROZEN.get(entity.getId());
        if (f == null) return;
        long now = System.nanoTime();
        if (now > f.until) return;
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
        // a shake across the blow, faster than a frame's worth of motion so it reads as impact, not drift
        double tremble = ((now / 16_000_000L) % 2 == 0 ? 1 : -1) * 0.035;
        state.x = f.x + tremble;
        state.y = f.y;
        state.z = f.z - tremble;
        state.ageInTicks = f.age;
        if (living != null) {
            living.walkAnimationPos = f.walkPos;
            living.walkAnimationSpeed = f.walkSpeed;
        }
    }

    static void reset() {
        FROZEN.clear();
        holdUntil = 0;
        pausedTotal = 0;
        kickTicks = 0;
    }
}
