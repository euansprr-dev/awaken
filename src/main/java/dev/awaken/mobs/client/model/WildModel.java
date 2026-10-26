package dev.awaken.mobs.client.model;

import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.anim.WildRenderState;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.life.LifeRig;
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
 * mount. Graph weights ease in and out, the fall crossfades from the live pose, and the life layer (SPEC §23) goes on
 * top, as on {@link HeroModel}.
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
    private final int idle, walk, run, fly, death, hurt, bite, angry, sleep, beat;
    private final LifeRig life;

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
        sleep = clip("sleep");
        beat = clip("beat");
        life = LifeRig.of(root, HeroModel.profile(rig.id), rig, idle >= 0);
    }

    public LifeRig life() {
        return life;
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

    /** A rig clip as a vanilla animation definition (bake it against the model's root). */
    public static AnimationDefinition definition(HeroRig.Clip c) {
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

    /** A one-shot clip, holding its last frame while it lets go after it ends (no pop). */
    private void once(int clip, float time, float weight) {
        if (clip < 0 || time < 0) return;
        float over = time - length(clip);
        if (over < HeroModel.LET_GO) play(clip, Math.min(time, length(clip)), over <= 0 ? weight : weight * (1.0F - over / HeroModel.LET_GO));
    }

    @Override
    public void setupAnim(WildRenderState s) {
        super.setupAnim(s);
        boolean bridled = (s.wild & WildPose.S_BRIDLED) != 0;
        for (ModelPart p : bridle) p.visible = bridled;
        Life l = Life.from(s);
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
        boolean swimming = (s.wild & WildPose.S_SWIM) != 0 && fly >= 0;
        boolean beached = (s.wild & WildPose.S_BEACHED) != 0;
        boolean hanging = (s.wild & WildPose.S_HANG) != 0;
        float moving = swimming || beached || hanging ? 0.0F : s.walk;
        float angryW = l != null ? l.ease(0, s.angry, 0.3F) : s.angry;
        play(idle, t, (1.0F - 0.55F * moving) * live);
        if (angry >= 0) play(angry, t, angryW * (1.0F - moving) * live);
        float swimRate = 1.0F + Math.min(2.5F, s.speed * 6.0F);
        float swimW = l != null ? l.ease(1, swimming ? 1.0F : 0.0F, 0.25F) : swimming ? 1.0F : 0.0F;
        if (fly >= 0 && walk < 0 && !beached) {
            play(fly, l != null ? l.clock(0, swimming ? swimRate : 1.0F) : t, live); // no legs: it swims, in water or falling
        } else if (fly >= 0 && swimW > 0.001F) {
            play(fly, l != null ? l.clock(0, swimRate) : t * swimRate, swimW * live);
        }
        if (moving > 0.01F) {
            if (walk >= 0) play(walk, s.gait / rig.walkStride * length(walk), moving * (1.0F - s.run) * live);
            if (run >= 0) play(run, s.gait / rig.runStride * length(run), moving * s.run * live);
            else if (walk >= 0 && s.run > 0) play(walk, s.gait / rig.walkStride * length(walk), moving * s.run * live);
        }
        for (int i = 0; i < HOLD_BITS.length; i++) {
            float want = (s.wild & HOLD_BITS[i]) != 0 ? 1.0F : 0.0F;
            float w = l != null && i + 2 < 12 ? l.ease(i + 2, want, HOLD_BITS[i] == WildPose.S_HANG ? 0.05F : 0.3F) : want;
            if (w > 0.001F) play(holds[i], t, w * live);
        }
        if (sleep >= 0) {
            // asleep, the seep heart's plates close slowly round its core; waking, they open as slowly
            float want = (s.wild & WildPose.S_SLEEP) != 0 ? 1.0F : 0.0F;
            float w = l != null ? l.ease(12, want, 0.7F) : want;
            if (w > 0.001F) play(sleep, t, w * live);
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
        play(s.prevClip, s.prevTime, s.prevWeight * live);
        play(s.cueClip, s.cueTime, s.cueWeight * live);
        once(bite, s.melee, live);
        once(hurt, s.hurt, live);
        once(beat, s.beat, live);
        life.apply(l, s);
    }
}
