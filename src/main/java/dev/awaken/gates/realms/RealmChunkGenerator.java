package dev.awaken.gates.realms;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.gates.realms.kit.V;
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
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Draws Realms (SPEC §50.7): each chunk reads its Realm's immutable plan and paints every pass into a scratch volume, then
 * the game's own decoration runs as it does anywhere else. A column outside any live Realm is left empty.
 */
public final class RealmChunkGenerator extends ChunkGenerator {
    public static final MapCodec<RealmChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(RealmChunkGenerator::new)));

    public RealmChunkGenerator(BiomeSource source) {
        super(source);
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
        int wx = chunk.getPos().x() * 16, wz = chunk.getPos().z() * 16;
        int cell = RealmCells.cellAt(wx, wz);
        RealmPlan plan = RealmCells.plan(cell);
        if (plan != null) {
            try {
                paint(chunk, plan.land(), RealmCells.centerX(cell), RealmCells.centerZ(cell));
            } catch (RuntimeException e) {
                Awaken.LOG.error("[realms] chunk {} failed", chunk.getPos(), e); // a hole, walkable round
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private static void paint(ChunkAccess chunk, Land land, int ox, int oz) {
        int wx = chunk.getPos().x() * 16, wz = chunk.getPos().z() * 16;
        V v = new V(wx - ox, wz - oz);
        land.paint(v);
        LevelChunkSection[] acquired = new LevelChunkSection[chunk.getSectionsCount()];
        int[] top = new int[256];
        Arrays.fill(top, Integer.MIN_VALUE);
        BlockState[] topS = new BlockState[256];
        int maxY = chunk.getMaxY(), minY = chunk.getMinY();
        try {
            v.emit((x, y, z, s) -> {
                if (y < minY || y > maxY) return;
                int si = chunk.getSectionIndex(y);
                LevelChunkSection sec = acquired[si];
                if (sec == null) {
                    sec = chunk.getSection(si);
                    sec.acquire();
                    acquired[si] = sec;
                }
                int lx = x - v.x0, lz = z - v.z0, i = lx + lz * 16;
                sec.setBlockState(lx, SectionPos.sectionRelative(y), lz, s, false);
                if (y > top[i]) {
                    top[i] = y;
                    topS[i] = s;
                }
            });
        } finally {
            for (LevelChunkSection s : acquired) if (s != null) s.release();
        }
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int i = 0; i < 256; i++) {
            if (top[i] == Integer.MIN_VALUE || topS[i] == null) continue;
            ocean.update(i & 15, top[i], i >> 4, topS[i]);
            surface.update(i & 15, top[i], i >> 4, topS[i]);
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return V.H;
    }

    @Override
    public int getSeaLevel() {
        return 62;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return 120;
    }

    private static int groundAt(int x, int z) {
        int cell = RealmCells.cellAt(x, z);
        RealmPlan plan = RealmCells.plan(cell);
        return plan == null ? 0 : plan.land().ground(x - RealmCells.centerX(cell), z - RealmCells.centerZ(cell));
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return groundAt(x, z) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        int top = groundAt(x, z);
        for (int i = 0; i < column.length; i++) column[i] = level.getMinY() + i <= top ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        return new NoiseColumn(level.getMinY(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos,
            net.minecraft.world.level.levelgen.densityfunction.SamplerContext ctx) {
    }
}
