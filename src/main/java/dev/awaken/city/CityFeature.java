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
 * share, written straight into its sections; past the reach, the chunks of the High Gate road's last stretch get the
 * road ({@link CityTrail}).
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
        boolean city = layout.touches(cx, cz), trail = CityTrail.touches(layout, cx, cz), near = CityTrail.near(layout, cx, cz);
        if (!city && !trail && !near) return false;
        if (!city && !trail) {
            CityTrail.sweep(level, layout, cx, cz); // a neighbour's trees out of the trail's columns
            return true;
        }
        ChunkAccess chunk = level.getChunk(cx, cz);
        CityBuffer buf = CityBuffer.chunk();
        long t0 = System.nanoTime();
        if (city) CityPainter.paintChunk(layout, cx, cz, level.getSeed(), buf);
        else buf.reset((cx << 4) - layout.cx, (cz << 4) - layout.cz);
        // the High Gate road on past the reach to the Wayfarers' fire, laid on this chunk's own ground (the Commons step)
        if (trail) CityTrail.paint(layout, chunk, buf, level.getSeed());
        long t1 = System.nanoTime();
        int n = CityWriter.toProto(chunk, buf, layout.streetY);
        int swept = CityWriter.sweepNeighbours(level, layout, cx, cz) + CityWriter.collectOrphans(level, layout, cx, cz)
                + (near ? CityTrail.sweep(level, layout, cx, cz) : 0);
        long t2 = System.nanoTime();
        City.STATS.add(t1 - t0, t2 - t1, n + swept);
        return true;
    }
}
