package dev.awaken.veil.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.veil.VeilContent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The Stillpoint: an endless sea of soft clouds (y 72..96, with holes down into the void) and the temple floating at the
 * origin. No structures, no mobs. Everything here is a pure function of the seed, so any worldgen thread may run it.
 */
public final class StillpointGenerator extends ChunkGenerator {
    public static final MapCodec<StillpointGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(StillpointGenerator::new)));

    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;
    static final int CLOUD_BASE = 84;

    private record Noises(long seed, SimplexNoise broad, SimplexNoise detail, SimplexNoise holes) {}

    private volatile @Nullable Noises noises;

    public StillpointGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private Noises noises(RandomState rs) {
        Noises n = this.noises;
        @SuppressWarnings("deprecation") long seed = rs.seed();
        if (n == null || n.seed() != seed) {
            var f = rs.getOrCreateRandomFactory(Awaken.id("stillpoint"));
            RandomSource a = f.fromHashOf("broad"), b = f.fromHashOf("detail"), c = f.fromHashOf("holes");
            this.noises = n = new Noises(seed, new SimplexNoise(a), new SimplexNoise(b), new SimplexNoise(c));
        }
        return n;
    }

    /** Cloud top and bottom (inclusive) at a column, or null where the clouds part. */
    static int @Nullable [] clouds(Noises n, int x, int z) {
        double broad = Mth.clamp(n.broad().get(x / 72.0, z / 72.0), -1.0, 1.0);
        double detail = Mth.clamp(n.detail().get(x / 22.0, z / 22.0), -1.0, 1.0);
        double hole = n.holes().get(x / 44.0, z / 44.0);
        if (hole > 0.58) return null;
        int top = CLOUD_BASE + (int) Math.round(broad * 7 + detail * 3);
        int thick = 3 + (int) Math.round((broad + 1) * 2.5 + (detail + 1) * 0.8);
        if (hole > 0.45) thick = Math.max(1, thick - (int) ((hole - 0.45) * 30)); // thin edges around the gaps
        return new int[]{top, top - thick + 1};
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
                                                       BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
                                                       Set<Holder<Biome>> possibleBiomes) {
        Noises n = noises(randomState);
        BlockState cloud = VeilContent.STILLPOINT_CLOUD.get().defaultBlockState();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int[] c = clouds(n, baseX + lx, baseZ + lz);
                if (c == null) continue;
                for (int y = c[1]; y <= c[0]; y++) {
                    chunk.setBlockState(m.set(lx, y, lz), cloud);
                    ocean.update(lx, y, lz, cloud);
                    surface.update(lx, y, lz, cloud);
                }
            }
        }
        if (Math.abs(baseX + 8) <= TempleBuilder.EXTENT + 8 && Math.abs(baseZ + 8) <= TempleBuilder.EXTENT + 8) {
            Long2ObjectMap<BlockState> temple = TempleBuilder.blocks();
            for (var e : temple.long2ObjectEntrySet()) {
                long p = e.getLongKey();
                int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
                if (x < baseX || x >= baseX + 16 || z < baseZ || z >= baseZ + 16) continue;
                BlockState s = e.getValue();
                chunk.setBlockState(m.set(x - baseX, y, z - baseZ), s);
                ocean.update(x - baseX, y, z - baseZ, s);
                surface.update(x - baseX, y, z - baseZ, s);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.getOrigin(randomState), this.biomeSource, Stream.empty());
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
        return TempleBuilder.FLOOR + 1;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        if (Math.abs(x) <= TempleBuilder.RADIUS && Math.abs(z) <= TempleBuilder.RADIUS) return TempleBuilder.FLOOR + 1;
        int[] c = clouds(noises(randomState), x, z);
        return c == null ? level.getMinY() : c[0] + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        int[] c = clouds(noises(randomState), x, z);
        if (c != null) {
            BlockState cloud = VeilContent.STILLPOINT_CLOUD.get().defaultBlockState();
            for (int y = c[1]; y <= c[0]; y++) if (y - level.getMinY() >= 0 && y - level.getMinY() < column.length) column[y - level.getMinY()] = cloud;
        }
        return new NoiseColumn(level.getMinY(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        result.add("Stillpoint");
    }
}
