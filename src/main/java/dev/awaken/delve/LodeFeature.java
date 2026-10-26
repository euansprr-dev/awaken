package dev.awaken.delve;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code awaken:lodes}: each overworld chunk writes its share of the lodes planned round it ({@link LodePlans}), at the
 * underground ores step, after the carvers, so a cave cuts across a seam and shows it on its wall. Only rock is
 * replaced (stone with the ore, deepslate with its deep twin, another ore with this one); air, water and anything
 * built stays. The heart goes in at the seam's centre. Pure and per chunk: nothing outside the chunk is touched.
 */
public record LodeFeature() implements Feature {
    public static final MapCodec<LodeFeature> CODEC = MapCodec.unit(LodeFeature::new);

    @Override
    public MapCodec<LodeFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        boolean any = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Lode l : LodePlans.touching(level.getSeed(), x0, z0, x0 + 15, z0 + 15, false)) {
            for (long c : l.cells()) {
                int x = BlockPos.getX(c), z = BlockPos.getZ(c);
                if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                m.set(x, BlockPos.getY(c), z);
                BlockState s = level.getBlockState(m);
                Block put = rockFor(l.ore(), s, c == l.heart());
                if (put == null) continue;
                level.setBlock(m, put.defaultBlockState(), Block.UPDATE_CLIENTS);
                any = true;
            }
        }
        return any;
    }

    /** What a lode's block becomes in this rock, or null to leave it (air, water, a build, bedrock). */
    static Block rockFor(Ore ore, BlockState s, boolean heart) {
        boolean deep;
        if (s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) deep = true;
        else if (s.is(BlockTags.STONE_ORE_REPLACEABLES)) deep = false;
        else if (Ore.of(s) != null || s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES.block())) deep = s.getBlock().getDescriptionId().contains("deepslate");
        else return null;
        return heart ? ore.heart() : ore.block(deep);
    }
}
