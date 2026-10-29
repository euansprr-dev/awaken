package dev.awaken.mobs.client.life;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A model's parts for its {@link LegRig} (SPEC §23, "Motion"): every frame, after every other layer, each leg is posed
 * so its foot reaches the spot {@link Legs} holds for it, blended over the clips' pose by the feet's weight (a rigid
 * leg's pivot slides with it, and its knee always keeps its rest). Made once per model; nothing allocated per frame.
 */
final class LegParts {
    final LegRig rig;
    private final ModelPart root;
    private final ModelPart[] hip, knee;
    private final ModelPart[][] up;
    private final Matrix4f fk = new Matrix4f(), inv = new Matrix4f();
    private final Matrix3f hr = new Matrix3f(), kr = new Matrix3f();
    private final Quaternionf q = new Quaternionf(), qc = new Quaternionf();
    private final Vector3f e = new Vector3f(), pv = new Vector3f();
    private ModelPart @Nullable [] last;

    private LegParts(LegRig rig, ModelPart root, ModelPart[] parts) {
        this.rig = rig;
        this.root = root;
        hip = new ModelPart[rig.n];
        knee = new ModelPart[rig.n];
        up = new ModelPart[rig.n][];
        for (int i = 0; i < rig.n; i++) {
            hip[i] = parts[rig.hip[i]];
            knee[i] = parts[rig.knee[i]];
            int[] chain = rig.up[i];
            // legs on one body share their parent: one array, so the solve reuses its product
            ModelPart[] same = null;
            for (int j = 0; j < i && same == null; j++) if (java.util.Arrays.equals(rig.up[j], chain)) same = up[j];
            if (same == null) {
                same = new ModelPart[chain.length];
                for (int k = 0; k < chain.length; k++) same[k] = parts[chain[k]];
            }
            up[i] = same;
        }
    }

    /** The legs of a model's part tree (bones by binding index), or null when it has fewer than two. */
    static @Nullable LegParts of(MotionProfile.Gait gait, LifeBones.Binding b, ModelPart root, ModelPart[] parts) {
        float[][] pose = new float[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            PartPose p = parts[i].getInitialPose();
            pose[i] = new float[]{p.x(), p.y(), p.z(), p.xRot(), p.yRot(), p.zRot()};
        }
        LegRig rig = LegRig.of(gait, b, pose, i -> box(parts[i]));
        return rig == null ? null : new LegParts(rig, root, parts);
    }

    /** The part's own cubes' box in its frame (min xyz, max xyz), or null if it has none. */
    private static float @Nullable [] box(ModelPart part) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (!path.isEmpty()) return; // a child's cubes (a foot) are not the shin
            box[0] = Math.min(box[0], cube.minX);
            box[1] = Math.min(box[1], cube.minY);
            box[2] = Math.min(box[2], cube.minZ);
            box[3] = Math.max(box[3], cube.maxX);
            box[4] = Math.max(box[4], cube.maxY);
            box[5] = Math.max(box[5], cube.maxZ);
        });
        return box[0] > box[3] ? null : box;
    }

    void apply(Legs legs) {
        float all = legs.weight;
        // a rigid leg has no knee, whatever its clips do (a bent shin on a blocky leg opens its joint)
        if (rig.rigid) for (int i = 0; i < rig.n; i++) set(knee[i], rig.restKnee(i), 1.0F);
        if (all <= 0.001F) return;
        last = null;
        for (int i = 0; i < rig.n; i++) {
            int r = rig.rank[i], pairs = rig.gait.rearPairs();
            float wgt = r < pairs ? all * (1.0F - legs.rear) : r == pairs ? all * (1.0F - legs.rear2) : all;
            if (wgt <= 0.001F) continue;
            ModelPart[] chain = up[i];
            if (chain != last) {
                fk.identity();
                transform(root);
                for (ModelPart p : chain) transform(p);
                fk.invertAffine(inv);
                last = chain;
            }
            ModelPart h = hip[i], k = knee[i];
            if (rig.rigid) {
                if (!rig.solveRigid(i, inv, h.x, h.y, h.z, legs.tx[i], legs.ty[i], legs.tz[i], hr, pv)) continue;
                set(h, hr, wgt);
                h.x += (pv.x - h.x) * wgt;
                h.y += (pv.y - h.y) * wgt;
                h.z += (pv.z - h.z) * wgt;
                continue;
            }
            if (!rig.solve(i, inv, h.x, h.y, h.z, legs.tx[i], legs.ty[i], legs.tz[i], hr, kr)) continue;
            set(h, hr, wgt);
            set(k, kr, wgt);
        }
    }

    private void transform(ModelPart p) {
        fk.translate(p.x, p.y, p.z).rotateZYX(p.zRot, p.yRot, p.xRot);
        if (p.xScale != 1.0F || p.yScale != 1.0F || p.zScale != 1.0F) fk.scale(p.xScale, p.yScale, p.zScale);
    }

    private void set(ModelPart p, Matrix3f m, float wgt) {
        if (wgt >= 0.999F) {
            m.getEulerAnglesZYX(e);
        } else {
            qc.rotationZYX(p.zRot, p.yRot, p.xRot);
            q.setFromNormalized(m);
            qc.slerp(q, wgt);
            qc.getEulerAnglesZYX(e);
        }
        p.xRot = e.x;
        p.yRot = e.y;
        p.zRot = e.z;
    }
}
