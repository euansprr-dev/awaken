package dev.awaken.terrain.fault;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.terrain.landmark.LandsClock;
import dev.awaken.terrain.landmark.Voxels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.jspecify.annotations.Nullable;

/**
 * The faults (docs/modules/lands.md, "Shattered Highlands"), once per chunk over that chunk's own columns: every column
 * of the Shattered Highlands within the fault band ({@link FaultNoise}) gets faultstone for its top three blocks and the
 * seam down the line's middle; where the trench noise opens the fault, the column is cut down by a V that is deepest on
 * the line, its new floor relined, and the side it shows to deeper columns beside it relined with faultstone. Every
 * decision comes from the two noises and the column itself, so a fault runs on across chunk borders without a seam and
 * without reading what a neighbour chunk has done. Never opens onto water or down to the sea.
 */
public final class FaultLinesFeature implements Feature {
    public static final FaultLinesFeature INSTANCE = new FaultLinesFeature();
    public static final MapCodec<FaultLinesFeature> CODEC = MapCodec.unit(INSTANCE);
    public static final ResourceKey<Biome> HIGHLANDS = ResourceKey.create(Registries.BIOME, Awaken.id("shattered_highlands"));
    /** A fault can't be near a chunk whose nine samples are all this far from zero. */
    static final double SKIP = 0.15;

    private FaultLinesFeature() {}

    @Override
    public MapCodec<FaultLinesFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        // never inside Carillon: the city is built over this ground, and nothing here would clear these
        if (dev.awaken.terrain.api.CityBasin.reservedWithin(origin.getX(), origin.getZ(), 32)) return false;
        long t0 = System.nanoTime();
        var rs = level.getLevel().getChunkSource().randomState();
        Noise fault = FaultNoise.fault(rs), trench = FaultNoise.trench(rs);
        if (fault == null || trench == null) return false;
        int painted = paint(level, fault, trench, origin.getX() & ~15, origin.getZ() & ~15, generator.getSeaLevel(), HIGHLANDS);
        LandsClock.CHUNKS.increment();
        LandsClock.add("fault_lines", System.nanoTime() - t0);
        return painted > 0;
    }

    /**
     * Lays the faults of the chunk whose corner is (cx, cz) into the columns of {@code biome} (any column when null);
     * returns the columns that became fault.
     */
    public static int paint(WorldGenLevel level, Noise fault, Noise trench, int cx, int cz, int sea, @Nullable ResourceKey<Biome> biome) {
        // a quick look first: no line comes near this chunk (most chunks), nothing to do
        boolean maybe = false;
        for (int k = 0; k < 9 && !maybe; k++) {
            if (Math.abs(fault.get(cx + (k % 3) * 8, 0, cz + (k / 3) * 8)) < SKIP) maybe = true;
        }
        if (!maybe) return 0;
        // the field's lattice over the chunk and a column round it (FaultNoise.CELL = 2: 10 x 10 points), then
        // distance and depth for every column of the chunk and the ring round it
        int lx = cx - 2, lz = cz - 2; // a lattice point (chunk corners are even)
        double[] n = new double[11 * 11];
        for (int i = 0; i < 11; i++) for (int j = 0; j < 11; j++) n[i * 11 + j] = fault.get(lx + i * 2, 0, lz + j * 2);
        double[] dist = new double[18 * 18];
        double[] open = new double[18 * 18];
        int[] depth = new int[18 * 18];
        boolean any = false;
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                int x = cx - 1 + i, z = cz - 1 + j;
                int ci = (x - lx) >> 1, cj = (z - lz) >> 1, c = ci * 11 + cj;
                double d = FaultNoise.distance(n[c], n[c + 11], n[c + 1], n[c + 12], ((x - lx) & 1) * 0.5, ((z - lz) & 1) * 0.5);
                dist[i * 18 + j] = d;
                if (d >= FaultNoise.MAX_HALF) continue;
                double o = FaultNoise.open(trench.get(cx - 1 + i, 0, cz - 1 + j));
                open[i * 18 + j] = o;
                if (d < FaultNoise.half(o)) any = true;
                depth[i * 18 + j] = FaultNoise.trenchDepth(d, o);
            }
        }
        if (!any) return 0;
        BlockState seam = FaultBlocks.FAULT_SEAM.get().defaultBlockState(), stone = FaultBlocks.FAULTSTONE.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        var surface = Voxels.surface(level);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int painted = 0;
        for (int i = 1; i <= 16; i++) {
            for (int j = 1; j <= 16; j++) {
                int k = i * 18 + j;
                double d = dist[k];
                if (d >= FaultNoise.half(open[k])) continue;
                int x = cx - 1 + i, z = cz - 1 + j;
                int top = level.getHeight(surface, x, z) - 1;
                if (top <= sea) continue;
                BlockState was = level.getBlockState(m.set(x, top, z));
                if (was.isAir() || !was.getFluidState().isEmpty() || !was.isSolidRender()) continue; // water, ice, plants: no fault through them
                if (biome != null && !level.getBiome(m).is(biome)) continue;
                int deepest = Math.max(Math.max(depth[k - 18], depth[k + 18]), Math.max(depth[k - 1], depth[k + 1]));
                int dig = Math.max(0, Math.min(depth[k], top - sea - 2));
                if ((dig > 0 || deepest > 0) && wet(level, m, x, z, top, Math.max(dig, deepest))) dig = 0;
                for (int y = top; y > top - dig; y--) level.setBlock(m.set(x, y, z), air, Block.UPDATE_CLIENTS);
                int floor = top - dig;
                level.setBlock(m.set(x, floor, z), d < FaultNoise.SEAM_HALF ? seam : stone, Block.UPDATE_CLIENTS);
                // faultstone under it, and down the side this column shows to deeper ones beside it (their tops taken as this one's)
                int lining = Math.max(2, floor - (top - deepest));
                for (int y = floor - 1; y >= floor - lining && y > floor - 12; y--) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir() || !s.getFluidState().isEmpty()) break; // a cave under it: leave its roof alone
                    level.setBlock(m, stone, Block.UPDATE_CLIENTS);
                }
                painted++;
            }
        }
        return painted;
    }

    private static boolean wet(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int z, int top, int reach) {
        for (int y = top + 1; y >= top - reach; y--) {
            if (!level.getFluidState(m.set(x, y, z)).isEmpty()) return true;
            if (!level.getFluidState(m.set(x + 1, y, z)).isEmpty() || !level.getFluidState(m.set(x - 1, y, z)).isEmpty()
                    || !level.getFluidState(m.set(x, y, z + 1)).isEmpty() || !level.getFluidState(m.set(x, y, z - 1)).isEmpty()) return true;
        }
        return false;
    }
}
