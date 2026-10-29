package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsSounds;
import dev.awaken.arms.FpSwings;
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

    /**
     * A struck body's freeze: held where the blow met it while the beat lasts, trembling along the blow, then let go
     * over {@link #RELEASE}. What it moved while held (its push, its animation's clock) is caught up on a fast ease-out,
     * so the body launches out of the hit instead of jumping to where the push has already carried it.
     */
    private static final class Freeze {
        final long from, until;
        boolean captured, released;
        double x, y, z;
        float age, walkPos, walkSpeed;
        // what the release has left to catch up (held minus real, on the frame it let go)
        double ox, oy, oz;
        float oAge, oWalk;
        /** Struck again while the last freeze was letting go: the catch-up it had left starts this one's hold. */
        boolean carry;

        Freeze(long from, long until) {
            this.from = from;
            this.until = until;
        }
    }

    /** How long a freeze takes to let go, and the furthest it catches up (a teleport is just shown). */
    static final long RELEASE = 110_000_000L;
    private static final double RELEASE_MAX_SQ = 2.5 * 2.5;

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
        long now = System.nanoTime();
        Freeze f = new Freeze(now, now + ms * 1_000_000L);
        Freeze old = FROZEN.get(entityId);
        if (old != null && old.captured) {
            if (now <= old.until) {
                // still held: hold on from there (a fresh capture would jump to where the push has got it)
                f.captured = true;
                f.x = old.x;
                f.y = old.y;
                f.z = old.z;
                f.age = old.age;
                f.walkPos = old.walkPos;
                f.walkSpeed = old.walkSpeed;
            } else if (old.released) {
                float k = release(now - old.until);
                f.carry = true;
                f.ox = old.ox * k;
                f.oy = old.oy * k;
                f.oz = old.oz * k;
                f.oAge = old.oAge * k;
                f.oWalk = old.oWalk * k;
            }
        }
        FROZEN.put(entityId, f);
    }

    @Override
    public void hold(int ms) {
        if (ms <= 0 || !ArmsComfort.hitStop()) return;
        long now = System.nanoTime();
        if (now < holdUntil) {
            holdUntil = Math.max(holdUntil, now + ms * 1_000_000L);
            return;
        }
        // a hold that ended since the last tick is banked first, or a quick second hit would lose its pause
        if (holdUntil != 0) pausedTotal += holdUntil - holdStart;
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

    /** A shot glances off the guard: the same snap, smaller (nothing in reach was answered). */
    static void glanced() {
        fov(-0.02F, 7);
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

    /** The fight's last foe falls: the view breathes out (SPEC §25 "Fight Feel"). */
    static void lastFoe() {
        fov(0.025F, 14);
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
        // an ended hold counts in full until tick() banks it, so the clocks never jump ahead and back between frames
        return holdUntil == 0 ? pausedTotal : pausedTotal + Math.min(System.nanoTime(), holdUntil) - holdStart;
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
        if (!FROZEN.isEmpty()) for (Iterator<Freeze> it = FROZEN.values().iterator(); it.hasNext(); ) if (it.next().until + RELEASE < now) it.remove();
    }

    private static final float[] LEAN = new float[3];

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (Minecraft.getInstance().level == null) return;
        lean(e);
        if (kickTicks <= 0) return;
        // eased out, never past where it came from (a critically damped settle)
        float t = (kickTicks - (float) e.getPartialTick()) / kickTotal;
        float w = Math.max(0, t) * Math.max(0, t);
        e.setYaw(e.getYaw() + kickYaw * w);
        e.setPitch(e.getPitch() + kickPitch * w);
        e.setRoll(e.getRoll() + kickRoll * w);
    }

    /**
     * The swing's own lean (FpSwings, first person only): the eye goes with the blow, a slam pitching it down on the
     * crash, a cut leaning it the way the blade travels, held through a hit-stop with the hands. The kick setting scales it.
     */
    private static void lean(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float k = ArmsComfort.kick();
        if (p == null || k <= 0 || !mc.options.getCameraType().isFirstPerson() || !ArmsPredict.fights(p)) return;
        Archetypes.Loaded style = ArmsPredict.style;
        Archetype.Move m = ArmsPredict.move;
        if (style == null || ArmsPredict.charging) m = null;
        float t = ArmsPredict.viewTime();
        boolean has = m != null && FpSwings.camera(FpSwings.style(style.id()), m, t, LEAN);
        if (!has) LEAN[0] = LEAN[1] = LEAN[2] = 0;
        // a change of move carries the lean across (a chained strike's starts level: the view never snaps; SwingBlend)
        float now = (System.nanoTime() - paused()) / ArmsPredict.nanosPerTick();
        if (m != leanMove || ArmsPredict.moveNanos != leanNanos) {
            leanMove = m;
            leanNanos = ArmsPredict.moveNanos;
            LEAN_V.zero();
            if (has && FpSwings.camera(FpSwings.style(style.id()), m, t + 0.1F, LEAN_AHEAD)) {
                LEAN_V.set(LEAN_AHEAD[0] - LEAN[0], LEAN_AHEAD[1] - LEAN[1], LEAN_AHEAD[2] - LEAN[2]).div(0.1F);
            }
            LEAN_BLEND.begin(now, LEAN_NOW.set(LEAN[0], LEAN[1], LEAN[2]), LEAN_V, 3F);
        }
        LEAN_BLEND.apply(now, LEAN_NOW.set(LEAN[0], LEAN[1], LEAN[2]));
        if (!has && !LEAN_BLEND.active(now)) return;
        float inv = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1F : -1F;
        e.setYaw(e.getYaw() + LEAN_NOW.x * inv * k);
        e.setPitch(e.getPitch() + LEAN_NOW.y * k);
        e.setRoll(e.getRoll() + LEAN_NOW.z * inv * k);
    }

    private static final dev.awaken.arms.SwingBlend.Vec LEAN_BLEND = new dev.awaken.arms.SwingBlend.Vec();
    private static final org.joml.Vector3f LEAN_NOW = new org.joml.Vector3f(), LEAN_V = new org.joml.Vector3f();
    private static final float[] LEAN_AHEAD = new float[3];
    private static Archetype.@org.jspecify.annotations.Nullable Move leanMove;
    private static long leanNanos;

    /** The release's share left {@code since} nanos after the hold ended: a cubic ease-out, fast first. */
    static float release(long since) {
        if (since >= RELEASE) return 0F;
        float u = 1F - Math.max(0, since) / (float) RELEASE;
        return u * u * u;
    }

    private static final float[] BLOW = new float[2];

    /** Render-state modifier for every entity: a frozen body holds its pose and trembles, then is let go smoothly. */
    static void modify(Entity entity, EntityRenderState state) {
        if (FROZEN.isEmpty()) return;
        Freeze f = FROZEN.get(entity.getId());
        if (f == null) return;
        long now = System.nanoTime();
        LivingEntityRenderState living = state instanceof LivingEntityRenderState l ? l : null;
        if (!f.captured) {
            if (now > f.until) return; // never drawn while held: nothing to let go
            f.captured = true;
            f.x = state.x + (f.carry ? f.ox : 0);
            f.y = state.y + (f.carry ? f.oy : 0);
            f.z = state.z + (f.carry ? f.oz : 0);
            f.age = state.ageInTicks + (f.carry ? f.oAge : 0);
            if (living != null) {
                f.walkPos = living.walkAnimationPos + (f.carry ? f.oWalk : 0);
                f.walkSpeed = living.walkAnimationSpeed;
            }
        }
        if (now <= f.until) {
            // a shake along the blow, faster than a frame's worth of motion so it reads as impact, not drift; hardest
            // on the hit, easing as the beat goes (diagonal when the blow's way isn't known)
            float u = (now - f.from) / (float) Math.max(1L, f.until - f.from);
            double amp = ((now / 16_000_000L) % 2 == 0 ? 1 : -1) * 0.045 * (1 - 0.6 * u);
            boolean along = CombatFeedback.blowOn(entity.getId(), BLOW) > 0;
            state.x = f.x + amp * (along ? BLOW[0] : 0.78);
            state.y = f.y;
            state.z = f.z + amp * (along ? BLOW[1] : -0.78);
            state.ageInTicks = f.age;
            if (living != null) {
                living.walkAnimationPos = f.walkPos;
                living.walkAnimationSpeed = f.walkSpeed;
            }
            return;
        }
        if (!f.released) {
            f.released = true;
            f.ox = f.x - state.x;
            f.oy = f.y - state.y;
            f.oz = f.z - state.z;
            if (f.ox * f.ox + f.oy * f.oy + f.oz * f.oz > RELEASE_MAX_SQ) f.ox = f.oy = f.oz = 0;
            f.oAge = f.age - state.ageInTicks;
            f.oWalk = living == null ? 0 : f.walkPos - living.walkAnimationPos;
        }
        float k = release(now - f.until);
        if (k <= 0) return;
        state.x += f.ox * k;
        state.y += f.oy * k;
        state.z += f.oz * k;
        state.ageInTicks += f.oAge * k;
        if (living != null) living.walkAnimationPos += f.oWalk * k;
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
