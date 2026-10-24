package dev.awaken.terrain.glass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.landmark.Voxels;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A cluster of glass shard spires (docs/modules/lands.md, "Glass Steppe"): sunshard crystals 4-14 tall standing out of
 * the steppe, all leaning roughly one way (the way the old blast threw them), the tallest in the middle, a little
 * fused glass spattered round their feet and slivers in the grass. Every spire's foot reaches down to the ground (so
 * none floats over a dip); the whole cluster stays within about twelve blocks of its centre (a feature may write one
 * chunk beyond its own).
 */
public record GlassShardsFeature(IntProvider count, IntProvider height, float spread) implements Feature {
    public static final MapCodec<GlassShardsFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            net.minecraft.util.valueproviders.IntProviders.codec(1, 12).fieldOf("count").forGetter(GlassShardsFeature::count),
            net.minecraft.util.valueproviders.IntProviders.codec(3, 16).fieldOf("height").forGetter(GlassShardsFeature::height),
            Codec.floatRange(0F, 5F).fieldOf("spread").forGetter(GlassShardsFeature::spread)
    ).apply(i, GlassShardsFeature::new));

    static final byte SHARD = 1;
    static final int EXTENT = 13;

    @Override
    public MapCodec<GlassShardsFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        long t0 = System.nanoTime();
        try {
            return placeCluster(level, random, origin);
        } finally {
            dev.awaken.terrain.landmark.LandsClock.add(height.maxInclusive() > 8 ? "glass_shards" : "small_glass_shards", System.nanoTime() - t0);
        }
    }

    private boolean placeCluster(WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // stand on dry steppe ground
        BlockState below = level.getBlockState(m.set(origin.getX(), origin.getY() - 1, origin.getZ()));
        if (!ground(below)) return false;
        int n = count.sample(random);
        double lean = random.nextDouble() * Math.PI * 2;
        Voxels v = new Voxels(EXTENT, -3, 18);
        int tallest = 0;
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = i == 0 ? 0 : 1.2 + random.nextDouble() * spread;
            double bx = Math.cos(a) * d, bz = Math.sin(a) * d;
            int h = height.sample(random);
            if (i == 0) h = Math.max(h, height.maxInclusive() - random.nextInt(3)); // the tallest in the middle
            else h = Math.max(3, (int) (h * (0.45 + 0.4 * random.nextDouble())));
            tallest = Math.max(tallest, h);
            double r0 = 0.75 + h * 0.1 + random.nextDouble() * 0.3;
            double la = lean + (random.nextDouble() - 0.5) * 0.7, l = 0.12 + random.nextDouble() * 0.22;
            v.prism(bx, -1, bz, h + 1, r0, Math.cos(la) * l, Math.sin(la) * l, random.nextDouble() * Math.PI, h < 7 && random.nextInt(3) == 0, SHARD);
        }
        v.seal();
        BlockState shard = GlassBlocks.SUNSHARD.get().defaultBlockState();
        BlockState fused = GlassBlocks.FUSED_GLASS.get().defaultBlockState();
        BlockState cluster = GlassBlocks.SUNSHARD_CLUSTER.get().defaultBlockState();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        // glass spattered round the feet first (the top block of the ground), slivers in it
        int spatter = 3 + (int) spread;
        for (int x = -spatter - 1; x <= spatter + 1; x++) {
            for (int z = -spatter - 1; z <= spatter + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > spatter + random.nextDouble() * 1.5) continue;
                int gx = ox + x, gz = oz + z;
                int gy = level.getHeight(Voxels.surface(level), gx, gz) - 1;
                if (Math.abs(gy - (oy - 1)) > 3) continue;
                BlockState s = level.getBlockState(m.set(gx, gy, gz));
                if (!ground(s)) continue;
                if (random.nextDouble() < 0.75 - r * 0.08) level.setBlock(m, fused, Block.UPDATE_CLIENTS);
                if (random.nextDouble() < 0.12 && level.getBlockState(m.set(gx, gy + 1, gz)).isAir()) level.setBlock(m, cluster, Block.UPDATE_CLIENTS);
            }
        }
        // the spires into air and plants only (and over slivers); each foot continues down to the ground
        v.place(level, origin, SHARD, shard, 7, s -> s.isAir() || s.canBeReplaced() || s.is(GlassBlocks.SUNSHARD_CLUSTER.get()));
        return tallest > 0;
    }

    static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRASS_BLOCK) || s.is(GlassBlocks.FUSED_GLASS.get())
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SANDSTONE);
    }
}
