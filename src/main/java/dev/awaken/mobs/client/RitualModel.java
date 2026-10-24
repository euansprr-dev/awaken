package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.client.anim.HeroRig;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ritual cast's bodies (the Seated One, the statues): a hero rig (tools/art/specs/ritual_art.py) with its clips baked
 * into vanilla keyframe animations. Unlike a hero beast's model it plays exactly the clip its stage asks for (each
 * clip is a whole pose, authored to start where the last one ends), crossfading from the previous one, and the head
 * follows where the entity looks at full strength.
 */
public final class RitualModel extends EntityModel<RitualModel.State> {
    /** What the cast's renderers hand the model each frame. */
    public static final class State extends LivingEntityRenderState {
        public int clip = -1, prevClip = -1;
        public float time, prevTime, weight = 1, prevWeight;
        /** 0..: how bright the glow layer is (the eyes), and its colour. */
        public float glow;
        public int glowRgb = 0xFFFFFF;
        /** 0..1: how bright the runes layer burns (the Seated One's halo, collar, sigil and veins). */
        public float runes;
        public int entityId = -1;
        /** How much the look follows the head's turn (0..1). */
        public float look = 1;
    }

    private final HeroRig rig;
    private final List<KeyframeAnimation> baked = new ArrayList<>();
    private final List<HeroRig.Clip> clips = new ArrayList<>();
    private final Map<String, Integer> clipIndex = new HashMap<>();
    private final ModelPart[] lookParts;
    private final float[] lookYaw, lookPitch;
    private final Map<String, List<ModelPart>> chains = new HashMap<>();

    public RitualModel(ModelPart root, HeroRig rig) {
        super(root, RenderTypes::entityCutout);
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
    }

    public HeroRig rig() {
        return rig;
    }

    public int clip(String name) {
        Integer i = clipIndex.get(name);
        return i == null ? -1 : i;
    }

    public float length(int clip) {
        return clip < 0 ? 0 : clips.get(clip).length();
    }

    public boolean loops(int clip) {
        return clip >= 0 && clips.get(clip).loop();
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
    public void setupAnim(State s) {
        super.setupAnim(s);
        play(s.prevClip, s.prevTime, s.prevWeight);
        play(s.clip, s.time, s.weight);
        float yaw = Mth.clamp(s.yRot, -80.0F, 80.0F) * Mth.DEG_TO_RAD * s.look;
        float pitch = Mth.clamp(s.xRot, -40.0F, 50.0F) * Mth.DEG_TO_RAD * s.look;
        for (int i = 0; i < lookParts.length; i++) {
            if (lookParts[i] == null) continue;
            lookParts[i].yRot += yaw * lookYaw[i];
            lookParts[i].xRot += pitch * lookPitch[i];
        }
    }

    /** The parts from the root down to a bone (cached), for measuring where a posed bone is. */
    private @Nullable List<ModelPart> chain(String bone) {
        List<ModelPart> c = chains.get(bone);
        if (c != null) return c.isEmpty() ? null : c;
        List<String> names = new ArrayList<>();
        int i = rig.bone(bone);
        while (i >= 0) {
            HeroRig.Bone b = rig.bones.get(i);
            names.addFirst(b.name());
            i = b.parent();
        }
        c = new ArrayList<>();
        ModelPart p = root();
        for (String n : names) {
            if (!p.hasChild(n)) {
                c.clear();
                break;
            }
            p = p.getChild(n);
            c.add(p);
        }
        chains.put(bone, c);
        return c.isEmpty() ? null : c;
    }

    /**
     * Where a point given in a bone's own space (model pixels) is, in the space of {@code pose} (the posed model's
     * frame at render time). Returns false when the bone doesn't exist.
     */
    public boolean measure(PoseStack pose, String bone, float px, float py, float pz, Vector3f out) {
        List<ModelPart> c = chain(bone);
        if (c == null) return false;
        pose.pushPose();
        root().translateAndRotate(pose);
        for (ModelPart p : c) p.translateAndRotate(pose);
        out.set(px / 16F, py / 16F, pz / 16F);
        pose.last().pose().transformPosition(out);
        pose.popPose();
        return true;
    }
}
