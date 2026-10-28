package dev.awaken.mobs.client.model;

import dev.awaken.Awaken;
import dev.awaken.mobs.client.anim.HeroRenderState;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.life.LifeProfile;
import dev.awaken.mobs.client.life.LifeRig;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One body for every hero rig (the named beasts, the Hollow Owl, the Bogwick): the mesh is built from
 * {@link HeroRig} and every clip is baked into a vanilla {@link KeyframeAnimation}. setupAnim layers them like a
 * small animation graph: idle breathing, a distance-locked walk that blends into the run (or a flight loop), head look
 * toward what it watches, the cue clip (a move's wind-up, its strike, a roar, a warning), the melee bite, the flinch,
 * and the fall, which the live pose crossfades into. The life layer (SPEC §23) goes on top: the creature's own idle
 * clock (so a herd never breathes in step, faster when winded), eased graph weights, springs, blinks, the slow head.
 */
public final class HeroModel extends EntityModel<HeroRenderState> {
    private final HeroRig rig;
    private final List<KeyframeAnimation> baked = new ArrayList<>();
    private final List<HeroRig.Clip> clips = new ArrayList<>();
    private final Map<String, Integer> clipIndex = new HashMap<>();
    private final ModelPart[] lookParts;
    private final float[] lookYaw, lookPitch;
    private final int idle, walk, run, fly, air, death, hurt, bite, angry;
    private final LifeRig life;

    public HeroModel(ModelPart root, HeroRig rig) {
        super(root, rig.translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
        this.rig = rig;
        for (HeroRig.Clip c : rig.clips.values()) {
            clipIndex.put(c.name(), baked.size());
            clips.add(c);
            baked.add(definition(c).bake(root)); // throws if a bone is missing, like any vanilla model
        }
        var lookup = root.createPartLookup();
        lookParts = new ModelPart[rig.look.size()];
        lookYaw = new float[lookParts.length];
        lookPitch = new float[lookParts.length];
        for (int i = 0; i < lookParts.length; i++) {
            HeroRig.Look l = rig.look.get(i);
            lookParts[i] = lookup.apply(l.bone());
            lookYaw[i] = l.yaw();
            lookPitch[i] = l.pitch();
        }
        idle = clip("idle");
        walk = clip("walk");
        run = clip("run");
        fly = clip("fly");
        air = clip("air");
        death = clip("death");
        hurt = clip("hurt");
        bite = clip("bite");
        angry = clip("angry");
        life = LifeRig.of(root, profile(rig.id), rig, idle >= 0);
        legs = new Leg[]{Leg.of(rig, lookup, "l"), Leg.of(rig, lookup, "r")};
    }

    /**
     * A kit body's leg (SPEC §34.4: the plans name them thigh_*, shin_*, foot_*), for planting a foot on the ground under
     * it with two-bone IK in the leg's own plane; null when the rig has no such leg.
     */
    private record Leg(ModelPart thigh, ModelPart shin, @org.jspecify.annotations.Nullable ModelPart foot, float upper, float lower) {
        static @org.jspecify.annotations.Nullable Leg of(HeroRig rig, java.util.function.Function<String, ModelPart> lookup, String side) {
            int t = rig.bone("thigh_" + side), sh = rig.bone("shin_" + side), f = rig.bone("foot_" + side);
            if (t < 0 || sh < 0) return null;
            HeroRig.Bone shin = rig.bones.get(sh);
            float upper = (float) Math.sqrt(shin.py() * shin.py() + shin.pz() * shin.pz());
            float lower;
            if (f >= 0) {
                HeroRig.Bone foot = rig.bones.get(f);
                lower = (float) Math.sqrt(foot.py() * foot.py() + foot.pz() * foot.pz());
            } else {
                lower = upper;
            }
            ModelPart tp = lookup.apply("thigh_" + side), sp = lookup.apply("shin_" + side);
            if (tp == null || sp == null || upper < 0.5F || lower < 0.5F) return null;
            return new Leg(tp, sp, f >= 0 ? lookup.apply("foot_" + side) : null, upper, lower);
        }

        /** Lifts the ankle {@code up} pixels by bending the knee (forward) and the hip; the foot stays level. */
        void reach(float up) {
            if (up < 0.25F) return;
            float a1 = thigh.xRot, a2 = shin.xRot;
            // the ankle in the hip's frame (y down, z back), from the angles the clips left
            float ky = upper * Mth.cos(a1), kz = upper * Mth.sin(a1);
            float fy = ky + lower * Mth.cos(a1 + a2), fz = kz + lower * Mth.sin(a1 + a2);
            float ty = fy - up, tz = fz;
            float d = Math.min((float) Math.sqrt(ty * ty + tz * tz), (upper + lower) * 0.999F);
            if (d < 0.1F) return;
            float knee = (float) Math.acos(Mth.clamp((upper * upper + lower * lower - d * d) / (2 * upper * lower), -1, 1));
            float hip = (float) Math.acos(Mth.clamp((upper * upper + d * d - lower * lower) / (2 * upper * d), -1, 1));
            float phi = (float) Math.atan2(tz, ty);
            float n1 = phi - hip, n2 = Mth.PI - knee;
            thigh.xRot = n1;
            shin.xRot = n2;
            if (foot != null) foot.xRot -= (n1 + n2) - (a1 + a2);
        }
    }

    private final Leg[] legs;

    /** The temperament of the creature a rig belongs to (its bestiary role), or "default". */
    public static LifeProfile profile(String id) {
        dev.awaken.mobs.def.MobDef d = dev.awaken.mobs.def.Bestiary.get(id);
        if (d == null) return LifeProfile.temperament("default");
        return LifeProfile.of(id, d.fauna != null ? d.fauna.name() : null, d.tier.name());
    }

    public LifeRig life() {
        return life;
    }

    public static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(Awaken.id("hero_" + id), "main");
    }

    public HeroRig rig() {
        return rig;
    }

    /** The clip slot for a name, or -1. */
    public int clip(String name) {
        Integer i = clipIndex.get(name);
        return i == null ? -1 : i;
    }

    public float length(int clip) {
        return clip < 0 ? 0.0F : clips.get(clip).length();
    }

    // ---- building ---------------------------------------------------------------------------------------------------

    public static LayerDefinition create(HeroRig rig) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition[] parts = new PartDefinition[rig.bones.size()];
        for (int i = 0; i < parts.length; i++) {
            HeroRig.Bone b = rig.bones.get(i);
            CubeListBuilder cubes = CubeListBuilder.create();
            for (HeroRig.Cube c : b.cubes()) {
                cubes.texOffs(c.u(), c.v()).mirror(c.mirror())
                        .addBox(c.x(), c.y(), c.z(), c.w(), c.h(), c.d(), c.inflate() == 0 ? CubeDeformation.NONE : new CubeDeformation(c.inflate()));
            }
            PartDefinition parent = b.parent() < 0 ? mesh.getRoot() : parts[b.parent()];
            parts[i] = parent.addOrReplaceChild(b.name(), cubes, PartPose.offsetAndRotation(b.px(), b.py(), b.pz(), b.rx(), b.ry(), b.rz()));
        }
        return LayerDefinition.create(mesh, rig.texW, rig.texH);
    }

    private static AnimationDefinition definition(HeroRig.Clip c) {
        AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(c.length());
        if (c.loop()) b.looping();
        for (HeroRig.Track t : c.tracks()) {
            Keyframe[] keys = new Keyframe[t.keys().size()];
            for (int k = 0; k < keys.length; k++) {
                HeroRig.Key key = t.keys().get(k);
                Vector3f v = switch (t.target()) {
                    case POS -> KeyframeAnimations.posVec(key.x(), key.y(), key.z());
                    case ROT -> KeyframeAnimations.degreeVec(key.x(), key.y(), key.z());
                    case SCALE -> KeyframeAnimations.scaleVec(key.x(), key.y(), key.z());
                };
                keys[k] = new Keyframe(key.t(), v, key.catmull() ? AnimationChannel.Interpolations.CATMULLROM : AnimationChannel.Interpolations.LINEAR);
            }
            AnimationChannel.Target target = switch (t.target()) {
                case POS -> AnimationChannel.Targets.POSITION;
                case ROT -> AnimationChannel.Targets.ROTATION;
                case SCALE -> AnimationChannel.Targets.SCALE;
            };
            b.addAnimation(t.bone(), new AnimationChannel(target, keys));
        }
        return b.build();
    }

    // ---- animating --------------------------------------------------------------------------------------------------

    private void play(int clip, float seconds, float weight) {
        if (clip >= 0 && weight > 0.001F) baked.get(clip).apply((long) (seconds * 1000.0F), Math.min(1.0F, weight));
    }

    /** Seconds a clip that just ended takes to let go of its last pose (no pop at its end). */
    static final float LET_GO = 0.2F;

    /** Plays a one-shot clip at {@code time}, holding its last frame while it fades for {@link #LET_GO} after it ends. */
    private void once(int clip, float time, float weight) {
        if (clip < 0 || time < 0) return;
        float over = time - length(clip);
        if (over < LET_GO) play(clip, Math.min(time, length(clip)), over <= 0 ? weight : weight * (1.0F - over / LET_GO));
    }

    @Override
    public void setupAnim(HeroRenderState s) {
        super.setupAnim(s);
        Life l = Life.from(s);
        // the fall: the live pose lets go over a quarter second instead of snapping to the clip's first frame
        float live = 1.0F;
        if (s.dying >= 0 && death >= 0) {
            live = 1.0F - Mth.smoothstep(Math.min(1.0F, s.dying / 0.25F));
            play(death, s.dying, 1.0F);
            if (live <= 0) {
                life.apply(l, s);
                return;
            }
        }
        float t = l != null ? l.idleTime() : s.ageInTicks / 20.0F;
        boolean grounded = !s.airborne || air < 0;
        float moving = grounded ? s.walk : 0.0F;
        float angryW = l != null ? l.ease(0, s.angry, 0.3F) : s.angry;
        float airW = air < 0 ? 0.0F : l != null ? l.ease(1, s.airborne ? 1.0F : 0.0F, 0.2F) : s.airborne ? 1.0F : 0.0F;
        play(idle, t, (1.0F - 0.55F * moving) * live);
        if (angry >= 0) play(angry, t, angryW * (1.0F - moving) * live);
        float flyRate = 1.0F + Math.min(2.0F, s.speed * 4.0F);
        if (fly >= 0) play(fly, l != null ? l.clock(0, flyRate) : t * flyRate, live);
        if (air >= 0) play(air, t, airW * live);
        if (moving > 0.01F) {
            if (walk >= 0) play(walk, s.gait / rig.walkStride * length(walk), moving * (1.0F - s.run) * live);
            if (run >= 0) play(run, s.gait / rig.runStride * length(run), moving * s.run * live);
            else if (walk >= 0 && s.run > 0) play(walk, s.gait / rig.walkStride * length(walk), moving * s.run * live);
        }
        // Look where the head is turned (the cue clips own the head while they play).
        float lookW = 1.0F - Mth.clamp(s.cueWeight, 0.0F, 1.0F) * 0.7F;
        float yaw = Mth.clamp(s.yRot, -170.0F, 170.0F) * Mth.DEG_TO_RAD * lookW;
        float pitch = Mth.clamp(s.xRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD * lookW;
        for (int i = 0; i < lookParts.length; i++) {
            if (lookParts[i] == null) continue;
            lookParts[i].yRot += yaw * lookYaw[i];
            lookParts[i].xRot += pitch * lookPitch[i];
        }
        play(s.prevClip, s.prevTime, s.prevWeight * live);
        play(s.cueClip, s.cueTime, s.cueWeight * live);
        once(bite, s.melee, live);
        once(hurt, s.hurt, live);
        for (int k = 0; k < s.layerClip.length; k++) play(s.layerClip[k], s.layerTime[k], s.layerWeight[k] * live);
        life.apply(l, s);
        // a kit body plants its feet on the ground under them (after every layer, so nothing undoes it)
        if (legs[0] != null) legs[0].reach(s.footL * live);
        if (legs[1] != null) legs[1].reach(s.footR * live);
    }
}
