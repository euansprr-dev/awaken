package dev.awaken.body.ascent.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Client effects other Ascent code asks for (block entities' client ticks). */
public final class AscentFxBridge {
    private AscentFxBridge() {}

    /** A sky platform's core breathing a ring of light. */
    public static void coreRing(BlockPos pos) {
        AscentFx.ring(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new Vec3(0, 1, 0), 0.45F);
    }
}
