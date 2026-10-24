package dev.awaken.lucidium.world;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The Lucidium's chunk generator ({@code awaken:dream_cells}). Everything outside a planned dream is open sky. Each
 * dream's blueprint is registered here (server thread) before anyone can reach its cell; worldgen threads only read.
 */
public final class DreamChunkGenerator extends ChunkGenerator {
    public static final MapCodec<DreamChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(DreamChunkGenerator::new)));

    private static final ConcurrentHashMap<Integer, DreamBlueprint> BLUEPRINTS = new ConcurrentHashMap<>();

    public DreamChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    public static void register(DreamBlueprint blueprint) {
        BLUEPRINTS.put(blueprint.cell, blueprint);
    }

    public static void unregister(int cell) {
        BLUEPRINTS.remove(cell);
    }

    public static @Nullable DreamBlueprint blueprint(int cell) {
        return BLUEPRINTS.get(cell);
    }

    static void clear() {
        BLUEPRINTS.clear();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long levelSeed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, levelSeed, this.getOrigin(randomState), this.biomeSource, Stream.empty());
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
                                                       StructureManager structureManager, BiomeManager biomeManager,
                                                       @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        int cell = Cells.cellAt(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
        if (cell >= 0) {
            Cells.generated(cell, chunk.getPos().pack());
            DreamBlueprint bp = BLUEPRINTS.get(cell);
            if (bp != null) bp.write(chunk);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
    }

    @Override
    public int getGenDepth() {
        return 256;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return 100;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(level.getMinY(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        int cell = Cells.cellAt(feetPos.getX(), feetPos.getZ());
        result.add("Lucidium cell " + cell + (BLUEPRINTS.containsKey(cell) ? " (dreaming)" : ""));
    }
}
