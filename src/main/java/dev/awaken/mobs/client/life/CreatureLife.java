package dev.awaken.mobs.client.life;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * The life layer for any module's bodies (SPEC §12, §23): breathing, blinking, a head that follows what it looks at
 * the slow way with the eyes first, springs on ears, tails, manes and anything that dangles, the step's weight, idle
 * fidgets, a blow's stagger. Driven by bone names and a temperament from {@code assets/awaken/life/creatures.json};
 * client only, nothing allocated per frame, the springs and fidgets off past 32 blocks.
 *
 * <pre>
 * // once, in the model's constructor
 * LifeRig rig = CreatureLife.rig(root, "citizen");
 * // once a frame, in the renderer's extractRenderState (after the state is filled):
 * Life life = CreatureLife.life(myHolder.life, rig, entity.getId());   // keep it on the entity or its client data
 * life.hurt = ...; life.walk = ...; life.busy = aClipIsPlaying; life.lookWeight = closeEnoughToLook ? 1 : 0;
 * CreatureLife.tick(life, state, true);       // true: write its slow head look over state.yRot / state.xRot
 * // in setupAnim, after the clips and the pose code:
 * CreatureLife.apply(rig, life, state);       // may run once per layer; adds the same offsets each time
 * // optional, in setupRotations after the yaw: the stagger and lean for bodies whose body bone doesn't carry the head
 * CreatureLife.rootPose(life, poseStack, state);
 * // a texture blink: draw the closed-eyes texture while CreatureLife.eyesClosed(life) &gt; 0.5
 * </pre>
 *
 * Clips win where they drive a bone only if they are applied after {@link #apply}; the layer adds, it never sets.
 */
public final class CreatureLife {
    private CreatureLife() {}

    /** Binds a model's part tree to a temperament by name ("citizen", "cute", "beast"...; unknown means "default"). */
    public static LifeRig rig(ModelPart root, String temperament) {
        return LifeRig.of(root, LifeProfile.temperament(temperament), null, false);
    }

    /**
     * As {@link #rig(ModelPart, String)}, and its feet plant by the profile {@code motion} names in
     * {@code assets/awaken/life/motion.json} (SPEC §23.1; its rig's id). With no profile of that name, exactly as without.
     * The renderer then sets {@link Life#grounded}, {@link Life#legsFree}, {@link Life#gaitRun} and {@link Life#bodyScale}.
     */
    public static LifeRig rig(ModelPart root, String temperament, String motion) {
        return LifeRig.of(root, LifeProfile.temperament(temperament), null, false, motion);
    }

    /** The creature's life on this rig: the one it holds if it fits, else a new one (once per creature). */
    public static Life life(@Nullable Object held, LifeRig rig, int seed) {
        return Life.of(held, rig, seed);
    }

    public static void tick(Life life, LivingEntityRenderState s, boolean steerHead) {
        life.tick(s, steerHead);
    }

    public static void apply(LifeRig rig, @Nullable Life life, LivingEntityRenderState s) {
        rig.apply(life, s);
    }

    public static void rootPose(Life life, PoseStack pose, LivingEntityRenderState s) {
        life.rootPose(pose, s);
    }

    /** 0..1: the eyes are shut this frame (a blink, a yawn, death). */
    public static float eyesClosed(@Nullable Life life) {
        return life == null ? 0.0F : life.closed();
    }
}
