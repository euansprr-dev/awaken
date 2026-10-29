package dev.awaken.mobs.client.life;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.LifeBones.Role;
import dev.awaken.mobs.client.life.LifeProfile.Idle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The life layer bound to one model (SPEC §23): its bones by role (from their names, {@link LifeBones}), where each
 * springy part points and how long it is (read from the baked cubes, so a cane standing up and a mane hanging down
 * each lag the right way), its temperament, and which idle fidgets its bones allow. Made once per model; every frame
 * {@link #apply} adds a creature's {@link Life} on top of whatever its clips and pose code set.
 */
public final class LifeRig {
    /** A body with no bones of ours (a vanilla model): only the root-level motions (stagger, a boss's fall). */
    public static LifeRig rootOnly(LifeProfile p) {
        return new LifeRig(p);
    }

    final LifeProfile p;
    final boolean bones;
    final LifeBones.@Nullable Binding binding;
    final @Nullable ModelPart body, head, neck, jaw;
    final ModelPart[] eyes, lids, chests, hind, arms;
    final float[] lidX, lidY, lidZ;
    final boolean[] lidRot;
    final int[] hindSide;
    // springs
    final int n;
    final ModelPart[] sp;
    final float[] dx, dy, dz, invLen, w2, damp, gain, maxAngle;
    final int[] chain, driver, role;
    final ModelPart[] drivers;
    /** The body's motions (stagger, lean, the step's dip) go on the pose stack: its body bone doesn't carry the head. */
    final boolean rootMotion;
    /** The model plays an authored idle (hero rigs): the procedural breath only adds what exertion brings. */
    final boolean idleClip;
    final boolean[] can = new boolean[Idle.values().length];
    final boolean hasEars, hasTail;
    /** Seconds into the death clip when the body comes to rest on the ground (its thud), or 0 when there's no clip. */
    final float impact;
    /** The body's motion profile (SPEC §23, "Motion") and its legs bound for the feet, or null: it moves as before. */
    final @Nullable MotionProfile motion;
    final @Nullable LegParts legs;

    private LifeRig(LifeProfile p) {
        this.p = p;
        this.bones = false;
        this.binding = null;
        body = head = neck = jaw = null;
        eyes = lids = chests = hind = arms = new ModelPart[0];
        lidX = lidY = lidZ = new float[0];
        lidRot = new boolean[0];
        hindSide = new int[0];
        n = 0;
        sp = new ModelPart[0];
        dx = dy = dz = invLen = w2 = damp = gain = maxAngle = new float[0];
        chain = driver = role = new int[0];
        drivers = new ModelPart[0];
        rootMotion = true;
        idleClip = false;
        hasEars = hasTail = false;
        impact = 0;
        motion = null;
        legs = null;
    }

    /**
     * Binds a model's part tree. {@code rig} (hero rigs) tells how its eyelids close (from its clips); {@code idleClip}
     * says the model already breathes through an authored idle.
     */
    public static LifeRig of(ModelPart root, LifeProfile p, @Nullable HeroRig rig, boolean idleClip) {
        // every part with geometry, by path, with its cubes' centre and size in model space
        Map<String, float[]> geo = new LinkedHashMap<>();
        PoseStack ps = new PoseStack();
        Vector3f c = new Vector3f();
        root.visit(ps, (pose, path, index, cube) -> {
            float vol = Math.max(0.01F, (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ));
            pose.pose().transformPosition((cube.minX + cube.maxX) / 32.0F, (cube.minY + cube.maxY) / 32.0F, (cube.minZ + cube.maxZ) / 32.0F, c);
            float ext = Math.max(cube.maxX - cube.minX, Math.max(cube.maxY - cube.minY, cube.maxZ - cube.minZ));
            float[] g = geo.computeIfAbsent(path, k -> new float[5]);
            g[0] += c.x * vol;
            g[1] += c.y * vol;
            g[2] += c.z * vol;
            g[3] += vol;
            g[4] = Math.max(g[4], ext);
        });
        // the bones: every path seen, parents first (an empty group appears through its children)
        List<String> paths = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        for (String path : geo.keySet()) addPath(path, paths, index);
        List<LifeBones.Bone> bones = new ArrayList<>(paths.size());
        ModelPart[] parts = new ModelPart[paths.size()];
        float[][] pivots = new float[paths.size()][];
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            int cut = path.lastIndexOf('/');
            String name = path.substring(cut + 1);
            Integer parent = cut > 0 ? index.get(path.substring(0, cut)) : null;
            bones.add(new LifeBones.Bone(name, parent == null ? -1 : parent));
            parts[i] = part(root, path);
            pivots[i] = pivot(root, path);
        }
        return new LifeRig(p, LifeBones.bind(bones), root, parts, pivots, geo, paths, rig, idleClip);
    }

    private static void addPath(String path, List<String> paths, Map<String, Integer> index) {
        if (path.isEmpty() || index.containsKey(path)) return;
        int cut = path.lastIndexOf('/');
        if (cut > 0) addPath(path.substring(0, cut), paths, index);
        index.put(path, paths.size());
        paths.add(path);
    }

    private static ModelPart part(ModelPart root, String path) {
        ModelPart p = root;
        for (String seg : path.substring(1).split("/")) p = p.getChild(seg);
        return p;
    }

    /** The part's pivot in model space (blocks), from the root's rest pose down its chain. */
    private static float[] pivot(ModelPart root, String path) {
        PoseStack ps = new PoseStack();
        root.translateAndRotate(ps);
        ModelPart p = root;
        for (String seg : path.substring(1).split("/")) {
            p = p.getChild(seg);
            p.translateAndRotate(ps);
        }
        Vector3f v = ps.last().pose().transformPosition(0, 0, 0, new Vector3f());
        return new float[]{v.x, v.y, v.z};
    }

    private LifeRig(LifeProfile p, LifeBones.Binding b, ModelPart root, ModelPart[] parts, float[][] pivots, Map<String, float[]> geo,
                    List<String> paths, @Nullable HeroRig rig, boolean idleClip) {
        this.p = p;
        this.bones = true;
        this.binding = b;
        this.idleClip = idleClip;
        body = b.body() >= 0 ? parts[b.body()] : null;
        head = b.head() >= 0 ? parts[b.head()] : null;
        neck = b.neck() >= 0 ? parts[b.neck()] : null;
        jaw = b.jaw() >= 0 ? parts[b.jaw()] : null;
        eyes = pick(parts, b.eyes());
        lids = pick(parts, b.lids());
        chests = pick(parts, b.chests());
        hind = pick(parts, b.hindLegs());
        arms = pick(parts, b.arms());
        hindSide = new int[b.hindLegs().length];
        for (int i = 0; i < hindSide.length; i++) hindSide[i] = b.sides()[b.hindLegs()[i]];
        // eyelids close the way the rig's own clips close them (the furthest any clip moves the lid), else slide down
        lidX = new float[lids.length];
        lidY = new float[lids.length];
        lidZ = new float[lids.length];
        lidRot = new boolean[lids.length];
        for (int i = 0; i < lids.length; i++) {
            String name = b.bones().get(b.lids()[i]).name();
            lidY[i] = 1.5F;
            if (rig == null) continue;
            float best = 0;
            for (HeroRig.Clip clip : rig.clips.values()) {
                for (HeroRig.Track t : clip.tracks()) {
                    if (!t.bone().equals(name) || t.target() == HeroRig.Target.SCALE) continue;
                    for (HeroRig.Key k : t.keys()) {
                        boolean rot = t.target() == HeroRig.Target.ROT;
                        float m = (Math.abs(k.x()) + Math.abs(k.y()) + Math.abs(k.z())) * (rot ? 0.05F : 1.0F);
                        if (m <= best) continue;
                        best = m;
                        lidRot[i] = rot;
                        float s = rot ? Mth.DEG_TO_RAD : 1.0F;
                        lidX[i] = k.x() * s;
                        lidY[i] = rot ? k.y() * s : -k.y(); // posVec flips y, like KeyframeAnimations.posVec
                        lidZ[i] = k.z() * s;
                    }
                }
            }
        }
        // body motions go on the bone only if that bone carries the head (hero rigs), else on the pose stack
        boolean carries = false;
        if (b.body() >= 0 && b.head() >= 0) {
            for (int k = b.head(); k >= 0; k = b.bones().get(k).parent()) if (k == b.body()) carries = true;
        }
        // a headless body that walks on the motion layer's feet (the weaver queen's face is on its thorax) carries it all
        if (b.body() >= 0 && b.head() < 0 && rig != null && MotionProfile.of(rig.id) != null) carries = true;
        rootMotion = !carries;
        // springs
        int[] s = b.springs();
        List<Integer> keep = new ArrayList<>();
        for (int k = 0; k < s.length; k++) {
            Role r = b.roles()[s[k]];
            if (r == Role.ARM && p.arms() <= 0) continue;
            if (!geo.containsKey(paths.get(s[k]))) continue; // an empty group: nothing to swing
            keep.add(k);
        }
        n = keep.size();
        sp = new ModelPart[n];
        dx = new float[n];
        dy = new float[n];
        dz = new float[n];
        invLen = new float[n];
        w2 = new float[n];
        damp = new float[n];
        gain = new float[n];
        maxAngle = new float[n];
        chain = new int[n];
        driver = new int[n];
        role = new int[n];
        Map<Integer, Integer> remap = new HashMap<>();
        List<ModelPart> drv = new ArrayList<>();
        Map<Integer, Integer> drvIndex = new HashMap<>();
        boolean ears = false, tail = false;
        for (int j = 0; j < n; j++) {
            int k = keep.get(j);
            int bone = s[k];
            remap.put(k, j);
            Role r = b.roles()[bone];
            ears |= r == Role.EAR;
            tail |= r == Role.TAIL;
            sp[j] = parts[bone];
            role[j] = r.ordinal();
            float[] g = geo.get(paths.get(bone));
            float[] pv = pivots[bone];
            float cx = g[0] / g[3] - pv[0], cy = g[1] / g[3] - pv[1], cz = g[2] / g[3] - pv[2];
            float len = Mth.sqrt(cx * cx + cy * cy + cz * cz) * 16.0F; // px from the pivot to the part's middle
            if (len < 0.5F) { // a part centred on its pivot (a fin, a skirt plate): it hangs the way its role says
                float[] d = rest(r, b.sides()[bone]);
                cx = d[0];
                cy = d[1];
                cz = d[2];
                len = Math.max(1.0F, g[4] * 0.5F);
            }
            float il = 1.0F / Mth.sqrt(cx * cx + cy * cy + cz * cz);
            dx[j] = cx * il;
            dy[j] = cy * il;
            dz[j] = cz * il;
            float lever = Mth.clamp(len * 2.0F, 2.0F, 28.0F); // the tip is about twice as far as the middle
            invLen[j] = 1.0F / lever;
            int depth = b.springDepth()[k];
            // a pendulum: longer parts swing slower; links further down a chain a little slower still
            float hz = p.springHz() * roleHz(r) * Mth.clamp(Mth.sqrt(10.0F / lever), 0.6F, 1.6F) * (float) Math.pow(0.88, depth);
            float w = Mth.TWO_PI * hz;
            w2[j] = w * w;
            damp[j] = 2.0F * p.springDamping() * (r == Role.ARM ? 1.3F : 1.0F) * w;
            gain[j] = p.springGain() * roleGain(r) * (r == Role.ARM ? p.arms() : 1.0F);
            maxAngle[j] = roleMax(r);
            int pk = b.springParent()[k];
            chain[j] = pk >= 0 && remap.containsKey(pk) ? remap.get(pk) : -1;
            int parentBone = b.bones().get(bone).parent();
            if (chain[j] < 0 && parentBone >= 0) {
                driver[j] = drvIndex.computeIfAbsent(parentBone, x -> {
                    drv.add(parts[x]);
                    return drv.size() - 1;
                });
            } else {
                driver[j] = -1;
            }
        }
        drivers = drv.toArray(new ModelPart[0]);
        hasEars = ears;
        hasTail = tail;
        impact = rig != null && b.body() >= 0 ? landing(rig, b.bones().get(b.body()).name()) : 0;
        // the feet ride a body that carries its head (its body bone moves over them); else the pose stack would carry them
        motion = rig != null ? MotionProfile.of(rig.id) : null;
        legs = motion != null && motion.gait() != null && !rootMotion ? LegParts.of(motion.gait(), b, root, parts) : null;
        // which fidgets this body can play
        boolean springs = n > 0, limbs = countRole(b, Role.LEG) >= 2;
        can[Idle.LOOK.ordinal()] = head != null;
        can[Idle.SNIFF.ordinal()] = head != null;
        can[Idle.FLICK.ordinal()] = springs;
        can[Idle.SHAKE.ordinal()] = body != null && (springs || head != null);
        can[Idle.STRETCH.ordinal()] = body != null && head != null && limbs;
        can[Idle.YAWN.ordinal()] = jaw != null;
        can[Idle.SCRATCH.ordinal()] = head != null && hind.length > 0 && this.legs == null; // planted feet don't kick
        can[Idle.PERK.ordinal()] = head != null;
        can[Idle.SETTLE.ordinal()] = body != null || rootMotion;
    }

    /**
     * When the fall lands: the first moment the death clip's body has sunk 85% of the way it will (its position track),
     * or the last third of the clip if the body only tilts.
     */
    static float landing(HeroRig rig, String body) {
        HeroRig.Clip death = rig.clip("death");
        if (death == null) return 0;
        for (HeroRig.Track t : death.tracks()) {
            if (!t.bone().equals(body) || t.target() != HeroRig.Target.POS) continue;
            float max = 0;
            for (HeroRig.Key k : t.keys()) max = Math.max(max, Math.abs(k.y()));
            if (max < 1.0F) break;
            HeroRig.Key prev = null;
            for (HeroRig.Key k : t.keys()) {
                if (Math.abs(k.y()) >= 0.85F * max) {
                    if (prev == null || Math.abs(k.y() - prev.y()) < 1e-4F) return k.t();
                    float u = (0.85F * max - Math.abs(prev.y())) / (Math.abs(k.y()) - Math.abs(prev.y()));
                    return prev.t() + (k.t() - prev.t()) * Mth.clamp(u, 0.0F, 1.0F);
                }
                prev = k;
            }
        }
        return death.length() * 0.66F;
    }

    private static int countRole(LifeBones.Binding b, Role r) {
        int c = 0;
        for (Role x : b.roles()) if (x == r) c++;
        return c;
    }

    private static ModelPart[] pick(ModelPart[] parts, int[] idx) {
        ModelPart[] out = new ModelPart[idx.length];
        for (int i = 0; i < idx.length; i++) out[i] = parts[idx[i]];
        return out;
    }

    /** Which way a part of this role points when its cubes don't say (model space: +y down, -z forward, +x left). */
    private static float[] rest(Role r, int side) {
        return switch (r) {
            case EAR -> new float[]{0.3F * side, -1, 0};
            case ANTENNA -> new float[]{0.2F * side, -0.6F, -0.8F};
            case TAIL -> new float[]{0, 0, 1};
            case WING -> new float[]{side == 0 ? 1 : side, 0, 0.2F};
            default -> new float[]{0, 1, 0};
        };
    }

    private static float roleHz(Role r) {
        return switch (r) {
            case EAR -> 1.2F;
            case ANTENNA -> 1.6F;
            case TAIL -> 0.9F;
            case DANGLE -> 0.8F;
            case WING -> 1.1F;
            case FIN -> 1.3F;
            case ARM -> 0.7F;
            default -> 1.0F;
        };
    }

    private static float roleGain(Role r) {
        return switch (r) {
            case ANTENNA -> 0.8F;
            case WING -> 0.5F;
            case FIN -> 0.6F;
            case ARM -> 0.7F;
            default -> 1.0F;
        };
    }

    private static float roleMax(Role r) {
        return switch (r) {
            case EAR -> 0.5F;
            case WING, ARM -> 0.35F;
            case DANGLE -> 0.7F;
            default -> 0.6F;
        };
    }

    public LifeProfile profile() {
        return p;
    }

    public int springs() {
        return n;
    }

    // ---- per frame --------------------------------------------------------------------------------------------------

    /**
     * Adds a creature's life to the pose its model just set (call at the end of setupAnim; it may run several times a
     * frame, once per layer, and adds the same offsets each time). Nothing here allocates.
     */
    public void apply(@Nullable Life life, net.minecraft.client.renderer.entity.state.LivingEntityRenderState s) {
        if (!bones || life == null || life.rig != this) return;
        if (life.springFrame != life.frame) {
            life.integrate(this);
            life.springFrame = life.frame;
        }
        float b = life.breath * life.depth;
        // breathing: the chest swells, the head lifts a little on the in-breath
        if (body != null) {
            float a = (idleClip ? 0.35F + 0.65F * Math.min(1.0F, life.exertion) : 1.0F) * b;
            body.xScale += 0.012F * a;
            body.zScale += 0.006F * a;
            body.yScale += 0.008F * a;
            if (!rootMotion) { // else the renderer puts these on the pose stack (Life.rootPose)
                body.y += life.dip;
                body.xRot += life.bodyPitch;
                body.zRot += life.bodyRoll;
            }
        }
        // the motion profile: the body sits on its feet, rides the ground and the step, turns a little toward its look,
        // and the head holds level against the gait
        Legs lg = life.legs;
        float levelPitch = 0, levelRoll = life.swayRoll;
        if (body != null && !rootMotion && motion != null) {
            MotionProfile.Body mb = motion.body();
            if (lg != null) {
                float wgt = lg.weight;
                float pitch = lg.groundPitch * mb.slope() * wgt, roll = (lg.groundRoll * mb.slope() + lg.gaitRoll) * wgt;
                body.y += (lg.rig.gait.crouch() + life.creep + lg.groundY + lg.gaitY + lg.vault) * wgt;
                body.x += life.shiftX * wgt;
                body.z += life.shiftZ * wgt;
                // leaning into the drift: the side it moves over sinks a little
                pitch += life.shiftZ * 0.02F * wgt;
                roll += life.shiftX * 0.03F * wgt;
                body.xRot += pitch;
                body.zRot += roll;
                levelPitch += pitch;
                levelRoll += roll;
            }
            body.yRot += life.twist;
            if (head != null && mb.headLevel() > 0) {
                head.xRot -= levelPitch * mb.headLevel();
                head.zRot -= levelRoll * mb.headLevel();
            }
            // a grazer's head bobs down as a forefoot lifts and comes up as it plants (the neck carries it when it has one)
            if (lg != null && mb.nod() != 0) {
                ModelPart n = neck != null ? neck : head;
                if (n != null) n.xRot += mb.nod() * lg.foreLift * lg.weight;
            }
        }
        for (ModelPart c : chests) {
            c.xScale += 0.04F * b;
            c.yScale += 0.03F * b;
            c.zScale += 0.04F * b;
        }
        if (head != null) {
            head.xRot += -0.018F * b + life.headNod;
            head.zRot += life.headRoll;
        }
        if (neck != null) neck.xRot += life.neckPitch;
        if (jaw != null) jaw.xRot += life.jawOpen;
        // the eyes lead the head toward what it looks at; with no eyelids they squint shut to blink
        for (ModelPart e : eyes) {
            e.yRot += life.eyeYaw;
            e.xRot += life.eyePitch;
            if (lids.length == 0 && life.closed > 0) e.yScale *= 1.0F - 0.85F * life.closed;
        }
        for (int i = 0; i < lids.length; i++) {
            float k = life.closed;
            if (k <= 0) continue;
            if (lidRot[i]) {
                lids[i].xRot += lidX[i] * k;
                lids[i].yRot += lidY[i] * k;
                lids[i].zRot += lidZ[i] * k;
            } else {
                lids[i].x += lidX[i] * k;
                lids[i].y += lidY[i] * k;
                lids[i].z += lidZ[i] * k;
            }
        }
        // a scratch: the hind leg on that side kicks at its ear
        if (life.scratch != 0) {
            for (int i = 0; i < hind.length; i++) if (hindSide[i] == life.scratchSide || hindSide[i] == 0) hind[i].xRot += life.scratch;
        }
        // the springs: ears, tails, manes, wings and everything that dangles
        float[] ax = life.ax, ay = life.ay, az = life.az;
        for (int j = 0; j < n; j++) {
            ModelPart q = sp[j];
            q.xRot += ax[j];
            q.yRot += ay[j];
            q.zRot += az[j];
        }
        // last: the legs reach for where the feet stand, whatever the body above them did
        if (legs != null && lg != null && lg.rig == legs.rig) legs.apply(lg);
    }
}
