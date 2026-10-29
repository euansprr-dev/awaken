package dev.awaken.mobs.client.life;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The ground the feet stand on in the client's world ({@link Legs.Ground}); kept apart so a test can walk the feet on a server. */
final class LegGround implements Legs.Ground {
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    /** The top of the ground under (x, z), from a block above the body's feet down to three below; y if none. */
    @Override
    public double top(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return y;
        probe.set(Mth.floor(x), Mth.floor(y + 0.99), Mth.floor(z));
        for (int k = 0; k < 4; k++) {
            VoxelShape shape = level.getBlockState(probe).getCollisionShape(level, probe);
            if (!shape.isEmpty()) {
                double top = probe.getY() + shape.max(Direction.Axis.Y);
                if (top <= y + 1.0) return Math.max(top, y - 1.0);
            }
            probe.move(0, -1, 0);
        }
        return y;
    }
}
