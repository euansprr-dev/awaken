package dev.awaken.hearth.trial;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The trials dimension: void with one walled arena per cell. Even cells are Reassessment halls (dark tile, amber lamps);
 * odd cells are the Hardline Penalty Zone's desert at night (sand, sandstone, cold lamps, cacti). Stateless and
 * deterministic, safe on worldgen threads.
 */
public final class TrialChunkGenerator extends ChunkGenerator {
    public static final MapCodec<TrialChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(TrialChunkGenerator::new)));

    public static final int CELL = 512;
    public static final int GRID = 32;
    public static final int SIZE = 48;
    public static final int FLOOR = 64;
    public static final int WALL = 8;
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;

    public TrialChunkGenerator(BiomeSource source) {
        super(source);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** North-west corner of cell {@code i}'s arena floor. */
    public static BlockPos origin(int i) {
        return new BlockPos((i % GRID) * CELL, FLOOR, (i / GRID) * CELL);
    }

    /** Where a player stands in cell {@code i}. */
    public static BlockPos centre(int i) {
        return origin(i).offset(SIZE / 2, 1, SIZE / 2);
    }

    public static boolean isDesert(int i) {
        return (i & 1) == 1;
    }

    /** The block at (lx, y, lz) of an arena (lx, lz in 0..SIZE-1). Pure so tests can check it. */
    public static @Nullable BlockState arenaBlock(boolean desert, int lx, int y, int lz) {
        boolean edge = lx == 0 || lz == 0 || lx == SIZE - 1 || lz == SIZE - 1;
        if (y == FLOOR - 1) return desert ? Blocks.SANDSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (y == FLOOR) {
            if (edge) return desert ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            if (!desert && lx % 8 == 4 && lz % 8 == 4) return Blocks.SHROOMLIGHT.defaultBlockState();
            if (!desert) return (lx + lz) % 2 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            return Blocks.SAND.defaultBlockState();
        }
        if (y > FLOOR && y <= FLOOR + WALL && edge) {
            boolean lamp = y == FLOOR + 3 && (lx + lz) % 8 == 4;
            if (lamp) return desert ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState();
            return desert ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        if (y > FLOOR + WALL && y <= FLOOR + WALL + 8 && edge) return Blocks.BARRIER.defaultBlockState();
        if (desert && y == FLOOR + 1 && !edge) {
            int h = (lx * 73 + lz * 31) % 97;
            if (h == 0 && lx > 3 && lz > 3 && lx < SIZE - 4 && lz < SIZE - 4 && (lx % 2 == 1)) return Blocks.CACTUS.defaultBlockState();
            if (h == 7) return Blocks.DEAD_BUSH.defaultBlockState();
        }
        return null;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structures,
                                                       BiomeManager biomes, @Nullable WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = bx + x, wz = bz + z;
                if (wx < 0 || wz < 0) continue;
                int cx = wx / CELL, cz = wz / CELL;
                if (cx >= GRID) continue;
                int lx = wx - cx * CELL, lz = wz - cz * CELL;
                if (lx >= SIZE || lz >= SIZE) continue;
                boolean desert = isDesert(cx + cz * GRID);
                for (int y = FLOOR - 1; y <= FLOOR + WALL + 8; y++) {
                    BlockState s = arenaBlock(desert, lx, y, lz);
                    if (s == null) continue;
                    m.set(wx, y, wz);
                    chunk.setBlockState(m, s, 0);
                    ocean.update(x, y, z, s);
                    surface.update(x, y, z, s);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return FLOOR + 1;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return inArena(x, z) ? FLOOR + 1 : level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        if (inArena(x, z)) {
            int cx = x / CELL, cz = z / CELL;
            boolean desert = isDesert(cx + cz * GRID);
            for (int y = FLOOR - 1; y <= FLOOR; y++) {
                BlockState s = arenaBlock(desert, x - cx * CELL, y, z - cz * CELL);
                if (s != null) column[y - level.getMinY()] = s;
            }
        }
        return new NoiseColumn(level.getMinY(), column);
    }

    private static boolean inArena(int x, int z) {
        if (x < 0 || z < 0 || x / CELL >= GRID) return false;
        return x % CELL < SIZE && z % CELL < SIZE;
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext ctx) {}

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.getOrigin(randomState), this.biomeSource, Stream.empty());
    }
}
