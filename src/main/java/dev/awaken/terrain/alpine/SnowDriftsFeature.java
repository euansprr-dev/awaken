package dev.awaken.terrain.alpine;

import com.mojang.serialization.MapCodec;
import dev.awaken.terrain.landmark.LandsClock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Snow in layers below the snow's edge (SPEC §31, P3), once per chunk at {@code top_layer_modification}: where
 * {@link AlpineSurface#driftLayers} says a drift lies (the band of about nine blocks under the line, in patches, thicker toward
 * the line, never on steep ground) the ground takes one to three of vanilla's snow layers, so the snow thins out instead of
 * stopping at a wall of blocks. Reads the chunk's own natural heights and a one-column ring (the generator's heightmap, no
 * noise column); a layer lies only where vanilla's own rule lets it survive (a whole block under it, open air over it).
 */
public final class SnowDriftsFeature implements Feature {
    public static final SnowDriftsFeature INSTANCE = new SnowDriftsFeature();
    public static final MapCodec<SnowDriftsFeature> CODEC = MapCodec.unit(INSTANCE);

    /** The lowest ground a drift can lie on: the line at its lowest (186 - 9 - 22) less the band and the patches. */
    static final int LOWEST = 130;

    private SnowDriftsFeature() {}

    @Override
    public MapCodec<SnowDriftsFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
        ChunkAccess[] chunks = new ChunkAccess[9];
        int max = Integer.MIN_VALUE;
        for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) chunks[a * 3 + b] = level.getChunk((cx >> 4) + a - 1, (cz >> 4) + b - 1);
        int[] h = new int[18 * 18];
        for (int j = 0; j < 18; j++) {
            for (int i = 0; i < 18; i++) {
                int x = cx - 1 + i, z = cz - 1 + j;
                ChunkAccess c = chunks[((x >> 4) - (cx >> 4) + 1) * 3 + (z >> 4) - (cz >> 4) + 1];
                h[j * 18 + i] = c.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);   // the top block's y already
                if (i >= 1 && i <= 16 && j >= 1 && j <= 16) max = Math.max(max, h[j * 18 + i]);
            }
        }
        if (max < LOWEST) return false;
        long t0 = System.nanoTime();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int j = 1; j <= 16; j++) {
            for (int i = 1; i <= 16; i++) {
                int k = j * 18 + i, y = h[k];
                if (y < LOWEST) continue;
                float gx = (h[k + 1] - h[k - 1]) * 0.5f, gz = (h[k + 18] - h[k - 18]) * 0.5f;
                int x = cx - 1 + i, z = cz - 1 + j;
                int layers = AlpineSurface.driftLayers(x, y, z, gx, gz, AlpineSurface.jitter(x, y, z));
                if (layers == 0) continue;
                if (!level.getBlockState(m.set(x, y + 1, z)).isAir()) continue;
                BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
                if (!snow.canSurvive(level, m)) continue;
                level.setBlock(m, snow, Block.UPDATE_CLIENTS);
                any = true;
            }
        }
        LandsClock.add("snow_drifts", System.nanoTime() - t0);
        return any;
    }
}
