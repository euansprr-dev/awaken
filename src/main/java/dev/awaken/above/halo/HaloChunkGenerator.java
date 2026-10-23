package dev.awaken.above.halo;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.above.ruin.RuinBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
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
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The Halo's generator: void everywhere except the station ({@link HaloLayout}), built chunk by chunk as it loads.
 * Loot caches (block entities) go in during the decoration step. No structures, no mobs.
 */
public final class HaloChunkGenerator extends ChunkGenerator {
    public static final MapCodec<HaloChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(HaloChunkGenerator::new)));

    public HaloChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private static boolean touches(ChunkPos c) {
        int e = HaloLayout.EXTENT;
        return c.getMaxBlockX() >= -e && c.getMinBlockX() <= e && c.getMaxBlockZ() >= -e && c.getMinBlockZ() <= e;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
                                                       BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        ChunkPos cp = chunk.getPos();
        if (!touches(cp)) return CompletableFuture.completedFuture(chunk);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int y0 = HaloLayout.FLOOR - 4, y1 = HaloLayout.FLOOR + 14;
        for (int y = y0; y <= y1; y++) {
            if (chunk.isOutsideBuildHeight(y)) continue;
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            int ly = SectionPos.sectionRelative(y);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    BlockState s = HaloLayout.at(cp.getMinBlockX() + lx, y, cp.getMinBlockZ() + lz);
                    if (s == null) continue;
                    section.setBlockState(lx, ly, lz, s, false);
                    oceanFloor.update(lx, y, lz, s);
                    worldSurface.update(lx, y, lz, s);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos cp = chunk.getPos();
        for (HaloLayout.Cache c : HaloLayout.CACHES) {
            BlockPos p = c.pos();
            if ((p.getX() >> 4) != cp.x() || (p.getZ() >> 4) != cp.z()) continue;
            level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, c.facing()), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, new XoroshiroRandomSource(level.getSeed() ^ p.asLong()), p,
                    RuinBuilder.loot("halo_cache"));
        }
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, getOrigin(randomState), this.biomeSource, Stream.empty());
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
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
        return HaloLayout.FLOOR + 1;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        for (int y = HaloLayout.FLOOR + 14; y >= HaloLayout.FLOOR - 4; y--) {
            BlockState s = HaloLayout.at(x, y, z);
            if (s != null && type.isOpaque().test(s)) return y + 1;
        }
        return level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        for (int y = HaloLayout.FLOOR - 4; y <= HaloLayout.FLOOR + 14; y++) {
            BlockState s = HaloLayout.at(x, y, z);
            if (s != null && y - level.getMinY() >= 0 && y - level.getMinY() < column.length) column[y - level.getMinY()] = s;
        }
        return new NoiseColumn(level.getMinY(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
    }
}
