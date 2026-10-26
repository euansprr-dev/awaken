package dev.awaken.terrain.client;

import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;

/** This client's view of {@link Wind}: one field, set once a frame (before anything draws), read by the waving shader's
 * texels, the falling leaves and the leaf rustle so all three move with the same gusts. */
public final class ClientWind {
    private ClientWind() {}

    private static final Wind.Field FIELD = new Wind.Field();
    private static boolean ready;
    /** Debug ({@code /awaken terrain wind force}): a fixed steady strength (-1 = real) and a floor under every gust. */
    static float forceSteady = -1F, forceGust;
    /** Showcase ({@code /awaken terrain wind front}): a fixed heading (NaN = real) and one front launched at a time. */
    static float forceDirX = Float.NaN, forceDirZ;
    static double frontAt = Double.NaN, frontX, frontZ;

    static void update(Minecraft mc, float partialTick) {
        if (mc.level == null) {
            ready = false;
            return;
        }
        FIELD.set(mc.level, partialTick);
        if (forceSteady >= 0F) FIELD.steady = forceSteady;
        FIELD.gustFloor = forceGust;
        if (!Float.isNaN(forceDirX)) {
            FIELD.dirX = forceDirX;
            FIELD.dirZ = forceDirZ;
        }
        FIELD.frontAt = frontAt;
        FIELD.frontX = frontX;
        FIELD.frontZ = frontZ;
        FIELD.frontDirX = Float.isNaN(forceDirX) ? 0F : forceDirX;
        FIELD.frontDirZ = Float.isNaN(forceDirX) ? 0F : forceDirZ;
        ready = true;
    }

    /**
     * Launches a showcase front from {@code back} blocks behind (x, z) along (dirX, dirZ), and holds the wind's heading
     * there so everything leans the way the front rolls ({@code wind real} lets go).
     */
    static void front(double x, double z, float dirX, float dirZ, double back) {
        forceDirX = dirX;
        forceDirZ = dirZ;
        frontX = x - dirX * back;
        frontZ = z - dirZ * back;
        frontAt = FIELD.seconds;
    }

    static void real() {
        forceSteady = -1F;
        forceGust = 0F;
        forceDirX = Float.NaN;
        frontAt = Double.NaN;
    }

    /** The wind this frame, or null outside a world. Read-only for callers. */
    public static Wind.Field field() {
        return ready ? FIELD : null;
    }
}
