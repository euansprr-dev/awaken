package dev.awaken.gates.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.gates.GatePortalBlockEntity;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.content.GatePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The gate dimension's generator: every chunk belongs to at most one cell; the cell's plan (from {@link GateCells})
 * is turned into rooms by {@link GateLayout} and painted by {@link GatePainter}. Everything else is void. Runs on
 * worldgen threads: it only reads immutable plans and layouts.
 */
public final class GateChunkGenerator extends ChunkGenerator {
    public static final MapCodec<GateChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("sky", false).forGetter(g -> g.sky)
    ).apply(i, i.stable(GateChunkGenerator::new)));

    /** The open sky's generator (SPEC §38.11): it builds only the open lands' floors; the gates' builds everything else. */
    final boolean sky;

    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;

    public GateChunkGenerator(BiomeSource biomeSource, boolean sky) {
        super(biomeSource);
        this.sky = sky;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, getOrigin(randomState), biomeSource, Stream.empty());
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
            StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
            Set<Holder<Biome>> possibleBiomes) {
        int chunkX = chunk.getPos().x(), chunkZ = chunk.getPos().z();
        int cell = Cells.cellAt(chunkX * 16, chunkZ * 16);
        GatePlan plan = GateCells.plan(cell);
        if (plan == null) return CompletableFuture.completedFuture(chunk);
        try {
            fill(chunk, plan, cell, chunkX, chunkZ, sky);
        } catch (RuntimeException e) {
            // never break chunk generation over one bad room: log, leave the chunk as built so far
            Awaken.LOG.error("[gates] failed to paint chunk {},{} of cell {}", chunkX, chunkZ, cell, e);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** Paints one chunk of a cell into a proto chunk. */
    static void fill(ChunkAccess chunk, GatePlan plan, int cell, int chunkX, int chunkZ) {
        fill(chunk, plan, cell, chunkX, chunkZ, false);
    }

    static void fill(ChunkAccess chunk, GatePlan plan, int cell, int chunkX, int chunkZ, boolean sky) {
        GateLayout layout = GateLayout.of(plan);
        int sections = chunk.getSectionsCount();
        LevelChunkSection[] acquired = new LevelChunkSection[sections];
        int minY = chunk.getMinY(), maxY = chunk.getMaxY();
        int[] top = new int[256];
        BlockState[] topState = new BlockState[256];
        Arrays.fill(top, Integer.MIN_VALUE);
        // doorway anchors painted in this chunk (an inner gate's): they need their block entity, which sections don't make
        List<BlockPos> anchors = new ArrayList<>(0);
        try {
            GatePainter.paint(layout, cell, chunkX, chunkZ, (x, y, z, state) -> {
                if (y < minY || y > maxY) return;
                int si = chunk.getSectionIndex(y);
                LevelChunkSection s = acquired[si];
                if (s == null) {
                    s = chunk.getSection(si);
                    s.acquire();
                    acquired[si] = s;
                }
                s.setBlockState(x, SectionPos.sectionRelative(y), z, state, false);
                if (state.getBlock() instanceof GateBlocks.Portal && state.getValue(GateBlocks.Portal.ROLE) == GateBlocks.Portal.Role.ANCHOR) {
                    anchors.add(new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z));
                }
                int idx = x + z * 16;
                if (y > top[idx]) {
                    top[idx] = y;
                    topState[idx] = state;
                }
            }, sky);
        } finally {
            for (LevelChunkSection s : acquired) if (s != null) s.release();
        }
        for (BlockPos a : anchors) chunk.setBlockEntity(new GatePortalBlockEntity(a, chunk.getBlockState(a)));
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int i = 0; i < 256; i++) {
            if (top[i] == Integer.MIN_VALUE) continue;
            ocean.update(i & 15, top[i], i >> 4, topState[i]);
            surface.update(i & 15, top[i], i >> 4, topState[i]);
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // no features, no structures: gates are built from their plan only
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

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
        return Cells.FLOOR1_Y;
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
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext ctx) {
        int cell = Cells.cellAt(feetPos.getX(), feetPos.getZ());
        GatePlan plan = GateCells.plan(cell);
        if (plan != null) result.add("Gate cell " + cell + ": " + plan.rank().label + " " + plan.theme().displayName + " · " + plan.layout().displayName);
    }
}
