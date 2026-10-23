package dev.awaken.descent.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.descent.Strata;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Chunk generator for the strata ("stratum": 1..10) and the Endless ("stratum": 0, one cell per depth). All the shape
 * lives in {@link Terrain}; this class writes it into chunks, cheaply and deterministically from the world seed.
 */
public final class StratumGenerator extends ChunkGenerator {
    public static final MapCodec<StratumGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.intRange(0, Strata.COUNT).fieldOf("stratum").forGetter(g -> g.stratum)
    ).apply(i, i.stable(StratumGenerator::new)));

    private final int stratum;

    public StratumGenerator(BiomeSource biomeSource, int stratum) {
        super(biomeSource);
        this.stratum = stratum;
    }

    public int stratum() {
        return stratum;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** Depth index whose terrain covers a world column (0 = outside any Endless cell). */
    public int depthAt(int x, int z) {
        if (stratum > 0) return stratum;
        int d = Strata.endlessCellAt(x, z);
        return d > 0 ? Strata.ENDLESS_BASE + d : 0;
    }

    @SuppressWarnings("deprecation")
    private static long seedOf(RandomState rs) {
        return rs.seed();
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
            StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
            Set<Holder<Biome>> possibleBiomes) {
        fill(chunk, seedOf(randomState));
        return CompletableFuture.completedFuture(chunk);
    }

    /** Writes the terrain of one chunk. Public for GameTests. */
    public void fill(ChunkAccess chunk, long seed) {
        ChunkPos cp = chunk.getPos();
        int minX = cp.getMinBlockX(), minZ = cp.getMinBlockZ();
        int depth = depthAt(minX + 8, minZ + 8);
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        if (depth == 0) {
            BlockState rock = dev.awaken.descent.DescentContent.ABYSS_STONE.get().defaultBlockState();
            writeSolid(chunk, rock, Blocks.BEDROCK.defaultBlockState(), ocean, surface);
            return;
        }
        Terrain t = Terrain.forDepth(depth, seed);
        int lx0 = minX - Strata.centerX(depth), lz0 = minZ - Strata.centerZ(depth);
        if (t.solidArea(lx0, lz0)) {
            writeSolid(chunk, t.solidFill(1), t.solidFill(0), ocean, surface);
            return;
        }
        Terrain.Scratch s = Terrain.SCRATCH.get();
        t.chunkNodes(lx0, lz0, s);
        int sections = chunk.getSectionsCount();
        LevelChunkSection[] secs = new LevelChunkSection[sections];
        for (int i = 0; i < sections; i++) secs[i] = chunk.getSection(i);
        int minY = chunk.getMinY();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                BlockState[] col = t.chunkColumn(lx0, lz0, lx, lz, s);
                int highest = -1;
                for (int y = 0; y < Layout.HEIGHT; y++) {
                    BlockState st = col[y];
                    if (st.isAir()) continue;
                    int wy = y + Layout.MIN_Y;
                    int si = (wy - minY) >> 4;
                    if (si < 0 || si >= sections) continue;
                    secs[si].setBlockState(lx, wy & 15, lz, st, false);
                    highest = wy;
                }
                if (highest >= 0) {
                    BlockState topState = col[highest - Layout.MIN_Y];
                    ocean.update(lx, highest, lz, topState);
                    surface.update(lx, highest, lz, topState);
                }
            }
        }
    }

    private static void writeSolid(ChunkAccess chunk, BlockState rock, BlockState bedrock, Heightmap ocean, Heightmap surface) {
        int minY = chunk.getMinY();
        int sections = chunk.getSectionsCount();
        for (int si = 0; si < sections; si++) {
            LevelChunkSection sec = chunk.getSection(si);
            int baseY = minY + (si << 4);
            for (int ly = 0; ly < 16; ly++) {
                int wy = baseY + ly;
                if (wy < Layout.MIN_Y || wy >= Layout.MIN_Y + Layout.HEIGHT) continue;
                BlockState st = wy == Layout.MIN_Y ? bedrock : rock;
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) sec.setBlockState(lx, ly, lz, st, false);
            }
        }
        int top = Layout.MIN_Y + Layout.HEIGHT - 1;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            ocean.update(lx, top, lz, rock);
            surface.update(lx, top, lz, rock);
        }
    }

    /** Loot caches need block entities, so they go in at the features step (3x3 window around the chunk). */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos cp = chunk.getPos();
        int depth = depthAt(cp.getMinBlockX() + 8, cp.getMinBlockZ() + 8);
        if (depth == 0) return;
        Terrain t = Terrain.forDepth(depth, level.getSeed());
        int cx = Strata.centerX(depth), cz = Strata.centerZ(depth);
        for (int i = 0; i < t.caches().size(); i++) {
            BlockPos local = t.caches().get(i);
            int wx = local.getX() + cx, wz = local.getZ() + cz;
            if ((wx >> 4) != cp.x() || (wz >> 4) != cp.z()) continue;
            for (int y = local.getY() + 4; y >= local.getY() - 5; y--) {
                BlockPos p = new BlockPos(wx, y, wz);
                if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()
                        && level.getBlockState(p.below()).isSolid()) {
                    Direction facing = Direction.fromYRot(Math.toDegrees(Math.atan2(-wx + cx, wz - cz)));
                    level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH), 2);
                    RandomizableContainer.setBlockEntityLootTable(level, new XoroshiroRandomSource(t.seed ^ i), p, t.pal.cacheLoot);
                    break;
                }
            }
        }
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, getOrigin(randomState), biomeSource, Stream.empty());
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return Layout.HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return Layout.MIN_Y;
    }

    @Override
    public int getMinY() {
        return Layout.MIN_Y;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return Layout.ENTRY_Y;
    }

    private BlockState[] sample(int x, int z, long seed) {
        int depth = depthAt(x, z);
        if (depth == 0) return null;
        Terrain t = Terrain.forDepth(depth, seed);
        return t.singleColumn(x - Strata.centerX(depth), z - Strata.centerZ(depth));
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] col = sample(x, z, seedOf(randomState));
        if (col == null) return Layout.MIN_Y + Layout.HEIGHT;
        for (int y = Layout.HEIGHT - 1; y >= 0; y--) {
            if (type.isOpaque().test(col[y])) return y + Layout.MIN_Y + 1;
        }
        return level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] col = sample(x, z, seedOf(randomState));
        BlockState[] out = new BlockState[level.getHeight()];
        for (int i = 0; i < out.length; i++) {
            int y = level.getMinY() + i - Layout.MIN_Y;
            out[i] = col == null ? Blocks.STONE.defaultBlockState()
                    : (y >= 0 && y < Layout.HEIGHT ? col[y] : Blocks.AIR.defaultBlockState());
        }
        return new NoiseColumn(level.getMinY(), out);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext ctx) {
        int depth = depthAt(feetPos.getX(), feetPos.getZ());
        result.add("AWAKEN " + Strata.label(depth) + " (form " + Strata.formOf(depth) + ")");
    }
}
