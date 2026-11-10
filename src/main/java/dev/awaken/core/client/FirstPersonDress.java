package dev.awaken.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.HumanoidArm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What your own limbs wear in first person (SPEC §44.1, "First person"). Vanilla's empty hand reaches the armour through
 * NeoForge's own arm event; every arm or leg our own code draws (a cast's arms, the hands' off arm, the wrist, the world
 * body's limbs) never does, so whoever draws one asks here straight after the skin, and whoever dresses limbs (the armour)
 * registers. The drawers never import the dresser and the dresser never imports them.
 */
public final class FirstPersonDress {
    private FirstPersonDress() {}

    public enum Limb { RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Draws what the limb wears, the pose on the limb's bone; true when it drew anything. */
    @FunctionalInterface
    public interface Dresser {
        boolean dress(Limb limb, PoseStack ps, SubmitNodeCollector out, int light);
    }

    private static final List<Dresser> DRESSERS = new CopyOnWriteArrayList<>();
    private static boolean broken;

    public static void register(Dresser dresser) {
        DRESSERS.add(dresser);
    }

    /**
     * For a drawer that has just drawn one of your limbs with the pose on that limb's bone (after its
     * {@code translateAndRotate}). A dresser that throws turns dressing off for the session and is logged once: the
     * drawer keeps its skin limb.
     */
    public static boolean limb(Limb limb, PoseStack ps, SubmitNodeCollector out, int light) {
        if (broken || DRESSERS.isEmpty()) return false;
        boolean drew = false;
        try {
            for (Dresser d : DRESSERS) drew |= d.dress(limb, ps, out, light);
        } catch (RuntimeException e) {
            broken = true;
            Awaken.LOG.error("Dressing a first-person limb failed; first-person limbs stay bare for this session", e);
        }
        return drew;
    }

    /**
     * For a drawer that has just drawn vanilla's empty hand itself ({@code AvatarRenderer.renderRightHand} or
     * {@code renderLeftHand}, which never fire the arm event): the same arm, dressed, at the same pose (the arm's rest
     * pose turned a tenth of a radian out, as vanilla's own hand is).
     */
    public static boolean hand(AvatarRenderer<?> renderer, HumanoidArm side, PoseStack ps, SubmitNodeCollector out, int light) {
        if (broken || DRESSERS.isEmpty()) return false;
        boolean right = side == HumanoidArm.RIGHT;
        ModelPart arm = right ? renderer.getModel().rightArm : renderer.getModel().leftArm;
        ps.pushPose();
        arm.resetPose();
        arm.zRot = right ? 0.1F : -0.1F;
        arm.translateAndRotate(ps);
        boolean drew = limb(right ? Limb.RIGHT_ARM : Limb.LEFT_ARM, ps, out, light);
        ps.popPose();
        return drew;
    }
}
