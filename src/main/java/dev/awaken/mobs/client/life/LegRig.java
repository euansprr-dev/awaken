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
        float[] hipX = new float[n], hipZ = new float[n];
        Matrix4f m = new Matrix4f();
        for (int i = 0; i < n; i++) {
            hip[i] = legs.get(i)[0];
            knee[i] = legs.get(i)[1];
            float[] h = pose[hip[i]], k = pose[knee[i]];
            r0[i] = new Matrix3f().rotationZYX(h[5], h[4], h[3]);
            s0[i] = new Matrix3f().rotationZYX(k[5], k[4], k[3]);
            v1[i] = new Vector3f(k[0], k[1], k[2]);
            Vector3f tip = tip(box.apply(knee[i]));
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
            pole[i] = bulge.lengthSquared() > 0.01F ? bulge.normalize() : new Vector3f(0, -1, 0);
            Vector3f hinge = new Vector3f(u).cross(w);
            if (hinge.lengthSquared() < 1e-4F) hinge.set(u).cross(pole[i]);
            hinge.normalize();
            Vector3f third = new Vector3f(u).cross(hinge);
            a0t[i] = new Matrix3f().set(u, hinge, third).transpose();
            // the foot and the hip at rest in model space
            rest(up[i], pose, m);
            Vector3f footM = m.transformPosition(new Vector3f(ft));
            Vector3f hipM = m.transformPosition(new Vector3f(hp));
            hipX[i] = hipM.x;
            hipZ[i] = hipM.z;
            side[i] = hipM.x > 0.01F ? 1 : hipM.x < -0.01F ? -1 : (footM.x >= 0 ? 1 : -1);
            homeX[i] = footM.x;
            homeY[i] = 24.0F;
            homeZ[i] = footM.z;
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
        for (int i = 0; i < n; i++) {
            offRipple[i] = (side[i] < 0 ? 0.5F : 0.0F) + (perSide - 1 - rank[i]) * (0.5F / perSide);
            offAlternate[i] = ((rank[i] + (side[i] < 0 ? 1 : 0)) % 2) * 0.5F;
            reachSum += Mth.sqrt(homeX[i] * homeX[i] + homeZ[i] * homeZ[i]) / 16.0F;
        }
        turnRadius = n > 0 ? reachSum / n : 0.5F;
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
     * The legs of a body: each LEG-role bone that hangs from a non-leg and has exactly one LEG-role child, which has no
     * leg below it (a two-bone leg). {@code pose}: each bone's rest (x, y, z px; x, y, z rotation radians);
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
            boolean deeper = false;
            for (int j = 0; j < count; j++) if (bones.get(j).parent() == child && b.roles()[j] == Role.LEG) deeper = true;
            if (deeper) continue;
            List<Integer> chain = new ArrayList<>();
            for (int k = p; k >= 0; k = bones.get(k).parent()) chain.addFirst(k);
            legs.add(new int[]{i, child});
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

    /** For checks: the knee's pivot in the hip frame, and the shin's tip in the knee frame (px). */
    public Vector3f kneePivot(int i) {
        return new Vector3f(v1[i]);
    }

    public Vector3f shinTip(int i) {
        return new Matrix3f(s0[i]).transpose().transform(new Vector3f(tip0[i])).mul(l2[i]);
    }

    public float reach(int i) {
        return l1[i] + l2[i];
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
