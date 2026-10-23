package dev.awaken.mobs.client.model;

import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.anim.WildRenderState;
import dev.awaken.mobs.entity.WildPose;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A P3 body (tools/art/specs/fauna3.py): a hero rig (its LayerDefinition is {@link HeroModel#create}) with the hero
 * animation graph (idle, a distance-locked walk and run, the cue clips, the bite, the flinch, the fall) plus what the
 * water and the caves need: in water the swim loop ({@code fly}) replaces the walk and runs faster the faster it
 * swims; held poses play their own clips ({@code hang}, {@code chirp}, {@code ripen}, {@code listen}, {@code deflate},
 * {@code carry}, {@code rest}, {@code beached}, {@code ridden}); bones named {@code bridle*} show only on a bridled
 * mount.
 */
public final class WildModel extends EntityModel<WildRenderState> {
    private static final int[] HOLD_BITS = {WildPose.S_HANG, WildPose.S_CHIRP, WildPose.S_RIPEN, WildPose.S_LISTEN, WildPose.S_DEFLATE,
            WildPose.S_CARRY, WildPose.S_REST, WildPose.S_BEACHED, WildPose.S_RIDDEN};
    private static final String[] HOLD_CLIPS = {"hang", "chirp", "ripen", "listen", "deflate", "carry", "rest", "beached", "ridden"};

    private final HeroRig rig;
    private final List<KeyframeAnimation> baked = new ArrayList<>();
    private final List<HeroRig.Clip> clips = new ArrayList<>();
    private final Map<String, Integer> clipIndex = new HashMap<>();
    private final ModelPart[] lookParts;
    private final float[] lookYaw, lookPitch;
    private final ModelPart[] bridle;
    private final int[] holds = new int[HOLD_BITS.length];
    private final int idle, walk, run, fly, death, hurt, bite, angry;

    public WildModel(ModelPart root, HeroRig rig) {
        super(root, rig.translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
        this.rig = rig;
        for (HeroRig.Clip c : rig.clips.values()) {
            clipIndex.put(c.name(), baked.size());
            clips.add(c);
            baked.add(definition(c).bake(root));
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
        List<ModelPart> straps = new ArrayList<>();
        for (HeroRig.Bone b : rig.bones) {
            if (!b.name().startsWith("bridle")) continue;
            ModelPart p = lookup.apply(b.name());
            if (p != null) straps.add(p);
        }
        bridle = straps.toArray(new ModelPart[0]);
        for (int i = 0; i < HOLD_CLIPS.length; i++) holds[i] = clip(HOLD_CLIPS[i]);
        idle = clip("idle");
        walk = clip("walk");
        run = clip("run");
        fly = clip("fly");
        death = clip("death");
        hurt = clip("hurt");
        bite = clip("bite");
        angry = clip("angry");
    }

    public HeroRig rig() {
        return rig;
    }

    public int clip(String name) {
        Integer i = clipIndex.get(name);
        return i == null ? -1 : i;
    }

    public float length(int clip) {
        return clip < 0 ? 0.0F : clips.get(clip).length();
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

    private void play(int clip, float seconds, float weight) {
        if (clip >= 0 && weight > 0.001F) baked.get(clip).apply((long) (seconds * 1000.0F), Math.min(1.0F, weight));
    }

    @Override
    public void setupAnim(WildRenderState s) {
        super.setupAnim(s);
        boolean bridled = (s.wild & WildPose.S_BRIDLED) != 0;
        for (ModelPart p : bridle) p.visible = bridled;
        if (s.dying >= 0 && death >= 0) {
            play(death, s.dying, 1.0F);
            return;
        }
        float t = s.ageInTicks / 20.0F;
        boolean swimming = (s.wild & WildPose.S_SWIM) != 0 && fly >= 0;
        boolean beached = (s.wild & WildPose.S_BEACHED) != 0;
        boolean hanging = (s.wild & WildPose.S_HANG) != 0;
        float moving = swimming || beached || hanging ? 0.0F : s.walk;
        play(idle, t, 1.0F - 0.55F * moving);
        if (angry >= 0) play(angry, t, s.angry * (1.0F - moving));
        if (swimming) {
            play(fly, t * (1.0F + Math.min(2.5F, s.speed * 6.0F)), 1.0F);
        } else if (fly >= 0 && walk < 0 && !beached) {
            play(fly, t, 1.0F); // a swimmer with no legs, out of water but not stranded (falling): keep swimming
        }
        if (moving > 0.01F) {
            if (walk >= 0) play(walk, s.gait / rig.walkStride * length(walk), moving * (1.0F - s.run));
            if (run >= 0) play(run, s.gait / rig.runStride * length(run), moving * s.run);
            else if (walk >= 0 && s.run > 0) play(walk, s.gait / rig.walkStride * length(walk), moving * s.run);
        }
        for (int i = 0; i < HOLD_BITS.length; i++) {
            if ((s.wild & HOLD_BITS[i]) != 0) play(holds[i], t, 1.0F);
        }
        float lookW = 1.0F - Mth.clamp(s.cueWeight, 0.0F, 1.0F) * 0.7F;
        if (hanging) lookW *= 0.3F;
        float yaw = Mth.clamp(s.yRot, -170.0F, 170.0F) * Mth.DEG_TO_RAD * lookW;
        float pitch = Mth.clamp(s.xRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD * lookW * (swimming ? 0.3F : 1.0F);
        for (int i = 0; i < lookParts.length; i++) {
            if (lookParts[i] == null) continue;
            lookParts[i].yRot += yaw * lookYaw[i];
            lookParts[i].xRot += pitch * lookPitch[i];
        }
        play(s.prevClip, s.prevTime, s.prevWeight);
        play(s.cueClip, s.cueTime, s.cueWeight);
        if (s.melee >= 0 && bite >= 0 && s.melee <= length(bite)) play(bite, s.melee, 1.0F);
        if (s.hurt >= 0 && hurt >= 0 && s.hurt <= length(hurt)) play(hurt, s.hurt, 1.0F);
    }
}
