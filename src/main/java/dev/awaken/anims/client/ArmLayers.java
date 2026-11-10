package dev.awaken.anims.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Layers over your own first-person arms (SPEC §32, "The arm"): after Hands draws a fist's arm or a grip's, every layer may
 * draw on it, in the arm cube's own space. Powers' light is one (a cast showing on the arm); any module may add another
 * (client side, at init). A layer that throws is dropped for the session; the arms never fail for it.
 * <p>
 * The space a layer draws in: blocks, x across the arm from {@code x0} to {@code x1} (pixels of the skin, sixteenths), y
 * along it from -2/16 at the shoulder end to 10/16 at the hand's end, z from -2/16 to 2/16. The arm's skin is 4 pixels a
 * side (3 across on a slim arm) by 12 long, so a layer can light whole pixels.
 */
public final class ArmLayers {
    private ArmLayers() {}

    /** One arm about to take its layers. Reused every call: read it, don't keep it. */
    public static final class Arm {
        /** The body's right arm (not the main hand's side). */
        public boolean right;
        /** The main hand's arm. */
        public boolean main;
        /** A grip's arm (it holds something) rather than an empty fist's. */
        public boolean grip;
        /** The cube's extent across, in pixels from the arm's pivot. */
        public float x0, x1;
        /** The clip's own light for this hand when {@code keyed} (0..1 along the arm, heat 0..1 and a little over). */
        public float charge, heat;
        public boolean keyed;
        public int packedLight;
        public float partialTick;
    }

    @FunctionalInterface
    public interface Layer {
        void draw(Arm arm, PoseStack ps, SubmitNodeCollector out);
    }

    private static final List<Layer> LAYERS = new CopyOnWriteArrayList<>();

    public static void add(Layer layer) {
        LAYERS.add(layer);
    }

    static boolean any() {
        return !LAYERS.isEmpty();
    }

    static void draw(Arm arm, PoseStack ps, SubmitNodeCollector out) {
        for (Layer l : LAYERS) {
            try {
                l.draw(arm, ps, out);
            } catch (RuntimeException ex) {
                LAYERS.remove(l);
                Awaken.LOG.error("A first-person arm layer failed and is off for this session", ex);
            }
        }
    }
}
