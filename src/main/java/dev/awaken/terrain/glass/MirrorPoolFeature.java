package dev.awaken.terrain.glass;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.landmark.Voxels;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A mirror pool (docs/modules/lands.md, "Glass Steppe"): a shallow pool on black scorched glass (so the still water
 * reads dark and glossy, and at night the glass's amber cracks glow up through it), ringed with ivory fused glass and
 * a few slivers of sunshard. The water's level is the rim's lowest ground, so it never spills; a pool only forms on
 * ground level enough to hold one. Radius 3-7: it stays within one chunk of its own.
 */
public record MirrorPoolFeature(IntProvider radius) implements Feature {
    public static final MapCodec<MirrorPoolFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            net.minecraft.util.valueproviders.IntProviders.codec(2, 7).fieldOf("radius").forGetter(MirrorPoolFeature::radius)
    ).apply(i, MirrorPoolFeature::new));

    @Override
    public MapCodec<MirrorPoolFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        // never inside Carillon: the city is built over this ground, and nothing here would clear these
        if (dev.awaken.terrain.api.CityBasin.reservedWithin(origin.getX(), origin.getZ(), 16)) return false;
        long t0 = System.nanoTime();
        try {
            return placePool(level, generator, random, origin);
        } finally {
            dev.awaken.terrain.landmark.LandsClock.add("mirror_pool", System.nanoTime() - t0);
        }
    }

    private boolean placePool(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int r = radius.sample(random);
        int ox = origin.getX(), oz = origin.getZ();
        double phase = random.nextDouble() * Math.PI * 2, phase2 = random.nextDouble() * Math.PI * 2;
        int span = r + 3, size = 2 * span + 1;
        int[] top = new int[size * size];
        byte[] kind = new byte[size * size]; // 0 outside, 1 rim, 2 water
        var surface = Voxels.surface(level);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int rimLow = Integer.MAX_VALUE, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = -span; x <= span; x++) {
            for (int z = -span; z <= span; z++) {
                double d = Math.sqrt(x * x + z * z), a = Math.atan2(z, x);
                double edge = r * (0.82 + 0.12 * Math.sin(2 * a + phase) + 0.08 * Math.sin(3 * a + phase2));
                int i = (x + span) * size + (z + span);
                if (d < edge) kind[i] = 2;
                else if (d < edge + 1.7) kind[i] = 1;
                else continue;
                int gx = ox + x, gz = oz + z;
                int t = level.getHeight(surface, gx, gz) - 1;
                BlockState s = level.getBlockState(m.set(gx, t, gz));
                if (!s.getFluidState().isEmpty() || !GlassShardsFeature.ground(s)) return false; // water already, or not steppe ground
                top[i] = t;
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
                if (kind[i] == 1) rimLow = Math.min(rimLow, t);
            }
        }
        if (rimLow == Integer.MAX_VALUE || hi - lo > 3 || rimLow <= generator.getSeaLevel()) return false;
        int water = rimLow;
        BlockState scorched = GlassBlocks.SCORCHED_GLASS.get().defaultBlockState(), fused = GlassBlocks.FUSED_GLASS.get().defaultBlockState();
        BlockState wet = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockState cluster = GlassBlocks.SUNSHARD_CLUSTER.get().defaultBlockState();
        for (int x = -span; x <= span; x++) {
            for (int z = -span; z <= span; z++) {
                int i = (x + span) * size + (z + span);
                if (kind[i] == 0) continue;
                int gx = ox + x, gz = oz + z;
                if (kind[i] == 1) {
                    // the rim: glass, a sliver standing in it now and then
                    level.setBlock(m.set(gx, top[i], gz), fused, Block.UPDATE_CLIENTS);
                    if (top[i] == water) level.setBlock(m.set(gx, top[i] - 1, gz), fused, Block.UPDATE_CLIENTS);
                    if (random.nextInt(9) == 0 && level.getBlockState(m.set(gx, top[i] + 1, gz)).isAir()) level.setBlock(m, cluster, Block.UPDATE_CLIENTS);
                    continue;
                }
                double d = Math.sqrt(x * x + z * z);
                int depth = d < r * 0.5 ? 2 : 1;
                // clear down to the water, then the water, then the black glass bed
                for (int y = Math.max(top[i] + 1, water + 1); y > water; y--) level.setBlock(m.set(gx, y, gz), air, Block.UPDATE_CLIENTS);
                for (int y = water; y > water - depth; y--) level.setBlock(m.set(gx, y, gz), wet, Block.UPDATE_CLIENTS);
                level.setBlock(m.set(gx, water - depth, gz), scorched, Block.UPDATE_CLIENTS);
                level.setBlock(m.set(gx, water - depth - 1, gz), scorched, Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }
}
