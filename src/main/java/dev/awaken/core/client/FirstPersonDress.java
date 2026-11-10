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
 * <p>
 * Two more things pass through here for whoever lights a limb (a power being cast, SPEC §32.N "The light on what you
 * wear"): {@link #glow} tells what dresses a limb which light is on it, so the dress can answer in its own materials; and
 * {@link #surface} hands back the shape of what the limb wears, so the light can be drawn on that shape and not on the
 * skin under it.
 */
public final class FirstPersonDress {
    private FirstPersonDress() {}

    public enum Limb { RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Draws what the limb wears, the pose on the limb's bone; true when it drew anything. */
    @FunctionalInterface
    public interface Dresser {
        boolean dress(Limb limb, PoseStack ps, SubmitNodeCollector out, int light);
    }

    /**
     * The shape of what a limb wears, for a drawer that lays light on it. All in the limb's bone space, as the dresser
     * draws it there: blocks, y down the limb, z back.
     */
    public interface Surface {
        /** The same object for as long as the shape is the same: a drawer may keep what it worked out against it. */
        Object key();

        /** Triangles, three corners each, x y z a corner. Two triangles in a row that share an edge are one flat face. */
        float[] positions();

        /** A corner's normal: x y z, each -127 to 127. */
        byte[] normals();

        /** A corner's texel: u v, 0 to 1 across the whole of {@link #texture}; they lie in its left half. */
        float[] texels();

        /**
         * The texture. Its left half is colour; the same place in its right half says what the surface is made of there:
         * red how much it is metal, green how glossy, blue how much it glows of itself.
         */
        net.minecraft.resources.Identifier texture();

        /** A name for the look (an armour set's id), for a drawer that treats some its own way. */
        String style();

        /** The look's own accent colour (0xRRGGBB; 0 for none): where the surface is painted in it, it is a line of the design. */
        default int accent() {
            return 0;
        }
    }

    /** Gives the shape of what a limb wears now, or null when it wears nothing of this giver's. */
    @FunctionalInterface
    public interface Surfaces {
        @org.jspecify.annotations.Nullable Surface of(Limb limb);
    }

    private static final List<Dresser> DRESSERS = new CopyOnWriteArrayList<>();
    private static final List<Surfaces> SURFACES = new CopyOnWriteArrayList<>();
    private static boolean broken;
    private static int glowRgb;
    private static float glowAmount;

    public static void register(Surfaces surfaces) {
        SURFACES.add(surfaces);
    }

    /** The shape of what this limb wears now, from the first giver that has one; null for a bare limb. */
    public static @org.jspecify.annotations.Nullable Surface surface(Limb limb) {
        if (broken) return null;
        try {
            for (Surfaces s : SURFACES) {
                Surface found = s.of(limb);
                if (found != null) return found;
            }
        } catch (RuntimeException e) {
            broken = true;
            Awaken.LOG.error("Reading what a first-person limb wears failed; first-person limbs stay bare for this session", e);
        }
        return null;
    }

    /**
     * For whoever lights your limbs: the light on the limb about to be dressed (0xRRGGBB, and how strong: 0 none, 1 a
     * full charge, a little over at a release). Set it straight before the limb is dressed and back to 0 straight after.
     * A dresser may answer it in its own materials (the armour's seams and edges take the colour); none has to.
     */
    public static void glow(int rgb, float amount) {
        glowRgb = rgb & 0xFFFFFF;
        glowAmount = Math.max(0F, amount);
    }

    public static int glowColour() {
        return glowRgb;
    }

    public static float glowAmount() {
        return glowAmount;
    }

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
