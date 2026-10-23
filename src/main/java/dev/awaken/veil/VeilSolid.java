package dev.awaken.veil;

import dev.awaken.veil.block.GhostDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The other half of the one rule: spirit matter is real to a spirit. A spirit passes through stone, but not through Veil
 * stone or a closed Ghost Door. Spectator movement is client-side, so the server checks the path every tick and puts the
 * spirit back where it was if it went through one.
 */
public final class VeilSolid {
    private VeilSolid() {}

    private static final double HALF_WIDTH = 0.28;
    private static final double HEIGHT = 1.7;

    public static boolean isSolidForSpirit(BlockState state) {
        if (state.is(VeilContent.VEIL_SOLID)) return true;
        return state.getBlock() instanceof GhostDoorBlock && !state.getValue(GhostDoorBlock.OPEN);
    }

    /** Whether moving the spirit's feet from {@code from} to {@code to} passes into spirit-solid matter. */
    public static boolean blocked(Level level, Vec3 from, Vec3 to) {
        if (overlaps(level, from)) return false; // already inside (a door closed on it): let it out
        double dist = from.distanceTo(to);
        if (dist < 1e-4) return false;
        int steps = Math.max(1, Mth.ceil(dist / 0.3));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            if (overlaps(level, p)) return true;
        }
        return false;
    }

    private static boolean overlaps(Level level, Vec3 feet) {
        int x0 = Mth.floor(feet.x - HALF_WIDTH), x1 = Mth.floor(feet.x + HALF_WIDTH);
        int y0 = Mth.floor(feet.y), y1 = Mth.floor(feet.y + HEIGHT);
        int z0 = Mth.floor(feet.z - HALF_WIDTH), z1 = Mth.floor(feet.z + HALF_WIDTH);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) continue;
                    if (isSolidForSpirit(level.getBlockState(m))) return true;
                }
            }
        }
        return false;
    }
}
