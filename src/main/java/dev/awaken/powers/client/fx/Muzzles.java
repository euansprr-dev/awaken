package dev.awaken.powers.client.fx;

import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Named cast points (docs/power-anims/PLAN.md section 5.2): where on a body an effect starts, found from the body that
 * is drawn, so a beam leaves the palm and a wave the planted foot.
 * <ul>
 *   <li><b>You, in first person</b>: the point is authored. Hands keys where each fist is on screen
 *   ({@link FirstPerson#LAST_FIST_MAIN}); the palm is that fist and a hand's depth toward the aim, put into the world the
 *   way Hands puts anything the world anchors (the lens factor).</li>
 *   <li><b>Everyone drawn</b> (you in the shoulder view, other players): {@link MuzzleLayer} reads the posed model each frame
 *   (the arm and leg exactly as the animation library has put them) and records the world position here.</li>
 *   <li><b>Not drawn</b> (far, culled): the stance's own numbers ({@link #baked}), body-relative, so the effect still
 *   starts at the body.</li>
 * </ul>
 * Fixed arrays, nothing allocated per frame.
 */
final class Muzzles {
    private Muzzles() {}

    static final int PALM = 0, PALM_OFF = 1, CORE = 2, FOOT = 3, N = 4;

    private static final int SLOTS = 48;
    private static final int[] ID = new int[SLOTS];
    private static final long[] AT = new long[SLOTS * N];
    private static final double[] P = new double[SLOTS * N * 3];
    private static int next;
    /** A sample is good for this long (nanoseconds): a drawn body samples every frame; a body that stopped being drawn does not. */
    private static final long FRESH = 150_000_000L;
    /** The hand's depth: the palm is this far past the fist's middle toward the aim (blocks). */
    private static final float PALM_FORWARD = 0.10f;
    /** Hands' own hand-pass field of view (degrees). */
    private static final float HAND_FOV = 70f;

    static {
        Arrays.fill(ID, Integer.MIN_VALUE);
    }

    // ---- the layer's samples ----------------------------------------------------------------------------------------

    static void put(int entity, int point, double x, double y, double z) {
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (ID[i] == entity) slot = i;
        if (slot < 0) {
            slot = next;
            next = (next + 1) % SLOTS;
            ID[slot] = entity;
            Arrays.fill(AT, slot * N, slot * N + N, 0L);
        }
        int i = slot * N + point;
        P[i * 3] = x;
        P[i * 3 + 1] = y;
        P[i * 3 + 2] = z;
        AT[i] = System.nanoTime();
    }

    static boolean sampled(int entity, int point, double[] out) {
        for (int s = 0; s < SLOTS; s++) {
            if (ID[s] != entity) continue;
            int i = s * N + point;
            if (AT[i] == 0 || System.nanoTime() - AT[i] > FRESH) return false;
            out[0] = P[i * 3];
            out[1] = P[i * 3 + 1];
            out[2] = P[i * 3 + 2];
            return true;
        }
        return false;
    }

    // ---- first person: a point in the hands' view space, put into the world -------------------------------------------

    /** (x right, y up, z toward you), in the hand pass's space, to where the world shows it on screen at the same depth. */
    static void viewToWorld(float x, float y, float z, double[] out) {
        Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
        var f = cam.forwardVector();
        var l = cam.leftVector();
        var u = cam.upVector();
        float r = (float) (Math.tan(Math.toRadians(cam.getFov() * 0.5)) / Math.tan(Math.toRadians(HAND_FOV * 0.5)));
        float depth = -z;
        Vec3 c = cam.position();
        out[0] = c.x + f.x() * depth + (-l.x() * x + u.x() * y) * r;
        out[1] = c.y + f.y() * depth + (-l.y() * x + u.y() * y) * r;
        out[2] = c.z + f.z() * depth + (-l.z() * x + u.z() * y) * r;
    }

    /** Whether the hands' fist for this hand was keyed within the last frames: your own palm in first person, into out. */
    static boolean fist(boolean off, double[] out) {
        if (System.nanoTime() - FirstPerson.lastFistNanos > FRESH) return false;
        float[] f = off ? FirstPerson.LAST_FIST_OFF : FirstPerson.LAST_FIST_MAIN;
        if (f[3] <= 0) return false;
        viewToWorld(f[0] / f[3], f[1] / f[3], f[2] / f[3] - PALM_FORWARD, out);
        return true;
    }

    // ---- the answers ------------------------------------------------------------------------------------------------

    /**
     * A hand's palm: the authored fist (your first person), else the drawn model's, else the stance's own, else false (the
     * caller keeps its old constant offset).
     */
    static boolean palm(Entity e, boolean off, float pt, boolean firstPerson, double[] out) {
        // first person: only a cast profile's fists are the palm (a sprint's pumping fist is not where a charge starts)
        if (firstPerson && CastAnims.profileOf(e.getId()) != 0 && fist(off, out)) return true;
        if (sampled(e.getId(), off ? PALM_OFF : PALM, out)) return true;
        return baked(e, off ? PALM_OFF : PALM, pt, out);
    }

    /** The ground under the stamping foot (CastProfiles "shockwave"): the foot's x and z, the ground at the body's feet. */
    static void plant(Entity e, float pt, double[] out) {
        if (sampled(e.getId(), FOOT, out)) {
            out[1] = Mth.lerp(pt, e.yo, e.getY());
            return;
        }
        baked(e, FOOT, pt, out);
    }

    /** The sternum: the drawn model's, else a point on the chest. */
    static void core(Entity e, float pt, double[] out) {
        Minecraft mc = Minecraft.getInstance();
        // your own first person: the chest is below the frame, so the core is a point low in the view, in front of the chest
        if (e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson() && CastAnims.profileOf(e.getId()) != 0) {
            viewToWorld(0f, -0.50f, -0.70f, out);
            return;
        }
        if (sampled(e.getId(), CORE, out)) return;
        baked(e, CORE, pt, out);
        if (out[1] == 0) {
            out[0] = Mth.lerp(pt, e.xo, e.getX());
            out[1] = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.72;
            out[2] = Mth.lerp(pt, e.zo, e.getZ());
        }
    }

    /**
     * The stance's own numbers where the body is not drawn: (right, up, forward) from the feet in the body's frame, read
     * off the baked clips (docs/power-anims/proof/numbers.json: beam_hold's palm, stomp's plant). Zero y means no answer.
     */
    private static boolean baked(Entity e, int point, float pt, double[] out) {
        int prof = CastAnims.profileOf(e.getId());
        double right, up, fwd;
        if (prof == CastProfilesIds.BEAM && point == PALM) {
            right = 0.35;
            up = 1.28;
            fwd = 0.88;
        } else if (prof == CastProfilesIds.BEAM && point == CORE) {
            right = 0.0;
            up = 1.26;
            fwd = 0.31;
        } else if (prof == CastProfilesIds.SHOCKWAVE && point == FOOT) {
            right = 0.19;
            up = 0.0;
            fwd = 0.19;
        } else {
            out[1] = 0;
            return false;
        }
        boolean left = e instanceof LivingEntity le && le.getMainArm() == HumanoidArm.LEFT;
        if (left) right = -right;
        float yaw = (e instanceof LivingEntity le ? le.getViewYRot(pt) : e.getYRot()) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        out[0] = Mth.lerp(pt, e.xo, e.getX()) + fx * fwd + rx * right;
        out[1] = Mth.lerp(pt, e.yo, e.getY()) + up;
        out[2] = Mth.lerp(pt, e.zo, e.getZ()) + fz * fwd + rz * right;
        return true;
    }
}
