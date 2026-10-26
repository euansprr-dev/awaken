package dev.awaken.arms.client;

import dev.awaken.arms.ArmsSounds;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.lens.client.LensClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The feel of a blow and the fight's one camera voice (SPEC §25 "Feel"), installed as {@link CombatFeedback}: the
 * struck body freezes and trembles for the blow's weight, the striker's hands and view hold for the same beat, the
 * view kicks along the blow and settles; the big moments move the field of view (a deflect snaps in, a break and an
 * Execution lean in, the Art's release and a perfect dodge breathe out, the dodge with Lens's colour drained for a
 * beat); under 30% health the heart beats and the frame's edges cool. Every layer has its comfort switch
 * ({@link ArmsComfort}); visual only, the game's clock never stops.
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
        if (ms <= 0 || entityId < 0 || !ArmsComfort.hitStop()) return;
        FROZEN.put(entityId, new Freeze(System.nanoTime() + ms * 1_000_000L));
    }

    @Override
    public void hold(int ms) {
        if (ms <= 0 || !ArmsComfort.hitStop()) return;
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
        float k = ArmsComfort.kick();
        if (k <= 0 || ticks <= 0) return;
        kickYaw = yaw * k;
        kickPitch = pitch * k;
        kickRoll = roll * k;
        kickTicks = kickTotal = ticks;
    }

    // ---- the moments: the field of view's push and pull (a fraction of it), eased in over the first fifth -----------

    private static float fovAmount;
    private static int fovTicks, fovTotal;

    /** The view's field narrows ({@code amount} < 0, leaning in) or widens over {@code ticks} and comes back. */
    static void fov(float amount, int ticks) {
        float k = ArmsComfort.moments();
        if (k <= 0 || ticks <= 0) return;
        // a bigger moment takes the view; a smaller one waits its turn
        if (fovTicks > 0 && Math.abs(fovNow(0)) > Math.abs(amount * k)) return;
        fovAmount = amount * k;
        fovTicks = fovTotal = ticks;
    }

    private static float fovNow(float partial) {
        if (fovTicks <= 0) return 0;
        float u = Math.min(1F, (fovTotal - fovTicks + partial) / fovTotal);
        // in fast over the first fifth, home on a long ease (a breath, not a cut)
        float w = u < 0.2F ? ArmsHands.easeOutSine(u / 0.2F) : 1 - ArmsHands.easeInOutSine((u - 0.2F) / 0.8F);
        return fovAmount * w;
    }

    static void onFov(ViewportEvent.ComputeFov e) {
        float f = fovNow((float) e.getPartialTick());
        if (f != 0) e.setFOV(e.getFOV() * (1 + f));
    }

    /** A deflect: the frame snaps in on the meeting of the blades. */
    static void deflected() {
        fov(-0.035F, 9);
    }

    static void guardBroken() {
        fov(0.025F, 10);
    }

    /** A stance breaks: the view leans in on the opening. */
    static void broke() {
        fov(-0.05F, 16);
    }

    /** An Execution lands: the deepest lean, held longest. */
    static void executed() {
        fov(-0.075F, 22);
    }

    /** The Art lets go: the frame opens with its wave. */
    static void art() {
        fov(0.06F, 16);
    }

    /** A perfect dodge: the view breathes out, and with Lens on the colour drains from the frame for a beat. */
    static void breath() {
        fov(0.05F, 18);
        if (ArmsComfort.moments() <= 0 || !LensClient.active()) return;
        String now = LensClient.overriding();
        if (now != null && !now.equals(BREATH)) return; // a story's moment keeps the frame
        LensClient.override(BREATH, 2);
        breathTicks = 12;
    }

    private static final String BREATH = "awaken:arms_breath";
    private static int breathTicks;

    // ---- low health: the heart and the cold edge -------------------------------------------------------------------

    /** Under this share of health the heart beats; over the second it stops (a gap so it doesn't flicker on the line). */
    static final float LOW = 0.3F, CLEAR = 0.36F;
    private static boolean low;
    private static int beatIn;
    /** The edge's strength (0..1, eased) and the last beat's swell (decaying), for {@link ArmsHud}. */
    static float edge, edgeWas, pulse;

    private static void lowHealth(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean alive = p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && ArmsComfort.lowHealth();
        float share = alive ? p.getHealth() / Math.max(1F, p.getMaxHealth()) : 1F;
        low = alive && (low ? share < CLEAR : share < LOW);
        edgeWas = edge;
        float target = low ? Mth.clamp((LOW - share) / LOW * 0.4F + 0.6F, 0.6F, 1F) : 0F;
        edge += (target - edge) * (target > edge ? 0.12F : 0.06F);
        pulse *= 0.82F;
        if (!low) {
            beatIn = 0;
            return;
        }
        if (--beatIn > 0) return;
        // slower when it's only low, quicker as it nears the end: 28 ticks a beat at 30%, 16 at nothing
        beatIn = Math.round(16 + 12 * Mth.clamp(share / LOW, 0, 1));
        if (!ArmsComfort.reduced()) pulse = 1F;
        mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ArmsSounds.HEARTBEAT.get(), SoundSource.PLAYERS, 1F, 1F, false);
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
        Minecraft mc = Minecraft.getInstance();
        if (fovTicks > 0) fovTicks--;
        if (breathTicks > 0 && --breathTicks == 0 && BREATH.equals(LensClient.overriding())) LensClient.clearOverride(14);
        if (mc.level != null) lowHealth(mc);
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
        fovTicks = 0;
        low = false;
        edge = edgeWas = pulse = 0;
        if (breathTicks > 0 && BREATH.equals(LensClient.overriding())) LensClient.clearOverride(4);
        breathTicks = 0;
    }
}
