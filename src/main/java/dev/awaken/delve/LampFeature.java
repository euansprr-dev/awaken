package dev.awaken.delve;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Delver lamps in natural caverns (SPEC §34.6): in one chunk in three, a few tries for a big open space under a roof
 * (at least {@value #HEIGHT} of air under it and {@value #WIDE} clear each way at half height), and where one is found
 * a dead lamp hangs from the roof on a short chain. Most tries find rock or a tunnel: about one lamp per 300 m of cave.
 */
public record LampFeature() implements Feature {
    public static final MapCodec<LampFeature> CODEC = MapCodec.unit(LampFeature::new);
    static final int HEIGHT = 8, WIDE = 6, TRIES = 3, LOW = -48, HIGH = 48;

    @Override
    public MapCodec<LampFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource r, BlockPos o) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int t = 0; t < TRIES; t++) {
            int x = (o.getX() & ~15) + 3 + r.nextInt(10), z = (o.getZ() & ~15) + 3 + r.nextInt(10);
            int y = LOW + r.nextInt(HIGH - LOW);
            m.set(x, y, z);
            if (!level.getBlockState(m).isAir()) continue;
            // up to the roof
            int roof = y;
            while (roof < y + 30 && level.getBlockState(m.set(x, roof + 1, z)).isAir()) roof++;
            if (roof >= y + 30 || !level.getBlockState(m.set(x, roof + 1, z)).isSolidRender()) continue;
            // a hall, not a shaft or a tunnel: tall under the roof, wide at half height
            boolean tall = true;
            for (int dy = 1; dy <= HEIGHT && tall; dy++) tall = level.getBlockState(m.set(x, roof - dy, z)).isAir();
            if (!tall || !wide(level, m, x, roof - HEIGHT / 2, z)) continue;
            int chain = roof - y > 14 ? 2 : 1;
            for (int c = 0; c < chain; c++) level.setBlock(m.set(x, roof - c, z), Blocks.IRON_CHAIN.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(m.set(x, roof - chain, z), DelveContent.DELVER_LAMP.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    private static boolean wide(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int y, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= WIDE; i++) if (!level.getBlockState(m.set(x + d.getStepX() * i, y, z + d.getStepZ() * i)).isAir()) return false;
        }
        return true;
    }
}
