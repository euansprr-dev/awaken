package dev.awaken.mobs.client.life;

import dev.awaken.mobs.client.life.LifeBones.Role;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A body's legs for the motion layer (SPEC §23, "Motion"): each leg is two bones found by role (a hip that swings, a
 * knee that bends), where its foot rests (home, model px), which way its knee bends (its bulge at rest), its place in
 * the gait, and the two-bone IK that poses it ({@link #solve}): the hip turns so the knee lies where the two lengths
 * meet on the side the knee bulged at rest, and the knee only bends about its own hinge. Made once per model.
 *
 * <p>Plain data (bone poses in, rotations out; JOML and no client classes), so GameTests check every profiled rig's
 * feet on the server; {@link LegParts} applies it to a model's parts.
 */
public final class LegRig {
    /** The vault's most, a share of the mean leg's length (a cow's long step drops its hips about a tenth of a leg). */
    public static final float VAULT = 0.25F;
    public final MotionProfile.Gait gait;
    public final int n;
    /** Each leg's hip and knee bone (binding indices), and the bones above the hip, root first. */
    final int[] hip, knee;
    final int[][] up;
    final float[] l1, l2;
    /** Rest: the hip's and the knee's own rotations, the knee pivot in the hip frame, the shin's direction there. */
    private final Matrix3f[] r0, s0;
    private final Vector3f[] v1, tip0;
    /** Rest, in the hip's parent frame: the thigh's frame (thigh, hinge, their cross) transposed, and the knee's bulge. */
    private final Matrix3f[] a0t;
    private final Vector3f[] pole;
    /** Where each foot stands at rest, model px (y = 24 is the ground). */
    public final float[] homeX, homeY, homeZ;
    /** +1 left, -1 right; the leg's rank on its side from the front (0). */
    final int[] side, rank;
    final int perSide;
    /** The gait's phase offsets: one foot at a time from the back, and the alternating sets. */
    final float[] offRipple, offAlternate;
    /** Legs whose feet must not lift together with each one's (the neighbours on its side and its opposite). */
    final int[][] near;
    /** Blocks from the body's middle to a foot, on average (a turn on the spot is this far a step). */
    final float turnRadius;
    /** Blocks from the front feet to the back ones, and from the left feet to the right (the slope's levers). */
    final float span, width;
    /** Each hip at rest, model px; the most (px) the body sinks to keep a long step's planted feet in reach (its vault). */
    public final float[] hipX, hipY, hipZ;
    public final float vaultMax;
    /**
     * Rigid legs ({@link MotionProfile.Knees#RIGID}): the rest leg (hip to foot) in the hip's parent frame, a unit
     * direction and its length; how far (px) above the hip it swings from; how far its pivot may slide forward, back
     * and down and still stay inside the body's cubes (its top never shows), in that frame. The same leg in model space.
     */
    final boolean rigid;
    /** How far (radians) each knee can bend before its joint opens (the thigh's end must cover the shin's top); pi bent at rest. */
    private final float[] hides;
    private final Vector3f[] g0;
    final float[] len, lift, fore, aft, drop;
    private final float[] gmx, gmy, gmz;

    private LegRig(MotionProfile.Gait gait, List<int[]> legs, List<int[]> chains, float[][] pose, IntFunction<float @Nullable []> box) {
        this.gait = gait;
        n = legs.size();
        hip = new int[n];
        knee = new int[n];
        up = chains.toArray(new int[0][]);
        l1 = new float[n];
        l2 = new float[n];
        r0 = new Matrix3f[n];
        s0 = new Matrix3f[n];
        v1 = new Vector3f[n];
        tip0 = new Vector3f[n];
        a0t = new Matrix3f[n];
        pole = new Vector3f[n];
        homeX = new float[n];
        homeY = new float[n];
        homeZ = new float[n];
        side = new int[n];
        rank = new int[n];
        hipX = new float[n];
        hipY = new float[n];
        hipZ = new float[n];
        rigid = gait.knees() == MotionProfile.Knees.RIGID;
        hides = new float[n];
        g0 = new Vector3f[n];
        len = new float[n];
        lift = new float[n];
        fore = new float[n];
        aft = new float[n];
        drop = new float[n];
        gmx = new float[n];
        gmy = new float[n];
        gmz = new float[n];
        Matrix4f m = new Matrix4f();
        for (int i = 0; i < n; i++) {
            hip[i] = legs.get(i)[0];
            knee[i] = legs.get(i)[1];
            float[] h = pose[hip[i]], k = pose[knee[i]];
            r0[i] = new Matrix3f().rotationZYX(h[5], h[4], h[3]);
            s0[i] = new Matrix3f().rotationZYX(k[5], k[4], k[3]);
            v1[i] = new Vector3f(k[0], k[1], k[2]);
            int fb = legs.get(i)[2];
            Vector3f tip = tip(box.apply(knee[i]));
            if (fb >= 0) { // the foot's far end, through its rest pose
                float[] f = pose[fb];
                tip = new Matrix3f().rotationZYX(f[5], f[4], f[3]).transform(tip(box.apply(fb))).add(f[0], f[1], f[2]);
            }
            l1[i] = Math.max(0.5F, v1[i].length());
            l2[i] = Math.max(0.5F, tip.length());
            tip0[i] = s0[i].transform(new Vector3f(tip)).normalize();
            // the rest leg in the hip's parent frame
            Vector3f hp = new Vector3f(h[0], h[1], h[2]);
            Vector3f thigh = r0[i].transform(new Vector3f(v1[i]));
            Vector3f kn = new Vector3f(hp).add(thigh);
            Vector3f shin = r0[i].transform(s0[i].transform(new Vector3f(tip)));
            Vector3f ft = new Vector3f(kn).add(shin);
            Vector3f u = new Vector3f(thigh).normalize(), w = new Vector3f(shin).normalize();
            Vector3f d = new Vector3f(ft).sub(hp).normalize();
            Vector3f bulge = new Vector3f(kn).sub(hp);
            bulge.sub(new Vector3f(d).mul(bulge.dot(d)));
            rest(up[i], pose, m);
            if (bulge.lengthSquared() > 0.01F) {
                pole[i] = bulge.normalize();
            } else { // a straight leg at rest: its knee bends the way the profile says (a foreleg's forward, a hock's back)
                pole[i] = new Matrix3f().set(m).transpose().transform(new Vector3f(0, 0, kneesForward(gait, i, legs, chains, pose) ? -1 : 1));
            }
            Vector3f hinge = new Vector3f(u).cross(w);
            if (hinge.lengthSquared() < 1e-4F) hinge.set(u).cross(pole[i]);
            hinge.normalize();
            Vector3f third = new Vector3f(u).cross(hinge);
            a0t[i] = new Matrix3f().set(u, hinge, third).transpose();
            // the foot and the hip at rest in model space
            Vector3f footM = m.transformPosition(new Vector3f(ft));
            Vector3f hipM = m.transformPosition(new Vector3f(hp));
            hipX[i] = hipM.x;
            hipY[i] = hipM.y;
            hipZ[i] = hipM.z;
            side[i] = hipM.x > 0.01F ? 1 : hipM.x < -0.01F ? -1 : (footM.x >= 0 ? 1 : -1);
            homeX[i] = footM.x;
            homeY[i] = 24.0F;
            homeZ[i] = footM.z;
            hides[i] = u.angle(w) > 10.0F * Mth.DEG_TO_RAD ? Mth.PI : hides(box.apply(hip[i]), v1[i], box.apply(knee[i]), tip);
            Vector3f leg = new Vector3f(ft).sub(hp);
            len[i] = Math.max(0.5F, leg.length());
            g0[i] = leg.div(len[i]);
            lift[i] = gait.shoulder() * len[i];
            Vector3f gm = new Matrix3f().set(m).transform(new Vector3f(g0[i]));
            gmx[i] = gm.x;
            gmy[i] = gm.y;
            gmz[i] = gm.z;
            // the room the leg's top has inside the body above it (its parent's own cubes), the hip's cubes at rest
            float[] top = box.apply(hip[i]), body = chains.get(i).length > 0 ? box.apply(chains.get(i)[chains.get(i).length - 1]) : null;
            if (top != null && body != null && hp.x + top[0] >= body[0] - 0.01F && hp.x + top[3] <= body[3] + 0.01F
                    && hp.z + top[2] >= body[2] - 0.01F && hp.z + top[5] <= body[5] + 0.01F && hp.y + top[1] < body[4]) {
                fore[i] = hp.z + top[2] - body[2];
                aft[i] = body[5] - (hp.z + top[5]);
                drop[i] = Math.max(0.0F, body[4] - (hp.y + top[1]) - 1.0F); // a px stays hidden for the top's tilted corners
            }
        }
        // ranks from the front (-z) on each side
        int most = 0;
        for (int i = 0; i < n; i++) {
            int r = 0;
            for (int j = 0; j < n; j++) if (j != i && side[j] == side[i] && (hipZ[j] < hipZ[i] || (hipZ[j] == hipZ[i] && j < i))) r++;
            rank[i] = r;
            most = Math.max(most, r + 1);
        }
        perSide = Math.max(1, most);
        // the stance: each foot out from its hip at the profile's radius, the front ones reaching forward, the back ones back
        if (gait.radius() > 0) {
            for (int i = 0; i < n; i++) {
                float k = perSide > 1 ? 1.0F - 2.0F * rank[i] / (perSide - 1) : 0.0F;
                float a = gait.fan() * k * Mth.DEG_TO_RAD;
                homeX[i] = hipX[i] + side[i] * gait.radius() * Mth.cos(a);
                homeZ[i] = hipZ[i] - gait.radius() * Mth.sin(a);
            }
        }
        offRipple = new float[n];
        offAlternate = new float[n];
        float reachSum = 0;
        // the ripple runs from the back forward: a pair steps a lag after the pair behind it (the larger offset steps first);
        // a negative lag runs it from the front
        float lag = gait.lag() != 0 ? gait.lag() : 0.5F / perSide;
        for (int i = 0; i < n; i++) {
            float o = (side[i] < 0 ? 0.5F : 0.0F) + rank[i] * lag;
            offRipple[i] = o - (float) Math.floor(o);
            offAlternate[i] = ((rank[i] + (side[i] < 0 ? 1 : 0)) % 2) * 0.5F;
            reachSum += Mth.sqrt(homeX[i] * homeX[i] + homeZ[i] * homeZ[i]) / 16.0F;
        }
        turnRadius = n > 0 ? reachSum / n : 0.5F;
        float legSum = 0;
        for (int i = 0; i < n; i++) legSum += l1[i] + l2[i];
        vaultMax = n > 0 ? VAULT * legSum / n : 0.0F;
        float fz = 0, bz = 0, lx = 0, rx = 0;
        int nf = 0, nb = 0, nl = 0, nr = 0;
        for (int i = 0; i < n; i++) {
            if (rank[i] == 0) {
                fz += homeZ[i];
                nf++;
            }
            if (rank[i] == perSide - 1) {
                bz += homeZ[i];
                nb++;
            }
            if (side[i] > 0) {
                lx += homeX[i];
                nl++;
            } else {
                rx += homeX[i];
                nr++;
            }
        }
        span = Math.max(0.1F, (nb > 0 ? bz / nb : 0) / 16.0F - (nf > 0 ? fz / nf : 0) / 16.0F);
        width = Math.max(0.1F, (nl > 0 ? lx / nl : 0) / 16.0F - (nr > 0 ? rx / nr : 0) / 16.0F);
        near = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> by = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                if ((side[j] == side[i] && Math.abs(rank[j] - rank[i]) == 1) || (side[j] != side[i] && rank[j] == rank[i])) by.add(j);
            }
            near[i] = by.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /**
     * Whether a straight leg's knee bends forward: as the profile says, else a quadruped's foreleg (a front leg of a body
     * with legs one behind another) forward and every other back. Ranks aren't known yet, so the front is found here.
     */
    private static boolean kneesForward(MotionProfile.Gait gait, int i, List<int[]> legs, List<int[]> chains, float[][] pose) {
        if (gait.knees() != MotionProfile.Knees.AUTO) return gait.knees() == MotionProfile.Knees.FORWARD;
        Matrix4f m = new Matrix4f();
        float[] z = new float[legs.size()];
        for (int j = 0; j < z.length; j++) {
            rest(chains.get(j), pose, m);
            float[] h = pose[legs.get(j)[0]];
            z[j] = m.transformPosition(new Vector3f(h[0], h[1], h[2])).z;
        }
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : z) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min > 1.0F && z[i] < (min + max) / 2;
    }

    /** The bones' rest product (root first): the frame they hand their child, into {@code m}. */
    public static void rest(int[] chain, float[][] pose, Matrix4f m) {
        m.identity();
        for (int b : chain) {
            float[] q = pose[b];
            m.translate(q[0], q[1], q[2]).rotateZYX(q[5], q[4], q[3]);
        }
    }

    /** The far end of the knee bone's own cubes (their box: min xyz, max xyz), out along its longest reach. */
    private static Vector3f tip(float @Nullable [] box) {
        if (box == null || box[0] > box[3]) return new Vector3f(0, 6, 0);
        float[] c = {(box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2};
        int axis = 0;
        float best = -1;
        for (int a = 0; a < 3; a++) {
            float far = Math.max(Math.abs(box[a]), Math.abs(box[a + 3]));
            if (far > best) {
                best = far;
                axis = a;
            }
        }
        c[axis] = Math.abs(box[axis + 3]) >= Math.abs(box[axis]) ? box[axis + 3] : box[axis];
        return new Vector3f(c[0], c[1], c[2]);
    }

    /**
     * The bend (radians) a straight leg's knee hides: how far the thigh's cubes reach past the knee pivot along the
     * thigh, against the shin's half-thickness (a shin pivoting right on the thigh's end face opens a wedge at any bend).
     */
    private static float hides(float @Nullable [] thigh, Vector3f pivot, float @Nullable [] shin, Vector3f tip) {
        if (thigh == null || shin == null) return 0.0F;
        Vector3f along = new Vector3f(pivot).normalize();
        float past = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            float x = (c & 1) == 0 ? thigh[0] : thigh[3], y = (c & 2) == 0 ? thigh[1] : thigh[4], z = (c & 4) == 0 ? thigh[2] : thigh[5];
            past = Math.max(past, x * along.x + y * along.y + z * along.z);
        }
        past -= pivot.length();
        if (past <= 0) return 0.0F;
        // the shin's half-thickness across its own length (its widest side opens first)
        Vector3f ax = new Vector3f(tip).normalize();
        float half = 0;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(ax.get(a)) > 0.7F) continue;
            half = Math.max(half, (shin[a + 3] - shin[a]) / 2);
        }
        return half <= 0 ? Mth.HALF_PI : (float) Math.atan2(past, half);
    }

    /**
     * The legs of a body: each LEG-role bone that hangs from a non-leg and has exactly one LEG-role child, which has at
     * most one leg below it (a foot, held as it stands at rest) with none below that (a two-bone leg, or three). {@code pose}: each bone's rest (x, y, z px; x, y, z rotation radians);
     * {@code box}: each bone's own cubes' box, or null. Null when the body has fewer than two such legs.
     */
    public static @Nullable LegRig of(MotionProfile.Gait gait, LifeBones.Binding b, float[][] pose, IntFunction<float @Nullable []> box) {
        List<LifeBones.Bone> bones = b.bones();
        int count = bones.size();
        List<int[]> legs = new ArrayList<>(), chains = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (b.roles()[i] != Role.LEG) continue;
            int p = bones.get(i).parent();
            if (p >= 0 && b.roles()[p] == Role.LEG) continue;
            int child = -1, kids = 0;
            for (int j = 0; j < count; j++) {
                if (bones.get(j).parent() == i && b.roles()[j] == Role.LEG) {
                    child = j;
                    kids++;
                }
            }
            if (kids != 1) continue;
            // below the knee at most a foot (a tarsus that rides the shin as it stands at rest), with nothing below it
            int foot = -1, feet = 0;
            for (int j = 0; j < count; j++) {
                if (bones.get(j).parent() == child && b.roles()[j] == Role.LEG) {
                    foot = j;
                    feet++;
                }
            }
            if (feet > 1) continue;
            boolean deeper = false;
            for (int j = 0; j < count && foot >= 0; j++) if (bones.get(j).parent() == foot && b.roles()[j] == Role.LEG) deeper = true;
            if (deeper) continue;
            List<Integer> chain = new ArrayList<>();
            for (int k = p; k >= 0; k = bones.get(k).parent()) chain.addFirst(k);
            legs.add(new int[]{i, child, foot});
            chains.add(chain.stream().mapToInt(Integer::intValue).toArray());
        }
        return legs.size() >= 2 ? new LegRig(gait, legs, chains, pose, box) : null;
    }

    // ---- the solve (per frame) --------------------------------------------------------------------------------------

    private final Matrix3f frame = new Matrix3f(), inv = new Matrix3f();
    private final Quaternionf q = new Quaternionf();
    private final Vector3f t = new Vector3f(), d = new Vector3f(), perp = new Vector3f(), kn = new Vector3f(), u = new Vector3f(),
            w = new Vector3f(), hinge = new Vector3f(), third = new Vector3f(), e = new Vector3f(), tt = new Vector3f();

    /**
     * Leg i's rotations that put its foot on the target (model px): {@code toParent} takes model space into the hip's
     * parent frame as it is this frame, ({@code hx, hy, hz}) is the hip's pivot there. Writes the hip's rotation into
     * {@code hipOut} and the knee's into {@code kneeOut}; false if the target sits on the hip. Out of reach, the leg
     * points straight at it. Nothing allocated.
     */
    public boolean solve(int i, Matrix4fc toParent, float hx, float hy, float hz, float tx, float ty, float tz, Matrix3f hipOut, Matrix3f kneeOut) {
        toParent.transformPosition(tx, ty, tz, t);
        d.set(t).sub(hx, hy, hz);
        float dist = d.length();
        if (dist < 1e-3F) return false;
        d.div(dist);
        float a1 = l1[i], a2 = l2[i];
        float dc = Mth.clamp(dist, Math.abs(a1 - a2) + 0.05F, (a1 + a2) * 0.999F);
        float along = (a1 * a1 - a2 * a2 + dc * dc) / (2.0F * dc);
        float off = Mth.sqrt(Math.max(0.0F, a1 * a1 - along * along));
        // the knee goes out on the side it bulged at rest
        perp.set(pole[i]).sub(e.set(d).mul(pole[i].dot(d)));
        if (perp.lengthSquared() < 1e-6F) perp.set(0, -1, 0).sub(e.set(d).mul(-d.y));
        perp.normalize();
        kn.set(d).mul(along).add(e.set(perp).mul(off)); // the knee, from the hip
        u.set(kn).normalize();
        w.set(d).mul(dc).sub(kn).normalize(); // knee to foot
        hinge.set(u).cross(w);
        if (hinge.lengthSquared() < 1e-6F) hinge.set(u).cross(perp);
        hinge.normalize();
        third.set(u).cross(hinge);
        // the hip: the thigh's rest frame turned onto this one, over its rest rotation
        frame.set(u, hinge, third).mul(a0t[i], hipOut).mul(r0[i]);
        // the knee: in the thigh's new frame, its rest direction turned onto the one it needs (about its hinge)
        hipOut.transpose(inv).transform(w, tt);
        q.rotationTo(tip0[i].x, tip0[i].y, tip0[i].z, tt.x, tt.y, tt.z);
        kneeOut.rotation(q).mul(s0[i]);
        return true;
    }

    /**
     * Rigid leg i (as {@link #solve}): the whole leg turns toward the target about a point {@code lift} px above its hip
     * (its top slides fore and aft inside the body, as far as the body has room), then its pivot slides straight up into
     * the body (or a touch down) so the foot stands on the target. Writes the hip's rotation into {@code hipOut} and its new
     * pivot into {@code pivot}; the knee keeps its rest. False if the target sits on the hip. Nothing allocated.
     */
    public boolean solveRigid(int i, Matrix4fc toParent, float hx, float hy, float hz, float tx, float ty, float tz, Matrix3f hipOut, Vector3f pivot) {
        toParent.transformPosition(tx, ty, tz, t);
        Vector3f g = g0[i];
        float k = lift[i];
        d.set(t).sub(hx - g.x * k, hy - g.y * k, hz - g.z * k);
        float far = d.length();
        if (far < 1e-3F) return false;
        d.div(far);
        pivot.set(hx, hy + (d.y - g.y) * k, Mth.clamp(hz + (d.z - g.z) * k, hz - fore[i], hz + aft[i]));
        // straight up or down (the top never slides out of the body's side) to where the leg's length meets the target
        float dx = t.x - pivot.x, dz = t.z - pivot.z, flat = len[i] * len[i] - dx * dx - dz * dz;
        float want = flat > 0 ? t.y - Mth.sqrt(flat) : t.y;
        pivot.y += Mth.clamp(want - pivot.y, -0.5F * len[i], drop[i]);
        d.set(t).sub(pivot);
        float dist = d.length();
        if (dist < 1e-3F) return false;
        d.div(dist);
        q.rotationTo(g.x, g.y, g.z, d.x, d.y, d.z);
        hipOut.rotation(q).mul(r0[i]);
        return true;
    }

    /** The knee's rest rotation (a rigid leg holds it). Not to be changed. */
    Matrix3f restKnee(int i) {
        return s0[i];
    }

    /** For checks: whether the legs are rigid, and how far (radians) leg i's knee can bend before its joint opens. */
    public boolean rigid() {
        return rigid;
    }

    public float hides(int i) {
        return hides[i];
    }

    /** For checks: rigid leg i's length (hip to foot), and how far (px) its pivot may slide forward, back and down. */
    public float length(int i) {
        return len[i];
    }

    public float room(int i, int way) {
        return way < 0 ? fore[i] : way > 0 ? aft[i] : drop[i];
    }

    /** For checks: the knee's pivot in the hip frame, and the shin's tip in the knee frame (px). */
    public Vector3f kneePivot(int i) {
        return new Vector3f(v1[i]);
    }

    /** For checks: the shin's tip from the knee pivot, in the hip frame at rest (px). */
    public Vector3f restShin(int i) {
        return new Vector3f(tip0[i]).mul(l2[i]);
    }

    public Vector3f shinTip(int i) {
        return new Matrix3f(s0[i]).transpose().transform(new Vector3f(tip0[i])).mul(l2[i]);
    }

    public float reach(int i) {
        return l1[i] + l2[i];
    }

    /**
     * How far (px) leg i's hip must drop from rest for its foot to stand at (x, y, z) model px within 97% of the leg's
     * length: a straight leg reaches only a little forward or back at full height, so a long step pulls the hips down.
     */
    public float sink(int i, float x, float y, float z) {
        if (rigid) { // as solveRigid has it: the pivot turned about its point above the hip, the leg and its drop the reach
            float k = lift[i], vx = hipX[i] - gmx[i] * k, vy = hipY[i] - gmy[i] * k, vz = hipZ[i] - gmz[i] * k;
            float ex = x - vx, ey = y - vy, ez = z - vz, el = Mth.sqrt(ex * ex + ey * ey + ez * ez);
            float py = el > 1e-3F ? hipY[i] + (ey / el - gmy[i]) * k : hipY[i];
            float pz = el > 1e-3F ? Mth.clamp(hipZ[i] + (ez / el - gmz[i]) * k, hipZ[i] - fore[i], hipZ[i] + aft[i]) : hipZ[i];
            float dx = x - hipX[i], dz = z - pz, r = 0.97F * len[i];
            float flat = r * r - dx * dx - dz * dz;
            return y - py - (flat > 0 ? Mth.sqrt(flat) : 0.0F) - drop[i];
        }
        float dx = x - hipX[i], dz = z - hipZ[i], r = 0.97F * (l1[i] + l2[i]);
        float flat = r * r - dx * dx - dz * dz;
        return y - hipY[i] - (flat > 0 ? Mth.sqrt(flat) : 0.0F);
    }

    public float fold(int i) {
        return Math.abs(l1[i] - l2[i]);
    }

    public int[] chain(int i) {
        return up[i];
    }

    public int hipBone(int i) {
        return hip[i];
    }
}
