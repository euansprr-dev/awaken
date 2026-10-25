package dev.awaken.citizens.client;

import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.CreatureLife;
import dev.awaken.mobs.client.life.LifeRig;
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
 * One of the city's animals (tools/art/specs/city_fauna.py): a hero rig whose clips the brain picks by name
 * ({@link CityCritters}), crossfaded so nothing pops, the head turned toward what the animal watches, and the life layer
 * (SPEC §23: breath, blinks, springy ears and tails, idle glances) on top. A cart's wheels turn by the distance rolled;
 * its loads (and a boat's) are bones shown one at a time; a boat's boatman is shown only while it's under way.
 */
final class CritterModel extends EntityModel<CritterRenderState> {
    private final HeroRig rig;
    private final List<KeyframeAnimation> baked = new ArrayList<>();
    private final List<HeroRig.Clip> clips = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final ModelPart[] lookParts;
    private final float[] lookYaw, lookPitch;
    private final ModelPart wheelL, wheelR, rower;
    private final ModelPart[] loads;
    final LifeRig life;

    CritterModel(ModelPart root, HeroRig rig, String temperament) {
        super(root, RenderTypes::entityCutout);
        this.rig = rig;
        for (HeroRig.Clip c : rig.clips.values()) {
            index.put(c.name(), baked.size());
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
        wheelL = rig.bone("wheel_l") >= 0 ? lookup.apply("wheel_l") : null;
        wheelR = rig.bone("wheel_r") >= 0 ? lookup.apply("wheel_r") : null;
        rower = rig.bone("rower") >= 0 ? lookup.apply("rower") : null;
        String[] ln = {"load_hay", "load_crates", "load_casks", "load_nets"};
        loads = new ModelPart[ln.length];
        for (int i = 0; i < ln.length; i++) loads[i] = rig.bone(ln[i]) >= 0 ? lookup.apply(ln[i]) : null;
        life = CreatureLife.rig(root, temperament);
    }

    int clip(String name) {
        Integer i = index.get(name);
        return i == null ? -1 : i;
    }

    float length(int clip) {
        return clip < 0 ? 0F : clips.get(clip).length();
    }

    boolean loops(int clip) {
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
        if (clip < 0 || weight <= 0.001F) return;
        float len = length(clip);
        // a one-shot holds its last frame (the brain lets it go by crossfading to the next clip)
        float t = loops(clip) ? seconds : Math.min(seconds, len);
        baked.get(clip).apply((long) (t * 1000F), Math.min(1F, weight));
    }

    @Override
    public void setupAnim(CritterRenderState s) {
        super.setupAnim(s);
        if (s.prevWeight > 0) play(s.prevClip, s.prevTime, s.prevWeight);
        play(s.clip, s.clipTime, 1F - s.prevWeight);
        float yaw = Mth.clamp(s.yRot, -150F, 150F) * Mth.DEG_TO_RAD, pitch = Mth.clamp(s.xRot, -60F, 60F) * Mth.DEG_TO_RAD;
        for (int i = 0; i < lookParts.length; i++) {
            if (lookParts[i] == null) continue;
            lookParts[i].yRot += yaw * lookYaw[i];
            lookParts[i].xRot += pitch * lookPitch[i];
        }
        if (wheelL != null) wheelL.xRot += s.wheel;
        if (wheelR != null) wheelR.xRot += s.wheel;
        for (int i = 0; i < loads.length; i++) if (loads[i] != null) loads[i].visible = i == s.load;
        if (rower != null) rower.visible = s.crew;
        if (s.life != null) CreatureLife.apply(life, s.life, s);
    }

    HeroRig rig() {
        return rig;
    }
}
