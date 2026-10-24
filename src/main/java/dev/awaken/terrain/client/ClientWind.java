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

    static void update(Minecraft mc, float partialTick) {
        if (mc.level == null) {
            ready = false;
            return;
        }
        FIELD.set(mc.level, partialTick);
        if (forceSteady >= 0F) FIELD.steady = forceSteady;
        FIELD.gustFloor = forceGust;
        ready = true;
    }

    /** The wind this frame, or null outside a world. Read-only for callers. */
    public static Wind.Field field() {
        return ready ? FIELD : null;
    }
}
