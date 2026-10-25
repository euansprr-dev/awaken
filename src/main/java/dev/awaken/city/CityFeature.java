package dev.awaken.city;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code awaken:city}: the city painted at worldgen, last of the decoration steps (after trees, so it clears them).
 * Only the Carillon data pack places it, and it does nothing unless this world has a city (the feature flag
 * {@code awaken:carillon} and a site from the terrain's basin): each overworld chunk inside the city's reach gets its
 * share, written straight into its sections.
 */
public record CityFeature() implements Feature {
    public static final MapCodec<CityFeature> CODEC = MapCodec.unit(CityFeature::new);

    @Override
    public MapCodec<CityFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        CityLayout layout = City.layout();
        if (layout == null || level.getLevel().dimension() != Level.OVERWORLD) return false;
        int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
        if (!layout.touches(cx, cz)) return false;
        ChunkAccess chunk = level.getChunk(cx, cz);
        CityBuffer buf = CityBuffer.chunk();
        long t0 = System.nanoTime();
        CityPainter.paintChunk(layout, cx, cz, level.getSeed(), buf);
        long t1 = System.nanoTime();
        int n = CityWriter.toProto(chunk, buf, layout.streetY);
        int swept = CityWriter.sweepNeighbours(level, layout, cx, cz) + CityWriter.collectOrphans(level, layout, cx, cz);
        long t2 = System.nanoTime();
        City.STATS.add(t1 - t0, t2 - t1, n + swept);
        return true;
    }
}
