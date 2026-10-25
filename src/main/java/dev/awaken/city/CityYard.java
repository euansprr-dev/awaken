package dev.awaken.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the gatehouse yard outside the High Gate clear ground (SPEC decision 29: the Living Wilds' Hale and the bounty
 * post stand there). A tree from the lands could grow over it when a neighbouring chunk decorated after the yard was
 * painted (M4 B2's tour world had one), so once per server start, as soon as the yard's chunks are loaded, every tree
 * piece (the writer's own vegetation test: natural logs and leaves, the lands' bark, vines) in the yard and a margin
 * round it is taken away, from the ground to 48 over it.
 */
final class CityYard {
    private CityYard() {}

    /** The yard (plan x0, z0, x1, z1; tools/city/plan.py GATE_YARD) and how far round it a tree is taken too. */
    static final int X0 = -16, Z0 = -255, X1 = 15, Z1 = -245, MARGIN = 8, HEIGHT = 48;
    private static @Nullable CityLayout doneFor;
    static int cleared;

    static void tick(MinecraftServer server) {
        CityLayout l = City.layout();
        if (l == null || doneFor == l || server.getTickCount() % 40 != 7) return;
        ServerLevel level = server.overworld();
        int x0 = l.cx + X0 - MARGIN, x1 = l.cx + X1 + MARGIN, z0 = l.cz + Z0 - MARGIN, z1 = l.cz + Z1 + MARGIN;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                if (!level.hasChunk(cx, cz)) return;
        doneFor = l;
        cleared = clear(level, l, x0, z0, x1, z1);
        if (cleared > 0) dev.awaken.Awaken.LOG.info("[city] cleared {} tree blocks over the High Gate's yard", cleared);
    }

    static int clear(ServerLevel level, CityLayout l, int x0, int z0, int x1, int z1) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = l.groundY(x, z);
                for (int y = g + 1; y <= g + HEIGHT; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir() || !CityWriter.vegetation(s)) continue;
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    n++;
                }
            }
        }
        return n;
    }
}
